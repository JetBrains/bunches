package org.jetbrains.bunches.precommitHook

import org.eclipse.jgit.api.Git;
import java.io.File;
import java.lang.System.exit
import java.util.*

fun getBunchExtensions(dotBunchFile: File): Set<String>? {
    val lines = dotBunchFile.readLines().map { it.trim() }.filter { !it.isEmpty() }
    if (lines.size <= 1) return null

    return lines.drop(1).map { it.split('_').first() }.toSet()
}

fun main(args: Array<String>) {
    if (args.size !== 0) {
        System.out.println("Precommit hook doesn't take any parameters")
        exit(1)
    }

    val dotBunchFile = File(".bunch")
    if (!dotBunchFile.exists()) {
        System.out.println("Project's .bunch file wasn't found, hook is disabled")
        //exit(0)
    }
    val extensions = getBunchExtensions(dotBunchFile) ?: TreeSet<String>()

    val git = Git.open(File(""))
    val status = git.status().call()
    val commitFiles = (status.modified).map { File(it) }

    val forgottenFiles = HashSet<File>()

    for (file in commitFiles) {
        System.out.println(file)
        if (file.extension in extensions) continue

        val parent = file.parent ?: continue
        val name = file.name
        for (extension in extensions) {
            val bunchFile = File(parent, "$name.$extension")
            if (bunchFile !in commitFiles && bunchFile.exists()) {
                forgottenFiles.add(bunchFile)
            }
        }
    }
    if (forgottenFiles.isEmpty()) exit(0)

    System.out.println("Some bunch files were not included in commit:\n${forgottenFiles.joinToString("\n")}\nDo you want to continue? (Yes/No)")
    when (Scanner(System.`in`).nextLine()) {
        "Yes" -> exit(0)
        else -> exit(1)
    }
}