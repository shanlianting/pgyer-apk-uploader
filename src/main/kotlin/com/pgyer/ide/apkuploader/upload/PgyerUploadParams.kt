package com.pgyer.ide.apkuploader.upload

import java.nio.file.Path

data class PgyerUploadParams(
    val apkPath: Path,
    val apiKey: String,
    val installType: InstallType = InstallType.PUBLIC,
    val installPassword: String = "",
    val updateDescription: String = "",
    val appDescription: String = "",
)

data class PgyerUploadResult(
    val buildKey: String,
    val buildName: String,
    val buildVersion: String,
    val buildBuildVersion: String,
    val shortcutUrl: String,
    val installPageUrl: String,
    val qrCodeUrl: String,
)
