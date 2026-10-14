package com.pgyer.ide.apkuploader.notifications

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.ide.BrowserUtil
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.ui.PgyerUploadResultDialog
import com.pgyer.ide.apkuploader.upload.PgyerUploadResult

object PgyerNotifier {
    private const val GROUP_ID = "Pgyer APK Uploader"

    fun notifySuccess(project: Project?, result: PgyerUploadResult) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                PgyerBundle.message("notify.success.title"),
                PgyerBundle.message("notify.success.body", result.displayName, result.versionLabel),
                NotificationType.INFORMATION,
            )
        notification.addAction(
            NotificationAction.createSimple(PgyerBundle.message("notify.action.showDetails")) {
                PgyerUploadResultDialog.show(project, result)
            }
        )
        if (result.installPageUrl.isNotBlank()) {
            notification.addAction(
                NotificationAction.createSimple(PgyerBundle.message("result.openInstallPage")) {
                    BrowserUtil.browse(result.installPageUrl)
                }
            )
        }
        if (result.qrCodeUrl.isNotBlank()) {
            notification.addAction(
                NotificationAction.createSimple(PgyerBundle.message("result.openQr")) {
                    BrowserUtil.browse(result.qrCodeUrl)
                }
            )
        }
        notification.notify(project)
    }

    fun notifyFailure(project: Project?, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                PgyerBundle.message("notify.failure.title"),
                message,
                NotificationType.ERROR,
            )
            .notify(project)
    }

    fun notifyInfo(project: Project?, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }
}
