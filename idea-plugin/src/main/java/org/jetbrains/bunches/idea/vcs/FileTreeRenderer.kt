package org.jetbrains.bunches.idea.vcs

import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.PsiFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import java.io.File
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

class FileTreeRenderer(files: Set<File>) : ColoredTreeCellRenderer(), TreeCellRenderer {

    private val forgottenFiles = files.map { it.name }.toSet()

    companion object {
        private val red = FileStatus.UNKNOWN.color
        private val green = FileStatus.MODIFIED.color
    }

    init {
        background = null
    }

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        if (value !is DefaultMutableTreeNode) {
            append(value.toString())
            return
        }

        val userObject = value.userObject

        if (userObject == null || userObject !is PsiFile) {
            append(value.toString())
            return
        }

        if (leaf) {
            append(userObject.name, getTextAttributes(userObject), true)
            append("  ")
            append(userObject.containingDirectory.virtualFile.path)
        } else {
            append(userObject.name)
            setIcon(userObject.fileType.icon)
        }
    }

    private fun getTextAttributes(file: PsiFile): SimpleTextAttributes {
        return SimpleTextAttributes(
            SimpleTextAttributes.STYLE_BOLD, if (forgottenFiles.contains(file.name)) {
                red
            } else {
                green
            }
        )
    }
}
