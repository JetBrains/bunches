package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.restore.RESTORE_CLEANUP_COMMIT_TITLE
import org.jetbrains.bunches.restore.Settings
import org.jetbrains.bunches.restore.doStepByStepSwitch
import org.jetbrains.bunches.restore.doSwitch


class BackgroundSwitch(project: Project,
                       val suffixes: List<String>,
                       val settings: Settings
) : Task.Backgroundable(project, "Switch") {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        try {
            if (settings.step) {
                this.title = "Step by step switch"
                doStepByStepSwitch(suffixes, settings)
            } else {
                doSwitch(suffixes, settings)
            }

            if (settings.doCleanup) {
                this.title = "Cleanup"
                cleanup(org.jetbrains.bunches.cleanup.Settings(
                        settings.repoPath,
                        extension = null,
                        commitTitle = RESTORE_CLEANUP_COMMIT_TITLE, isNoCommit = false))
            }
        } catch (e: BunchException) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, e.message, "Switch error")
            }
        }
    }

}
