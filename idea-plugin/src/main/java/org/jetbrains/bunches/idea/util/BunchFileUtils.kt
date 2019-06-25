@file:Suppress("DEPRECATION")

package org.jetbrains.bunches.idea.util

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.roots.VcsRootDetector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.git.isGitRoot
import org.jetbrains.bunches.reduce.getReducibleFiles
import java.io.File

object BunchFileUtils {
    fun bunchFile(project: Project): VirtualFile? {
        val baseDir = project.baseDir ?: return null
        return baseDir.findChild(BUNCH_FILE_NAME)
    }

    fun vcsRootPath(project: Project) : String? {
        val roots = ServiceManager.getService(project, VcsRootDetector::class.java)
            .detect()
            .filter { v -> isGitRoot(File(simplePath(v))) }

        if (roots.isEmpty()) {
            Messages.showMessageDialog("No vcs roots detected", "Project  error", Messages.getErrorIcon())
            return null
        }

        return simplePath(roots.first())
    }

    fun bunchPath(project: Project) : String? {
        if (project.basePath == null) {
            Messages.showMessageDialog("No project base path found", "Project  error", Messages.getErrorIcon())
            return null
        }
        return project.basePath.toString()
    }

    fun bunchExtensions(project: Project): Set<String>? {
        val bunchFile: VirtualFile = bunchFile(project) ?: return null
        val extensions = bunchExtensionsWithCache(bunchFile)
        if (extensions.isEmpty()) return null
        return extensions
    }

    private class ExtensionsData(val timeStamp: Long, val extensions: Set<String>)

    private val bunchFileCache = ContainerUtil.createConcurrentWeakMap<VirtualFile, ExtensionsData>()

    private fun bunchExtensionsWithCache(bunchFile: VirtualFile): Set<String> {
        val timeStamp = bunchFile.timeStamp
        val extensionsData = bunchFileCache[bunchFile]
        if (extensionsData != null && extensionsData.timeStamp == timeStamp) {
            return extensionsData.extensions
        }

        val extensions = bunchExtensions(bunchFile)
        bunchFileCache[bunchFile] = ExtensionsData(timeStamp, extensions)

        val invalidFiles = bunchFileCache.keys.filter { !it.isValid }
        if (invalidFiles.isEmpty()) {
            for (invalidFile in invalidFiles) {
                bunchFileCache.remove(invalidFile)
            }
        }

        return extensions
    }

    private fun bunchExtensions(bunchFile: VirtualFile): Set<String> {
        val file = File(bunchFile.path)
        if (!file.exists()) return emptySet()

        val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return emptySet()

        return lines.drop(1).map { it.split('_').first() }.toSet()
    }

    private fun simplePath(root: VcsRoot) : String {
        return root.path.toString().removePrefix("file:")
    }
}