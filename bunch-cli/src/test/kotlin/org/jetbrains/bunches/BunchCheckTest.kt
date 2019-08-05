package org.jetbrains.bunches

import org.jetbrains.bunches.check.checkOneCommit
import org.jetbrains.bunches.check.getCreateFileCommitIndexMap
import org.jetbrains.bunches.git.CommitInfo
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty

class BunchCheckTest : BunchBaseTest() {
    private fun checkCommit(commit: CommitInfo, extensions: List<String>, vararg commits: CommitInfo): List<String> {
        return checkOneCommit(
            commit,
            extensions,
            directory.absolutePath,
            getCreateFileCommitIndexMap(commits.toList(), extensions),
            commits.indexOf(commit)
        )
    }

    @Test
    fun noProblemOneCommitCheckTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.a")

        val commit = commitCurrentChanges("init")
        val problemFiles = checkCommit(commit, listOf("b", "a"), commit)

        expectThat(problemFiles).isEmpty()
        assertDirectoryFiles(file, bunchFile)
        assertCommitHistoryEquals(commit)
    }

    @Test
    fun nonExistingBunchFilesCheckTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.a")

        val commit = commitCurrentChanges("init")
        val problemFiles = checkCommit(commit, listOf("a", "b"), commit)

        expectThat(problemFiles).isEmpty()
        assertDirectoryFiles(file, bunchFile)
        assertCommitHistoryEquals(commit)
    }

    @Test
    fun problemsWithCreatedAfterBound() {
        val file = createAndAddFile("file")
        val initCommit = commitCurrentChanges()

        val modCommit = writeTextAndCommitChanges(file, "new text")
        val bunchFile = createAndAddFile("file.a")
        val bunchInitCommit = commitCurrentChanges()

        val problemFiles = checkCommit(modCommit, listOf("a"), bunchInitCommit, modCommit, initCommit)

        expectThat(problemFiles).isEmpty()
        assertCommitHistoryEquals(bunchInitCommit, modCommit, initCommit)
        assertDirectoryFiles(file, bunchFile)
    }

    @Test
    fun notInCommitsBunchFileCheckTest() {
        val main = createAndAddFile("cat")
        val bunch = createFile("cat.a")
        val initCommit = commitCurrentChanges()

        val modCommit = writeTextAndCommitChanges(main, "new_text")
        val problemFiles = checkCommit(modCommit, listOf("a", "b"), modCommit, initCommit)

        expectThat(problemFiles).containsExactly(bunch.relativePath())
        assertCommitHistoryEquals(modCommit, initCommit)
        assertDirectoryFiles(
            main, bunch
        )
    }

    @Test
    fun deletedBunchFileCheckTest() {
        val main = createAndAddFile("cat")
        val bunch = createAndAddFile("cat.a")
        val initCommit = commitCurrentChanges()

        delete(bunch)
        val deleteCommit = commitCurrentChanges()

        val modCommit = writeTextAndCommitChanges(main, "new_text")
        val problemFiles = checkCommit(modCommit, listOf("a", "b"), modCommit, deleteCommit, initCommit)

        expectThat(problemFiles).isEmpty()
        assertCommitHistoryEquals(modCommit, deleteCommit, initCommit)
        assertDirectoryFiles(main)
    }

    @Test
    fun addAfterDeleteBunchFileTest() {
        val mainFile = createAndAddFile("file")
        var bunchFile = createAndAddFile("file.aa")
        val initCommit = commitCurrentChanges()

        val firstModCommit = writeTextAndCommitChanges(mainFile, "new_text")

        delete(bunchFile)
        val deleteCommit = commitCurrentChanges()

        val secondModCommit = writeTextAndCommitChanges(mainFile, "ne new_text")

        bunchFile = createAndAddFile("file.aa")
        val bunchAddCommit = commitCurrentChanges()

        val problemFiles = checkCommit(
            secondModCommit, listOf("aa"),
            bunchAddCommit, secondModCommit, deleteCommit, firstModCommit, initCommit
        )

        expectThat(problemFiles).isEmpty()
        assertDirectoryFiles(mainFile, bunchFile)
    }

    @Test
    fun severalBunchFilesProblemTest() {
        val mainFile = createAndAddFile("file.txt")
        val bunchFiles = (1..100).map { createFile("file.txt.$it") }
        val initCommit = commitCurrentChanges()

        val problemFiles = checkCommit(initCommit, (1..100).map { it.toString() }, initCommit)

        assertEquals(bunchFiles.map { it.relativePath() }, problemFiles)
        assertDirectoryFiles(bunchFiles.plus(mainFile))
        assertCommitHistoryEquals(initCommit)
    }

    @Test
    fun severalMainFilesProblemTest() {
        val mainFiles = (1..100).map { createAndAddFile("main$it.txt") }
        val bunchFiles = mainFiles.map { createAndAddBunchFile(it, "bunch") }
        val initCommit = commitCurrentChanges()

        mainFiles.filter { mainFiles.indexOf(it) % 2 != 0}.forEach {
            it.writeText("another_new_text")
            gitManager.gitAdd(it)
        }
        val modCommit = commitCurrentChanges()

        val problemFiles = checkCommit(modCommit, listOf("bunch"), modCommit, initCommit)
        expectThat(problemFiles)
            .containsExactlyInAnyOrder(bunchFiles.filter { bunchFiles.indexOf(it) % 2 != 0 }.map { it.relativePath() })
        assertCommitHistoryEquals(modCommit, initCommit)
        assertDirectoryFiles(bunchFiles.plus(mainFiles))
    }

}