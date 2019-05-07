package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.restore.Settings

class SwitchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val basePath = project.basePath
        if (basePath == null) {
            Messages.showMessageDialog("BasePath not found", "Switch error", Messages.getErrorIcon())
            return
        }

        val suffixes = BunchFileUtils.bunchExtension(project)
        if (suffixes == null) {
            Messages.showMessageDialog("Can't find list of supported bunches", "Switch error", Messages.getErrorIcon())
            return
        }

        val dialog = SwitchDialogKt(project, suffixes)
        if (!dialog.showAndGet()) {
            return
        }

        val switchSettings = dialog.getParameters()
        val settings = Settings(basePath,
                switchSettings.branch,
                switchSettings.commitMessage,
                switchSettings.stepByStep,
                switchSettings.doCleanup)

        val task = BackgroundSwitch(project, suffixes, settings)
        val progressIndicator = BackgroundableProcessIndicator(task)
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
                task,
                progressIndicator
        )

    }
}