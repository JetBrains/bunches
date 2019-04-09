package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList

class SwitchAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val dialog = SwitchDialog()
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.title = "Switch bunches"
        dialog.isVisible = true
    }
}