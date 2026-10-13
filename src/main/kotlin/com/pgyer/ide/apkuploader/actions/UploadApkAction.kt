package com.pgyer.ide.apkuploader.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.pgyer.ide.apkuploader.notifications.PgyerNotifier
import com.pgyer.ide.apkuploader.settings.PgyerCredentialsStore
import com.pgyer.ide.apkuploader.settings.PgyerSettingsState
import com.pgyer.ide.apkuploader.ui.PgyerUploadDialog
import com.pgyer.ide.apkuploader.upload.PgyerUploadService
import java.nio.file.Paths

class UploadApkAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = true
        if (file != null && file.name.endsWith(".apk", ignoreCase = true)) {
            e.presentation.text = "Upload ${file.name} to Pgyer"
        } else {
            e.presentation.text = "Upload APK to Pgyer"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (PgyerCredentialsStore.getApiKey().isBlank()) {
            PgyerNotifier.notifyFailure(project, com.pgyer.ide.apkuploader.PgyerBundle.message("notify.missingApiKey"))
            OpenPgyerSettingsAction.openSettings(project)
            return
        }

        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?.takeIf { it.name.endsWith(".apk", ignoreCase = true) }
            ?.let { Paths.get(it.path) }

        if (selected != null) {
            val dialog = PgyerUploadDialog(project, selected)
            if (dialog.showAndGet()) {
                val params = dialog.buildParams() ?: return
                val error = PgyerUploadService.validateApk(params.apkPath)
                if (error != null) {
                    PgyerNotifier.notifyFailure(project, error)
                    return
                }
                PgyerUploadService.startUpload(project, params)
            }
            return
        }

        // No APK selected: prefer tool window if available, else dialog
        val toolWindow = project?.let { ToolWindowManager.getInstance(it).getToolWindow("Pgyer Upload") }
        if (toolWindow != null) {
            toolWindow.show()
            return
        }

        val last = PgyerSettingsState.getInstance().lastApkPath
            .takeIf { it.isNotBlank() }
            ?.let { Paths.get(it) }
        val dialog = PgyerUploadDialog(project, last)
        if (dialog.showAndGet()) {
            val params = dialog.buildParams() ?: return
            val error = PgyerUploadService.validateApk(params.apkPath)
            if (error != null) {
                PgyerNotifier.notifyFailure(project, error)
                return
            }
            PgyerUploadService.startUpload(project, params)
        }
    }
}
