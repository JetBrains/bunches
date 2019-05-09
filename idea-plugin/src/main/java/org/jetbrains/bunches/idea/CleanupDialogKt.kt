package org.jetbrains.bunches.idea

import com.intellij.openapi.project.Project
import org.jetbrains.bunches.cleanup.DEFAULT_CLEANUP_COMMIT_TITLE
import javax.swing.JComponent

class CleanupDialogKt(project: Project, extensions: List<String>) : CleanupDialog(project) {
    init {
        title = "Cleanup Bunches"
        if (extensions.isNotEmpty())
            extensions.forEach { comboCleanup.addItem(it) }
        else {
            comboCleanup.addItem("Nothing to show")
            comboCleanup.isEnabled = false
        }
        textFieldCommitMessage.text = DEFAULT_CLEANUP_COMMIT_TITLE

        checkNoCommit.addActionListener {
            panelCommitMessage.isVisible = !checkNoCommit.isSelected
        }
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return comboCleanup
    }

    fun getParameters(): CleanupParameters {
        return CleanupParameters(
            comboCleanup.selectedItem as String,
            textFieldCommitMessage.text,
            checkNoCommit.isSelected
        )
    }
}