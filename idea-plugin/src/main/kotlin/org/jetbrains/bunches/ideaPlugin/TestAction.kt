package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.bunches.ideaPlugin.*

class TestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val td = TestDialog()
        td.pack()
        td.setLocationRelativeTo(null)
        td.title = "Hello world!"
        td.isVisible = true
    }

}