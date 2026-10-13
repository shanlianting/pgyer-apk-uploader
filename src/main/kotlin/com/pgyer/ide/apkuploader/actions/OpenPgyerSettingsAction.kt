package com.pgyer.ide.apkuploader.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.pgyer.ide.apkuploader.settings.PgyerConfigurable

class OpenPgyerSettingsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        openSettings(e.project)
    }

    companion object {
        fun openSettings(project: Project?) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, PgyerConfigurable::class.java)
        }
    }
}
