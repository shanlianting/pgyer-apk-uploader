package com.pgyer.ide.apkuploader.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallTypeTest {
    @Test
    fun fromCodeMapsKnownValues() {
        assertEquals(InstallType.PUBLIC, InstallType.fromCode(1))
        assertEquals(InstallType.PASSWORD, InstallType.fromCode(2))
        assertEquals(InstallType.INVITE, InstallType.fromCode(3))
        assertEquals(InstallType.PUBLIC, InstallType.fromCode(99))
    }
}
