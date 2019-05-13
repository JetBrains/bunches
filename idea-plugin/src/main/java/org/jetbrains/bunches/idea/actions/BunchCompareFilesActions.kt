package org.jetbrains.bunches.idea.actions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.actions.CompareFilesAction
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bunches.idea.util.BunchFileUtils

class BunchCompareFilesActions : CompareFilesAction() {
    override fun getDiffRequest(e: AnActionEvent): DiffRequest? {
        val project = e.project ?: return null

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return null
        val extensions = BunchFileUtils.bunchExtension(project) ?: return null
        if (files.any { !it.isValid }) return null

        val file1 = files[0]
        val file2 = files[1]
        val docContent1 = getDocumentContent(project, file1, extensions)
        val docContent2 = getDocumentContent(project, file2, extensions)

        val diffFactory = DiffRequestFactory.getInstance()

        return SimpleDiffRequest(
            diffFactory.getTitle(file1, file2),
            docContent1 ?: return null,
            docContent2 ?: return null,
            diffFactory.getTitle(file1),
            diffFactory.getTitle(file2)
        )
    }

    override fun isAvailable(e: AnActionEvent): Boolean {
        val project = e.project ?: return false

        val extensions = BunchFileUtils.bunchExtension(project) ?: return false

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false

        if (files.size != 2) return false

        if (files.any { it.extension in extensions }) {
            return true
        }
        return false
    }

    fun getBunchFileRealExtension(file: VirtualFile, bunchExtensions: List<String>): String {
        val nameParts = file.name.split('.')

        return if (nameParts.last() in bunchExtensions) {
            nameParts[nameParts.lastIndex - 1]
        } else {
            nameParts.last()
        }
    }

    fun getDocumentContent(project: Project, file: VirtualFile, extensions: List<String>): DiffContent? {
        val document = FileDocumentManager.getInstance()
            .getDocument(file) ?: return null

        val fileType = FileTypeManager
            .getInstance()
            .getFileTypeByExtension(
                getBunchFileRealExtension(file, extensions)
            )

        val diffContent = DiffContentFactory.getInstance()
            .create(
                project,
                document.text,
                fileType
            )
        return diffContent
    }
}
