package com.pgyer.ide.apkuploader.upload

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/**
 * Draws the install QR locally.
 *
 * Pgyer's `buildQRCodeURL` points at an HTML/image endpoint. Loading that URL with
 * `ImageIO` inside the IDE often yields a blank icon (plugin class loader has no
 * ImageIO readers, and `Image.getScaledInstance` does not finish before the label paints).
 * The scannable payload is the install page, so the image is generated here.
 */
object InstallQrCode {
    const val DEFAULT_SIZE = 180

    fun payload(installPageUrl: String, qrCodeUrl: String): String {
        val page = installPageUrl.trim()
        if (page.startsWith("http://") || page.startsWith("https://")) return page
        return qrCodeUrl.trim()
    }

    /**
     * Absolute QR image URL for the "open QR" action.
     * Accepts a full URL, a protocol-relative URL, or builds one from the short link.
     */
    fun imageUrl(rawQr: String?, shortcut: String): String {
        val trimmed = rawQr?.trim().orEmpty()
        when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> return trimmed
            trimmed.startsWith("//") -> return "https:$trimmed"
        }
        val key = shortcut.trim().trim('/')
        if (key.startsWith("http://") || key.startsWith("https://") || key.isBlank()) {
            return trimmed
        }
        return "https://www.pgyer.com/app/qrcode/$key"
    }

    fun encode(content: String, size: Int = DEFAULT_SIZE): BufferedImage? {
        val text = content.trim()
        if (text.isEmpty() || size <= 0) return null
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                image.setRGB(x, y, if (matrix.get(x, y)) BLACK else WHITE)
            }
        }
        return image
    }

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
}
