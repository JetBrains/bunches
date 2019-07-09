package org.jetbrains.bunches.idea.vcs

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBScrollPane
import java.awt.GridLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.tree.DefaultMutableTreeNode

class SimpleCheckToolWindow(
    private val toolWindow: ToolWindow,
    forgottenFiles: Set<File>,
    all: Map<PsiFile, List<PsiFile>>,
    private val checkInProjectPanel: CheckinProjectPanel
) {

    private val project = checkInProjectPanel.project
    private val panel: SimpleToolWindowPanel = SimpleToolWindowPanel(false)
    private val filesTree: ForgottenFilesTree = ForgottenFilesTree(
        DefaultMutableTreeNode("root"),
        forgottenFiles, all
    )

    val content: JComponent
        get() = panel

    init {
        panel.apply {
            toolbar = JToolBar()
            setContent(JBScrollPane(filesTree).apply {
                border = null
            })
        }

        setToolBar()

        toolWindow.apply {
            setDefaultContentUiType(ToolWindowContentUiType.TABBED)
            isToHideOnEmptyContent = true
        }
    }

    private fun closeTab() {
        val content = toolWindow.contentManager.getContent(panel) ?: return
        toolWindow.contentManager.removeContent(content, true)
    }

    private fun setToolBar() {
        val actionGroup = DefaultActionGroup()
        actionGroup.addAction(CommitWithoutCheck()).setAsSecondary(false)
        panel.toolbar = JPanel(GridLayout()).apply {
            add(
                ActionManager.getInstance()
                    .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, false).component
            )
        }
    }

    private inner class CommitWithoutCheck :
        AnAction("Commit", "Commit all previously chosen files", AllIcons.Actions.Commit) {

        override fun actionPerformed(e: AnActionEvent) {
            project.bunchFileCheckEnabled = false
            if (CommitChangeListDialog.commitChanges(
                project,
                checkInProjectPanel.selectedChanges,
                null,
                null,
                checkInProjectPanel.commitMessage
                )
            ) {
                closeTab()
            }
            project.bunchFileCheckEnabled = true
        }
    }
}
