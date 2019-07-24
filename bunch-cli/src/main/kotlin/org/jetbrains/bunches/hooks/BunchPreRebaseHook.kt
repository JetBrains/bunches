package org.jetbrains.bunches.hooks

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.git.*
import java.io.File
import java.lang.System.exit
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

const val GENERATED_COMMIT_MARK = "[POMOG CHEM SMOG]"

fun checkPreRebase(args: Array<String>) {
    if (args.size != 4) {
        System.err.println("Invalid arguments")
        println(1)
        exit(1)
        return
    }
    val mode = args[3] == "1"

    val first = args[0]
    val second = args[1]
    val parent = getParent(first, second, mode) ?: return

//    seems it should work like this, but I'm not sure
    val gitPath = ""

    val firstBranchCommits = readCommits(gitPath, first, parent)
    val secondBranchCommits = readCommits(gitPath, second, parent)

    val fixedFiles = secondBranchCommits.filter { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }
        .map { it.fileActions }.flatten().map { it.newPath }


    val added = readDiffFiles(first, second, firstBranchCommits, mode) ?: return
    val deleted = readDiffFiles(second, first, secondBranchCommits, mode) ?: return

    fixedFiles.forEach { System.err.println("$it fixed") }
    added.forEach { System.err.println("$it added") }
    deleted.forEach { System.err.println("$it deleted") }

    var message = added.minus(fixedFiles).filterNotNull().joinToString { filename ->
        val commit = findAddCommit(filename, secondBranchCommits) ?: return@joinToString ""
        "$filename presents in $second [added in ${commit.title} ${commit.hash}], but does not in $first\n"
    } + deleted.joinToString { filename ->
        val commit = findAddCommit(filename, firstBranchCommits) ?: return@joinToString ""
        "$filename presents in $first [added in ${commit.title} ${commit.hash}], but does not in $second\n"
    }

    if (message.isEmpty()) {
        exitWithCode(0, mode)
        return
    } else {
        message += "Create friendly commits to remind about it?\n"
    }

    val result = showInfo(message, mode)

    if (result) {
        resolveRemoved(deleted, firstBranchCommits, gitPath)
        resolveAdded(added, firstBranchCommits, gitPath)
        exitWithCode(1, mode)
    } else {
        exitWithCode(0, mode)
    }

}

private fun forgottenBunchFilesFilter(file: String, commits: List<CommitInfo>, extensions: Set<String>): Boolean {
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
        val commit = findAddCommit(file, commits) ?: continue
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

private fun isTextFile(file: File): Boolean {
    val mainFile = File(file.parent ?: ".", file.nameWithoutExtension)
    val type = Files.probeContentType(mainFile.toPath()) ?: return true
    return type.contentEquals("text")
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
        files.filter {
            isTextFile(it)
        }.forEach { Files.write(Paths.get(it.path), "\n".toByteArray(), StandardOpenOption.APPEND) }
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

private fun readDiffFiles(
    firstBranch: String,
    secondBranch: String,
    commits: List<CommitInfo>, mode: Boolean
): List<String>? {
    try {
        val extensions = getExtensions()
        return readCommandLines(
            "git diff-tree -r --diff-filter=A" +
                    " --name-only $firstBranch $secondBranch"
        )
            .filter {
                forgottenBunchFilesFilter(it, commits, extensions)
            }
    } catch (exception: BunchException) {
        System.err.println(exception.message)
        exitWithCode(1, mode)
    }
    return null
}

private fun readCommandLines(command: String): List<String> {
    return Runtime.getRuntime().exec(command).inputStream.bufferedReader().readLines()
}

private fun getParent(firstBranch: String, secondBranch: String, mode: Boolean): String? {
    val parents = readCommandLines("git merge-base $firstBranch $secondBranch")
    if (parents.size != 1) {
        exitWithCode(1, mode)
        return null
    }
    return parents.first()
}