package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.project.Project
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JList


class ReduceDialogKt(project : Project, files : ArrayList<File>?) : ReduceDialog(project) {

    init {
        title = "Reduce"
        if (files == null || files.isEmpty()) {
            label.text = "All files are useful! Congratulations!"
        } else {
            val model = DefaultListModel<String>()

            for (file in files) {
                model.addElement(file.name)
            }

            filesList.model = model
        }
    }

    fun isCommitNeeded() : Boolean {
        return checkCommit.isSelected
    }
}