package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.bunches.restore.RESTORE_COMMIT_TITLE
import javax.swing.*

class SwitchDialog(project: Project, bunches: List<String>?) : DialogWrapper(project, false) {
    private lateinit var contentPane: JPanel
    private lateinit var comboSwitch: JComboBox<String>
    private lateinit var checkStepByStep: JCheckBox
    private lateinit var checkCleanup: JCheckBox
    private lateinit var textFieldCommitMessage: JTextField

    init {
        init()
        title = "Switch Bunches"
        if (bunches != null && bunches.isNotEmpty()) bunches.forEach { comboSwitch.addItem(it) }
        else {
            comboSwitch.addItem("Nothing to show")
            comboSwitch.isEnabled = false
        }
        textFieldCommitMessage.text = RESTORE_COMMIT_TITLE

    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return comboSwitch
    }

    override fun createCenterPanel(): JComponent? {
        return contentPane
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
