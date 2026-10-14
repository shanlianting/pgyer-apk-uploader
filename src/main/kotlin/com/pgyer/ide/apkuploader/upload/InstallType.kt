package com.pgyer.ide.apkuploader.upload

import com.pgyer.ide.apkuploader.PgyerBundle

enum class InstallType(val code: Int) {
    PUBLIC(1),
    PASSWORD(2),
    INVITE(3);

    override fun toString(): String = when (this) {
        PUBLIC -> PgyerBundle.message("installType.public")
        PASSWORD -> PgyerBundle.message("installType.password")
        INVITE -> PgyerBundle.message("installType.invite")
    }

    companion object {
        fun fromCode(code: Int): InstallType =
            entries.firstOrNull { it.code == code } ?: PUBLIC
    }
}
