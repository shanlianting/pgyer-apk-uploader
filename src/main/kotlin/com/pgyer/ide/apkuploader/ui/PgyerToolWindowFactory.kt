package com.pgyer.ide.apkuploader.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.actions.OpenPgyerSettingsAction
import com.pgyer.ide.apkuploader.notifications.PgyerNotifier
import com.pgyer.ide.apkuploader.settings.PgyerCredentialsStore
import com.pgyer.ide.apkuploader.settings.PgyerSettingsState
import com.pgyer.ide.apkuploader.upload.ApkDetector
import com.pgyer.ide.apkuploader.upload.InstallType
import com.pgyer.ide.apkuploader.upload.PgyerUploadService
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

class PgyerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PgyerUploadPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class PgyerUploadPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val apkPathField = JBTextField()
    private val recentCombo = ComboBox<java.nio.file.Path>()
    private val updateDescriptionArea = JBTextArea(5, 30)
    private val appDescriptionArea = JBTextArea(3, 30)
    private val installTypeCombo = ComboBox(InstallType.entries.toTypedArray())
    private val passwordField = JBPasswordField()
    private val statusLabel = JBLabel(" ").apply { border = JBUI.Borders.empty(4, 0) }

    init {
        border = JBUI.Borders.empty(12)
        val state = PgyerSettingsState.getInstance()
        apkPathField.text = state.lastApkPath
        updateDescriptionArea.text = state.defaultUpdateDescription
        appDescriptionArea.text = state.defaultAppDescription
        updateDescriptionArea.lineWrap = true
        updateDescriptionArea.wrapStyleWord = true
        appDescriptionArea.lineWrap = true
        appDescriptionArea.wrapStyleWord = true
        installTypeCombo.selectedItem = InstallType.fromCode(state.defaultInstallType)
        passwordField.text = PgyerCredentialsStore.getDefaultInstallPassword()

        recentCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is java.nio.file.Path) {
                    text = ApkDetector.formatLabel(value)
                }
                return c
            }
        }
        recentCombo.addActionListener {
            (recentCombo.selectedItem as? java.nio.file.Path)?.let {
                apkPathField.text = it.toString()
            }
        }

        val browseButton = JButton(PgyerBundle.message("toolwindow.browse")).apply {
            addActionListener { browseApk() }
        }
        val detectButton = JButton(PgyerBundle.message("toolwindow.detect")).apply {
            addActionListener { refreshRecent(selectFirst = true) }
        }
        val uploadButton = JButton(PgyerBundle.message("toolwindow.upload")).apply {
            addActionListener { upload() }
        }
        val settingsButton = JButton(PgyerBundle.message("toolwindow.openSettings")).apply {
            addActionListener { OpenPgyerSettingsAction.openSettings(project) }
        }

        val pathRow = JPanel(BorderLayout(8, 0)).apply {
            add(apkPathField, BorderLayout.CENTER)
            add(browseButton, BorderLayout.EAST)
        }
        val recentRow = JPanel(BorderLayout(8, 0)).apply {
            add(recentCombo, BorderLayout.CENTER)
            add(detectButton, BorderLayout.EAST)
        }
        val actions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(uploadButton)
            add(Box.createHorizontalStrut(8))
            add(settingsButton)
            add(Box.createHorizontalGlue())
        }

        val form = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Pgyer APK Uploader</b></html>").apply {
                horizontalAlignment = SwingConstants.LEFT
            }, 1)
            .addLabeledComponent(JBLabel(PgyerBundle.message("toolwindow.apkPath")), pathRow, 1, false)
            .addLabeledComponent(JBLabel("Recent APKs:"), recentRow, 1, false)
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("toolwindow.updateDescription")),
                JBScrollPane(updateDescriptionArea),
                1,
                true
            )
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("toolwindow.appDescription")),
                JBScrollPane(appDescriptionArea),
                1,
                true
            )
            .addLabeledComponent(JBLabel(PgyerBundle.message("toolwindow.installType")), installTypeCombo, 1, false)
            .addLabeledComponent(JBLabel(PgyerBundle.message("toolwindow.password")), passwordField, 1, false)
            .addComponent(actions, 1)
            .addComponent(statusLabel, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        add(form, BorderLayout.CENTER)
        refreshRecent(selectFirst = apkPathField.text.isBlank())
    }

    private fun browseApk() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("apk")
            .withTitle("Select APK")
        val toSelect = apkPathField.text.takeIf { it.isNotBlank() }
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val file = FileChooser.chooseFile(descriptor, project, toSelect) ?: return
        apkPathField.text = file.path
    }

    private fun refreshRecent(selectFirst: Boolean) {
        recentCombo.removeAllItems()
        val apks = ApkDetector.findRecentApks(project)
        apks.forEach { recentCombo.addItem(it) }
        if (selectFirst && apks.isNotEmpty()) {
            recentCombo.selectedIndex = 0
            apkPathField.text = apks.first().toString()
        }
        statusLabel.text = if (apks.isEmpty()) "No recent APKs under build/outputs." else "Found ${apks.size} APK(s)."
    }

    private fun upload() {
        if (PgyerCredentialsStore.getApiKey().isBlank()) {
            PgyerNotifier.notifyFailure(project, PgyerBundle.message("notify.missingApiKey"))
            OpenPgyerSettingsAction.openSettings(project)
            return
        }
        val path = Paths.get(apkPathField.text.trim())
        val error = PgyerUploadService.validateApk(path)
        if (error != null) {
            Messages.showErrorDialog(project, error, "Pgyer Upload")
            return
        }
        val params = PgyerUploadService.buildParamsFromUi(
            apkPath = path,
            installType = installTypeCombo.selectedItem as InstallType,
            installPassword = String(passwordField.password),
            updateDescription = updateDescriptionArea.text.orEmpty(),
            appDescription = appDescriptionArea.text.orEmpty(),
        ) ?: return
        statusLabel.text = "Uploading..."
        PgyerUploadService.startUpload(project, params)
    }
}
