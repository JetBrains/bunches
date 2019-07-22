package org.jetbrains.bunches.idea.vcs


import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

internal open class ForgottenFilesTree(
    root: DefaultMutableTreeNode,
    private val forgottenFiles: Set<VirtualFile>,
    all: Map<PsiFile, List<PsiFile>>
) :
    Tree(root), DataProvider {

    private val selectedFiles = mutableSetOf<VirtualFile>()
    private val levelCount = 3

    private val renderer = FileTreeRenderer()

    internal val psiFile: PsiFile?
        get() {
            val element = (selectionPath.lastPathComponent as DefaultMutableTreeNode).userObject
            return element as? PsiFile
        }

    init {
        isRootVisible = true
        background = null
        setToggleClickCount(3)
        isOpaque = false

        setCellRenderer(renderer)
        for ((mainFile, bunchFiles) in all) {
            val node = DefaultMutableTreeNode(mainFile)
            root.add(node)
            for (inner in bunchFiles) {
                node.add(DefaultMutableTreeNode(inner))
            }
        }

        TreeSpeedSearch(this, Convertor<TreePath, String> { it.toString() }, true)
        EditSourceOnDoubleClickHandler.install(this)

        TreeUtil.expand(this, levelCount)
        setShowsRootHandles(true)

        isRootVisible = false

        updateUI()
    }

    internal fun addSelected(file: VirtualFile?) {
        selectedFiles.add(file ?: return)
    }

    internal fun removeSelected(file: VirtualFile?) {
        selectedFiles.remove(file)
    }

    internal fun getSelected(): List<VirtualFile> {
        return selectedFiles.toList()
    }


    override fun getData(dataId: String): Any? {
        return if (CommonDataKeys.NAVIGATABLE.`is`(dataId)) {
            psiFile
        } else null
    }

    protected open inner class FileTreeRenderer : ColoredTreeCellRenderer(), TreeCellRenderer {
        private val unknownColor = FileStatus.UNKNOWN.color
        private val modifiedColor = FileStatus.MODIFIED.color
        private val addedColor = FileStatus.ADDED.color

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
                append(userObject.name, getTextAttributes(userObject))
            } else {
                icon = userObject.fileType.icon
                append(userObject.name)
                append("  ")
                append(userObject.containingDirectory.virtualFile.path, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }

        open fun getTextAttributes(file: PsiFile): SimpleTextAttributes {
            return SimpleTextAttributes(
                SimpleTextAttributes.STYLE_PLAIN,
                when {
                    file.virtualFile in selectedFiles -> addedColor
                    file.virtualFile in forgottenFiles -> unknownColor
                    else -> modifiedColor
                }
            )
        }
    }

}
