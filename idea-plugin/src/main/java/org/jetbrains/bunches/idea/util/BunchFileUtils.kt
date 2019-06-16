package org.jetbrains.bunches.idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import java.io.File

object BunchFileUtils {
    fun bunchFile(project: Project): VirtualFile? {
        @Suppress("DEPRECATION") val baseDir = project.baseDir ?: return null
        return baseDir.findChild(BUNCH_FILE_NAME)
    }

    fun bunchExtensions(project: Project): Set<String>? {
        val bunchFile: VirtualFile = bunchFile(project) ?: return null
        val file = File(bunchFile.path)
        if (!file.exists()) return null

        val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return null

        return lines.drop(1).map { it.split('_').first() }.toSet()
    }
}