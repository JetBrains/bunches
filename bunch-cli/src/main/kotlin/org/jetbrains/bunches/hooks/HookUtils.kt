package org.jetbrains.bunches.hooks

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.CommitInfo
import java.awt.Component
import java.io.File
import javax.swing.JOptionPane
import javax.swing.JTextArea
import kotlin.system.exitProcess

// TODO: not specific to hooks
fun getBunchExtensions(dotBunchFile: File): Set<String>? {
    val lines = dotBunchFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size <= 1) return null

    return lines.drop(1).map { it.split('_').first() }.toSet()
}

// TODO: It's better to have git root as parameter
fun getExtensions(): Set<String> {
    val dotBunchFile = File(".bunch")
    if (!dotBunchFile.exists()) {
        exitWithUsageError("Project's .bunch file wasn't found, hook is disabled")
    }
    return getBunchExtensions(dotBunchFile) ?: emptySet()
}

// TODO: Better name or remove
fun fileWithoutExtension(filename: String): String {
    return filename.removeSuffix(".${File(filename).extension}")
}

// TODO: better name
// TODO: better result
fun checkCommitInterval(commits: List<CommitInfo>): String {
    var message = ""

    val extensions = getExtensions()
    for (commit in commits) {
        for (action in commit.fileActions) {
            if (action.changeType == DiffEntry.ChangeType.ADD
                && File(action.newPath ?: continue).extension in extensions
            ) {
                if (isDeleted(action.newPath, commits.reversed().dropWhile { it != commit })) {
                    continue
                }
                val mainFile = mainFile(action.newPath)
                val onlyMainFileModificationCommits = commits.reversed().dropWhile { it != commit }
                    .filter {
                        it.fileActions.any { current ->
                            current.newPath == mainFile
                        }
                    }
                    .filter {
                        it.fileActions.all { current ->
                            current.newPath != action.newPath
                        }
                    }

                if (onlyMainFileModificationCommits.isNotEmpty()) {
                    message += "Affected $mainFile, but didnt ${action.newPath}:\n${onlyMainFileModificationCommits.joinToString(",\n") { "${it.title} ${it.hash}" }}\n"
                }
            }
        }
    }

    val allAffectedFiles = commits.map { it.fileActions.map { action -> action.newPath } }.flatten().filterNotNull()
    for (bunchFile in allAffectedFiles.filter { isBunchFile(it, extensions) }) {
        if (!isCreated(bunchFile, commits) && !isDeleted(bunchFile, commits)) {
            val mainFile = mainFile(bunchFile)
            val danger = commits.reversed()
                .filter { it.fileActions.any { current -> current.newPath == mainFile } }
                .filter { it.fileActions.all { current -> current.newPath != bunchFile } }

            if (danger.isNotEmpty()) {
                message += "Affected $mainFile, but did not $bunchFile:\n${danger.joinToString(",\n") { "${it.title} ${it.hash}" }}\n"
            }
        }
    }

    return message
}

private fun mainFile(affectedFile: String) = fileWithoutExtension(affectedFile)

private fun isBunchFile(
    affectedFile: String,
    extensions: Set<String>
) = File(affectedFile).extension in extensions

fun findCommitWithType(file: String, commits: List<CommitInfo>, type: DiffEntry.ChangeType): CommitInfo? {
    return commits.firstOrNull {
        it.fileActions.any { action -> action.changeType == type && action.newPath == file }
    }
}

fun findAddCommit(file: String, commits: List<CommitInfo>): CommitInfo? {
    return findCommitWithType(file, commits, DiffEntry.ChangeType.ADD)
}

fun isCreated(filePath: String, commits: List<CommitInfo>): Boolean {
    return findAddCommit(filePath, commits) != null
}

fun isDeleted(filePath: String, commits: List<CommitInfo>): Boolean {
    return findCommitWithType(filePath, commits, DiffEntry.ChangeType.DELETE) != null
}

fun showOptionalMessage(message: String, options: Array<String>, initialValue: String, parent: Component? = null): Int {
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

fun exitWithCode(code: Int, mode: Boolean) {
    if (mode) {
        println(code)
    } else {
        exitProcess(code)
    }
}

fun showInfo(message: String, mode: Boolean): Boolean {
    if (mode) {
        return showOptionalMessage(
            message,
            arrayOf("Yes", "No"),
            "Yes"
        ) == JOptionPane.YES_OPTION
    } else {
        print("-----------------\n\n$message(y or n):")
        val answer = readLine() ?: return false
        return answer.contains("y")
    }
}