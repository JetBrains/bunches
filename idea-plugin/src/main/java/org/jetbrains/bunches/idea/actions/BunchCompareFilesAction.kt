package org.jetbrains.bunches.idea.actions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.actions.CompareFilesAction
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.bunches.idea.util.BunchFileUtils

class BunchCompareFilesAction : CompareFilesAction() {
    override fun getDiffRequest(e: AnActionEvent): DiffRequest? {
        val project = e.project ?: return null

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return null
        if (files.size != 1) return null
        if (files.any { !it.isValid }) return null

        val extensions = BunchFileUtils.bunchExtension(project) ?: return null

        val bunchFile = files.single()
        val baseFile = VirtualFileManager.getInstance().findFileByUrl(bunchFile.url.substringBeforeLast('.'))

        if (baseFile == null) {
            Notification(
                "Bunch tool",
                "Bunch tool error",
                "Base file was not found",
                NotificationType.ERROR
            ).notify(project)

            return null
        }

        val bunchDiffContent = getDocumentContent(project, bunchFile, extensions) ?: return null
        val baseDiffContent = getDocumentContent(project, baseFile, extensions) ?: return null

        val diffFactory = DiffRequestFactory.getInstance()

        return SimpleDiffRequest(
            diffFactory.getTitle(bunchFile, baseFile),
            bunchDiffContent,
            baseDiffContent,
            diffFactory.getTitle(bunchFile),
            diffFactory.getTitle(baseFile)
        )
    }

    override fun isAvailable(e: AnActionEvent): Boolean {
        val project = e.project ?: return false

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
        val file = files.singleOrNull() ?: return false

        val extensions = BunchFileUtils.bunchExtension(project) ?: return false

        return file.extension in extensions
    }

    private fun getBunchFileRealExtension(file: VirtualFile, bunchExtensions: List<String>): String? {
        return if (file.extension in bunchExtensions) {
            file.nameWithoutExtension.substringAfterLast('.').takeIf { !it.isBlank() }
        } else {
            file.extension
        }
    }

    private fun getDocumentContent(project: Project, file: VirtualFile, extensions: List<String>): DiffContent? {
        val bunchFileRealExtension = getBunchFileRealExtension(file, extensions) ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(bunchFileRealExtension)
        return DiffContentFactory.getInstance().create(project, document.text, fileType)
    }
}
