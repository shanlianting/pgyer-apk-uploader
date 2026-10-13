package com.pgyer.ide.apkuploader.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.upload.InstallType
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class PgyerConfigurable : Configurable {

    private var panel: JPanel? = null
    private val apiKeyField = JBPasswordField()
    private val installTypeCombo = ComboBox(InstallType.entries.toTypedArray())
    private val passwordField = JBPasswordField()
    private val updateDescriptionArea = JBTextArea(4, 40)
    private val appDescriptionArea = JBTextArea(3, 40)

    override fun getDisplayName(): String = PgyerBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        apiKeyField.columns = 36
        passwordField.columns = 36
        updateDescriptionArea.lineWrap = true
        updateDescriptionArea.wrapStyleWord = true
        appDescriptionArea.lineWrap = true
        appDescriptionArea.wrapStyleWord = true

        val comment = JBLabel(
            "<html><body style='width:360px'>${PgyerBundle.message("settings.apiKey.comment")}</body></html>"
        ).apply {
            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            border = JBUI.Borders.emptyTop(4)
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(PgyerBundle.message("settings.apiKey.label")), apiKeyField, 1, false)
            .addComponent(comment, 1)
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("settings.defaultInstallType.label")),
                installTypeCombo,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("settings.defaultPassword.label")),
                passwordField,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel(PgyerBundle.message("settings.defaultDescription.label")),
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
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = PgyerSettingsState.getInstance()
        val apiKey = String(apiKeyField.password)
        val password = String(passwordField.password)
        val selectedType = (installTypeCombo.selectedItem as InstallType).code
        return apiKey != PgyerCredentialsStore.getApiKey() ||
            password != PgyerCredentialsStore.getDefaultInstallPassword() ||
            selectedType != state.defaultInstallType ||
            updateDescriptionArea.text != state.defaultUpdateDescription ||
            appDescriptionArea.text != state.defaultAppDescription
    }

    override fun apply() {
        val state = PgyerSettingsState.getInstance()
        PgyerCredentialsStore.setApiKey(String(apiKeyField.password).trim())
        PgyerCredentialsStore.setDefaultInstallPassword(String(passwordField.password))
        state.defaultInstallType = (installTypeCombo.selectedItem as InstallType).code
        state.defaultUpdateDescription = updateDescriptionArea.text.orEmpty()
        state.defaultAppDescription = appDescriptionArea.text.orEmpty()
    }

    override fun reset() {
        val state = PgyerSettingsState.getInstance()
        apiKeyField.text = PgyerCredentialsStore.getApiKey()
        passwordField.text = PgyerCredentialsStore.getDefaultInstallPassword()
        installTypeCombo.selectedItem = InstallType.fromCode(state.defaultInstallType)
        updateDescriptionArea.text = state.defaultUpdateDescription
        appDescriptionArea.text = state.defaultAppDescription
    }

    override fun disposeUIResources() {
        panel = null
    }
}
