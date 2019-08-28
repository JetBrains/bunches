package org.jetbrains.bunches

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.FileAction
import org.jetbrains.bunches.switch.*
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BunchSwitchTest : BunchBaseTest() {
    private val filesInitialText = mutableMapOf<File, String>()

    private fun savedFileText(file: File): String {
        return filesInitialText[file] ?: throw BunchException("No saved text for file ${file.name}")
    }

    private fun assertEqualsSavedFileText(file: File, otherFile: File) {
        assertFileContent(file, savedFileText(otherFile))
    }

    private fun createAndAddFileWithSaving(filename: String, text: String? = null): File {
        val newFile = super.createAndAddFile(filename, text)
        filesInitialText[newFile] = newFile.readText()
        return newFile
    }

    private fun generateFileAction(
        affectedFile: File,
        changeType: DiffEntry.ChangeType,
        textSource: File
    ): FileAction {
        return FileAction(
            changeType,
            affectedFile.relativePath(),
            savedFileText(textSource)
        )
    }

    private fun assertCorrectSwitchHappens(
        mainFile: File,
        bunchFile: File,
        newMainFile: File,
        otherBunchFiles: List<File>
    ) {
        assertEqualsSavedFileText(newMainFile, mainFile)
        assertEqualsSavedFileText(mainFile, bunchFile)
        for (file in otherBunchFiles.plus(bunchFile)) {
            assertEqualsSavedFileText(file, file)
        }
    }

    private fun assertCorrectSwitchHappens(
        mainFile: File,
        bunchFile: File,
        newMainFile: File,
        vararg otherBunchFiles: File
    ) {
        assertCorrectSwitchHappens(mainFile, bunchFile, newMainFile, otherBunchFiles.toList())
    }

    @Test
    fun oneFileOneStepSwitchTest() {
        val mainFile = createAndAddFileWithSaving("file.txt")
        val bunchFile = createAndAddFileWithSaving("file.txt.123")
        commitCurrentChanges()

        doOneStepSwitch(listOf("122", "123"), directory.absolutePath, "switch to 123")

        val savedMainFile = getFile(mainFile.name, "122")
        assertCorrectSwitchHappens(mainFile, bunchFile, savedMainFile)

        assertDirectoryFiles(savedMainFile, bunchFile, mainFile)
    }

    @Test
    fun oneFileStepByStepSwitchTest() {
        val mainFile = createAndAddFileWithSaving("file.txt")
        val bunchFile = createAndAddFileWithSaving("file.txt.123")
        commitCurrentChanges()

        doStepByStepSwitch(listOf("122", "123"), directory.absolutePath, "switch to 123")

        val savedMainFile = getFile(mainFile.name, "122")
        assertCorrectSwitchHappens(mainFile, bunchFile, savedMainFile)

        assertDirectoryFiles(savedMainFile, bunchFile, mainFile)
    }

    @Test
    fun switchThrowsNoBranchFilesTest() {
        val mainFile = createAndAddFileWithSaving("file")
        val bunchFile = createAndAddFileWithSaving("file.123")
        val savedMainFile = createAndAddFileWithSaving("file.122")
        val initCommit = commitCurrentChanges()

        assertThrows<BunchException>(SAVED_VALUE_EXISTS) {
            doOneStepSwitch(listOf("122", "123"), directory.absolutePath, RESTORE_COMMIT_TITLE)
        }
        assertThrows<BunchException>(SAVED_VALUE_EXISTS) {
            doStepByStepSwitch(listOf("122", "123"), directory.absolutePath, RESTORE_COMMIT_TITLE)
        }

        assertCommitHistoryEquals(initCommit)

        assertEqualsSavedFileText(mainFile, mainFile)
        assertEqualsSavedFileText(bunchFile, bunchFile)
        assertEqualsSavedFileText(savedMainFile, savedMainFile)
    }

    @Test
    fun switchThrowsNoGitRepoTest(@TempDir dir: File) {
        assertThrows<BunchException>(NO_GIT_EXCEPTION_TEXT) {
            doOneStepSwitch(
                listOf("122", "123"),
                dir.absolutePath,
                "try to switch"
            )
        }

        assertThrows<BunchException>(NO_GIT_EXCEPTION_TEXT) {
            doStepByStepSwitch(
                listOf("122", "123"),
                dir.absolutePath,
                "try to switch"
            )
        }

        assertDirectoryFiles()
    }

    @Test
    fun intermediateFilesOneStepSwitchTest() {
        val branches = listOf("122", "124", "123")

        val mainFile = createAndAddFileWithSaving("file")
        val firstBunchFile = createAndAddFileWithSaving("file.123")
        val secondBunchFile = createAndAddFileWithSaving("file.124")

        val initCommit = commitCurrentChanges()

        doOneStepSwitch(branches, directory.absolutePath, RESTORE_COMMIT_TITLE)
        val switchCommit = getLastCommit()

        val newMainFile = getFile(mainFile.name, "122")

        assertCorrectSwitchHappens(mainFile, firstBunchFile, newMainFile, secondBunchFile)

        assertDirectoryFiles(newMainFile, mainFile, firstBunchFile, secondBunchFile)
        assertCommitHistoryEquals(switchCommit, initCommit)
    }

    @Test
    fun intermediateFilesStepByStepSwitchTest() {
        val bunch = listOf("122", "124", "123", "125")

        val mainFile = createAndAddFileWithSaving("file")
        val bunchFileList = bunch.drop(1).map { createAndAddFileWithSaving("file.$it") }

        val initCommit = commitCurrentChanges()

        doStepByStepSwitch(bunch, directory.absolutePath, RESTORE_COMMIT_TITLE)
        val switchCommits = getCommitsAfter(initCommit)
        val newMainFile = getFile(mainFile.name, "122")

        assertCorrectSwitchHappens(mainFile, bunchFileList.last(), newMainFile, bunchFileList)

        assertDirectoryFiles(bunchFileList.plus(newMainFile).plus(mainFile))

        val creationCommit = switchCommits.last()
        checkCommitActions(creationCommit, generateFileAction(newMainFile, DiffEntry.ChangeType.ADD, mainFile))

        val bunchStepsCommits = switchCommits.dropLast(1)
        for ((commit, bunchFile) in bunchStepsCommits.zip(bunchFileList.reversed())) {
            checkCommitActions(commit, generateFileAction(mainFile, DiffEntry.ChangeType.MODIFY, bunchFile))
        }
    }

    @Test
    fun severalFilesOneStepSwitchTest() {
        val branches = listOf("122", "124")

        val mainFiles = mutableListOf<File>()
        val bunchFiles = mutableListOf<File>()

        for (i in 0..10) {
            mainFiles.add(createAndAddFileWithSaving("file$i"))
            bunchFiles.add(createAndAddFileWithSaving("file$i.124"))
        }

        val initCommit = commitCurrentChanges()

        doOneStepSwitch(branches, directory.absolutePath, RESTORE_COMMIT_TITLE)
        val switchCommit = getLastCommit()

        for ((mainFile, bunchFile) in mainFiles.zip(bunchFiles)) {
            assertCorrectSwitchHappens(mainFile, bunchFile, getFile(mainFile.name, "122"))
        }

        assertCommitHistoryEquals(switchCommit, initCommit)
        assertDirectoryFiles(mainFiles.map { getFile(it.name, "122") }.plus(mainFiles).plus(bunchFiles))
    }

    @Test
    fun severalFilesStepByStepSwitchTest() {
        val branches = listOf("122", "124")

        val mainFiles = mutableListOf<File>()
        val bunchFiles = mutableListOf<File>()

        for (i in 0..10) {
            mainFiles.add(createAndAddFileWithSaving("file$i"))
            bunchFiles.add(createAndAddFileWithSaving("file$i.124"))
        }

        val initCommit = commitCurrentChanges()
        gitLog()

        doStepByStepSwitch(branches, directory.absolutePath, RESTORE_COMMIT_TITLE)
        val switchCommits = getCommitsAfter(initCommit)
        assertEquals(branches.size, switchCommits.size)

        val addingCommit = switchCommits.last()
        checkCommitActions(
            addingCommit,
            mainFiles.map { generateFileAction(getFile(it.name, "122"), DiffEntry.ChangeType.ADD, it) })

        val stepsCommit = switchCommits.dropLast(1)
        for (commit in stepsCommit) {
            val changes =
                bunchFiles.map { generateFileAction(getFile(it.nameWithoutExtension), DiffEntry.ChangeType.MODIFY, it) }
            checkCommitActions(commit, changes)
        }

        for ((mainFile, bunchFile) in mainFiles.zip(bunchFiles)) {
            assertCorrectSwitchHappens(mainFile, bunchFile, getFile(mainFile.name, "122"))
        }

        assertDirectoryFiles(mainFiles.map { getFile(it.name, "122") }.plus(mainFiles).plus(bunchFiles))
    }

    @Test
    fun filesFromDifferentBranchesOneStepSwitchTest() {
        val branches = listOf("122", "192", "ct", "193", "mfti")
        val bunchFiles = mutableListOf<File>()
        val mainFiles = mutableListOf<File>()

        for (extension in branches.drop(1)) {
            mainFiles.add(createAndAddFileWithSaving("main$extension"))
            for (innerExtension in branches.drop(1).dropLastWhile { it != extension }) {
                bunchFiles.add(createAndAddFileWithSaving("main$extension.$innerExtension"))
            }
        }

        val initCommit = commitCurrentChanges()

        doOneStepSwitch(branches, directory.absolutePath, "testing multi branch switch")
        val switchCommit = getLastCommit()

        for (mainFile in mainFiles) {
            val currentBunchFiles = bunchFiles.filter { it.nameWithoutExtension == mainFile.name }
            assertCorrectSwitchHappens(
                mainFile,
                currentBunchFiles.last(),
                getFile(mainFile.name, "122"),
                currentBunchFiles
            )
        }

        assertDirectoryFiles(mainFiles.map { getFile(it.name, "122") }.plus(mainFiles).plus(bunchFiles))
        assertCommitHistoryEquals(switchCommit, initCommit)
    }

    @Test
    fun filesFromDifferentBranchesStepByStepSwitchTest() {
        val branches = listOf("122", "192", "ct", "193", "fift")
        val bunchFiles = mutableListOf<File>()
        val mainFiles = mutableListOf<File>()

        for (extension in branches.drop(1)) {
            mainFiles.add(createAndAddFileWithSaving("main$extension"))
            for (innerExtension in branches.drop(1).dropLastWhile { it != extension }) {
                bunchFiles.add(createAndAddFileWithSaving("main$extension.$innerExtension"))
            }
        }

        val initCommit = commitCurrentChanges()

        doStepByStepSwitch(branches, directory.absolutePath, "testing multi branch switch")
        val newCommits = getCommitsAfter(initCommit)

        val addingCommit = newCommits.last()
        val stepsCommits = newCommits.dropLast(1)

        val addingFileChanges = mainFiles.map {
            generateFileAction(getFile(it.name, "122"), DiffEntry.ChangeType.ADD, it)
        }
        checkCommitActions(addingCommit, addingFileChanges)

        val bunchesExtensions = branches.drop(1).reversed()
        for ((extension, commit) in bunchesExtensions.zip(stepsCommits)) {
            val currentBunchFiles = bunchFiles.filter { it.extension == extension }
            val currentStepFileChanges =
                currentBunchFiles.map {
                    generateFileAction(
                        getFile(it.nameWithoutExtension),
                        DiffEntry.ChangeType.MODIFY,
                        it
                    )
                }
            checkCommitActions(commit, currentStepFileChanges)
        }

        for (mainFile in mainFiles) {
            val currentBunchFiles = bunchFiles.filter { it.nameWithoutExtension == mainFile.name }
            assertCorrectSwitchHappens(
                mainFile,
                currentBunchFiles.last(),
                getFile(mainFile.name, "122"),
                currentBunchFiles
            )
        }
        assertDirectoryFiles(mainFiles.map { getFile(it.name, "122") }.plus(mainFiles).plus(bunchFiles))
    }
}