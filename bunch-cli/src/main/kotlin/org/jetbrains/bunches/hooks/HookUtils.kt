package org.jetbrains.bunches.hooks

import java.io.File
import kotlin.system.exitProcess

fun getBunchExtensions(dotBunchFile: File): Set<String>? {
    val lines = dotBunchFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size <= 1) return null

    return lines.drop(1).map { it.split('_').first() }.toSet()
}

fun getExtensions(): Set<String> {

    val dotBunchFile = File(".bunch")
    if (!dotBunchFile.exists()) {
        println("Project's .bunch file wasn't found, hook is disabled")
        exitProcess(0)
    }

    return getBunchExtensions(dotBunchFile) ?: emptySet()
}

fun getMainBranchFiles(extensions: Set<String>, args: List<String>): Set<String> {
    val mainBranchFiles = mutableSetOf<String>()

    for (file in args) {
        if (File(file).extension !in extensions) {
            mainBranchFiles.add(file)
        }
    }

    return mainBranchFiles
}

fun fileWithoutExtension(filename: String): String {
    return filename.removeSuffix(".${File(filename).extension}")
}