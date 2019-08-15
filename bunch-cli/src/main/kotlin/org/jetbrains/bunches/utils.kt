package org.jetbrains.bunches

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.CommitInfo
import java.io.File

fun findCommitWithType(file: String, commits: List<CommitInfo>, type: DiffEntry.ChangeType): CommitInfo? {
    return commits.firstOrNull {
        it.fileActions.any { action -> action.changeType == type && action.newPath == file }
    }
}

fun findAddCommit(file: String, commits: List<CommitInfo>): CommitInfo? {
    return findCommitWithType(file, commits, DiffEntry.ChangeType.ADD)
}

fun pathToMainFile(filePath: String): String {
    return filePath.removeSuffix(".${File(filePath).extension}")
}

fun <T> Sequence<T>.processWithConsoleProgressBar(progressChar: Char = '=', itemsInProgress: Int = 50): List<T> {
    var count = 0
    val result = this.mapTo(ArrayList()) {
        count++
        if (count % itemsInProgress == 0) {
            print(progressChar)
        }
        it
    }
    println()

    return result
}