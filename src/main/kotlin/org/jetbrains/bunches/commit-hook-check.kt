@file:JvmName("CheckCommitHook")
package org.jetbrains.bunches.commithook

import org.jetbrains.bunches.general.exitWithError
import javax.swing.JOptionPane

fun main(args: Array<String>) {
    checkCommitHook(args)
}

fun checkCommitHook(args: Array<String>) {
    if (!showWarning("Bada", "Mana")) {
        exitWithError()
    }
}

private fun showWarning(reason: String, targetBranch: String): Boolean {
    val baseMessage = "You are about to commit to '" + targetBranch + "',\n" +
            "and it is probably not the best idea.\n" +
            "Please think twice before pressing 'Yes'."

    val confirmationMessage = "Do you still want to commit to '$targetBranch'?"

    val completeMessage = baseMessage + "\n\nReason: " + reason.replace("\\n", "\n") + "\n\n" + confirmationMessage

    val result = JOptionPane.showOptionDialog(
        null, completeMessage, "Friendly warning",
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
        arrayOf("Yes", "No"), "No"
    )

    return result == JOptionPane.YES_OPTION
}
