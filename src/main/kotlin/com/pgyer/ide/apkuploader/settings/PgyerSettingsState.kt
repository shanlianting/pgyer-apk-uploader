package com.pgyer.ide.apkuploader.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Non-secret preferences. API Key is stored separately in [PgyerCredentialsStore].
 */
@Service(Service.Level.APP)
@State(name = "PgyerApkUploaderSettings", storages = [Storage("pgyerApkUploader.xml")])
class PgyerSettingsState : PersistentStateComponent<PgyerSettingsState> {

    /** 1 = public, 2 = password, 3 = invite */
    var defaultInstallType: Int = 1
    var defaultUpdateDescription: String = ""
    var defaultAppDescription: String = ""
    /** Last selected APK path (convenience only; not a secret). */
    var lastApkPath: String = ""

    override fun getState(): PgyerSettingsState = this

    override fun loadState(state: PgyerSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): PgyerSettingsState = service()
    }
}
