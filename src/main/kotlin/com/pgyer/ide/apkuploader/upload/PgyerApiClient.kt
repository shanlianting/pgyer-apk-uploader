package com.pgyer.ide.apkuploader.upload

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
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
 */
class PgyerApiClient(
    private val apiHost: String = DEFAULT_HOST,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun interface ProgressListener {
        fun onProgress(message: String)
    }

    fun upload(params: PgyerUploadParams, listener: ProgressListener = ProgressListener { }): PgyerUploadResult {
        require(params.apiKey.isNotBlank()) { "API Key is required" }
        require(Files.isRegularFile(params.apkPath)) { "APK not found: ${params.apkPath}" }
        require(params.apkPath.fileName.toString().endsWith(".apk", ignoreCase = true)) {
            "Only .apk files are supported in v1"
        }

        listener.onProgress("Requesting upload token...")
        val token = getCosToken(params)

        listener.onProgress("Uploading ${params.apkPath.fileName}...")
        uploadToCos(params.apkPath, token)

        listener.onProgress("Waiting for Pgyer to process the build...")
        return pollBuildInfo(params.apiKey, token.buildKey, listener)
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

        val response = postMultipart("$apiBase/apiv2/app/getCOSToken", form, filePart = null)
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

    private fun uploadToCos(apkPath: Path, token: CosToken) {
        val fields = LinkedHashMap(token.fields)
        fields["x-cos-meta-file-name"] = apkPath.fileName.toString()
        val status = postMultipart(token.endpoint, fields, filePart = FilePart("file", apkPath)).statusCode
        // Tencent COS returns 204 No Content on success
        if (status != 204 && status !in 200..299) {
            throw IOException("COS upload failed with HTTP $status")
        }
    }

    private fun pollBuildInfo(apiKey: String, buildKey: String, listener: ProgressListener): PgyerUploadResult {
        var attempt = 0
        while (attempt < MAX_POLL_ATTEMPTS) {
            attempt++
            listener.onProgress("Checking build status ($attempt/$MAX_POLL_ATTEMPTS)...")
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
            // 1246 / 1247 = still processing
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
        return PgyerUploadResult(
            buildKey = data.get("buildKey")?.asString.orEmpty(),
            buildName = data.get("buildName")?.asString.orEmpty(),
            buildVersion = data.get("buildVersion")?.asString.orEmpty(),
            buildBuildVersion = data.get("buildBuildVersion")?.asString.orEmpty(),
            shortcutUrl = shortcut,
            installPageUrl = installPage,
            qrCodeUrl = data.get("buildQRCodeURL")?.asString.orEmpty(),
        )
    }

    private data class CosToken(
        val endpoint: String,
        val buildKey: String,
        val fields: Map<String, String>,
    )

    private data class FilePart(val name: String, val path: Path)

    private data class MultipartResult(val statusCode: Int, val body: String)

    private fun postMultipart(
        url: String,
        fields: Map<String, String>,
        filePart: FilePart?,
    ): MultipartResult {
        val boundary = "----PgyerBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val bytes = buildMultipartBody(boundary, fields, filePart)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(10))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (filePart == null && response.statusCode() !in 200..299) {
            throw IOException("POST $url failed with HTTP ${response.statusCode()}: ${response.body()}")
        }
        return MultipartResult(response.statusCode(), response.body())
    }

    private fun buildMultipartBody(
        boundary: String,
        fields: Map<String, String>,
        filePart: FilePart?,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val charset = StandardCharsets.UTF_8
        fun write(s: String) = out.write(s.toByteArray(charset))

        for ((name, value) in fields) {
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            write(value)
            write("\r\n")
        }
        if (filePart != null) {
            val fileName = filePart.path.fileName.toString()
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"${filePart.name}\"; filename=\"$fileName\"\r\n")
            write("Content-Type: application/octet-stream\r\n\r\n")
            out.write(Files.readAllBytes(filePart.path))
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
