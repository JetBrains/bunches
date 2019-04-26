package org.jetbrains.bunches.precommitHook

import org.eclipse.jgit.api.Git
import java.io.File
import java.lang.System.exit
import java.util.*

fun getBunchExtensions(dotBunchFile: File): Set<String>? {
    val lines = dotBunchFile.readLines().map { it.trim() }.filter { !it.isEmpty() }
    if (lines.size <= 1) return null

    return lines.drop(1).map { it.split('_').first() }.toSet()
}

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        println("Precommit hook doesn't take any parameters")
        exit(1)
    }

    val dotBunchFile = File(".bunch")
    if (!dotBunchFile.exists()) {
        println("Project's .bunch file wasn't found, hook is disabled")
        exit(0)
    }
    val extensions = getBunchExtensions(dotBunchFile) ?: return

    val git = Git.open(File(""))
    val status = git.status().call()
    val commitFiles = (status.added + status.changed + status.removed).map { File(it) }.toSet()

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
    if (forgottenFiles.isEmpty()) exit(0)

    println("""
        Some bunch files were not included in commit:
        ${forgottenFiles.joinToString("\n")}
        Do you want to continue? (Y/N)
        """.trimIndent())
    when (readLine()) {
        "Y", "y", "Yes", "yes", "" -> exit(0)
        "N", "n", "No", "no" -> exit(1)
        else -> exit(1)
    }
}