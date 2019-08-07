package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.BunchBaseTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.succeeded
import java.io.File

class BunchPreCommitCheckerTest : BunchBaseTest() {
    private fun runPreCommitCheck(arguments: List<String>): List<File> {
        val mutableArgumentList = arguments.toMutableList()
        mutableArgumentList.add(0, directory.absolutePath)
        return precommitLostFiles(mutableArgumentList.toTypedArray()).toList()
    }

    private fun forgottenFiles(): List<File> {
        val arguments = gitManager.gitDiff().map { it.absolutePath }
            .toMutableList()
        return runPreCommitCheck(arguments)
    }

    //For some reason JGit can not do some actions without root commit
    @BeforeEach
    fun initBunchFile() {
        configureBunchFile("aaa", "124", "cat", "533")
        gitManager.gitAdd(getFile(".bunch"))
        commitCurrentChanges("adding bunch [root commit]")
    }

    @Test
    fun noForgottenFilesPreCommitTest() {
        createAndAddFile("main.txt")
        createAndAddFile("main.txt.aaa")
        expectThat(forgottenFiles()).isEmpty()
    }

    @Test
    fun forgottenFileWithoutBunchPreCommitTest() {
        createAndAddFile("file")
        createAndAddFile("file.123")

        expectThat(forgottenFiles()).isEmpty()
    }

    @Test
    fun oneAddedMainFilePreCommitTest() {
        createAndAddFile("file")
        createFile("file", "aaa")

        expectThat(forgottenFiles()).isEmpty()
    }

    @Test
    fun oneModifiedMainFilePreCommitTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddBunchFile(file, "533")
        commitCurrentChanges()

        file.writeText("new")
        gitManager.gitAdd(file)

        expectThat(forgottenFiles()).containsExactly(bunchFile)
    }

    @Test
    fun oneDeletedMainFilePreCommitTest() {
        val file = createAndAddFile("file")
        val bunchFile = createAndAddBunchFile(file, "533")
        commitCurrentChanges()

        delete(file)

        expectThat(forgottenFiles()).containsExactly(bunchFile)
    }

    @Test
    fun noExtensionsFromBunchFilePreCommitTest() {
        createAndAddFile("file")
        createFile("file.aaa")
        expectCatching {
            precommitLostFiles(arrayOf(directory.path + "/nonExistentDirectory", "file"))
        }.succeeded().isEmpty()
    }

    @Test
    fun severalMainFilesPreCommitTest() {
        val files = (1..50).map { createAndAddFile("file$it.doc") }
        val bunchFiles = files.map { createAndAddBunchFile(it, "533") }
        commitCurrentChanges()

        files.forEach {
            it.writeText("new unique text for ${it.name}")
            gitManager.gitAdd(it)
        }
        expectThat(forgottenFiles()).containsExactlyInAnyOrder(bunchFiles)
    }

    @Test
    fun onlyBunchFilesCommitPreCommitTest() {
        val file = createFile("file")
        createAndAddBunchFile(file, "cat")

        expectThat(forgottenFiles()).isEmpty()
    }
}