package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class SwitchAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val dialog = SwitchDialog(e.project!!, BunchFileUtils.bunchExtension(e.project!!))
        dialog.show()
        Messages.showMessageDialog(dialog.getParameters().toString(), "Test", Messages.getInformationIcon())
    }
}