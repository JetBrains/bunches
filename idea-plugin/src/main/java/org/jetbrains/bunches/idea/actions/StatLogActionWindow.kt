package org.jetbrains.bunches.idea.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.readCommits
import org.jetbrains.bunches.idea.util.BunchFileUtils
import org.jetbrains.bunches.stats.log.Settings
import org.jetbrains.bunches.stats.log.countBunchChanges
import org.jetbrains.bunches.stats.log.getGitLogFilter
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable

class StatLogActionWindow(
    private val project: Project,
    private val repoPath: String
) {
    private var settings = Settings(repoPath)
    private val bunchExtensions =
        BunchFileUtils.bunchExtensions(project, true)?.toList() ?: listOf()
    private val panel: SimpleToolWindowPanel = SimpleToolWindowPanel(false)
    val content: JComponent
        get() = panel

    init {
        val actionGroup = DefaultActionGroup().apply {
            addAction(UpdateAction())
            addAction(FilterAction())
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

        loadCommits(false)
    }

    private fun update(commitList: List<CommitInfo>) {
        val data = commitList.map {
                arrayOf(
                    countBunchChanges(it, DiffEntry.ChangeType.ADD, bunchExtensions),
                    countBunchChanges(it, DiffEntry.ChangeType.ADD, bunchExtensions),
                    it.hash,
                    it.title,
                    it.author?.name ?: "unknown author",
                    it.author?.`when` ?: "unknown time"
                )
            }.filter {
                it[0] != 0 || it[1] != 0
            }.toTypedArray()
        val column = arrayOf("Additions", "Deletions", "Commit Hash", "Commit title", "Commit author", "Commit Date")
        panel.setContent(JScrollPane(JTable(data, column)).apply { border = null })
        panel.updateUI()
    }

    private fun loadCommits(noUserFilter: Boolean) {
        if (!noUserFilter) {
            val dialog = CommitFilterDialogKt(project)
            if (!dialog.showAndGet()) {
                return
            }
            val parameters = dialog.getParameters()
            settings = Settings(repoPath, parameters.startDate, parameters.amount)
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Counting statistics", false) {
                private var result = emptyList<CommitInfo>()

                override fun run(indicator: ProgressIndicator) {
                    val gitLogFilter = getGitLogFilter(settings)
                    result = readCommits(settings.repoPath, true, gitLogFilter)
                }

                override fun onSuccess() {
                    update(result)
                }
            }
        )
    }

    private inner class UpdateAction : AnAction("Refresh", "Reload log", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            loadCommits(true)
        }
    }

    private inner class FilterAction : AnAction("Filter", "Filter commits", AllIcons.General.Filter) {
        override fun actionPerformed(e: AnActionEvent) {
            loadCommits(false)
        }
    }
}