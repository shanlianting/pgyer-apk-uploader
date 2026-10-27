package com.pgyer.ide.apkuploader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImageLoaderTest {
    @Test
    fun resolveQrUrlPrefersApiField() {
        val url = QrImageLoader.resolveQrUrl(
            qrCodeUrl = "https://www.pgyer.com/app/qrcode/abcd",
            shortcutUrl = "zzzz",
            installPageUrl = "https://www.pgyer.com/zzzz",
        )
        assertEquals("https://www.pgyer.com/app/qrcode/abcd", url)
    }

    @Test
    fun resolveQrUrlFallsBackToShortcut() {
        val url = QrImageLoader.resolveQrUrl(
            qrCodeUrl = "",
            shortcutUrl = "abcd",
            installPageUrl = "https://www.pgyer.com/abcd",
        )
        assertEquals("https://www.pgyer.com/app/qrcode/abcd", url)
    }

    @Test
    fun generateFallbackQrFromInstallUrl() {
        val image = QrImageLoader.load(
            qrCodeUrl = "",
            fallbackContent = "https://www.pgyer.com/abcd",
            sizePx = 280,
        )
        assertNotNull(image)
        assertEquals(280, image!!.width)
        assertEquals(280, image.height)
        // QR modules are drawn; center should not be uniformly white.
        var dark = 0
        for (x in 40 until 240 step 8) {
            for (y in 40 until 240 step 8) {
                if (image.getRGB(x, y) and 0xFFFFFF == 0) dark++
            }
        }
        assertTrue(dark > 10)
    }
}
