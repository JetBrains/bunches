package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import org.jetbrains.bunches.idea.util.BunchFileUtils.bunchPath
import org.jetbrains.bunches.idea.util.BunchFileUtils.vcsRootPath
import org.jetbrains.bunches.reduce.*
import org.jetbrains.bunches.reduce.ReduceAction

class ReduceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project
        if (project == null) {
            return
        }

        val repoPath = vcsRootPath(project)
        if (repoPath == null) {
            return
        }

        val bunchPath = bunchPath(project)
        if (bunchPath == null) {
            return
        }


        val files = getReducibleFiles(repoPath, bunchPath)
        val dialog = ReduceDialogKt(project, files)

        if (dialog.showAndGet()) {
            val settings = if (dialog.isCommitNeeded())
                Settings(repoPath, bunchPath, ReduceAction.COMMIT, DEFAULT_REDUCE_COMMIT_TITLE)
            else
                Settings(repoPath, bunchPath, ReduceAction.DELETE, DEFAULT_REDUCE_COMMIT_TITLE)

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Reduce", false) {
                    override fun run(indicator: ProgressIndicator) {
                        doReduce(settings)
                    }
                })
        }
    }

}