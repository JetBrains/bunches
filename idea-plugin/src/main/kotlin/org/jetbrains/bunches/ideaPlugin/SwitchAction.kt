package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.restore.Settings
import org.jetbrains.bunches.restore.doSwitch

class SwitchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val basePath = project.basePath
        if (basePath == null) {
            Messages.showMessageDialog("BasePath not found", "Switch error", Messages.getErrorIcon())
            return
        }

        val extensions = BunchFileUtils.bunchExtension(project)
        if (extensions == null) {
            Messages.showMessageDialog("Can't find list of supported bunches", "Switch error", Messages.getErrorIcon())
            return
        }

        val dialog = SwitchDialogKt(project, extensions)
        if (!dialog.showAndGet()) {
            return
        }

        val switchSettings = dialog.getParameters()
        val settings = Settings(basePath,
                switchSettings.branch,
                switchSettings.commitMessage,
                switchSettings.stepByStep,
                switchSettings.doCleanup)

        val task = object : Task.Backgroundable(project, "Switch") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    doSwitch(settings)
                } catch (e: BunchException) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, e.message, "Switch error")
                    }
                }
            }
        }

        val progressIndicator = BackgroundableProcessIndicator(task)
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, progressIndicator)
    }
}