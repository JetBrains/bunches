package org.jetbrains.bunches.idea.actions

import com.intellij.codeInsight.actions.FormatChangedTextUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diff.impl.patch.TextPatchBuilder
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.bunches.idea.util.BunchFileUtils.getAllBunchFiles
import org.jetbrains.bunches.idea.util.BunchFileUtils.isBunchFile

open class ApplyChangesAction : AnAction(
    "Apply changes to all",
    "Applying file changes to all unmodified bunch files",
    AllIcons.Vcs.Patch
) {
    protected fun applyMainToAll(file: VirtualFile, project: Project): List<String> {
        val change = getChanges(file, project) ?: return listOf()
        return getAllBunchFiles(file, project).filter {
            getChanges(it, project) == null && applyPatch(
                change,
                it,
                project
            )
        }.mapNotNull { it.extension }
    }

    protected fun showInfoBalloon(list: List<String>, file: VirtualFile, project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val text = if (list.isEmpty()) {
            "No bunch files changed"
        } else {
            "Changes from ${file.name} applied to:\n" + list.joinToString(", ") { ".$it" }
        }
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
                text, MessageType.INFO, null
            )
            .setFadeoutTime(2500)
            .createBalloon()
            .show(
                RelativePoint.getSouthEastOf(statusBar.component),
                Balloon.Position.above
            )
    }

    protected open fun getChanges(file: VirtualFile, project: Project): Change? {
        return ChangeListManagerImpl.getInstanceImpl(project).getChange(file)
    }

    private fun applyPatch(change: Change, file: VirtualFile, project: Project): Boolean {
        val beforeContent = change.beforeRevision?.content ?: return false
        val afterContent = change.afterRevision?.content ?: return false

        if (change.fileStatus != FileStatus.MODIFIED) {
            return false
        }

        val document = FileDocumentManager.getInstance().getDocument(file) ?: return false

        val appliedPatch = GenericPatchApplier.apply(
            document.text,
            TextPatchBuilder.buildPatchHunks(beforeContent, afterContent)
        )
        if (appliedPatch != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                FormatChangedTextUtil.getInstance()
                    .runHeavyModificationTask(project, document) { document.setText(appliedPatch.patchedText) }
                FileDocumentManager.getInstance().saveDocument(document)
            }
            return true
        }
        return false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val file = files.singleOrNull {
            it.isValid
        } ?: return

        if (!isBunchFile(file, project)) {
            val list = applyMainToAll(file, project)
            showInfoBalloon(list, file, project)
        }
    }
}