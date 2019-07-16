package org.jetbrains.bunches.hooks

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane

const val GENERATED_COMMIT_MARK = "[POMOG CHEM SMOG]"

fun checkPreRebase(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("Invalid arguments")
        println(1)
        return
    }

    val first = args[0]
    val second = args[1]

    val parent = Runtime.getRuntime().exec("git merge-base $first $second")
        .inputStream.bufferedReader().readLine()

    val gitPath = ""
    val firstBranchCommits = readCommits(gitPath, first, parent)
    val secondBranchCommits = readCommits(gitPath, second, parent)
    val fixedFiles = secondBranchCommits.filter { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }
        .map { it.fileActions }.flatten().map { it.newPath }

    var message = ""
    val deletedFiles = Runtime.getRuntime()
        .exec("git diff-tree -r --diff-filter=D --name-only $first $second")
    val addedFiles = Runtime.getRuntime()
        .exec("git diff-tree -r --diff-filter=A --name-only $first $second")


    firstBranchCommits.forEach { System.err.println(it) }
    secondBranchCommits.forEach { System.err.println(it) }

    val added = addedFiles.inputStream.bufferedReader().readLines().filter {
        forgottenBunchFilesFilter(it, firstBranchCommits)
    }
    val deleted = deletedFiles.inputStream.bufferedReader().readLines().filter {
        forgottenBunchFilesFilter(it, secondBranchCommits)
    }

    fixedFiles.forEach { System.err.println("$it fixed") }
    added.forEach { System.err.println("$it added") }
    deleted.forEach { System.err.println("$it deleted") }

    for (filename in added.minus(fixedFiles)) {
        val commit = findFirstCommit(filename ?: continue, secondBranchCommits) ?: continue
        message += "$filename presents in $second [added in ${commit.title} ${commit.hash}], but does not in $first\n"
    }
    for (filename in deleted) {
        val commit = findFirstCommit(filename, firstBranchCommits) ?: continue
        message += "$filename presents in $first [added in ${commit.title} ${commit.hash}], but does not in $second\n"
    }

    if (message == "") {
        println(0)
        return
    } else {
        message += "Create friendly commits to remind about it?\n"
    }

    val result = JOptionPane.showOptionDialog(
        null, message, "Friendly warning",
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
        arrayOf("Yes", "No"), "Yes"
    )

    if (JOptionPane.YES_OPTION == result) {
        resolveRemoved(deleted, firstBranchCommits, gitPath)
        resolveAdded(added, firstBranchCommits, gitPath)
        println(1)
    } else {
        println(0)
    }
}

private fun forgottenBunchFilesFilter(file: String, commits: List<CommitInfo>): Boolean {
    val extensions = getExtensions()
    if (File(file).extension !in extensions) {
        return false
    }
    val mainFile = fileWithoutExtension(file)
    return commits.any {
        it.fileActions.any { action ->
            action.changeType == DiffEntry.ChangeType.MODIFY && action.newPath == mainFile
        }
    }
}

private fun resolveRemoved(deleted: List<String>, commits: List<CommitInfo>, gitPath: String) {
    val mismatchedFiles = mutableMapOf<CommitInfo, HashSet<File>>()
    for (file in deleted) {
        val newFile = File(file)
        val commit = findFirstCommit(file, commits) ?: continue
        if (!mismatchedFiles.containsKey(commit)) {
            mismatchedFiles[commit] = HashSet<File>()
        }
        mismatchedFiles[commit]?.add(newFile)
    }
    for ((commit, files) in mismatchedFiles) {
        for (file in files) {
            val main = File(fileWithoutExtension(file.path))
            file.createNewFile()
            file.writeText(main.readText())
        }
        val filesList = files.map { FileChange(ChangeType.ADD, it) }
        commitChanges(
            gitPath,
            filesList,
            "Files for correct merge with same files created" +
                    " in [${commit.title}] ${commit.hash} $GENERATED_COMMIT_MARK"
        )
    }
}

private fun resolveAdded(added: List<String>, commits: List<CommitInfo>, gitPath: String) {
    val mismatchedFiles = mutableMapOf<CommitInfo, HashSet<File>>()
    for (file in added) {
        val newFile = File(file)
        for (commit in affectingCommits(fileWithoutExtension(file), commits)) {
            System.err.println("In commit ${commit.title} modified main file of $file")
            if (!mismatchedFiles.containsKey(commit)) {
                mismatchedFiles[commit] = HashSet<File>()
            }
            mismatchedFiles[commit]?.add(newFile)
        }
    }

    for ((commit, files) in mismatchedFiles) {
        files.forEach { Files.write(Paths.get(it.path), "\n".toByteArray(), StandardOpenOption.APPEND) }
        val filesList = files.map { FileChange(ChangeType.MODIFY, it) }
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