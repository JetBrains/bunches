package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.restore.RESTORE_CLEANUP_COMMIT_TITLE
import org.jetbrains.bunches.restore.Settings
import org.jetbrains.bunches.restore.doStepByStepSwitch
import org.jetbrains.bunches.restore.doSwitch

class SwitchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val bunchFile = BunchFileUtils.bunchFile(e.project!!)
        if (bunchFile !== null) {
            val suffixes = BunchFileUtils.bunchExtension(e.project!!)

            val dialog = SwitchDialog(e.project!!, suffixes)
            dialog.show()

            val switchSettings = dialog.getParameters()
            val settings = Settings(e.project?.basePath!!,
                    switchSettings.branch!!,
                    switchSettings.commitMessage!!,
                    true,
                    switchSettings.doCleanup!!)

            if (switchSettings.stepByStep!!) {
                doStepByStepSwitch(suffixes!!, settings)
            } else {
                doSwitch(suffixes!!, settings)
            }
            if (settings.doCleanup) {
                cleanup(org.jetbrains.bunches.cleanup.Settings(
                        settings.repoPath, extension = null, commitTitle = RESTORE_CLEANUP_COMMIT_TITLE, isNoCommit = false))
            }
        } else {
            Messages.showMessageDialog("File '.bunch' not found", "Switch error", Messages.getErrorIcon())
            return
        }

    }
}