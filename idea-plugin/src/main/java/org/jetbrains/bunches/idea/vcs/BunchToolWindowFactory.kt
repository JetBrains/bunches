package org.jetbrains.bunches.idea.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiFile
import com.intellij.ui.content.ContentFactory
import org.jetbrains.bunches.idea.actions.ReduceActionWindow

class BunchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.hide(null)
    }

    companion object {
        private const val reduceDisplayName = "Reduce"

        fun createCommitCheckToolWindowContent(
            toolWindow: ToolWindow,
            files: Set<VirtualFile>,
            all: Map<PsiFile, List<PsiFile>>,
            checkinProjectPanel: CheckinProjectPanel
        ) {
            val window = SimpleCheckToolWindow(toolWindow, files, all, checkinProjectPanel)
            val contentFactory = ContentFactory.SERVICE.getInstance()
            val commitMessage =
                "Commit: " +
                        if (checkinProjectPanel.commitMessage.length > 20)
                            checkinProjectPanel.commitMessage.take(20) + "..."
                        else
                            checkinProjectPanel.commitMessage
            val content = contentFactory.createContent(window.content, commitMessage, false)
            toolWindow.apply {

                contentManager.apply {
                    var index = contentCount
                    contents.filter { it.displayName != reduceDisplayName }.forEach {
                        index = getIndexOfContent(it)
                        removeContent(it, true)
                    }
                    addContent(content, index)
                    setSelectedContent(content)
                }

                isToHideOnEmptyContent = true
                show(null)
            }
        }

        fun createReduceActionWindow(
            toolWindow: ToolWindow,
            project: Project,
            repoPath: String,
            bunchPath: String
        ) {
            val window = ReduceActionWindow(toolWindow, project, repoPath, bunchPath)
            val contentFactory = ContentFactory.SERVICE.getInstance()
            val content = contentFactory.createContent(window.content, reduceDisplayName, false)
            toolWindow.apply {
                contentManager.apply {
                    var index = contentCount
                    val oldContent = findContent(reduceDisplayName)
                    if (oldContent != null) {
                        index = getIndexOfContent(oldContent)
                        removeContent(oldContent, true)
                    }
                    addContent(content, index)
                    setSelectedContent(content)
                }
                isToHideOnEmptyContent = true
                show(null)
            }
        }
    }
}
