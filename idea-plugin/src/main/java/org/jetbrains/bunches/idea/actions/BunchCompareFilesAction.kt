package org.jetbrains.bunches.idea.actions

import com.intellij.diff.actions.BaseShowDiffAction
import com.intellij.diff.actions.CompareFileWithEditorAction
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.bunches.idea.util.BunchFileUtils

class BunchCompareFilesAction : CompareFileWithEditorAction() {

    override fun getDiffRequestChain(e: AnActionEvent): DiffRequestChain? {
        val project = e.project ?: return null

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return null
        if (files.size != 1) {
            return null
        }

        if (files.any { !it.isValid }) {
            return null
        }

        val bunchFile = files.singleOrNull() ?: return null

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

        val chain = BaseShowDiffAction.createMutableChainFromFiles(project, baseFile, bunchFile)

        val editorContent = chain.content2

        if (editorContent is DocumentContent) {
            val editors = EditorFactory.getInstance().getEditors(editorContent.document)
            if (editors.isNotEmpty()) {
                val currentLine = editors.first().caretModel.logicalPosition.line
                chain.putRequestUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentLine))
            }
        }

        return chain
    }

    override fun isAvailable(e: AnActionEvent): Boolean {
        val project = e.project ?: return false

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
        val file = files.singleOrNull() ?: return false

        val extensions = BunchFileUtils.bunchExtensions(project) ?: return false

        return file.extension in extensions
    }
}
