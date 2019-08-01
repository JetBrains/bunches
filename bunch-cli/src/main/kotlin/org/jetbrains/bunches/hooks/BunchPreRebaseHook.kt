package org.jetbrains.bunches.hooks

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.findAddCommit
import org.jetbrains.bunches.git.*
import org.jetbrains.bunches.pathToMainFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

const val GENERATED_COMMIT_MARK = "[POMOG CHEM SMOG]"

fun checkPreRebase(args: Array<String>) {
    if (args.size != 4) {
        exitWithMessage(1, "Invalid arguments in pre-rebase check")
    }
    val mode = args[3] == IDEA_OUTPUT_MODE

    val first = args[0]
    val second = args[1]
    val repoPath = args[2]
    val parent = getLowestCommonAncestorHash(first, second)
    val extensions = readExtensionsFromFile(repoPath).resultIfSuccess?.toSet() ?: emptySet()

    val firstBranchCommits = readCommits(repoPath, first, parent)
    val secondBranchCommits = readCommits(repoPath, second, parent)

    val fixedFiles = secondBranchCommits.filter { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }
        .map { it.fileActions }.flatten().map { it.newPath }
    val added = readDiffFiles(first, second, firstBranchCommits, extensions)
    val deleted = readDiffFiles(second, first, secondBranchCommits, extensions)

    var message = added.minus(fixedFiles).filterNotNull().joinToString { filename ->
        val commit = findAddCommit(filename, secondBranchCommits) ?: return@joinToString ""
        "$filename presents in $second [added in ${commit.title} ${commit.hash}], but does not in $first\n"
    } + deleted.joinToString { filename ->
        val commit = findAddCommit(filename, firstBranchCommits) ?: return@joinToString ""
        "$filename presents in $first [added in ${commit.title} ${commit.hash}], but does not in $second\n"
    }

    if (message.isEmpty()) {
        exitWithMessage(0)
    } else {
        message += "Create friendly commits to remind about it?\n"
    }

    val result = showAndGet(message, mode)

    if (result) {
        resolveRemoved(deleted, firstBranchCommits, repoPath)
        resolveAdded(added, firstBranchCommits, repoPath)
        exitWithMessage(1, "Commits created")
    } else {
        exitWithMessage(0)
    }
}

private fun forgottenBunchFilesFilter(file: String, commits: List<CommitInfo>, extensions: Set<String>): Boolean {
    if (File(file).extension !in extensions) {
        return false
    }
    val mainFile = pathToMainFile(file)
    return commits.any {
        it.fileActions.any { action ->
            action.changeType == DiffEntry.ChangeType.MODIFY && action.newPath == mainFile
        }
    }
}

private fun resolveRemoved(deletedFilesPaths: List<String>, commits: List<CommitInfo>, gitPath: String) {
    val mismatchedFiles = mutableMapOf<CommitInfo, HashSet<File>>()
    for (deletedFilePath in deletedFilesPaths) {
        val commit = findAddCommit(deletedFilePath, commits) ?: continue
        if (!mismatchedFiles.containsKey(commit)) {
            mismatchedFiles[commit] = HashSet<File>()
        }
        mismatchedFiles[commit]?.add(File(deletedFilePath))
    }
    for ((commit, files) in mismatchedFiles) {
        for (file in files) {
            val main = File(pathToMainFile(file.path))
            file.createNewFile()
            file.writeText(main.readText())
        }
        val filesList = files.map { FileChange(ChangeType.ADD, it.absoluteFile) }
        commitChanges(
            gitPath,
            filesList,
            "Files for correct merge with same files created" +
                    " in [${commit.title}] ${commit.hash} $GENERATED_COMMIT_MARK"
        )
    }
}

private fun isTextFile(file: File): Boolean {
    val mainFile = File(file.parent ?: ".", file.nameWithoutExtension)
    val connection = mainFile.toURI().toURL().openConnection()
    val mimeType = connection.contentType

    return mimeType.contains("text") || mimeType.contains("unknown")
}

private fun resolveAdded(addedFilesPaths: List<String>, commits: List<CommitInfo>, gitPath: String) {
    val mismatchedFiles = mutableMapOf<CommitInfo, HashSet<File>>()
    for (addedFilePath in addedFilesPaths) {
        for (commit in affectingCommits(pathToMainFile(addedFilePath), commits)) {
            if (!mismatchedFiles.containsKey(commit)) {
                mismatchedFiles[commit] = HashSet<File>()
            }
            mismatchedFiles[commit]?.add(File(addedFilePath))
        }
    }

    for ((commit, files) in mismatchedFiles) {
        files.filter {
            isTextFile(it)
        }.forEach { Files.write(Paths.get(it.path), "\n".toByteArray(), StandardOpenOption.APPEND) }
        val filesList = files.map { FileChange(ChangeType.MODIFY, it.absoluteFile) }
        commitChanges(
            gitPath,
            filesList,
            "These files have changes in main file in [${commit.title}] ${commit.hash} $GENERATED_COMMIT_MARK"
        )
    }
}

private fun affectingCommits(file: String, commits: List<CommitInfo>): List<CommitInfo> {
    return commits.filter {
        it.fileActions.any { action -> action.newPath == file && action.changeType == DiffEntry.ChangeType.MODIFY }
    }
}

private fun readDiffFiles(
    firstBranch: String,
    secondBranch: String,
    commits: List<CommitInfo>,
    extensions: Set<String>
): List<String> {
    return readCommandLines("git diff-tree -r --diff-filter=A --name-only $firstBranch $secondBranch")
        .filter {
            forgottenBunchFilesFilter(it, commits, extensions)
        }
}

private fun readCommandLines(command: String): List<String> {
    return Runtime.getRuntime().exec(command).inputStream.bufferedReader().readLines()
}

private fun getLowestCommonAncestorHash(firstBranch: String, secondBranch: String): String {
    val parents = readCommandLines("git merge-base $firstBranch $secondBranch")
    if (parents.size != 1) {
        exitWithMessage(1, "Invalid result of branches lowest common ancestor finding operation")
    }
    return parents.first()
}