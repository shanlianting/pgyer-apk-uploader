package com.pgyer.ide.apkuploader.upload

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

class InstallQrCodeTest {
    @Test
    fun encodesInstallPageAsScannableQr() {
        val url = "https://www.pgyer.com/abcd"
        val image = InstallQrCode.encode(url, 180)
        assertNotNull(image)
        assertEquals(180, image!!.width)
        assertEquals(180, image.height)
        assertTrue(image.getRGB(0, 0) == 0xFFFFFF || image.getRGB(0, 0) == -1)
        assertEquals(url, decode(image))
    }

    @Test
    fun blankContentProducesNoImage() {
        assertNull(InstallQrCode.encode("  "))
        assertNull(InstallQrCode.encode(""))
    }

    @Test
    fun payloadPrefersInstallPage() {
        assertEquals(
            "https://www.pgyer.com/abcd",
            InstallQrCode.payload("https://www.pgyer.com/abcd", "https://www.pgyer.com/app/qrcode/abcd"),
        )
        assertEquals(
            "https://www.pgyer.com/app/qrcode/abcd",
            InstallQrCode.payload("", "https://www.pgyer.com/app/qrcode/abcd"),
        )
    }

    @Test
    fun imageUrlFillsMissingQrFromShortcut() {
        assertEquals(
            "https://www.pgyer.com/app/qrcode/abcd",
            InstallQrCode.imageUrl(null, "abcd"),
        )
        assertEquals(
            "https://www.pgyer.com/app/qrcode/abcd",
            InstallQrCode.imageUrl("//www.pgyer.com/app/qrcode/abcd", "ignored"),
        )
        assertEquals(
            "https://cdn.example/qr.png",
            InstallQrCode.imageUrl("https://cdn.example/qr.png", "abcd"),
        )
    }

    private fun decode(image: BufferedImage): String {
        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminance(image)))
        return QRCodeReader().decode(bitmap).text
    }

    private class BufferedImageLuminance(image: BufferedImage) : LuminanceSource(image.width, image.height) {
        private val pixels: ByteArray

        init {
            val rgb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            pixels = ByteArray(rgb.size)
            for (i in rgb.indices) {
                val pixel = rgb[i]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                pixels[i] = ((r + 2 * g + b) / 4).toByte()
            }
        }

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val width = width
            val out = if (row == null || row.size < width) ByteArray(width) else row
            System.arraycopy(pixels, y * width, out, 0, width)
            return out
        }

        override fun getMatrix(): ByteArray = pixels
    }
}
