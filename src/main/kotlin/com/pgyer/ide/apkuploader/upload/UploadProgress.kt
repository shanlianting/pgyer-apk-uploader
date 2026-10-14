package com.pgyer.ide.apkuploader.upload

/**
 * UI-facing upload progress. [fraction] is in `0.0 .. 1.0`.
 */
data class UploadProgress(
    val stage: Stage,
    val message: String,
    val fraction: Double,
) {
    enum class Stage {
        TOKEN,
        UPLOAD,
        PROCESS,
        DONE,
        FAILED,
    }

    val percent: Int
        get() = (fraction.coerceIn(0.0, 1.0) * 100).toInt()
}
