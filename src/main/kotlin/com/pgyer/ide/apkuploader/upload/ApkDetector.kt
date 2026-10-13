package com.pgyer.ide.apkuploader.upload

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Finds recently modified APK files under typical Android Gradle output directories.
 */
object ApkDetector {

    private val OUTPUT_HINTS = listOf(
        "build/outputs/apk",
        "build/outputs/bundle",
        "outputs/apk",
    )

    fun findRecentApks(project: Project, limit: Int = 20): List<Path> {
        val basePath = project.basePath ?: return emptyList()
        val root = Paths.get(basePath)
        if (!root.exists()) return emptyList()

        val candidates = mutableListOf<Path>()
        try {
            Files.walk(root, 12).use { stream ->
                stream
                    .filter { it.isRegularFile() }
                    .filter { it.extension.equals("apk", ignoreCase = true) }
                    .filter { path ->
                        val normalized = path.toString().replace('\\', '/')
                        OUTPUT_HINTS.any { hint -> normalized.contains(hint) } ||
                            normalized.contains("/build/") ||
                            normalized.contains("/outputs/")
                    }
                    .forEach { candidates.add(it) }
            }
        } catch (_: Exception) {
            // Fall back to VFS scan if walk fails (permissions, etc.)
            collectFromVfs(basePath, candidates)
        }

        return candidates
            .distinct()
            .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
            .take(limit)
    }

    private fun collectFromVfs(basePath: String, into: MutableList<Path>) {
        val base = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
        VfsUtilCore.iterateChildrenRecursively(base, null) { file: VirtualFile ->
            if (!file.isDirectory && file.name.endsWith(".apk", ignoreCase = true)) {
                into.add(Paths.get(file.path))
            }
            true
        }
    }

    fun formatLabel(path: Path): String {
        val sizeKb = try {
            Files.size(path) / 1024
        } catch (_: Exception) {
            0L
        }
        return "${path.name}  (${sizeKb} KB) — $path"
    }
}
