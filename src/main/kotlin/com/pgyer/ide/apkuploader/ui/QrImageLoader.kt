package com.pgyer.ide.apkuploader.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.intellij.util.io.HttpRequests
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Loads a QR image for the success view.
 *
 * Prefer downloading [qrCodeUrl] via IntelliJ [HttpRequests] (respects IDE proxy/SSL).
 * Fall back to generating a QR from [fallbackContent] (usually the install page URL),
 * because bare [javax.imageio.ImageIO.read] on a URL often fails in the IDE JVM
 * (no User-Agent / proxy / redirects).
 */
object QrImageLoader {

    private const val USER_AGENT = "PgyerApkUploader-IDE/1.0 (IntelliJ Platform)"

    fun load(qrCodeUrl: String, fallbackContent: String, sizePx: Int): BufferedImage? {
        if (qrCodeUrl.startsWith("http://") || qrCodeUrl.startsWith("https://")) {
            val downloaded = downloadImage(qrCodeUrl)
            if (downloaded != null) {
                return scale(downloaded, sizePx)
            }
        }
        val content = fallbackContent.takeIf { it.isNotBlank() }
            ?: qrCodeUrl.takeIf { it.isNotBlank() }
            ?: return null
        return generateQr(content, sizePx)
    }

    fun resolveQrUrl(qrCodeUrl: String, shortcutUrl: String, installPageUrl: String): String {
        if (qrCodeUrl.isNotBlank()) return qrCodeUrl.trim()
        val shortcut = shortcutUrl.trim().removePrefix("https://www.pgyer.com/").removePrefix("http://www.pgyer.com/")
        if (shortcut.isNotBlank() && !shortcut.contains('/') && !shortcut.contains(' ')) {
            return "https://www.pgyer.com/app/qrcode/$shortcut"
        }
        // Last resort: some builds only expose the install page; loader will generate QR from it.
        return installPageUrl.trim()
    }

    private fun downloadImage(url: String): BufferedImage? {
        return try {
            val bytes = HttpRequests.request(url)
                .userAgent(USER_AGENT)
                .connectTimeout(15_000)
                .readTimeout(30_000)
                .redirectLimit(5)
                .readBytes(null)
            if (bytes.isEmpty()) return null
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            null
        }
    }

    private fun generateQr(content: String, sizePx: Int): BufferedImage? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val image = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    image.setRGB(x, y, if (matrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
                }
            }
            image
        } catch (_: Exception) {
            null
        }
    }

    private fun scale(source: BufferedImage, sizePx: Int): BufferedImage {
        if (source.width == sizePx && source.height == sizePx) return source
        val scaled = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        try {
            g.drawImage(source, 0, 0, sizePx, sizePx, null)
        } finally {
            g.dispose()
        }
        return scaled
    }
}
