package org.jetbrains.bunches

import org.jetbrains.bunches.cp.Settings
import org.jetbrains.bunches.cp.cherryPick
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.generatePickedCommitMessage
import org.jetbrains.bunches.restore.doOneStepSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class BunchCPTest : BunchBaseTest() {

    private fun runCPWithRedirectedOutput(sinceCommit: CommitInfo, utilCommit: CommitInfo, suffix: String): CommitInfo {
        val tempOutputStream = ByteArrayOutputStream()
        val savedOutput = System.out
        System.setOut(PrintStream(tempOutputStream))
        cherryPick(
            Settings(
                directory.absolutePath,
                getCommitHash(sinceCommit),
                getCommitHash(utilCommit),
                suffix
            )
        )
        System.out.flush()
        System.out.flush()
        System.setOut(savedOutput)
        testOutput.append(tempOutputStream.toString())
        return getLastCommit()
    }

    private fun assertContentEquals(file: File, text: String) {
        assertTrue(file.exists())
        assertEquals(text, file.readText())
    }

    private fun assertContentEquals(first: File, second: File) {
        assertTrue(first.exists())
        assertTrue(second.exists())
        assertEquals(first.readLines(), second.readLines())
    }

    private fun writeTextAndCommitChanges(file: File, text: String, message: String? = null): CommitInfo {
        file.writeText(text)
        gitManager.gitAdd(file)
        return commitCurrentChanges(message)
    }

    private fun addAndCommitChanges(file: File, text: String, message: String? = null): CommitInfo {
        return writeTextAndCommitChanges(file, file.readText() + text, message)
    }

    private fun compareCommitsWithoutHashAndTime(first: CommitInfo, second: CommitInfo, extension: String) {
        assertEquals(generatePickedCommitMessage(first, extension), second.title)
        assertEquals(first.author?.emailAddress, second.author?.emailAddress)
        assertEquals(first.author?.name, second.author?.name)
        assertEquals(first.fileActions.map { it.content }, second.fileActions.map { it.content })
        assertEquals(first.fileActions.map { it.newPath + ".$extension" }, second.fileActions.map { it.newPath })

    }

    private fun checkCommitsAfterCP(lastCommitBefore: CommitInfo, branch: String, commits: List<CommitInfo>) {
        val realCommits = getCommitsAfter(lastCommitBefore)

        assertEquals(commits.size, realCommits.size)
        for ((first, second) in realCommits.zip(commits)) {
            compareCommitsWithoutHashAndTime(second, first, branch)
        }
    }

    private fun checkCommitsAfterCP(branch: String, commits: List<CommitInfo>) {
        checkCommitsAfterCP(commits.first(), branch, commits)
    }

    private fun checkCommitsAfterCP(branch: String, vararg commits: CommitInfo) {
        checkCommitsAfterCP(branch, commits.toList())
    }

    @Test
    fun oneCommitExistingFileCPTest() {
        val mainFile = createAndAddFile("file")
        val bunchFile = createAndAddFile("file.234")

        val initCommit = commitCurrentChanges()

        val newText = mainFile.readText() + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val modCommit = writeTextAndCommitChanges(mainFile, newText)
        runCPWithRedirectedOutput(modCommit, initCommit, "234")

        assertContentEquals(mainFile, newText)
        assertContentEquals(bunchFile, newText)
        checkCommitsAfterCP("234", modCommit)
        assertDirectoryFiles(mainFile, bunchFile)
    }


    @Test
    fun oneCommitNonExistingFileCPTest() {
        val mainFile = createAndAddFile("main")
        val initCommit = commitCurrentChanges()

        val changeCommit = addAndCommitChanges(mainFile, "\nfixed line")
        val newText = mainFile.readText()
        runCPWithRedirectedOutput(changeCommit, initCommit, "122")

        val bunchFile = File(directory, "main.122")
        assertContentEquals(bunchFile, newText)
        assertContentEquals(mainFile, newText)
        checkCommitsAfterCP("122", changeCommit)
        assertDirectoryFiles(mainFile, bunchFile)
    }

    @Test
    fun notApplicableCommitCPTest() {
        val mainFile = createAndAddFile("otherFile")
        val bunchFile = createAndAddFile("otherFile.346",
            mainFile.readText().dropLast(5) + " additional info")

        val initCommit = commitCurrentChanges()

        val text = """
            some interesting text
            it has a lot of lines
        """.trimIndent()
        val changeCommit = writeTextAndCommitChanges(mainFile, text)

        runCPWithRedirectedOutput(changeCommit, initCommit, "346")
        checkCommitsAfterCP("346", changeCommit)

        assertContentEquals(mainFile, text)
        assertContentEquals(mainFile, bunchFile)

        assertDirectoryFiles(mainFile, bunchFile)
    }

    @Test
    fun severalCommitsCPTest() {
        val mainFile = createAndAddFile("cat.txt")
        val bunchFile = createAndAddFile("cat.txt.12")

        val initCommit = commitCurrentChanges()

        val changeCommits = (1..10).map {
            addAndCommitChanges(mainFile, "\nchanges in commit $it\n", "committed_in_$it")
        }
        val finalMainFileText = mainFile.readText()

        runCPWithRedirectedOutput(changeCommits.last(), initCommit, "12")
        checkCommitsAfterCP("12", changeCommits.reversed())
        assertEquals(finalMainFileText, mainFile.readText())
        assertContentEquals(mainFile, bunchFile)
        assertDirectoryFiles(mainFile, bunchFile)
    }

    @Test
    fun cpAfterSwitchEqualityTest() {
        val bunchFile = createAndAddFile("file.394")
        val mainFile = createAndAddFile("file")
        commitCurrentChanges()

        addAndCommitChanges(mainFile, "kot")
        val mainFileText = mainFile.readText()

        doOneStepSwitch(listOf("300", "394"), directory.absolutePath, "switch to 394")
        val switchCommit = getLastCommit()

        val bunchFileChangeCommit = addAndCommitChanges(mainFile, "sovsem ne kot")
        val bunchFileText = mainFile.readText()

        runCPWithRedirectedOutput(bunchFileChangeCommit, switchCommit, "394")
        mainFile.writeText(getFile("file.300").readText())
        assertTrue(getFile("file.300").delete())

        assertContentEquals(mainFile, mainFileText)
        assertContentEquals(bunchFile, bunchFileText)

        assertDirectoryFiles(mainFile, bunchFile)
    }

    @Test
    fun severalFilesCPTest() {
        val files = (1..10).map { createAndAddFile("file$it.doc") }
        val initCommit = commitCurrentChanges()
        val texts = files.map {
            it.writeText("new text in ${it.name}")
            gitManager.gitAdd(it)
            it.readText()
        }

        val changesCommit = commitCurrentChanges()
        runCPWithRedirectedOutput(changesCommit, initCommit, "42")

        for ((text, file) in texts.zip(files)) {
            assertContentEquals(file, text)
            assertContentEquals(getFile(file.name, "42"), text)
        }

        checkCommitsAfterCP("42", changesCommit)
        assertDirectoryFiles(files.plus(files.map { getFile(it.name, "42") }))
    }

    @Test
    fun cpTwiceTest() {
        val file = createAndAddFile("file")
        val initCommit = commitCurrentChanges()

        val modCommit = addAndCommitChanges(file, "info")

        val firstCPCommit = runCPWithRedirectedOutput(modCommit, initCommit, "42")
        compareCommitsWithoutHashAndTime(modCommit, firstCPCommit, "42")

        val secondCPCommit = runCPWithRedirectedOutput(modCommit, initCommit,"43")
        compareCommitsWithoutHashAndTime(modCommit, secondCPCommit, "43")

        val firstBunchFile = getFile(file.name, "42")
        val secondBunchFile = getFile(file.name, "43")

        assertContentEquals(file, firstBunchFile)
        assertContentEquals(file, secondBunchFile)
        assertDirectoryFiles(file, firstBunchFile, secondBunchFile)

        assertCommitHistoryEquals(secondCPCommit, firstCPCommit, modCommit, initCommit)
    }

    @Test
    fun notAllCommitsPickTest() {
        val mainFile = createAndAddFile("newFile")
        val initCommit = commitCurrentChanges()

        val firstModCommit = addAndCommitChanges(mainFile, "new text")
        val firstText = mainFile.readText()
        val secondModCommit = addAndCommitChanges(mainFile, "text")
        val thirdModCommit = addAndCommitChanges(mainFile, "another text")
        val thirdText = mainFile.readText()

        val firstCPCommit = runCPWithRedirectedOutput(firstModCommit, initCommit, "32")
        val secondCPCommit = runCPWithRedirectedOutput(thirdModCommit, secondModCommit, "33")

        val firstCommitBunchFile = getFile(mainFile.name, "32")
        val secondCommitBunchFile = getFile(mainFile.name, "33")

        compareCommitsWithoutHashAndTime(firstModCommit, firstCPCommit, "32")
        compareCommitsWithoutHashAndTime(thirdModCommit, secondCPCommit, "33")

        assertContentEquals(firstCommitBunchFile, firstText)
        assertContentEquals(secondCommitBunchFile, thirdText)
        assertContentEquals(mainFile, thirdText)

        assertCommitHistoryEquals(secondCPCommit, firstCPCommit, thirdModCommit,
            secondModCommit, firstModCommit, initCommit)
        assertDirectoryFiles(mainFile, firstCommitBunchFile, secondCommitBunchFile)
    }
}
