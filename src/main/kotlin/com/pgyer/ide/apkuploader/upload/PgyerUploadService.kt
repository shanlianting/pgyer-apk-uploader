package com.pgyer.ide.apkuploader.upload

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.notifications.PgyerNotifier
import com.pgyer.ide.apkuploader.settings.PgyerCredentialsStore
import com.pgyer.ide.apkuploader.settings.PgyerSettingsState
import com.pgyer.ide.apkuploader.ui.PgyerUploadResultDialog
import java.nio.file.Files
import java.nio.file.Path

interface UploadUiCallback {
    fun onProgress(progress: UploadProgress)
    fun onSuccess(result: PgyerUploadResult) {}
    fun onFailure(message: String) {}
}

object PgyerUploadService {

    /**
     * @param uiCallback optional UI hooks (tool window progress / result panel)
     * @param showResultDialog when true, open the success result dialog (default for menu/dialog uploads)
     */
    fun startUpload(
        project: Project?,
        params: PgyerUploadParams,
        uiCallback: UploadUiCallback? = null,
        showResultDialog: Boolean = true,
    ) {
        object : Task.Backgroundable(project, PgyerBundle.message("progress.taskTitle"), true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = PgyerBundle.message("progress.gettingToken")
                try {
                    val client = PgyerApiClient()
                    val result = client.upload(params) { progress ->
                        indicator.checkCanceled()
                        indicator.fraction = progress.fraction.coerceIn(0.0, 1.0)
                        indicator.text = progress.message
                        ApplicationManager.getApplication().invokeLater {
                            uiCallback?.onProgress(progress)
                        }
                    }
                    ApplicationManager.getApplication().invokeLater {
                        PgyerSettingsState.getInstance().lastApkPath = params.apkPath.toString()
                        uiCallback?.onSuccess(result)
                        PgyerNotifier.notifySuccess(project, result)
                        if (showResultDialog) {
                            PgyerUploadResultDialog.show(project, result)
                        }
                    }
                } catch (e: Exception) {
                    val message = e.message ?: e.toString()
                    ApplicationManager.getApplication().invokeLater {
                        uiCallback?.onProgress(
                            UploadProgress(UploadProgress.Stage.FAILED, message, indicator.fraction)
                        )
                        uiCallback?.onFailure(message)
                        PgyerNotifier.notifyFailure(project, message)
                    }
                }
            }
        }.queue()
    }

    fun buildParamsFromUi(
        apkPath: Path,
        installType: InstallType,
        installPassword: String,
        updateDescription: String,
        appDescription: String,
    ): PgyerUploadParams? {
        val apiKey = PgyerCredentialsStore.getApiKey().trim()
        if (apiKey.isEmpty()) {
            return null
        }
        return PgyerUploadParams(
            apkPath = apkPath,
            apiKey = apiKey,
            installType = installType,
            installPassword = installPassword,
            updateDescription = updateDescription,
            appDescription = appDescription,
        )
    }

    fun validateApk(path: Path?): String? {
        if (path == null || !Files.isRegularFile(path)) {
            return PgyerBundle.message("notify.missingApk")
        }
        if (!path.fileName.toString().endsWith(".apk", ignoreCase = true)) {
            return PgyerBundle.message("notify.invalidApk", path.fileName.toString())
        }
        return null
    }
}
