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

const val GENERATED_COMMIT_MARK = "[BUNCH_TOOL_REBASE]"

fun checkPreRebase(args: Array<String>) {
    if (args.size != 4) {
        exitWithMessage(1, "Invalid arguments in pre-rebase check")
    }
    val mode = args[3] == IDEA_OUTPUT_MODE
    val result = PreRebaseCheckResult.compute(
        firstBranch = args[0],
        secondBranch = args[1],
        repositoryPath = args[2]
    )

    if (result.isOk()) {
        exitWithMessage(0)
    }

    if (showAndGet(result.messageToShow(), mode)) {
        result.createCommits()
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

private fun readDiffFiles(
    firstBranch: String,
    secondBranch: String,
    commits: List<CommitInfo>,
    extensions: Set<String>,
    repository: File
): List<String> {
    return readCommandLines("git diff-tree -r --diff-filter=A --name-only $firstBranch $secondBranch", repository)
        .filter {
            forgottenBunchFilesFilter(it, commits, extensions)
        }
}

private fun readCommandLines(command: String, repository: File): List<String> {
    return Runtime.getRuntime().exec(command, null, repository).inputStream.bufferedReader().readLines()
}

private fun getLowestCommonAncestorHash(firstBranch: String, secondBranch: String, repository: File): String {
    val parents = readCommandLines("git merge-base $firstBranch $secondBranch", repository)
    if (parents.size != 1) {
        exitWithMessage(1, "Invalid result of branches lowest common ancestor finding operation")
    }
    return parents.first()
}

internal fun generatedAddedCommitMessage(title: String?, hash: String?): String {
    return "Files for correct merge with same files created" +
            " in [$title] $hash $GENERATED_COMMIT_MARK"
}

internal fun generatedModifiedCommitMessage(title: String?, hash: String?): String {
    return "These files have changes in main file in [$title] $hash $GENERATED_COMMIT_MARK"
}

internal class PreRebaseCheckResult private constructor(
    private val repositoryPath: String,
    val addedFiles: List<String>,
    val deletedFiles: List<String>,
    private val currentBranchCommits: List<CommitInfo>,
    private val message: String
) {

    companion object {
        internal fun compute(firstBranch: String, secondBranch: String, repositoryPath: String): PreRebaseCheckResult {
            val repository = File(repositoryPath)
            val parent = getLowestCommonAncestorHash(firstBranch, secondBranch, repository)
            val extensions = readExtensionsFromFile(repositoryPath).resultIfSuccess?.toSet() ?: emptySet()

            val firstBranchCommits = readCommits(repositoryPath, firstBranch, parent)
            val secondBranchCommits = readCommits(repositoryPath, secondBranch, parent)

            val fixedFiles = secondBranchCommits.filter { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }
                .map { it.fileActions }.flatten().map { it.newPath }
            val added = readDiffFiles(firstBranch, secondBranch, firstBranchCommits, extensions, repository)
            val deleted = readDiffFiles(secondBranch, firstBranch, secondBranchCommits, extensions, repository)
            val messageBuilder = StringBuilder()
            for (addedFile in added.minus(fixedFiles).filterNotNull()) {
                val commit = findAddCommit(addedFile, secondBranchCommits) ?: continue
                messageBuilder.append("$addedFile presents in $secondBranch [added in ${commit.title} ${commit.hash}], but does not in $firstBranch\n")
            }

            for (deletedFile in deleted) {
                val commit = findAddCommit(deletedFile, firstBranchCommits) ?: continue
                messageBuilder.append("$deletedFile presents in $firstBranch [added in ${commit.title} ${commit.hash}], but does not in $secondBranch\n")
            }

            return PreRebaseCheckResult(
                repositoryPath,
                added, deleted,
                firstBranchCommits,
                messageBuilder.toString().replace("\n", System.lineSeparator())
            )
        }
    }

    fun isOk(): Boolean {
        return message.isEmpty()
    }

    fun messageToShow(): String {
        return message + "Create friendly commits to remind about it?\n"
    }

    fun createCommits() {
        resolveRemoved()
        resolveAdded()
    }

    private fun resolveRemoved() {
        val mismatchedFiles = mutableMapOf<CommitInfo, HashSet<File>>()
        for (deletedFilePath in deletedFiles) {
            val commit = findAddCommit(deletedFilePath, currentBranchCommits) ?: continue
            if (!mismatchedFiles.containsKey(commit)) {
                mismatchedFiles[commit] = HashSet<File>()
            }
            mismatchedFiles[commit]?.add(File(repositoryPath, deletedFilePath))
        }
        for ((commit, files) in mismatchedFiles) {
            for (file in files) {
                val main = File(pathToMainFile(file.path))
                file.createNewFile()
                file.writeText(main.readText())
            }
            val filesList = files.map { FileChange(ChangeType.ADD, it.absoluteFile) }
            commitChanges(
                repositoryPath,
                filesList,
                generatedAddedCommitMessage(commit.title, commit.hash)
            )
        }
    }

    private fun isTextFile(file: File): Boolean {
        val mainFile = File(file.parent ?: ".", file.nameWithoutExtension)
        val connection = mainFile.toURI().toURL().openConnection()
        val mimeType = connection.contentType

        return mimeType.contains("text") || mimeType.contains("unknown")
    }


    private fun resolveAdded() {
        val mismatchedFiles = mutableMapOf<CommitInfo, HashSet<File>>()
        for (addedFilePath in addedFiles) {
            for (commit in affectingCommits(pathToMainFile(addedFilePath), currentBranchCommits)) {
                if (!mismatchedFiles.containsKey(commit)) {
                    mismatchedFiles[commit] = HashSet<File>()
                }
                mismatchedFiles[commit]?.add(File(repositoryPath, addedFilePath))
            }
        }

        for ((commit, files) in mismatchedFiles) {
            files.filter {
                isTextFile(it)
            }.forEach { Files.write(Paths.get(it.path), "\n".toByteArray(), StandardOpenOption.APPEND) }
            val filesList = files.map { FileChange(ChangeType.MODIFY, it.absoluteFile) }
            commitChanges(
                repositoryPath,
                filesList,
                generatedModifiedCommitMessage(commit.title, commit.hash)
            )
        }
    }

    private fun affectingCommits(file: String, commits: List<CommitInfo>): List<CommitInfo> {
        return commits.filter {
            it.fileActions.any { action -> action.newPath == file && action.changeType == DiffEntry.ChangeType.MODIFY }
        }
    }
}
