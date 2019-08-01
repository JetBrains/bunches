package org.jetbrains.bunches.idea.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import org.jetbrains.bunches.idea.util.BunchFileUtils.getMainFile
import org.jetbrains.bunches.idea.util.BunchFileUtils.refreshFileSystem
import org.jetbrains.bunches.idea.util.BunchFileUtils.toPsiFile
import org.jetbrains.bunches.idea.util.BunchFileUtils.updateGitLog
import org.jetbrains.bunches.idea.vcs.ForgottenFilesTree
import org.jetbrains.bunches.reduce.*
import org.jetbrains.bunches.reduce.ReduceAction
import org.jetbrains.bunches.reduce.ReduceAction.COMMIT
import org.jetbrains.bunches.reduce.ReduceAction.DELETE
import java.awt.GridLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode

class ReduceActionWindow(
    private val toolWindow: ToolWindow,
    private val project: Project,
    private val repoPath: String,
    private val bunchPath: String
) {
    private val panel: SimpleToolWindowPanel = SimpleToolWindowPanel(false)
    val content: JComponent
        get() = panel

    private fun closeTab() {
        val content = toolWindow.contentManager.getContent(panel) ?: return
        toolWindow.contentManager.removeContent(content, true)
    }

    init {
        val actionGroup = DefaultActionGroup().apply {
            addAction(
                ActionForReduce(
                    "Reduce",
                    "Remove all unnecessary bunch files",
                    AllIcons.General.Remove,
                    DELETE
                )
            )
            addAction(
                ActionForReduce(
                    "Reduce with commit",
                    "Remove all unnecessary files and commit these changes",
                    AllIcons.Actions.Cancel,
                    COMMIT
                )
            )
            addAction(UpdateAction())
        }

        panel.toolbar = JPanel(GridLayout()).apply {
            add(
                ActionManager.getInstance()
                    .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, false).component
            )
        }
        panel.setContent(JScrollPane().apply {
            border = null
        })

        loadFiles()
    }

    private fun update(filesForReduce: List<File>) {
        val psiFiles = filesForReduce
            .mapNotNull { toPsiFile(it, project) }
            .groupBy { getMainFile(it) ?: it }
            .filterNot { it.value.size == 1 && it.key == it.value.firstOrNull() }
        panel.setContent(JScrollPane(ReduceTree(DefaultMutableTreeNode(), psiFiles)).apply { border = null })
        panel.updateUI()
    }

    private inner class ActionForReduce(text: String, hint: String, icon: Icon?, action: ReduceAction) :
        AnAction(text, hint, icon) {
        val settings = Settings(
            repoPath,
            bunchPath,
            action,
            DEFAULT_REDUCE_COMMIT_TITLE
        )
        private var filesToReduce = emptyList<File>()

        override fun actionPerformed(e: AnActionEvent) {
            FileDocumentManager.getInstance().saveAllDocuments()
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Bunch Reduce", false) {
                    override fun run(indicator: ProgressIndicator) {
                        filesToReduce = getReducibleFiles(settings.repoPath, settings.bunchPath)
                        deleteReducibleFiles(settings, filesToReduce)
                    }

                    override fun onSuccess() {
                        refreshFileSystem(filesToReduce.mapNotNull { toPsiFile(it, project)?.virtualFile })
                        if (settings.action == COMMIT) {
                            updateGitLog(project)
                        }
                        closeTab()
                    }
                }
            )
        }
    }

    private fun loadFiles() {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Reducible Files Counting", false) {
                private var result = emptyList<File>()

                override fun run(indicator: ProgressIndicator) {
                    result = getReducibleFiles(repoPath, bunchPath)
                }

                override fun onSuccess() {
                    update(result)
                }
            }
        )
    }

    private inner class UpdateAction : AnAction("Refresh", "Reload reducible files", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            FileDocumentManager.getInstance().saveAllDocuments()
            loadFiles()
        }
    }

    private inner class ReduceTree(root: DefaultMutableTreeNode, map: Map<PsiFile, List<PsiFile>>) :
        ForgottenFilesTree(root, emptySet(), map) {
        init {
            setCellRenderer(MyRenderer())
        }

        private inner class MyRenderer : FileTreeRenderer() {
            override fun getTextAttributes(file: PsiFile): SimpleTextAttributes {
                return REGULAR_ATTRIBUTES
            }
        }
    }
}