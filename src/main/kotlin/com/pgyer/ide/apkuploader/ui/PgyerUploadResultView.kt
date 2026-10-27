package com.pgyer.ide.apkuploader.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.upload.InstallQrCode
import com.pgyer.ide.apkuploader.upload.PgyerUploadResult
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Success result panel: app name, version, QR code, and install links.
 */
class PgyerUploadResultView : JPanel(BorderLayout()) {

    private val titleLabel = JBLabel(PgyerBundle.message("result.title")).apply {
        font = font.deriveFont(Font.BOLD, 14f)
        border = JBUI.Borders.emptyBottom(8)
    }
    private val nameLabel = JBLabel(" ").apply {
        font = font.deriveFont(Font.BOLD, 16f)
    }
    private val versionLabel = JBLabel(" ")
    private val buildNoLabel = JBLabel(" ")
    private val sizeLabel = JBLabel(" ")
    private val keyLabel = JBLabel(" ")
    private val urlLabel = JBLabel(" ").apply {
        foreground = JBColor.BLUE
    }
    private val qrLabel = JLabel(PgyerBundle.message("result.qr.loading"), SwingConstants.CENTER).apply {
        preferredSize = Dimension(180, 180)
        minimumSize = Dimension(180, 180)
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
        border = BorderFactory.createLineBorder(JBColor.border())
    }
    private val openPageButton = JButton(PgyerBundle.message("result.openInstallPage"))
    private val openQrButton = JButton(PgyerBundle.message("result.openQr"))
    private var currentResult: PgyerUploadResult? = null

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(12),
        )
        isVisible = false

        openPageButton.addActionListener {
            currentResult?.installPageUrl?.takeIf { it.isNotBlank() }?.let { BrowserUtil.browse(it) }
        }
        openQrButton.addActionListener {
            currentResult?.qrCodeUrl?.takeIf { it.isNotBlank() }?.let { BrowserUtil.browse(it) }
        }

        val info = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            add(row(PgyerBundle.message("result.appName"), nameLabel))
            add(Box.createVerticalStrut(6))
            add(row(PgyerBundle.message("result.version"), versionLabel))
            add(Box.createVerticalStrut(4))
            add(row(PgyerBundle.message("result.buildNo"), buildNoLabel))
            add(Box.createVerticalStrut(4))
            add(row(PgyerBundle.message("result.fileSize"), sizeLabel))
            add(Box.createVerticalStrut(4))
            add(row(PgyerBundle.message("result.buildKey"), keyLabel))
            add(Box.createVerticalStrut(4))
            add(row(PgyerBundle.message("result.installUrl"), urlLabel))
            add(Box.createVerticalStrut(10))
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                alignmentX = LEFT_ALIGNMENT
                add(openPageButton)
                add(Box.createHorizontalStrut(8))
                add(openQrButton)
            })
        }

        val qrPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            add(JBLabel(PgyerBundle.message("result.qr")).apply {
                alignmentX = CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(6))
            add(qrLabel)
        }
        qrLabel.alignmentX = CENTER_ALIGNMENT

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(qrPanel)
            add(Box.createVerticalStrut(12))
            add(info)
        }

        add(titleLabel, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
    }

    fun showResult(result: PgyerUploadResult) {
        currentResult = result
        nameLabel.text = result.displayName
        versionLabel.text = result.buildVersion.ifBlank { "-" }
        buildNoLabel.text = result.buildBuildVersion.ifBlank { "-" }
        sizeLabel.text = result.buildFileSize.ifBlank { "-" }
        keyLabel.text = result.buildKey.ifBlank { "-" }
        urlLabel.text = "<html><a href='${result.installPageUrl}'>${result.installPageUrl.ifBlank { "-" }}</a></html>"
        openPageButton.isEnabled = result.installPageUrl.isNotBlank()
        openQrButton.isEnabled = result.qrCodeUrl.isNotBlank()
        showQr(InstallQrCode.payload(result.installPageUrl, result.qrCodeUrl))
        isVisible = true
        revalidate()
        repaint()
    }

    fun clear() {
        currentResult = null
        isVisible = false
        qrLabel.icon = null
        qrLabel.text = " "
    }

    private fun showQr(payload: String) {
        val image = InstallQrCode.encode(payload)
        if (image == null) {
            qrLabel.icon = null
            qrLabel.text = PgyerBundle.message("result.qr.unavailable")
            return
        }
        val size = Dimension(image.width, image.height)
        qrLabel.preferredSize = size
        qrLabel.minimumSize = size
        qrLabel.maximumSize = size
        // The bitmap is already complete, so ImageIcon paints it. Scaling a
        // downloaded image with getScaledInstance() left this label blank.
        qrLabel.icon = ImageIcon(image)
        qrLabel.text = null
    }

    private fun row(caption: String, value: JLabel): JPanel {
        value.alignmentX = LEFT_ALIGNMENT
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            add(JBLabel(caption).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(11f)
                alignmentX = LEFT_ALIGNMENT
            })
            add(value)
        }
    }
}
