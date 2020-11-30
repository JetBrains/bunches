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
    fun bunchFile(project: Project): VirtualFile? =
        project.baseDir?.findChild(BUNCH_FILE_NAME) // baseDir is deprecated!

    fun getGitRoots(project: Project): List<VcsRoot> =
        ServiceManager
            .getService(project, VcsRootDetector::class.java)
            .detect()
            .filter { v -> isGitRoot(File(simplePath(v))) }

    fun vcsRootPath(project: Project): String? =
        getGitRoots(project)
            .takeIf { it.isNotEmpty() }
            ?.let {
                simplePath(it.first())
            }
            ?: null.also {
                Messages.showMessageDialog(
                    "No VCS roots detected",
                    "Project  error",
                    Messages.getErrorIcon()
                )
            }

    fun isBunchFile(file: VirtualFile, project: Project): Boolean =
        bunchExtensions(project, true)
            ?.let { file.extension in it }
            ?: false

    fun getMainFile(file: VirtualFile, project: Project): VirtualFile? =
        takeIf { isBunchFile(file, project) }
            ?.let {
                VirtualFileManager.getInstance()
                    .findFileByUrl(file.url.substringBeforeLast('.'))
            }

    fun getAllBunchFiles(file: VirtualFile, project: Project, includeMain: Boolean = false): List<VirtualFile> =
        bunchExtensions(project, includeMain)
            ?.let { extensions ->
                extensions.mapNotNull {
                    VirtualFileManager.getInstance().findFileByUrl(file.url + ".$it")
                }
            }
            ?: emptyList()

    fun bunchPath(project: Project): String? =
        project.basePath
            ?: null.also {
                Messages.showMessageDialog(
                    "No project base path found",
                    "Project  error",
                    Messages.getErrorIcon()
                )
            }

    fun bunchExtensions(project: Project, includeMain: Boolean = false): Set<String>? =
        bunchFile(project)
            ?.let { bunchFile ->
                bunchExtensionsWithCache(bunchFile)
                    .let { allExtensions ->
                        if (includeMain) allExtensions else allExtensions.drop(1).toSet()
                    }
                    .takeIf { it.isNotEmpty() }
            }

    private data class ExtensionsData(val timeStamp: Long, val extensions: Set<String>)

    private val bunchFileCache = ContainerUtil.createConcurrentWeakMap<VirtualFile, ExtensionsData>()

    private fun bunchExtensionsWithCache(bunchFile: VirtualFile): Set<String> =
        bunchFile.timeStamp.let { ts ->
            bunchFileCache[bunchFile]
                ?.takeIf { it.timeStamp == ts }
                ?.extensions
                ?: bunchExtensions(bunchFile)
                    .also { extensions ->
                        bunchFileCache[bunchFile] = ExtensionsData(ts, extensions)
                    }
                    .also { _ ->
                        bunchFileCache.keys
                            .filter { !it.isValid }
                            .forEach { invalidFile -> bunchFileCache.remove(invalidFile) }
                    }
        }

    private fun bunchExtensions(bunchFile: VirtualFile): Set<String> =
        File(bunchFile.path).takeIf { it.exists() }
            ?.let {
                it.readLines()
                    .map { line -> line.trim() }
                    .filter { line -> line.isNotEmpty() }
                    .takeIf { lines -> lines.size > 1 }
            }?.map {
                it.split('_').first()
            }?.toSet()
            ?: emptySet()

    private fun simplePath(root: VcsRoot): String =
        root.path.toString().removePrefix("file:")

    fun toPsiFile(file: File, project: Project): PsiFile? =
        LocalFileSystem.getInstance().findFileByIoFile(file)
            ?.let { virtualFile -> PsiManager.getInstance(project).findFile(virtualFile) }

    fun getMainFile(file: PsiFile): PsiFile? =
        toPsiFile(File(file.parent?.virtualFile?.path, file.virtualFile.nameWithoutExtension), file.project)


    fun updateGitLog(project: Project) =
        VcsProjectLog.getInstance(project).dataManager
            ?.let {
                it.refresh(
                    getGitRoots(project).map { root -> root.path }
                )
            }

    fun refreshFileSystem(files: List<VirtualFile>) =
        LocalFileSystem.getInstance().refreshFiles(files)
}