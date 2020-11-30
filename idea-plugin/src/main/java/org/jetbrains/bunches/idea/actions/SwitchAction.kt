package org.jetbrains.bunches.idea.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.idea.util.BunchFileUtils
import org.jetbrains.bunches.switch.Settings
import org.jetbrains.bunches.switch.doSwitch

class SwitchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val basePath = project.basePath
        if (basePath == null) {
            Messages.showMessageDialog("BasePath not found", "Switch error", Messages.getErrorIcon())
            return
        }

        val extensions = BunchFileUtils.bunchExtensions(project)
        if (extensions == null) {
            Messages.showMessageDialog("Can't find list of supported bunches", "Switch error", Messages.getErrorIcon())
            return
        }
        val vcsRoots = BunchFileUtils.getGitRoots(project).mapNotNull { it.path }
        if (vcsRoots.isEmpty()) {
            Messages.showMessageDialog("No git root for project found", "Switch error", Messages.getErrorIcon())
            return
        }
        val dialog = SwitchDialogKt(project, extensions, vcsRoots.map { it.path })
        if (!dialog.showAndGet()) {
            return
        }

        val bunchPath = BunchFileUtils.bunchPath(project)
        if (bunchPath == null) {
            return
        }

        val switchSettings = dialog.getParameters()
        val settings = Settings(
            switchSettings.repoPath,
            bunchPath,
            switchSettings.branch,
            switchSettings.commitMessage,
            switchSettings.stepByStep,
            switchSettings.doCleanup
        )

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Bunch Switch", false) {
                override fun run(indicator: ProgressIndicator) {
                    doSwitch(settings)
                }

                override fun onThrowable(error: Throwable) =
                    Notification(
                        "Bunch tool",
                        "Switch error",
                        error.message ?: "Switch fail",
                        NotificationType.ERROR
                    ).notify(project)

                override fun onSuccess() {
                    BunchFileUtils.updateGitLog(project)
                }
            }
        )
    }
}