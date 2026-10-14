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
import com.intellij.ui.JBColor
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
import com.pgyer.ide.apkuploader.upload.PgyerUploadResult
import com.pgyer.ide.apkuploader.upload.PgyerUploadService
import com.pgyer.ide.apkuploader.upload.UploadProgress
import com.pgyer.ide.apkuploader.upload.UploadUiCallback
import java.awt.BorderLayout
import java.awt.Font
import java.nio.file.Paths
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JProgressBar
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
    private val updateDescriptionArea = JBTextArea(4, 30)
    private val appDescriptionArea = JBTextArea(2, 30)
    private val installTypeCombo = ComboBox(InstallType.entries.toTypedArray())
    private val passwordField = JBPasswordField()
    private val progressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
        string = "0%"
        isVisible = false
    }
    private val statusLabel = JBLabel(" ").apply {
        border = JBUI.Borders.empty(4, 0)
    }
    private val resultView = PgyerUploadResultView()
    private val uploadButton = JButton(PgyerBundle.message("toolwindow.upload"))
    private var uploading = false

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
        uploadButton.addActionListener { upload() }
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
        val uploadProgressPanel = JPanel(BorderLayout(0, 4)).apply {
            border = JBUI.Borders.emptyTop(8)
            add(progressBar, BorderLayout.NORTH)
            add(statusLabel, BorderLayout.SOUTH)
        }

        val form = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Pgyer APK Uploader</b></html>").apply {
                horizontalAlignment = SwingConstants.LEFT
            }, 1)
            .addLabeledComponent(JBLabel(PgyerBundle.message("toolwindow.apkPath")), pathRow, 1, false)
            .addLabeledComponent(JBLabel(PgyerBundle.message("toolwindow.recentApks")), recentRow, 1, false)
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
            .addComponent(uploadProgressPanel, 1)
            .addComponent(resultView, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        add(JBScrollPane(form).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
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
        if (!uploading) {
            setIdleStatus(
                if (apks.isEmpty()) PgyerBundle.message("toolwindow.noRecentApks")
                else PgyerBundle.message("toolwindow.foundApks", apks.size)
            )
        }
    }

    private fun upload() {
        if (uploading) return
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

        setUploading(true)
        resultView.clear()
        applyProgress(
            UploadProgress(
                UploadProgress.Stage.TOKEN,
                PgyerBundle.message("progress.gettingToken"),
                0.0,
            )
        )

        // Result panel in the tool window is the primary success view; dialog is optional via notification.
        PgyerUploadService.startUpload(
            project = project,
            params = params,
            showResultDialog = false,
            uiCallback = object : UploadUiCallback {
                override fun onProgress(progress: UploadProgress) {
                    applyProgress(progress)
                }

                override fun onSuccess(result: PgyerUploadResult) {
                    setUploading(false)
                    statusLabel.foreground = JBColor(0x2E7D32, 0xA5D6A7)
                    statusLabel.font = statusLabel.font.deriveFont(Font.BOLD)
                    statusLabel.text = PgyerBundle.message(
                        "progress.successStatus",
                        result.displayName,
                        result.versionLabel,
                    )
                    progressBar.value = 100
                    progressBar.string = "100%"
                    progressBar.isVisible = true
                    resultView.showResult(result)
                }

                override fun onFailure(message: String) {
                    setUploading(false)
                    statusLabel.foreground = JBColor(0xC62828, 0xEF9A9A)
                    statusLabel.font = statusLabel.font.deriveFont(Font.BOLD)
                    statusLabel.text = PgyerBundle.message("progress.failureStatus", message)
                    progressBar.isVisible = true
                }
            },
        )
    }

    private fun applyProgress(progress: UploadProgress) {
        progressBar.isVisible = true
        progressBar.isIndeterminate = progress.stage == UploadProgress.Stage.TOKEN && progress.fraction < 0.1
        if (!progressBar.isIndeterminate) {
            progressBar.value = progress.percent
            progressBar.string = "${progress.percent}%"
        } else {
            progressBar.string = PgyerBundle.message("progress.preparing")
        }
        statusLabel.foreground = when (progress.stage) {
            UploadProgress.Stage.FAILED -> JBColor(0xC62828, 0xEF9A9A)
            UploadProgress.Stage.DONE -> JBColor(0x2E7D32, 0xA5D6A7)
            else -> JBUI.CurrentTheme.Label.foreground()
        }
        statusLabel.font = statusLabel.font.deriveFont(
            if (progress.stage == UploadProgress.Stage.DONE || progress.stage == UploadProgress.Stage.FAILED) {
                Font.BOLD
            } else {
                Font.PLAIN
            }
        )
        statusLabel.text = progress.message
    }

    private fun setUploading(value: Boolean) {
        uploading = value
        uploadButton.isEnabled = !value
        uploadButton.text = if (value) {
            PgyerBundle.message("toolwindow.uploading")
        } else {
            PgyerBundle.message("toolwindow.upload")
        }
    }

    private fun setIdleStatus(text: String) {
        statusLabel.foreground = JBUI.CurrentTheme.Label.foreground()
        statusLabel.font = statusLabel.font.deriveFont(Font.PLAIN)
        statusLabel.text = text
        progressBar.isVisible = false
    }
}
