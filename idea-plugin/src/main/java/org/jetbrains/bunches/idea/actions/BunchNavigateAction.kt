package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.bunches.idea.util.BunchFileUtils
import org.jetbrains.bunches.idea.util.getVirtualFile


class BunchNavigateAction : AnAction("Navigate to related") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getVirtualFile() ?: return
        val mainFile = if (BunchFileUtils.isBunchFile(currentFile, project)) {
            BunchFileUtils.getMainFile(currentFile, project) ?: return
        } else {
            currentFile
        }

        val files = BunchFileUtils.getAllBunchFiles(mainFile, project)
        val filesPopupList = ListPopupImpl(MyItemsList(project, currentFile, mainFile, files))
        DebuggerUIUtil.registerExtraHandleShortcuts(filesPopupList, "BunchNavigateAction")

        val window = WindowManager.getInstance().suggestParentWindow(project) ?: return
        filesPopupList.showInCenterOf(window)
    }

    private class MyItemsList(val project: Project,
                              file: VirtualFile,
                              mainFile: VirtualFile,
                              bunchFiles: List<VirtualFile>)
        : BaseListPopupStep<VirtualFile>("Navigate to ", getList(file, mainFile, bunchFiles), mainFile.fileType.icon) {
        companion object {
            private fun getList(file: VirtualFile, mainFile: VirtualFile, bunchFiles: List<VirtualFile>): MutableList<VirtualFile> {
                val list = mutableListOf<VirtualFile>()
                if (file != mainFile) {
                    list.add(mainFile)
                }
                list.addAll(bunchFiles.minus(file))
                return list
            }
        }

        override fun onChosen(selectedValue: VirtualFile, finalChoice: Boolean): PopupStep<*>? {
            if (finalChoice) {
                val psiMainFile = PsiManager.getInstance(project).findFile(selectedValue)
                    ?: return PopupStep.FINAL_CHOICE
                psiMainFile.navigate(true)
            }
            return PopupStep.FINAL_CHOICE
        }

        override fun getTextFor(value: VirtualFile): String {
            return value.name
        }
    }
}