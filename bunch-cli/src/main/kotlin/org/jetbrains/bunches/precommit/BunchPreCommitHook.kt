package org.jetbrains.bunches.precommit

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.lang.System.exit
import java.util.*

fun getBunchExtensions(dotBunchFile: File): Set<String>? {
    val lines = dotBunchFile.readLines().map { it.trim() }.filter { !it.isEmpty() }
    if (lines.size <= 1) return null

    return lines.drop(1).map { it.split('_').first() }.toSet()
}

class PrecommitHookCommand : CliktCommand(
    name = "precommit",
    help = "technical function"
) {
    val commitFiles by argument().file().multiple()

    override fun run() {
        precommitHook(commitFiles.toSet())
    }
}

fun precommitHook(commitFiles: Set<File>) {
    val dotBunchFile = File(".bunch")
    if (!dotBunchFile.exists()) {
        println("Project's .bunch file wasn't found, hook is disabled")
        exit(0)
    }
    val extensions = getBunchExtensions(dotBunchFile) ?: return

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