package org.jetbrains.bunches.hooks

import java.awt.Component
import javax.swing.JOptionPane
import javax.swing.JTextArea
import kotlin.system.exitProcess

fun showOptionalMessage(
    message: String,
    options: Array<String>,
    initialValue: String,
    parent: Component? = null
): Int {
    val area = JTextArea()
    area.isEditable = false
    area.text = message
    area.isOpaque = false

    return JOptionPane.showOptionDialog(
        parent, area, "Bunch Tool Warning",
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
        options, initialValue
    )
}

fun exitWithMessage(code: Int, message: String? = null): Nothing {
    if (message != null) {
        System.err.println(message)
    }
    exitProcess(code)
}

fun showAndGet(message: String, mode: Boolean): Boolean {
    return if (mode) {
        showOptionalMessage(
            message,
            arrayOf("Yes", "No"),
            "Yes"
        ) == JOptionPane.YES_OPTION
    } else {
        print("\n$message(y or n):")
        val answer = readLine() ?: return false
        answer.contains("y")
    }
}