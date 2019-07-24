package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.getExtensions
import java.io.File
import java.lang.System.exit
import java.nio.file.Path

fun precommitLostFiles(args: Array<String>): HashSet<File>? {
    val extensions = getExtensions(args[0])
    val commitFiles = args.drop(1).map { File(it) }.toSet()
    val forgottenFiles = HashSet<File>()

    for (file in commitFiles) {
        if (file.extension in extensions) continue

        for (extension in extensions) {
            val bunchFile = File(file.absolutePath + ".$extension")
            if (bunchFile !in commitFiles && bunchFile.exists()) {
                forgottenFiles.add(bunchFile)
            }
        }
    }
    return forgottenFiles
}

fun precommitHook(args: Array<String>) {
    val forgottenFiles = precommitLostFiles(args) ?: return
    if (forgottenFiles.isEmpty()) {
        exit(0)
    }

    println("""
        |Some bunch files were not included in commit:
        |${forgottenFiles.joinToString("\n|")}
        |Do you want to continue? (Y/N)
    """.trimMargin())
    when (readLine()) {
        "Y", "y", "Yes", "yes", "" -> exit(0)
        "N", "n", "No", "no" -> exit(1)
        else -> exit(1)
    }
}