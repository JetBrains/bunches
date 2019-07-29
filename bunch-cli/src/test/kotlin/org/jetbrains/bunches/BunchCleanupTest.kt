package org.jetbrains.bunches

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.cleanup.DEFAULT_CLEANUP_COMMIT_TITLE
import org.jetbrains.bunches.cleanup.EXT_PATTERN
import org.jetbrains.bunches.cleanup.Settings
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.FileAction
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

class BunchCleanupTest : BunchBaseTest() {

    private fun doCleanup(
        extension: String?,
        title: String = DEFAULT_CLEANUP_COMMIT_TITLE
    ): CommitInfo {
        cleanup(Settings(directory.path, directory.path, extension, title, false))
        return getLastCommit()
    }

    private fun checkCleanupCommit(
        commit: CommitInfo,
        files: List<File>,
        message: String = DEFAULT_CLEANUP_COMMIT_TITLE
    ) {
        val extensions = files.map { it.extension }.toSet()
        val commitsMessage = message.replace(EXT_PATTERN, extensions.singleOrNull() ?: "")
        assertEquals(
            files.map {
                FileAction(
                    DiffEntry.ChangeType.DELETE,
                    it.relativePath(),
                    ""
                )
            }.toSet(),
            commit.fileActions.toSet()
        )
        assertEquals(commit.title, commitsMessage)
    }

    private fun checkCleanupCommit(
        commit: CommitInfo,
        vararg files: File,
        message: String = DEFAULT_CLEANUP_COMMIT_TITLE
    ) {
        checkCleanupCommit(commit, files.toList(), message)
    }

    @Test
    fun simpleOneFileCleanupTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()

        val cleanupCommit = doCleanup("123")

        assertDirectoryFiles(file)
        assertCommitHistoryEquals(cleanupCommit, initCommit)
        checkCleanupCommit(cleanupCommit, bunchFile)
    }

    @Test
    fun additionalFileCleanupTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.122")
        val otherBunchFile = createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()

        val cleanupCommit = doCleanup("123")

        assertDirectoryFiles(file, bunchFile)
        assertCommitHistoryEquals(cleanupCommit, initCommit)
        checkCleanupCommit(cleanupCommit, otherBunchFile)
    }

    @Test
    fun removeAllFilesCleanupTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.122")
        createAndAddFile("file.line")
        createAndAddFile("file.123")
        val initCommit = commitCurrentChanges()
        configureBunchFile("line", "123")
        val cleanupCommit = doCleanup(null)

        assertDirectoryFiles(file, bunchFile)
        assertCommitHistoryEquals(cleanupCommit, initCommit)
    }

    @Test
    fun removeSeveralFilesCleanupTest() {
        val files = (1..10).map { createAndAddFile("main$it") }
        val aBunchFiles = (1..10 step 2).map { createAndAddFile("main$it.a") }
        val bBunchFiles = files.map { createAndAddFile("${it.name}.b") }

        val initCommit = commitCurrentChanges()
        val cleanupCommit = doCleanup("a")

        assertCommitHistoryEquals(cleanupCommit, initCommit)
        checkCleanupCommit(cleanupCommit, aBunchFiles)
        assertDirectoryFiles(bBunchFiles.plus(files))
    }

    @Test
    fun noCommitCleanupTest() {
        val files = (1..10).map { createAndAddFile("main$it") }
        files.map { createAndAddFile("${it.name}.a") }
        val initCommit = commitCurrentChanges()

        cleanup(Settings(directory.path, directory.path, "a", DEFAULT_CLEANUP_COMMIT_TITLE, true))

        assertCommitHistoryEquals(initCommit)
        assertDirectoryFiles(files)
    }

    @Test
    fun severalCleanupsTest() {
        val files = (1..5).map { createAndAddFile("file$it.txt") }
        val bunchFiles = (1..8).map { index ->
            files.map { createAndAddFile("${it.name}.$index") }
        }

        val initCommit = commitCurrentChanges()

        val commits = bunchFiles.map { bunch ->
            val extension = bunch.map { it.extension }.toSet().single()
            val message = "cleanup $extension"
            val commit = doCleanup(extension, message)
            checkCleanupCommit(commit, bunch, message)
            commit
        }

        assertCommitHistoryEquals(commits.reversed().plus(initCommit))
        assertDirectoryFiles(files)
    }
}