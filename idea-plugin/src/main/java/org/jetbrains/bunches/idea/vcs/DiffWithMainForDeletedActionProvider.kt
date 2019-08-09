package org.jetbrains.bunches.idea.vcs

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bunches.idea.util.BunchFileUtils
import java.io.File

@Suppress("UnstableApiUsage")
class DiffWithMainForDeletedActionProvider : AnAction(), DumbAware, AnActionExtensionProvider {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return

        val change = e.getChange() ?: return
        val revision = change.afterRevision ?: change.beforeRevision ?: return
        val file = revision.file
        val mainFile = getMainFile(e.project, file.ioFile.absolutePath) ?: return

        val text = revision.content ?: return
        val diffContentFactory = DiffContentFactoryImpl.getInstance()
        val request: DiffRequest = SimpleDiffRequest(
            "Comparison with local main file ${mainFile.path}", diffContentFactory.create(project, mainFile),
            diffContentFactory.create(text),
            "Local ${mainFile.name}", "${file.ioFile.name} from ${revision.revisionNumber.asString()}"
        )

        DiffManager.getInstance().showDiff(project, request)
    }

    override fun update(e: AnActionEvent) {
        if (isActive(e)) {
            e.presentation.icon = AllIcons.Vcs.Branch
            e.presentation.text = "Compare with main file"
            e.presentation.description = "Compare bunch file with local main file"
        }
    }

    override fun isActive(e: AnActionEvent): Boolean {
        val change = e.getChange() ?: return false

        val deletedFile = change.afterRevision?.file ?: change.beforeRevision?.file ?: return false
        if (deletedFile.ioFile.exists()) return false

        getMainFile(e.project, deletedFile.ioFile.absolutePath) ?: return false
        return true
    }

    private fun AnActionEvent.getChange(): Change? {
        return this.getRequiredData(VcsDataKeys.SELECTED_CHANGES).singleOrNull()
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
}