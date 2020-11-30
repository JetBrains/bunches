package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.project.Project
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.switch.RESTORE_COMMIT_TITLE
import javax.swing.JComponent

class SwitchDialogKt(project: Project, bunches: Collection<String>, private val vcsRoots: List<String>) :
    SwitchDialog(project) {
    init {
        title = "Switch Bunches"
        if (bunches.isNotEmpty())
            bunches.forEach { comboSwitch.addItem(it) }
        else {
            comboSwitch.addItem("Nothing to show")
            comboSwitch.isEnabled = false
        }
        if (vcsRoots.size > 1) {
            vcsRoots.forEach { vcsRootComboBox.addItem(it) }
        } else {
            vcsRootComboBox.isVisible = false
            vcsRootComboBox.isEnabled = false
            vcsRootLabel.isVisible = false
        }
        textFieldCommitMessage.text = RESTORE_COMMIT_TITLE
    }

    override fun getPreferredFocusedComponent(): JComponent? = comboSwitch

    private fun getVcsRoot(): String =
        when {
            vcsRoots.size == 1 -> vcsRoots.first()
            vcsRoots.size > 1 -> vcsRootComboBox.selectedItem as String
            else -> throw BunchException("No VCS root found for project")
        }

    fun getParameters() =
        SwitchParameters(
            repoPath = getVcsRoot(),
            branch = comboSwitch.selectedItem as String,
            stepByStep = checkStepByStep.isSelected,
            doCleanup = checkCleanup.isSelected,
            commitMessage = textFieldCommitMessage.text
        )
}
