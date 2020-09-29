package org.jetbrains.bunches.idea.vcs

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.psi.PsiFile
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.bunches.idea.actions.ApplyChangesAction
import org.jetbrains.bunches.idea.actions.BunchCompareFilesAction
import org.jetbrains.bunches.idea.util.BunchFileUtils
import org.jetbrains.bunches.idea.util.BunchFileUtils.isBunchFile
import java.awt.GridLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.tree.DefaultMutableTreeNode

class SimpleCheckToolWindow(
    private val toolWindow: ToolWindow,
    forgottenFiles: Set<VirtualFile>,
    all: Map<PsiFile, List<PsiFile>>,
    private val checkInProjectPanel: CheckinProjectPanel
) {

    private val allFiles = checkInProjectPanel.virtualFiles
    private val project = checkInProjectPanel.project
    private val mainChanges = checkInProjectPanel.selectedChanges.toList()
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
        setPopupMenu()

        toolWindow.apply {
            setDefaultContentUiType(ToolWindowContentUiType.TABBED)
            setToHideOnEmptyContent(true)
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

    private fun inCommit(file: PsiFile): Boolean {
        return file.virtualFile in allFiles
    }

    private fun isSelected(file: PsiFile): Boolean {
        return file.virtualFile in filesTree.getSelected()
    }

    private fun setPopupMenu() {
        val actionGroup = DefaultActionGroup().apply {
            add(SetCheckedAction())
            add(SetUncheckedAction())
            addSeparator()
            add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE))
            addSeparator()
            add(CreateAndApplyAction())
            add(DiffAction())
        }
        PopupHandler.installPopupHandler(filesTree, actionGroup, ActionPlaces.POPUP, ActionManager.getInstance())
    }

    private inner class CommitWithoutCheck :
        AnAction("Commit", "Commit previously chosen and added files", AllIcons.Actions.Commit) {

        init {
            registerCustomShortcutSet(CustomShortcutSet.fromString("ctrl shift K"), panel)
        }

        override fun actionPerformed(e: AnActionEvent) {
            val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
            val changes = mainChanges.toMutableList()
            val firstChange = (changes.firstOrNull() ?: return) as ChangeListChange

            changes.addAll(filesTree.getSelected().mapNotNull { changeListManager.getChange(it) }.map {
                ChangeListChange(
                    it,
                    firstChange.changeListName,
                    firstChange.changeListId
                )
            })

            project.bunchFileCheckEnabled = false
            if (CommitChangeListDialog.commitChanges(
                    project,
                    changes,
                    LocalChangeListImpl.Builder(project, firstChange.changeListName).setChanges(changes).build(),
                    null,
                    checkInProjectPanel.commitMessage
                )
            ) {
                closeTab()
                BunchFileUtils.updateGitLog(project)
            }
            project.bunchFileCheckEnabled = true
        }
    }

    private inner class SetCheckedAction :
        AnAction("Add to commit", "Add all current changes to commit", AllIcons.Actions.Redo) {
        init {
            registerCustomShortcutSet(CommonShortcuts.ENTER, panel)
        }

        override fun actionPerformed(e: AnActionEvent) {
            filesTree.getAllChosenFiles().forEach { filesTree.addSelected(it.virtualFile) }
        }

        private fun isAvailable(file: PsiFile): Boolean {
            return !isSelected(file)
                    && isBunchFile(file.virtualFile, project)
                    && !inCommit(file)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = filesTree.getAllChosenFiles().all { isAvailable(it) }

        }
    }

    private inner class SetUncheckedAction :
        AnAction("Remove from commit", "Remove all current changes from commit", AllIcons.Actions.Undo) {
        init {
            registerCustomShortcutSet(CommonShortcuts.ENTER, panel)
        }

        override fun actionPerformed(e: AnActionEvent) {
            filesTree.getAllChosenFiles().forEach { filesTree.removeSelected(it.virtualFile) }
        }

        private fun isAvailable(file: PsiFile): Boolean {
            return isSelected(file)
                    && isBunchFile(file.virtualFile, project)
                    && !inCommit(file)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = filesTree.getAllChosenFiles().all { isAvailable(it) }
        }
    }

    private inner class DiffAction : BunchCompareFilesAction() {
        init {
            val diffShortcutSet = ActionManager.getInstance()
                .getAction("BunchCompareFilesActions").shortcutSet
            registerCustomShortcutSet(diffShortcutSet, panel)
        }

        override fun getFile(e: AnActionEvent): VirtualFile? {
            return filesTree.psiFile?.virtualFile
        }
    }

    inner class CreateAndApplyAction : ApplyChangesAction() {
        init {
            registerCustomShortcutSet(CommonShortcuts.ALT_ENTER, panel)
        }

        override fun update(e: AnActionEvent) {
            val file = filesTree.psiFile?.virtualFile ?: return
            e.presentation.isEnabled = !(getChanges(file, project) == null || isBunchFile(file, project))
        }

        override fun getChanges(file: VirtualFile, project: Project): Change? {
            return mainChanges.firstOrNull { it.affectsFile(File(file.path)) }
                ?: super.getChanges(file, project)
        }

        override fun actionPerformed(e: AnActionEvent) {
            val file = filesTree.psiFile ?: return
            val affected = applyMainToAll(file.virtualFile, project)
            affected.forEach { filesTree.addSelected(it) }
            showInfoBalloon(affected.mapNotNull { it.extension }, file.virtualFile, project)
        }
    }
}
