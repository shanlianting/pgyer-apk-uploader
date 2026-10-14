package com.pgyer.ide.apkuploader.notifications

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.ide.BrowserUtil
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.upload.PgyerUploadResult

object PgyerNotifier {
    private const val GROUP_ID = "Pgyer APK Uploader"

    fun notifySuccess(project: Project?, result: PgyerUploadResult) {
        val versionLabel = listOf(result.buildVersion, result.buildBuildVersion)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
            .ifBlank { result.buildKey }
        val name = result.buildName.ifBlank { "APK" }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                PgyerBundle.message("notify.success.title"),
                PgyerBundle.message("notify.success.body", name, versionLabel),
                NotificationType.INFORMATION,
            )
        if (result.installPageUrl.isNotBlank()) {
            notification.addAction(
                NotificationAction.createSimple("Open install page") {
                    BrowserUtil.browse(result.installPageUrl)
                }
            )
        }
        if (result.qrCodeUrl.isNotBlank()) {
            notification.addAction(
                NotificationAction.createSimple("Open QR code") {
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
