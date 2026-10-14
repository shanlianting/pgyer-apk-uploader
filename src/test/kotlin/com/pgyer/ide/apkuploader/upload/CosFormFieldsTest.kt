package com.pgyer.ide.apkuploader.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CosFormFieldsTest {
    @Test
    fun fieldOrderMatchesOfficialShellDemo() {
        val fields = PgyerApiClient.buildCosFormFields(
            uploadKey = "apps/a/b/c.apk",
            signature = "sig",
            securityToken = "token",
            fileName = "app-release.apk",
        )
        assertEquals(
            listOf("key", "signature", "x-cos-security-token", "x-cos-meta-file-name"),
            fields.keys.toList(),
        )
        assertEquals("apps/a/b/c.apk", fields["key"])
        assertEquals("app-release.apk", fields["x-cos-meta-file-name"])
    }

    @Test
    fun sanitizeFileNameRemovesUnsafeCharacters() {
        assertEquals("_____v1.apk", PgyerApiClient.sanitizeFileName("我的应用 v1.apk"))
        assertEquals("___.apk", PgyerApiClient.sanitizeFileName("@@@"))
        assertEquals("release.apk", PgyerApiClient.sanitizeFileName("release.apk"))
        assertEquals("app.apk", PgyerApiClient.sanitizeFileName(""))
    }

    @Test
    fun http400IsNotTransient() {
        assertFalse(PgyerApiClient.isTransientNetworkError(CosUploadException(400, "bad request")))
        assertTrue(PgyerApiClient.isTransientNetworkError(java.io.IOException("HTTP/1.1 header parser received no bytes")))
    }
}
