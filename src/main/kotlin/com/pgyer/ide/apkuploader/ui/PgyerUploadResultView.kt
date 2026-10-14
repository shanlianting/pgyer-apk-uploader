package com.pgyer.ide.apkuploader.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.upload.PgyerUploadResult
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Image
import java.net.URI
import javax.imageio.ImageIO
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

        val qrPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyLeft(16)
            add(JBLabel(PgyerBundle.message("result.qr"), SwingConstants.CENTER), BorderLayout.NORTH)
            add(qrLabel, BorderLayout.CENTER)
        }

        val body = JPanel(BorderLayout()).apply {
            add(info, BorderLayout.CENTER)
            add(qrPanel, BorderLayout.EAST)
        }

        add(titleLabel, BorderLayout.NORTH)
        add(JBScrollPane(body).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
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
        qrLabel.icon = null
        qrLabel.text = if (result.qrCodeUrl.isBlank()) {
            PgyerBundle.message("result.qr.unavailable")
        } else {
            PgyerBundle.message("result.qr.loading")
        }
        isVisible = true
        revalidate()
        repaint()
        loadQrAsync(result.qrCodeUrl)
    }

    fun clear() {
        currentResult = null
        isVisible = false
        qrLabel.icon = null
        qrLabel.text = " "
    }

    private fun loadQrAsync(url: String) {
        if (url.isBlank()) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val image = ImageIO.read(URI.create(url).toURL())
                if (image == null) {
                    ApplicationManager.getApplication().invokeLater {
                        if (currentResult?.qrCodeUrl == url) {
                            qrLabel.text = PgyerBundle.message("result.qr.unavailable")
                        }
                    }
                    return@executeOnPooledThread
                }
                val scaled = image.getScaledInstance(160, 160, Image.SCALE_SMOOTH)
                ApplicationManager.getApplication().invokeLater {
                    if (currentResult?.qrCodeUrl == url) {
                        qrLabel.icon = ImageIcon(scaled)
                        qrLabel.text = null
                    }
                }
            } catch (_: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    if (currentResult?.qrCodeUrl == url) {
                        qrLabel.text = PgyerBundle.message("result.qr.unavailable")
                    }
                }
            }
        }
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
