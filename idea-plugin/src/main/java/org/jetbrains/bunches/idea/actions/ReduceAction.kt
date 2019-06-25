package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.reduce.DEFAULT_REDUCE_COMMIT_TITLE
import org.jetbrains.bunches.reduce.ReduceAction
import org.jetbrains.bunches.reduce.Settings
import org.jetbrains.bunches.reduce.doReduce
import java.io.File

class ReduceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project
        if (project == null) {
            return
        }

        if (project.basePath == null) {
            return
        }
        
        val path = File(project.basePath).parent
        if (path == null) {
            return
        }

        val files = doReduce(Settings(path, ReduceAction.PRINT, DEFAULT_REDUCE_COMMIT_TITLE))

        val dialog = ReduceDialogKt(project, files)

        if (dialog.showAndGet()) {
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Reduce", false) {
                override fun run(indicator: ProgressIndicator) {
                    if (dialog.isCommitNeeded()) {
                        doReduce(Settings(path, ReduceAction.COMMIT, DEFAULT_REDUCE_COMMIT_TITLE ))
                    } else {
                        doReduce(Settings(path, ReduceAction.DELETE, DEFAULT_REDUCE_COMMIT_TITLE ))
                    }
                }
            } )
        }
    }

}