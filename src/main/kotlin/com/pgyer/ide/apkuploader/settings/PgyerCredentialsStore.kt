package com.pgyer.ide.apkuploader.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores the Pgyer API Key in the IDE PasswordSafe (OS keychain / encrypted storage).
 *
 * API 2.0 authenticates with `_api_key` only. Legacy User Key is not required for upload.
 */
object PgyerCredentialsStore {
    private const val SUBSYSTEM = "PgyerApkUploader"
    private const val API_KEY_USER = "apiKey"
    private const val INSTALL_PASSWORD_USER = "defaultInstallPassword"

    private fun attributes(user: String): CredentialAttributes =
        CredentialAttributes(generateServiceName(SUBSYSTEM, user))

    fun getApiKey(): String =
        PasswordSafe.instance.getPassword(attributes(API_KEY_USER)).orEmpty()

    fun setApiKey(apiKey: String) {
        val attrs = attributes(API_KEY_USER)
        if (apiKey.isBlank()) {
            PasswordSafe.instance.set(attrs, null)
        } else {
            PasswordSafe.instance.set(attrs, Credentials(API_KEY_USER, apiKey))
        }
    }

    fun getDefaultInstallPassword(): String =
        PasswordSafe.instance.getPassword(attributes(INSTALL_PASSWORD_USER)).orEmpty()

    fun setDefaultInstallPassword(password: String) {
        val attrs = attributes(INSTALL_PASSWORD_USER)
        if (password.isBlank()) {
            PasswordSafe.instance.set(attrs, null)
        } else {
            PasswordSafe.instance.set(attrs, Credentials(INSTALL_PASSWORD_USER, password))
        }
    }
}
