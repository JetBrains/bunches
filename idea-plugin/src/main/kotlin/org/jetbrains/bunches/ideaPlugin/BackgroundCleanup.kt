package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.cleanup.Settings
import org.jetbrains.bunches.cleanup.cleanup

class BackgroundCleanup(project: Project,
                        val settings: Settings) : Task.Backgroundable(project, "Cleanup") {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        try {
            cleanup(settings)
        } catch (e: Throwable) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, e.message, "Cleanup error")
            }
        }
    }

}