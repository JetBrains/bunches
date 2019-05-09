package org.jetbrains.bunches.idea

import com.intellij.openapi.project.Project
import org.jetbrains.bunches.restore.RESTORE_COMMIT_TITLE
import javax.swing.JComponent

class SwitchDialogKt(project: Project, bunches: List<String>) : SwitchDialog(project) {
    init {
        title = "Switch Bunches"
        if (bunches.isNotEmpty())
            bunches.forEach { comboSwitch.addItem(it) }
        else {
            comboSwitch.addItem("Nothing to show")
            comboSwitch.isEnabled = false
        }
        textFieldCommitMessage.text = RESTORE_COMMIT_TITLE

    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return comboSwitch
    }

    fun getParameters(): SwitchParameters {
        return SwitchParameters(
            comboSwitch.selectedItem as String,
            checkStepByStep.isSelected,
            checkCleanup.isSelected,
            textFieldCommitMessage.text
        )
    }
}
