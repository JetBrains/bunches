package org.jetbrains.bunches.idea.vcs

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.psi.PsiFile
import com.intellij.ui.SmartExpander
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.containers.Convertor
import java.io.File
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ForgottenFilesTree(root: DefaultMutableTreeNode, files: Set<File>, all: Map<PsiFile, List<PsiFile>>) :
    Tree(root), DataProvider {

    private val psiFile: PsiFile?
        get() {
            val element = (selectionPath.lastPathComponent as DefaultMutableTreeNode).userObject
            return element as? PsiFile
        }

    init {
        isRootVisible = true
        background = null
        setToggleClickCount(3)
        isOpaque = false

        setCellRenderer(FileTreeRenderer(files))
        for ((mainFile, bunchFiles) in all) {
            val node = DefaultMutableTreeNode(mainFile)
            root.add(node)
            for (inner in bunchFiles) {
                node.add(DefaultMutableTreeNode(inner))
            }
        }

        TreeSpeedSearch(this, Convertor<TreePath, String> { it.toString() }, true)
        SmartExpander.installOn(this)
        EditSourceOnDoubleClickHandler.install(this)

        setShowsRootHandles(true)
        expandRow(0)
        isRootVisible = false

        updateUI()
    }

    override fun getData(dataId: String): Any? {
        return if (CommonDataKeys.NAVIGATABLE.`is`(dataId)) {
            psiFile
        } else null
    }
}
