package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.bunches.idea.util.BunchFileUtils
import org.jetbrains.bunches.idea.vcs.BunchToolWindowFactory
import java.io.File

class ReduceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val window = ToolWindowManager.getInstance(project).getToolWindow("Bunch Tool") ?: return
        val repoPath = BunchFileUtils.vcsRootPath(project)
        if (repoPath == null) {
            Messages.showMessageDialog("No git root found", "Reduce error", Messages.getErrorIcon())
            return
        }
        val bunchPath = File(BunchFileUtils.bunchPath(project), ".bunch")
        if (!bunchPath.exists()) {
            Messages.showMessageDialog("No .bunch file found", "Reduce error", Messages.getErrorIcon())
            return
        }
        ServiceManager.getService(project, BunchToolWindowFactory::class.java)
            .createReduceActionWindow(window, project, repoPath, bunchPath.parentFile.absolutePath)
    }
}