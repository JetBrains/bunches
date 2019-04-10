package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.bunches.restore.RESTORE_COMMIT_TITLE
import javax.swing.*

class SwitchDialog(project: Project, bunches: List<String>?) : DialogWrapper(project, false) {
    private var contentPane: JPanel? = null
    private var comboSwitch: JComboBox<String>? = null
    private var checkStepByStep: JCheckBox? = null
    private var checkCleanup: JCheckBox? = null
    private var textFieldCommitMessage: JTextField? = null

    init {
        init()
        title = "Switch Bunches"
        when {
            bunches != null -> bunches.forEach { comboSwitch!!.addItem(it) }
            else -> {
                comboSwitch!!.addItem("Nothing to show")
                comboSwitch!!.isEnabled = false
            }
        }
        textFieldCommitMessage!!.text = RESTORE_COMMIT_TITLE

    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return comboSwitch
    }

    override fun createCenterPanel(): JComponent? {
        return contentPane
    }

    fun getParameters(): SwitchParameters {
        return SwitchParameters(
                comboSwitch?.selectedItem as? String,
                checkStepByStep?.isSelected,
                checkCleanup?.isSelected,
                textFieldCommitMessage?.text
        )
    }
}
