package org.jetbrains.bunches.idea.vcs

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bunches.idea.util.BunchFileUtils
import java.io.File

@Suppress("UnstableApiUsage")
class DiffWithMainAction : AnAction(
    "Compare with main file",
    "Compare bunch file with the local main file",
    AllIcons.Vcs.Branch
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return

        val change = e.getChange() ?: return
        val revision = change.getRevision() ?: return
        val file = revision.file
        val mainFile = getMainFile(e.project, file.ioFile.absolutePath)

        val text = revision.content ?: return
        val diffContentFactory = DiffContentFactoryImpl.getInstance()

        val request: DiffRequest =
            if (mainFile != null) {
                SimpleDiffRequest(
                    "Comparison with the local main file ${mainFile.path}", diffContentFactory.create(project, mainFile),
                    diffContentFactory.create(text),
                    "Local ${mainFile.name}", "${file.name} from ${revision.revisionNumber.asString()}"
                )
            } else {
                ErrorDiffRequest(
                    "Comparison with the local main file ${file.parentPath}${file.ioFile.nameWithoutExtension}",
                    "Failed to find local main file content"
                )
            }
        DiffManager.getInstance().showDiff(project, request)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isActive(e)
    }

    private fun isActive(e: AnActionEvent): Boolean {
        val revision = e.getChange()?.getRevision() ?: return false
        val extension = revision.file.ioFile.extension
        val bunchExtensions = BunchFileUtils.bunchExtensions(e.project ?: return false) ?: return false
        return extension in bunchExtensions
    }

    private fun AnActionEvent.getChange(): Change? {
        return this.getData(VcsDataKeys.SELECTED_CHANGES)?.singleOrNull()
    }

    private fun getMainFile(project: Project?, path: String): VirtualFile? {
        if (project == null) return null
        val extensions = BunchFileUtils.bunchExtensions(project) ?: return null
        val bunchFile = File(path)
        if (bunchFile.extension !in extensions) {
            return null
        }
        val mainFile = File(bunchFile.parentFile.absolutePath, bunchFile.nameWithoutExtension)
        return LocalFileSystem.getInstance().findFileByIoFile(mainFile)
    }

    private fun Change.getRevision(): ContentRevision? {
        return afterRevision ?: beforeRevision
    }
}