package com.pgyer.ide.apkuploader.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PgyerApiClientNetworkTest {
    @Test
    fun detectsHeaderParserErrorAsTransient() {
        val error = IOException("HTTP/1.1 header parser received no bytes")
        assertTrue(PgyerApiClient.isTransientNetworkError(error))
        val wrapped = PgyerApiClient.wrapNetworkError("COS upload", error)
        assertTrue(wrapped.message!!.contains("retry", ignoreCase = true))
    }

    @Test
    fun nonNetworkErrorsAreNotTransient() {
        assertFalse(PgyerApiClient.isTransientNetworkError(IllegalArgumentException("bad apk")))
    }
}
