package org.jetbrains.bunches

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.FileAction
import org.jetbrains.bunches.reduce.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream


class BunchReduceTest : BunchBaseTest() {
    private fun reduce(
        action: ReduceAction,
        tempOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
    ): CommitInfo? {
        val savedOutput = System.out
        System.setOut(PrintStream(tempOutputStream))

        doReduce(
            Settings(
                directory.absolutePath,
                directory.absolutePath,
                action,
                "reduce"
            )
        )
        val commit = if (action == ReduceAction.COMMIT) {
            getLastCommit()
        } else {
            null
        }
        System.out.flush()
        System.setOut(savedOutput)
        testOutput.append(tempOutputStream.toString())

        return commit
    }

    private fun checkDeletedFiles(commit: CommitInfo, files: List<File>) {
        val changes = files.map { FileAction(DiffEntry.ChangeType.DELETE, it.relativePath(), "") }
        expectThat(commit.fileActions).containsExactlyInAnyOrder(changes)
    }

    private fun checkDeletedFiles(commit: CommitInfo, vararg files: File) {
        checkDeletedFiles(commit, files.toList())
    }

    private fun commitReduce(tempOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()): CommitInfo {
        return reduce(ReduceAction.COMMIT, tempOutputStream) ?: throw BunchException("No reduce commit")
    }

    private fun reducibleFiles(): List<File> {
        return getReducibleFiles(directory.absolutePath, directory.absolutePath)
    }

    @BeforeEach
    fun configBranchesFile() {
        configureBunchFile("122", "123", "124", "cat", "125", "126")
    }

    @Test
    fun oneReducibleFilesReduceTest() {
        val file = createFile("file")
        val bunchFile = createFile("file.124")
        bunchFile.writeText(file.readText())

        val files = reducibleFiles()

        expectThat(files).containsExactly(bunchFile)
    }

    @Test
    fun oneNoExistingBranchReducibleFilesReduceTest() {
        val file = createFile("file")
        val bunchFile = createFile("file.129")
        bunchFile.writeText(file.readText())

        val files = reducibleFiles()

        expectThat(files).isEmpty()
    }

    @Test
    fun oneNonReducibleFilesReduceTest() {
        val file = createFile("file")
        val bunchFile = createFile("file.125")
        bunchFile.writeText(file.readText() + "1")

        val files = reducibleFiles()

        expectThat(files).isEmpty()
    }

    @Test
    fun severalReducibleFilesReduceTest() {
        val files = (1..100).map { createFile("file$it") }
        val bunchFiles = files.map {
            val file = createFile("${it.name}.125")
            file.writeText(it.readText())
            file
        }
        expectThat(reducibleFiles()).containsExactlyInAnyOrder(bunchFiles)
    }

    @Test
    fun severalExtensionsReduceTest() {
        (1..100).map { createFile("file$it") }
        val bunchFiles = (1..100).map {
            val extension = 123 + it % 4
            val file = createFile("file$it.$extension")
            file.writeText(getFile("file$it").readText())
            file
        }
        expectThat(reducibleFiles()).containsExactlyInAnyOrder(bunchFiles)
    }

    @Test
    fun oneFileDeleteReduceTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.123")
        bunchFile.writeText(file.readText())
        reduce(ReduceAction.DELETE)
        assertDirectoryFiles(file)
    }

    @Test
    fun noCommitDeleteReduceTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()
        bunchFile.writeText(file.readText())
        gitManager.gitAdd(bunchFile)
        val modCommit = commitCurrentChanges()
        reduce(ReduceAction.DELETE)
        assertDirectoryFiles(file)
        assertCommitHistoryEquals(modCommit, initCommit)
    }

    @Test
    fun commitReducedChangesTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()
        bunchFile.writeText(file.readText())
        gitManager.gitAdd(bunchFile)
        val modCommit = commitCurrentChanges()
        val reduceCommit = commitReduce()

        assertDirectoryFiles(file)
        assertCommitHistoryEquals(reduceCommit, modCommit, initCommit)
        checkDeletedFiles(reduceCommit, bunchFile)
    }

    @Test
    fun noFilesToReduceCommitTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()
        bunchFile.writeText(file.readText() + "1")
        gitManager.gitAdd(bunchFile)
        val modCommit = commitCurrentChanges()
        commitReduce()

        assertDirectoryFiles(file, bunchFile)
        assertCommitHistoryEquals(modCommit, initCommit)
    }

    @Test
    fun noFilesToReducePrintTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()
        bunchFile.writeText(file.readText() + "1")
        gitManager.gitAdd(bunchFile)
        val modCommit = commitCurrentChanges()
        val outputStream = ByteArrayOutputStream()
        reduce(ReduceAction.PRINT, outputStream)

        assertDirectoryFiles(file, bunchFile)
        assertCommitHistoryEquals(modCommit, initCommit)
        assertEquals(EMPTY_REDUCE_MESSAGE, outputStream.toString())
    }

    @Test
    fun fileToReducePrintTest() {
        val file = createFile("file")
        val bunchFile = createFile("file.123")
        bunchFile.writeText(file.readText())

        val outputStream = ByteArrayOutputStream()
        reduce(ReduceAction.PRINT, outputStream)

        assertDirectoryFiles(file, bunchFile)
        expectThat(outputStream.toString().split(Regex("\\s")).filter { it.isNotBlank() })
            .containsExactly(bunchFile.relativePath())
    }

    @Test
    fun reduceWithUncommittedChangesTest() {
        val file = createAndAddFile("file")
        assertThrows<BunchException>(UNCOMMITTED_CHANGES_MESSAGE) { commitReduce() }
        val added = gitManager.gitStatus().added
        expectThat(added).containsExactly(file.relativePath())
        assertDirectoryFiles(file)
    }
}