@file:Suppress("DEPRECATION")

package org.jetbrains.bunches.idea.util

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.roots.VcsRootDetector
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.git.isGitRoot
import java.io.File

object BunchFileUtils {
    fun bunchFile(project: Project): VirtualFile? {
        val baseDir = project.baseDir ?: return null
        return baseDir.findChild(BUNCH_FILE_NAME)
    }

    private fun getGitRoots(project: Project): List<VcsRoot> {
        return ServiceManager.getService(project, VcsRootDetector::class.java)
            .detect()
            .filter { v -> isGitRoot(File(simplePath(v))) }
    }

    fun vcsRootPath(project: Project) : String? {
        val roots = getGitRoots(project)

        if (roots.isEmpty()) {
            Messages.showMessageDialog("No vcs roots detected", "Project  error", Messages.getErrorIcon())
            return null
        }

        return simplePath(roots.first())
    }

    fun isBunchFile(file: VirtualFile, project: Project): Boolean {
        return file.extension in ( bunchExtensions(project, true) ?: return false)
    }

    fun getMainFile(file: VirtualFile, project: Project): VirtualFile? {
        if (!isBunchFile(file, project)) {
            return null
        }
        return VirtualFileManager.getInstance().findFileByUrl(file.url.substringBeforeLast('.'))
    }

    fun getAllBunchFiles(file: VirtualFile, project: Project, includeMain: Boolean = false): List<VirtualFile> {
        val extensions = bunchExtensions(project, includeMain) ?: return listOf()
        return extensions.mapNotNull {
            VirtualFileManager.getInstance().findFileByUrl(file.url + ".$it")
        }
    }

    fun bunchPath(project: Project) : String? {
        if (project.basePath == null) {
            Messages.showMessageDialog("No project base path found", "Project  error", Messages.getErrorIcon())
            return null
        }
        return project.basePath.toString()
    }

    fun bunchExtensions(project: Project, includeMain: Boolean = false): Set<String>? {
        val bunchFile: VirtualFile = bunchFile(project) ?: return null
        val allExtensions = bunchExtensionsWithCache(bunchFile)
        val extensions = if (includeMain) allExtensions else allExtensions.drop(1).toSet()
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
        if (invalidFiles.isNotEmpty()) {
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

        return lines.map { it.split('_').first() }.toSet()
    }

    private fun simplePath(root: VcsRoot) : String {
        return root.path.toString().removePrefix("file:")
    }

    fun toPsiFile(file: File, project: Project): PsiFile? {
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }

    fun getMainFile(file: PsiFile): PsiFile? {
        return toPsiFile(File(file.parent?.virtualFile?.path, file.virtualFile.nameWithoutExtension), file.project)
    }

    fun updateGitLog(project: Project) {
        val roots = getGitRoots(project)
        val vcsLogData = VcsProjectLog.getInstance(project).dataManager ?: return
        vcsLogData.refresh(roots.mapNotNull { it.path })
    }

    fun refreshFileSystem(files: List<VirtualFile>) {
        LocalFileSystem.getInstance().refreshFiles(files)
    }
}