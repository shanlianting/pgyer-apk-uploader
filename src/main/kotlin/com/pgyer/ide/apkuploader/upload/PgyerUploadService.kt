package com.pgyer.ide.apkuploader.upload

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.notifications.PgyerNotifier
import com.pgyer.ide.apkuploader.settings.PgyerCredentialsStore
import com.pgyer.ide.apkuploader.settings.PgyerSettingsState
import java.nio.file.Files
import java.nio.file.Path

object PgyerUploadService {

    fun startUpload(project: Project?, params: PgyerUploadParams) {
        object : Task.Backgroundable(project, "Uploading APK to Pgyer", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = PgyerBundle.message("progress.gettingToken")
                try {
                    val client = PgyerApiClient()
                    val result = client.upload(params) { message ->
                        indicator.checkCanceled()
                        indicator.text = message
                    }
                    ApplicationManager.getApplication().invokeLater {
                        PgyerSettingsState.getInstance().lastApkPath = params.apkPath.toString()
                        PgyerNotifier.notifySuccess(project, result)
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        PgyerNotifier.notifyFailure(project, e.message ?: e.toString())
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
