package org.jetbrains.bunches.idea.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiFile
import com.intellij.ui.content.ContentFactory

class CheckToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.hide(null)
    }

    companion object {

        fun createToolWindowContent(
            toolWindow: ToolWindow,
            files: Set<VirtualFile>,
            all: Map<PsiFile, List<PsiFile>>,
            checkinProjectPanel: CheckinProjectPanel
        ) {
            val window = SimpleCheckToolWindow(toolWindow, files, all, checkinProjectPanel)
            val contentFactory = ContentFactory.SERVICE.getInstance()
            val content = contentFactory.createContent(window.content, checkinProjectPanel.commitMessage, false)
            toolWindow.apply {
                contentManager.removeAllContents(true)
                contentManager.addContent(content)
                contentManager.setSelectedContent(content)
                isToHideOnEmptyContent = true
                show(null)
            }
        }
    }
}
