package org.jetbrains.bunches

import org.eclipse.jgit.api.Git
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.readCommits
import java.io.File

class GitCommandManager(private val directory: File, private val output: StringBuilder) {
    private val userName = "name"
    private val userEmail = "email@mail.ru"
    private val git: Git

    init {
        val initCommand = Git.init()
        git = initCommand.setDirectory(directory).call()

        runCommand("git config user.name $userName")
        runCommand("git config user.email $userEmail")
    }

    internal fun gitAdd(file: File) {
        val path = file.relativeTo(directory.absoluteFile).path.replace('\\', '/')
        git.add().addFilepattern(path).call()
    }

    internal fun gitCommit(message: String): CommitInfo {
        git.commit().setMessage(message).call()
        //commit.toCommitInfo causes NPE for unknown reason
        return readCommits(directory.absolutePath).first()
    }

    internal fun gitLog() {
        for (commit in readCommits(directory.absolutePath)) {
            output.append(commit.title + "\n" + commit.author + " " + commit.hash + "\n")
            output.append(commit.fileActions.joinToString("\n") {
                (it.newPath ?: "no name") + " " + it.changeType
            } + "\n")
        }
    }

    internal fun isNotEmpty(): Boolean {
        return git.branchList().call().isNotEmpty()
    }

    internal fun close() {
        git.repository.close()
    }

    //blocks main thread
    private fun runCommand(command: String) {
        output.append("running $command\n")
        val process = getProcessForCommand(command)
        process.waitFor()
        val processOutput = process.inputStream.bufferedReader().readText()
        val processErrorStream = process.errorStream.bufferedReader().readText()
        if (processOutput.isNotEmpty()) {
            output.append("Output:\n$processOutput\n")
        }
        if (processErrorStream.isNotEmpty()) {
            output.append("Error:\n$processErrorStream\n")
        }
    }

    private fun getProcessForCommand(command: String): Process {
        return Runtime.getRuntime().exec(command, null, directory)
    }
}