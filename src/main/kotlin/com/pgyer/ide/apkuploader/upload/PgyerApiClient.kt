package com.pgyer.ide.apkuploader.upload

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * Pgyer API 2.0 fast upload client.
 *
 * Flow:
 * 1. POST /apiv2/app/getCOSToken
 * 2. multipart POST file to returned COS endpoint (expect HTTP 204)
 * 3. poll GET /apiv2/app/buildInfo until published
 *
 * Docs: https://www.pgyer.com/doc/view/api_upload
 *
 * Note: COS uploads use [HttpURLConnection] with a known Content-Length.
 * Java [HttpClient] `ofInputStream` uses chunked transfer encoding, which
 * Tencent COS often rejects by closing the socket — surfacing as
 * "HTTP/1.1 header parser received no bytes".
 */
class PgyerApiClient(
    private val apiHosts: List<String> = DEFAULT_API_HOSTS,
) {
    fun interface ProgressListener {
        fun onProgress(progress: UploadProgress)
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile
    private var activeApiHost: String = apiHosts.first()

    fun upload(params: PgyerUploadParams, listener: ProgressListener = ProgressListener { }): PgyerUploadResult {
        require(params.apiKey.isNotBlank()) { "API Key is required" }
        require(Files.isRegularFile(params.apkPath)) { "APK not found: ${params.apkPath}" }
        require(params.apkPath.fileName.toString().endsWith(".apk", ignoreCase = true)) {
            "Only .apk files are supported in v1"
        }

        listener.onProgress(
            UploadProgress(UploadProgress.Stage.TOKEN, "Requesting upload token...", 0.05)
        )
        val token = withRetry(stage = "getCOSToken") {
            getCosToken(params)
        }

        listener.onProgress(
            UploadProgress(
                UploadProgress.Stage.UPLOAD,
                "Uploading ${params.apkPath.fileName}...",
                0.12,
            )
        )
        withRetry(stage = "COS upload", maxAttempts = 2) {
            uploadToCos(params.apkPath, token) { uploaded, total ->
                val fileFraction = if (total > 0) uploaded.toDouble() / total.toDouble() else 0.0
                val overall = 0.12 + fileFraction * 0.63
                val pct = (fileFraction * 100).toInt()
                listener.onProgress(
                    UploadProgress(
                        UploadProgress.Stage.UPLOAD,
                        "Uploading ${params.apkPath.fileName}… $pct%",
                        overall,
                    )
                )
            }
        }

        listener.onProgress(
            UploadProgress(
                UploadProgress.Stage.PROCESS,
                "Waiting for Pgyer to process the build...",
                0.78,
            )
        )
        val result = pollBuildInfo(params.apiKey, token.buildKey, listener)
        listener.onProgress(
            UploadProgress(UploadProgress.Stage.DONE, "Upload finished", 1.0)
        )
        return result
    }

    private fun getCosToken(params: PgyerUploadParams): CosToken {
        val form = linkedMapOf(
            "_api_key" to params.apiKey,
            "buildType" to "android",
            "buildInstallType" to params.installType.code.toString(),
        )
        if (params.installPassword.isNotBlank()) {
            form["buildPassword"] = params.installPassword
        }
        if (params.appDescription.isNotBlank()) {
            form["buildDescription"] = params.appDescription
        }
        if (params.updateDescription.isNotBlank()) {
            form["buildUpdateDescription"] = params.updateDescription
        }

        val response = postMultipartForm("/apiv2/app/getCOSToken", form)
        val body = response.body
        val root = JsonParser.parseString(body).asJsonObject
        val code = root.get("code")?.asInt ?: -1
        if (code != 0) {
            throw PgyerApiException(code, root.get("message")?.asString ?: body)
        }
        val data = root.getAsJsonObject("data")
            ?: throw PgyerApiException(code, "Missing data in getCOSToken response")
        val endpoint = data.get("endpoint")?.asString
            ?: throw PgyerApiException(code, "Missing endpoint in getCOSToken response")
        val cosParams = data.getAsJsonObject("params")
            ?: throw PgyerApiException(code, "Missing params in getCOSToken response")
        val buildKey = cosParams.get("key")?.asString
            ?: data.get("key")?.asString
            ?: throw PgyerApiException(code, "Missing build key in getCOSToken response")

        val fields = linkedMapOf<String, String>()
        for ((key, value) in cosParams.entrySet()) {
            if (value.isJsonPrimitive) {
                fields[key] = value.asString
            }
        }
        return CosToken(endpoint = endpoint, buildKey = buildKey, fields = fields)
    }

    private fun uploadToCos(
        apkPath: Path,
        token: CosToken,
        onBytes: (uploaded: Long, total: Long) -> Unit,
    ) {
        val fields = LinkedHashMap(token.fields)
        fields["x-cos-meta-file-name"] = apkPath.fileName.toString()
        val status = postMultipartFileFixedLength(token.endpoint, fields, apkPath, onBytes).statusCode
        if (status != 204 && status !in 200..299) {
            throw IOException("COS upload failed with HTTP $status")
        }
    }

    private fun pollBuildInfo(apiKey: String, buildKey: String, listener: ProgressListener): PgyerUploadResult {
        var attempt = 0
        while (attempt < MAX_POLL_ATTEMPTS) {
            attempt++
            val processFraction = 0.78 + (attempt.toDouble() / MAX_POLL_ATTEMPTS) * 0.20
            listener.onProgress(
                UploadProgress(
                    UploadProgress.Stage.PROCESS,
                    "Checking build status ($attempt/$MAX_POLL_ATTEMPTS)...",
                    processFraction.coerceAtMost(0.98),
                )
            )
            val body = withRetry(stage = "buildInfo", maxAttempts = 2) {
                val uri = URI.create(
                    "$apiBase/apiv2/app/buildInfo?_api_key=${urlEncode(apiKey)}&buildKey=${urlEncode(buildKey)}"
                )
                val request = HttpRequest.newBuilder(uri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .header("Connection", "close")
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                if (response.statusCode() !in 200..299) {
                    throw IOException("buildInfo HTTP ${response.statusCode()}: ${response.body()}")
                }
                response.body()
            }
            val root = JsonParser.parseString(body).asJsonObject
            val code = root.get("code")?.asInt ?: -1
            if (code == 1246 || code == 1247) {
                Thread.sleep(POLL_INTERVAL_MS)
                continue
            }
            if (code != 0) {
                throw PgyerApiException(code, root.get("message")?.asString ?: body)
            }
            val data = root.getAsJsonObject("data")
                ?: throw PgyerApiException(code, "Missing build data")
            return parseResult(data)
        }
        throw IOException("Timed out waiting for Pgyer to process the build after $MAX_POLL_ATTEMPTS attempts")
    }

    private fun parseResult(data: JsonObject): PgyerUploadResult {
        val shortcut = data.get("buildShortcutUrl")?.asString.orEmpty()
        val installPage = when {
            shortcut.startsWith("http://") || shortcut.startsWith("https://") -> shortcut
            shortcut.isNotBlank() -> "https://www.pgyer.com/$shortcut"
            else -> {
                val key = data.get("buildKey")?.asString.orEmpty()
                if (key.isNotBlank()) "https://www.pgyer.com/$key" else ""
            }
        }
        val buildIcon = data.get("buildIcon")?.asString.orEmpty()
        val iconUrl = if (buildIcon.length >= 5) {
            val path = buildIcon.take(5).toCharArray().joinToString("/")
            "https://cdn-app-icon2.pgyer.com/$path/$buildIcon?x-oss-process=image/resize,m_lfit,h_120,w_120/format,jpg"
        } else {
            ""
        }
        return PgyerUploadResult(
            buildKey = data.get("buildKey")?.asString.orEmpty(),
            buildName = data.get("buildName")?.asString.orEmpty(),
            buildVersion = data.get("buildVersion")?.asString.orEmpty(),
            buildBuildVersion = data.get("buildBuildVersion")?.asString.orEmpty(),
            buildFileSize = data.get("buildFileSize")?.asString.orEmpty(),
            buildType = data.get("buildType")?.asString.orEmpty(),
            shortcutUrl = shortcut,
            installPageUrl = installPage,
            qrCodeUrl = data.get("buildQRCodeURL")?.asString.orEmpty(),
            buildIconUrl = iconUrl,
        )
    }

    private data class CosToken(
        val endpoint: String,
        val buildKey: String,
        val fields: Map<String, String>,
    )

    private data class MultipartResult(val statusCode: Int, val body: String)

    private fun postMultipartForm(path: String, fields: Map<String, String>): MultipartResult {
        val boundary = "----PgyerBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val body = buildFormOnlyBody(boundary, fields)
        var lastError: Exception? = null
        for (host in apiHosts) {
            try {
                val url = "https://$host$path"
                val request = HttpRequest.newBuilder(URI.create(url))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Connection", "close")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                if (response.statusCode() !in 200..299) {
                    throw IOException("POST $url failed with HTTP ${response.statusCode()}: ${response.body()}")
                }
                activeApiHost = host
                return MultipartResult(response.statusCode(), response.body())
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw wrapNetworkError("getCOSToken", lastError)
    }

    /**
     * Upload with a known Content-Length via HttpURLConnection.
     * Avoids chunked encoding that COS commonly rejects.
     */
    private fun postMultipartFileFixedLength(
        endpoint: String,
        fields: Map<String, String>,
        filePath: Path,
        onBytes: (uploaded: Long, total: Long) -> Unit,
    ): MultipartResult {
        val boundary = "----PgyerBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val charset = StandardCharsets.UTF_8
        val preamble = buildString {
            for ((name, value) in fields) {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                append(value)
                append("\r\n")
            }
            val fileName = filePath.fileName.toString()
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(charset)
        val epilogue = "\r\n--$boundary--\r\n".toByteArray(charset)
        val fileSize = Files.size(filePath)
        val totalBytes = preamble.size + fileSize + epilogue.size

        val connection = (URI.create(endpoint).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            useCaches = false
            instanceFollowRedirects = true
            connectTimeout = 60_000
            readTimeout = 30 * 60_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Connection", "close")
            // Critical: fixed length — not chunked
            setFixedLengthStreamingMode(totalBytes)
        }

        onBytes(0, totalBytes)
        try {
            connection.outputStream.use { out ->
                writeAndCount(out, preamble, 0, totalBytes, onBytes)
                var uploaded = preamble.size.toLong()
                Files.newInputStream(filePath).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        uploaded += n
                        onBytes(uploaded.coerceAtMost(totalBytes), totalBytes)
                    }
                }
                out.write(epilogue)
                out.flush()
                onBytes(totalBytes, totalBytes)
            }

            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return MultipartResult(status, responseBody)
        } catch (e: Exception) {
            throw wrapNetworkError("COS upload", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun writeAndCount(
        out: OutputStream,
        bytes: ByteArray,
        alreadyUploaded: Long,
        totalBytes: Long,
        onBytes: (Long, Long) -> Unit,
    ) {
        out.write(bytes)
        onBytes((alreadyUploaded + bytes.size).coerceAtMost(totalBytes), totalBytes)
    }

    private fun buildFormOnlyBody(boundary: String, fields: Map<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val charset = StandardCharsets.UTF_8
        fun write(s: String) = out.write(s.toByteArray(charset))
        for ((name, value) in fields) {
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            write(value)
            write("\r\n")
        }
        write("--$boundary--\r\n")
        return out.toByteArray()
    }

    private fun <T> withRetry(stage: String, maxAttempts: Int = 3, block: () -> T): T {
        var last: Exception? = null
        repeat(maxAttempts) { index ->
            try {
                return block()
            } catch (e: PgyerApiException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (index < maxAttempts - 1 && isTransientNetworkError(e)) {
                    // Rotate API host on next attempt when relevant
                    rotateApiHost()
                    Thread.sleep(500L * (index + 1))
                } else if (!isTransientNetworkError(e)) {
                    throw wrapNetworkError(stage, e)
                }
            }
        }
        throw wrapNetworkError(stage, last)
    }

    private fun rotateApiHost() {
        val idx = apiHosts.indexOf(activeApiHost)
        activeApiHost = apiHosts[(idx + 1).coerceAtLeast(0) % apiHosts.size]
    }

    private val apiBase: String get() = "https://$activeApiHost"

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        val DEFAULT_API_HOSTS = listOf("www.pgyer.com", "api.pgyer.com", "www.xcxwo.com")
        private const val USER_AGENT = "PgyerApkUploader-IDE/1.0 (IntelliJ Platform)"
        private const val MAX_POLL_ATTEMPTS = 60
        private const val POLL_INTERVAL_MS = 2_000L

        fun isTransientNetworkError(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                val message = current.message.orEmpty()
                if (
                    message.contains("header parser received no bytes", ignoreCase = true) ||
                    message.contains("Connection reset", ignoreCase = true) ||
                    message.contains("Broken pipe", ignoreCase = true) ||
                    message.contains("GOAWAY", ignoreCase = true) ||
                    message.contains("timed out", ignoreCase = true) ||
                    message.contains("connection closed", ignoreCase = true) ||
                    current is java.net.SocketException ||
                    current is java.net.http.HttpTimeoutException
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun wrapNetworkError(stage: String, error: Throwable?): IOException {
            val raw = error?.message ?: "unknown network error"
            if (raw.contains("header parser received no bytes", ignoreCase = true) ||
                (error != null && isTransientNetworkError(error))
            ) {
                return IOException(
                    "Network connection closed during $stage ($raw). " +
                        "Please retry. If it keeps failing, check VPN/proxy/firewall access to pgyer.com and the COS upload host.",
                    error,
                )
            }
            return IOException("Failed during $stage: $raw", error)
        }
    }
}

class PgyerApiException(val code: Int, override val message: String) : Exception("Pgyer API error $code: $message")
