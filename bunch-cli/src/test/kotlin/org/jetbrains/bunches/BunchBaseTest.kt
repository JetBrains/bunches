package org.jetbrains.bunches

import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.FileAction
import org.jetbrains.bunches.git.readCommits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.io.File
import java.io.InvalidObjectException
import java.nio.file.Files
import java.util.*

@ExtendWith(BunchBaseTest.MyTestWatcher::class)
open class BunchBaseTest {
    val directory = Files.createTempDirectory("directory").toFile()
        ?: throw InvalidObjectException("failed to create temp directory")

    protected val testOutput = StringBuilder()
    protected val gitManager = GitCommandManager(directory, testOutput)

    protected fun File.relativePath(): String {
        return directory.absoluteFile.toPath().relativize(this.absoluteFile.toPath()).toString()
    }

    protected open fun createAndAddFile(filename: String, text: String? = null): File {
        val newFile = File(directory, filename)
        if (!newFile.createNewFile()) {
            throw BunchException("Failed to create new file $filename")
        }
        newFile.writeText(
            text ?: "generated text with random number ${Random().nextInt()}"
        )
        gitManager.gitAdd(newFile)
        return newFile
    }

    protected fun assertFileContent(file: File, content: String) {
        assertTrue(file.exists())
        assertEquals(content, file.readText())
    }

    protected fun getCommitHash(commit: CommitInfo): String {
        return commit.hash ?: throw InvalidObjectException("commit has no hash")
    }

    protected fun getLastCommit(): CommitInfo {
        return getAllCommits()
            .firstOrNull() ?: throw InvalidObjectException("no last commit")
    }

    private fun getAllCommits(): List<CommitInfo> {
        return readCommits(directory.absolutePath)
    }

    protected fun commitCurrentChanges(message: String? = null): CommitInfo {
        return gitManager.gitCommit(message ?: "commit${Random().nextInt()}")
    }

    private fun assertCommitHistoryEquals(commits: List<CommitInfo>) {
        assertEquals(commits, getAllCommits())
    }


    protected fun assertDirectoryFiles(files: List<File>) {
        assertEquals(files.toSet(), directory.listFiles()?.filterNot { it.isHidden }?.toSet())
    }

    protected fun assertCommitHistoryEquals(vararg commits: CommitInfo) {
        assertCommitHistoryEquals(commits.toList())
    }

    protected fun assertDirectoryFiles(vararg files: File) {
        assertDirectoryFiles(files.toMutableList())
    }

    protected fun getCommitsAfter(commit: CommitInfo): List<CommitInfo> {
        return readCommits(directory.absolutePath, null, getCommitHash(commit))
    }

    protected fun checkCommitActions(commit: CommitInfo, fileActions: List<FileAction>) {
        assertEquals(fileActions.toSet(), commit.fileActions.toSet())
    }

    protected fun checkCommitActions(commit: CommitInfo, vararg fileActions: FileAction) {
        checkCommitActions(commit, fileActions.toList())
    }

    protected fun getFile(filename: String, extension: String = ""): File {
        if (extension.isNotEmpty()) {
            return File(directory, "$filename.$extension")
        }
        return File(directory, filename)
    }

    protected fun gitLog() {
        gitManager.gitLog()
    }

    class MyTestWatcher : TestWatcher {
        override fun testFailed(context: ExtensionContext, cause: Throwable) {
            val currentClass = context.testInstance.get() as BunchBaseTest
            if (currentClass.gitManager.isNotEmpty()) {
                currentClass.gitLog()
            }
            println(currentClass.testOutput.toString().replace("\n", System.lineSeparator()))
        }
    }

    @AfterEach
    fun close() {
        gitManager.close()
    }
}