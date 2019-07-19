package org.jetbrains.bunches.idea.vcs

import com.intellij.BundleBase.replaceMnemonicAmpersand
import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.PairConsumer
import org.jetbrains.bunches.idea.util.BunchFileUtils
import org.jetbrains.bunches.idea.util.BunchFileUtils.getAllBunchFiles
import org.jetbrains.bunches.idea.util.NotNullableUserDataProperty
import java.awt.GridLayout
import javax.security.auth.callback.ConfirmationCallback.NO
import javax.security.auth.callback.ConfirmationCallback.YES
import javax.swing.JComponent
import javax.swing.JPanel

internal var Project.bunchFileCheckEnabled: Boolean by NotNullableUserDataProperty(
    Key.create("IS_BUNCH_FILE_CHECK_ENABLED"),
    true
)

class BunchFileCheckInHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return BunchCheckInHandler(panel)
    }

    class BunchCheckInHandler(
        private val checkInProjectPanel: CheckinProjectPanel
    ) : CheckinHandler() {
        private val project get() = checkInProjectPanel.project


        override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent? {
            BunchFileUtils.bunchFile(project) ?: return null

            val bunchFilesCheckBox = NonFocusableCheckBox(replaceMnemonicAmpersand("Check &bunch files"))
            return object : RefreshableOnComponent {
                override fun getComponent(): JComponent {
                    val panel = JPanel(GridLayout(1, 0))
                    panel.add(bunchFilesCheckBox)
                    return panel
                }

                override fun refresh() {}
                override fun saveState() {
                    project.bunchFileCheckEnabled = bunchFilesCheckBox.isSelected
                }

                override fun restoreState() {
                    bunchFilesCheckBox.isSelected = project.bunchFileCheckEnabled
                }
            }
        }

        private fun getPsiFile(file: VirtualFile): PsiFile? {
            return PsiManager.getInstance(project).findFile(file)
        }

        override fun beforeCheckin(
            executor: CommitExecutor?,
            additionalDataConsumer: PairConsumer<Any, Any>?
        ): ReturnResult {
            if (!project.bunchFileCheckEnabled) return ReturnResult.COMMIT

            val extensions = BunchFileUtils.bunchExtensions(project)?.toSet() ?: return ReturnResult.COMMIT

            val forgottenFiles = mutableSetOf<VirtualFile>()
            val allFiles = mutableMapOf<PsiFile, List<PsiFile>>()
            val commitFiles = checkInProjectPanel.virtualFiles.toSet()


            for (file in commitFiles) {
                if (file.extension in extensions) continue
                val psiFile = getPsiFile(file) ?: continue

                val bunchFiles = getAllBunchFiles(psiFile.virtualFile, project)

                allFiles[psiFile] = bunchFiles.mapNotNull { getPsiFile(it) }.toList()
                forgottenFiles.addAll(bunchFiles.filter { it !in commitFiles })
            }

            if (forgottenFiles.isEmpty()) {
                return ReturnResult.COMMIT
            }

            when (Messages.showYesNoCancelDialog(
                project,
                "Several bunch files haven't been updated\nDo you want to review them before commit?",
                "Forgotten Bunch Files",
                "Yes",
                "No",
                CommonBundle.getCancelButtonText(),
                Messages.getWarningIcon()
            )) {
                YES -> {
                    val window = ToolWindowManager.getInstance(project).getToolWindow("Bunch Tool")
                    BunchToolWindowFactory.createToolWindowContent(
                        window, forgottenFiles, allFiles,
                        checkInProjectPanel
                    )
                    return ReturnResult.CLOSE_WINDOW
                }
                NO -> return ReturnResult.COMMIT
            }

            return ReturnResult.CANCEL
        }
    }
}