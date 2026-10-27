package com.pgyer.ide.apkuploader.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.pgyer.ide.apkuploader.PgyerBundle
import com.pgyer.ide.apkuploader.upload.PgyerUploadResult
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Modal success dialog showing app name, version, QR code, and install link.
 */
class PgyerUploadResultDialog(
    project: Project?,
    private val result: PgyerUploadResult,
) : DialogWrapper(project) {

    private val resultView = PgyerUploadResultView()

    init {
        title = PgyerBundle.message("result.dialog.title")
        init()
        resultView.showResult(result)
        setOKButtonText(PgyerBundle.message("result.dialog.close"))
    }

    override fun createCenterPanel(): JComponent {
        return resultView.apply {
            // Room for ~280px QR beside app metadata.
            preferredSize = Dimension(720, 420)
        }
    }

    override fun createActions() = arrayOf(okAction)

    companion object {
        fun show(project: Project?, result: PgyerUploadResult) {
            PgyerUploadResultDialog(project, result).show()
        }
    }
}
