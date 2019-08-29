package org.jetbrains.bunches.idea.actions

import com.intellij.diff.actions.BaseShowDiffAction
import com.intellij.diff.actions.CompareFileWithEditorAction
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bunches.idea.util.BunchFileUtils.getMainFile
import org.jetbrains.bunches.idea.util.BunchFileUtils.isBunchFile
import org.jetbrains.bunches.idea.util.getVirtualFile

open class BunchCompareFilesAction : CompareFileWithEditorAction() {

    init {
        val presentation = templatePresentation
        presentation.text = "Compare with main file"
        presentation.description = "Show the difference with main file"
        presentation.icon = AllIcons.Actions.Diff
    }

    open fun getFile(e: AnActionEvent): VirtualFile? {
        return e.getVirtualFile()
    }

    override fun getDiffRequestChain(e: AnActionEvent): DiffRequestChain? {
        val project = e.project ?: return null
        val bunchFile = getFile(e) ?: return null
        val baseFile = getMainFile(bunchFile, project)

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
        val file = getFile(e) ?: return false

        return isBunchFile(file, project)
    }
}
