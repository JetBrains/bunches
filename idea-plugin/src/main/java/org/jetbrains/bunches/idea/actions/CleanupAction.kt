package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.cleanup.Settings
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.idea.util.BunchFileUtils

class CleanupAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val basePath = project.basePath
        if (basePath == null) {
            Messages.showMessageDialog("BasePath not found", "Cleanup error", Messages.getErrorIcon())
            return
        }
        val extensions = BunchFileUtils.bunchExtension(project)
        if (extensions == null) {
            Messages.showMessageDialog("Can't find list of supported bunches", "Cleanup error", Messages.getErrorIcon())
            return
        }
        val dialog = CleanupDialogKt(project, extensions)
        if (!dialog.showAndGet()) {
            return
        }

        val cleanupSettings = dialog.getParameters()
        val settings = Settings(
            basePath,
            cleanupSettings.extension,
            cleanupSettings.commitTitle,
            cleanupSettings.isNoCommit
        )

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Bunch Cleanup", false) {
            override fun run(indicator: ProgressIndicator) {
                cleanup(settings)
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "Cleanup error")
            }
        })
    }
}
