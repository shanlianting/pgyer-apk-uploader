package com.pgyer.ide.apkuploader.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.actions.OpenPgyerSettingsAction
import com.pgyer.ide.apkuploader.notifications.PgyerNotifier
import com.pgyer.ide.apkuploader.settings.PgyerCredentialsStore
import com.pgyer.ide.apkuploader.settings.PgyerSettingsState
import com.pgyer.ide.apkuploader.upload.ApkDetector
import com.pgyer.ide.apkuploader.upload.InstallType
import com.pgyer.ide.apkuploader.upload.PgyerUploadParams
import com.pgyer.ide.apkuploader.upload.PgyerUploadService
import java.awt.BorderLayout
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class PgyerUploadDialog(
    private val project: Project?,
    initialApk: Path? = null,
) : DialogWrapper(project) {

    private val apkPathField = JBTextField()
    private val updateDescriptionArea = JBTextArea(4, 40)
    private val appDescriptionArea = JBTextArea(3, 40)
    private val installTypeCombo = ComboBox(InstallType.entries.toTypedArray())
    private val passwordField = JBPasswordField()
    private val recentCombo = ComboBox<Path>()

    init {
        title = "Upload APK to Pgyer"
        val state = PgyerSettingsState.getInstance()
        apkPathField.text = (initialApk?.toString() ?: state.lastApkPath).orEmpty()
        updateDescriptionArea.text = state.defaultUpdateDescription
        appDescriptionArea.text = state.defaultAppDescription
        installTypeCombo.selectedItem = InstallType.fromCode(state.defaultInstallType)
        passwordField.text = PgyerCredentialsStore.getDefaultInstallPassword()
        updateDescriptionArea.lineWrap = true
        updateDescriptionArea.wrapStyleWord = true
        appDescriptionArea.lineWrap = true
        appDescriptionArea.wrapStyleWord = true
        init()
        if (project != null) {
            refreshRecentApks()
        }
    }

    override fun createCenterPanel(): JComponent {
        val browseButton = JButton(PgyerBundle.message("toolwindow.browse")).apply {
            addActionListener { browseApk() }
        }
        val detectButton = JButton(PgyerBundle.message("toolwindow.detect")).apply {
            addActionListener { refreshRecentApks(selectFirst = true) }
        }
        val pathRow = JPanel(BorderLayout(8, 0)).apply {
            add(apkPathField, BorderLayout.CENTER)
            add(browseButton, BorderLayout.EAST)
        }
        recentCombo.addActionListener {
            val selected = recentCombo.selectedItem as? Path
            if (selected != null) {
                apkPathField.text = selected.toString()
            }
        }
        val recentRow = JPanel(BorderLayout(8, 0)).apply {
            add(recentCombo, BorderLayout.CENTER)
            add(detectButton, BorderLayout.EAST)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(PgyerBundle.message("toolwindow.apkPath")), pathRow, 1, false)
            .addLabeledComponent(JBLabel("Recent APKs:"), recentRow, 1, false)
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("toolwindow.updateDescription")),
                JScrollPane(updateDescriptionArea),
                1,
                true
            )
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("toolwindow.appDescription")),
                JScrollPane(appDescriptionArea),
                1,
                true
            )
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("toolwindow.installType")),
                installTypeCombo,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("toolwindow.password")),
                passwordField,
                1,
                false
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    fun buildParams(): PgyerUploadParams? {
        if (PgyerCredentialsStore.getApiKey().isBlank()) {
            PgyerNotifier.notifyFailure(project, PgyerBundle.message("notify.missingApiKey"))
            OpenPgyerSettingsAction.openSettings(project)
            return null
        }
        val path = Paths.get(apkPathField.text.trim())
        val error = PgyerUploadService.validateApk(path)
        if (error != null) {
            Messages.showErrorDialog(project, error, "Pgyer Upload")
            return null
        }
        return PgyerUploadService.buildParamsFromUi(
            apkPath = path,
            installType = installTypeCombo.selectedItem as InstallType,
            installPassword = String(passwordField.password),
            updateDescription = updateDescriptionArea.text.orEmpty(),
            appDescription = appDescriptionArea.text.orEmpty(),
        )
    }

    private fun browseApk() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("apk")
            .withTitle("Select APK")
            .withDescription("Choose an Android APK to upload to Pgyer")
        val toSelect = apkPathField.text.takeIf { it.isNotBlank() }
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val file = FileChooser.chooseFile(descriptor, project, toSelect) ?: return
        apkPathField.text = file.path
    }

    private fun refreshRecentApks(selectFirst: Boolean = false) {
        recentCombo.removeAllItems()
        val project = project ?: return
        val apks = ApkDetector.findRecentApks(project)
        apks.forEach { recentCombo.addItem(it) }
        if (selectFirst && apks.isNotEmpty()) {
            recentCombo.selectedItem = apks.first()
            apkPathField.text = apks.first().toString()
        }
        if (apks.isEmpty() && selectFirst) {
            Messages.showInfoMessage(project, "No recent APKs found under build/outputs.", "Pgyer Upload")
        }
    }
}
