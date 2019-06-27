package org.jetbrains.bunches.hooks

import java.io.File
import javax.swing.JOptionPane

fun checkPreRebase(args: Array<String>) {
    if (args.size != 2) {
        print("Wrong arguments")
        return
    }

    val first = args[0]
    val second = args[1]

    var message = ""
    val deletedFiles = Runtime.getRuntime().exec("git diff-tree -r --diff-filter=D --name-only $first $second")
    val addedFiles = Runtime.getRuntime().exec("git diff-tree -r --diff-filter=A --name-only $first $second")
    val modifiedFiles = Runtime.getRuntime().exec("git diff-tree -r --diff-filter=M --name-only $first $second")

    val modified = modifiedFiles.inputStream.bufferedReader().readLines()
    val extensions = getExtensions()
    val mainFile = getMainBranchFiles(extensions, modified)
    val added = addedFiles.inputStream.bufferedReader().readLines()
    val deleted = deletedFiles.inputStream.bufferedReader().readLines()

    for (filename in added) {
        if (File(filename).extension in extensions && fileWithoutExtension(filename) in mainFile) {
            message += "$filename presents in $second, but does not in $first\n"
        }
    }

    for (filename in deleted) {
        if (File(filename).extension in extensions && fileWithoutExtension(filename) in mainFile) {
            message += "$filename presents in $first, but does not in $second\n"
        }
    }

    if (message == "") {
        println(0)
        return
    } else {
        message += "Do you want to check it?\n"
    }

    System.err.println(message)

    val result = JOptionPane.showOptionDialog(
        null, message, "Friendly warning",
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
        arrayOf("Yes", "No"), "Yes"
    )

    if (JOptionPane.YES_OPTION == result) {
        println(1)
    } else {
        println(0)
    }
}