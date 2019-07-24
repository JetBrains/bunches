package org.jetbrains.bunches

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.git.CommitInfo
import java.io.File

fun getExtensions(bunchFileDirectory: String): Set<String> {
    return readExtensionsFromFile(File(bunchFileDirectory))?.toSet() ?: emptySet()
}

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