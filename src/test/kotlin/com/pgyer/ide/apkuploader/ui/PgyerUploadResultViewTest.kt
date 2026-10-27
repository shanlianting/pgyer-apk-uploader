package com.pgyer.ide.apkuploader.ui

import com.pgyer.ide.apkuploader.upload.PgyerUploadResult
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class PgyerUploadResultViewTest {
    @Test
    fun showResultPaintsQrModules() {
        val view = PgyerUploadResultView()
        view.showResult(
            PgyerUploadResult(
                buildKey = "a1b2c3d4e5f6",
                buildName = "示例应用",
                buildVersion = "1.2.3",
                buildBuildVersion = "28",
                buildFileSize = "12345678",
                shortcutUrl = "abcd",
                installPageUrl = "https://www.pgyer.com/abcd",
                qrCodeUrl = "https://www.pgyer.com/app/qrcode/abcd",
            )
        )
        view.setSize(420, 560)
        layoutTree(view)
        val image = BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        view.paint(graphics)
        graphics.dispose()

        var dark = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y) and 0xFFFFFF
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < 40 && g < 40 && b < 40) dark++
            }
        }
        assertTrue("expected painted QR modules, darkPixels=$dark", dark > 500)
    }

    private fun layoutTree(component: java.awt.Component) {
        component.doLayout()
        if (component is java.awt.Container) {
            component.components.forEach { layoutTree(it) }
        }
    }
}
