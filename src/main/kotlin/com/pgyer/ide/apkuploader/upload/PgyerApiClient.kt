package com.pgyer.ide.apkuploader.upload

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Pgyer API 2.0 fast upload client.
 *
 * Flow:
 * 1. POST /apiv2/app/getCOSToken
 * 2. multipart POST file to returned COS endpoint (expect HTTP 204)
 * 3. poll GET /apiv2/app/buildInfo until published
 *
 * Docs: https://www.pgyer.com/doc/view/api_upload
 */
class PgyerApiClient(
    private val apiHost: String = DEFAULT_HOST,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun interface ProgressListener {
        fun onProgress(progress: UploadProgress)
    }

    fun upload(params: PgyerUploadParams, listener: ProgressListener = ProgressListener { }): PgyerUploadResult {
        require(params.apiKey.isNotBlank()) { "API Key is required" }
        require(Files.isRegularFile(params.apkPath)) { "APK not found: ${params.apkPath}" }
        require(params.apkPath.fileName.toString().endsWith(".apk", ignoreCase = true)) {
            "Only .apk files are supported in v1"
        }

        listener.onProgress(
            UploadProgress(UploadProgress.Stage.TOKEN, "Requesting upload token...", 0.05)
        )
        val token = getCosToken(params)

        listener.onProgress(
            UploadProgress(
                UploadProgress.Stage.UPLOAD,
                "Uploading ${params.apkPath.fileName}...",
                0.12,
            )
        )
        uploadToCos(params.apkPath, token) { uploaded, total ->
            val fileFraction = if (total > 0) uploaded.toDouble() / total.toDouble() else 0.0
            // Map file bytes onto 12%..75% of overall progress
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

        val response = postMultipartForm("$apiBase/apiv2/app/getCOSToken", form)
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
        val status = postMultipartFile(token.endpoint, fields, apkPath, onBytes).statusCode
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
            val uri = URI.create(
                "$apiBase/apiv2/app/buildInfo?_api_key=${urlEncode(apiKey)}&buildKey=${urlEncode(buildKey)}"
            )
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) {
                throw IOException("buildInfo HTTP ${response.statusCode()}: ${response.body()}")
            }
            val root = JsonParser.parseString(response.body()).asJsonObject
            val code = root.get("code")?.asInt ?: -1
            if (code == 1246 || code == 1247) {
                Thread.sleep(POLL_INTERVAL_MS)
                continue
            }
            if (code != 0) {
                throw PgyerApiException(code, root.get("message")?.asString ?: response.body())
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

    private fun postMultipartForm(url: String, fields: Map<String, String>): MultipartResult {
        val boundary = "----PgyerBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val body = buildFormOnlyBody(boundary, fields)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(2))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw IOException("POST $url failed with HTTP ${response.statusCode()}: ${response.body()}")
        }
        return MultipartResult(response.statusCode(), response.body())
    }

    private fun postMultipartFile(
        url: String,
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
        val uploaded = AtomicLong(0)

        fun counting(stream: InputStream, length: Long): InputStream {
            return object : InputStream() {
                override fun read(): Int {
                    val b = stream.read()
                    if (b >= 0) {
                        val n = uploaded.incrementAndGet()
                        if (n % 64_000L == 0L || n >= totalBytes) {
                            onBytes(n.coerceAtMost(totalBytes), totalBytes)
                        }
                    }
                    return b
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = stream.read(b, off, len)
                    if (n > 0) {
                        val total = uploaded.addAndGet(n.toLong())
                        onBytes(total.coerceAtMost(totalBytes), totalBytes)
                    }
                    return n
                }

                override fun close() = stream.close()
            }
        }

        val streams = listOf(
            counting(ByteArrayInputStream(preamble), preamble.size.toLong()),
            counting(Files.newInputStream(filePath), fileSize),
            counting(ByteArrayInputStream(epilogue), epilogue.size.toLong()),
        )
        val sequence = SequenceInputStream(Collections.enumeration(streams))
        onBytes(0, totalBytes)

        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(30))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofInputStream { sequence })
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        onBytes(totalBytes, totalBytes)
        return MultipartResult(response.statusCode(), response.body())
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

    private val apiBase: String get() = "https://$apiHost"

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_HOST = "www.pgyer.com"
        private const val MAX_POLL_ATTEMPTS = 60
        private const val POLL_INTERVAL_MS = 2_000L
    }
}

class PgyerApiException(val code: Int, override val message: String) : Exception("Pgyer API error $code: $message")
