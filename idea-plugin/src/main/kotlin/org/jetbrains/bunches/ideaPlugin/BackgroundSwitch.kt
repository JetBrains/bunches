package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.restore.*


class BackgroundSwitch(project: Project,
                       val settings: Settings
) : Task.Backgroundable(project, "Switch") {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        try {
            val suffixes = getRuleSuffixes(settings)
            if (suffixes.isEmpty()) {
                exitWithError("Suffixes not found for rule ${settings.rule}")
            }
            if (suffixes.size != 1) {
                if (settings.step) {
                    this.title = "Step by step switch"
                    doStepByStepSwitch(suffixes, settings)
                } else {
                    doSwitch(suffixes, settings)
                }
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
