package org.jetbrains.bunches

import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.generatePickedCommitMessage
import org.jetbrains.bunches.restore.GENERATED_CP_BRANCH
import org.jetbrains.bunches.restore.doRestore
import org.jetbrains.bunches.restore.getSettings
import org.jetbrains.bunches.restore.parseExtensionFromCommit
import org.jetbrains.bunches.switch.RESTORE_COMMIT_TITLE
import org.jetbrains.bunches.switch.doOneStepSwitch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class BunchRestoreTest : BunchBaseTest() {
    private val mainBranch = "branch"
    private val backupBranch = GENERATED_CP_BRANCH + mainBranch
    private var savedCommits = emptyList<CommitInfo>()
    private var switchCommit: CommitInfo? = null
    private var isBackupNeeded = true
    private var noCheck = false
    private var restoreExtension: String? = null
    private val savedFilesValue = mutableMapOf<File, String>()
    private val fileAfterRestore = mutableMapOf<String, String>()

    @BeforeEach
    fun configureProject() {
        configureBunchFile("a", "b", "c", "d")
        createAndAddFile("readme", "aga")
        commitCurrentChanges("init commit")
        gitManager.crateBranch(mainBranch)
        gitManager.checkout(mainBranch)
    }

    private fun doSwitch(branch: String) {
        doOneStepSwitch(listOf("a", branch), directory.absolutePath, RESTORE_COMMIT_TITLE.replace("{target}", branch))
        switchCommit = getLastCommit()
    }

    private fun switchToBranch(branch: String) {
        commitCurrentChanges()
        saveAllFiles()
        doSwitch(branch)
    }

    private fun restore(commit: CommitInfo? = null, extension: String? = null, backup: Boolean = true) {
        savedCommits = getAllCommits(mainBranch)
        val untilRef = commit?.hash ?: ""
        if (extension != null) {
            restoreExtension = extension
        }

        val tempOutputStream = ByteArrayOutputStream()
        val savedOutput = System.out
        System.setOut(PrintStream(tempOutputStream))

        doRestore(getSettings(directory.absolutePath, untilRef, extension, backup))

        System.out.flush()
        System.setOut(savedOutput)
        testOutput.append(tempOutputStream.toString())
    }

    private fun saveFileText(file: File, text: String? = null) {
        savedFilesValue[file] = text ?: file.readText()
    }

    private fun saveFileText(file: File, textFrom: File) {
        savedFilesValue[file] = textFrom.readText()
    }

    private fun createAndSave(filename: String): File {
        val file = createAndAddFile(filename)
        saveFileText(file)
        return file
    }

    private fun saveAllFiles() {
        for (file in getDirectoryFilesList()) {
            saveFileText(file)
        }
    }

    private fun addFileAfterRestore(file: File, otherFile: File) {
        fileAfterRestore[file.relativePath()] = otherFile.relativePath()
    }

    private fun writeAndSave(file: File) {
        touch(file)
        saveFileText(file)
    }

    @AfterEach
    fun check() {
        if (noCheck) {
            return
        }

        if (isBackupNeeded) {
            expectThat(getAllCommits(backupBranch)).isEqualTo(savedCommits)
        } else {
            expectThat(gitManager.branchExist(backupBranch)).isFalse()
        }

        checkFilesInDirectory()
        checkCommits()
    }

    private fun compareCommitsWithoutHashAndTime(
        copiedCommit: CommitInfo,
        initialCommit: CommitInfo,
        extension: String?
    ) {
        expectThat(copiedCommit) {
            hasModifiedFieldWith(
                initialCommit,
                CommitInfo::title,
                { generatePickedCommitMessage(it, extension, "changes in $extension") })
            hasEqualFieldWith(initialCommit) { commitInfo -> commitInfo.author?.name }
            hasEqualFieldWith(initialCommit) { commitInfo -> commitInfo.author?.emailAddress }
            hasEqualFieldWith(initialCommit) { commitInfo -> commitInfo.fileActions.map { it.content } }
        }
        expectThat(copiedCommit.fileActions).hasModifiedFieldsFrom(
            initialCommit.fileActions,
            { it.newPath },
            { fileAfterRestore[it.newPath] })
    }

    private fun checkCommits() {
        val allCommits = getAllCommits(mainBranch)
        val previousCommits = savedCommits.takeLastWhile { it != switchCommit }
        val movedCommits = savedCommits.takeWhile { it != switchCommit }
        val modifiedCommits = allCommits.dropLast(previousCommits.size)
        expectThat(allCommits.takeLast(previousCommits.size)).isEqualTo(previousCommits)
        val extension = restoreExtension
            ?: parseExtensionFromCommit(switchCommit?.message ?: "")
            ?: throw BunchException("No bunch extension in switch commit")
        expectThat(modifiedCommits).equalsBy(movedCommits) { modified, initial ->
            compareCommitsWithoutHashAndTime(modified, initial, extension)
        }
    }

    private fun checkFilesInDirectory() {
        val allFiles = getDirectoryFilesList()
        expectThat(allFiles).containsExactlyInAnyOrder(savedFilesValue.keys)
        for ((file, text) in savedFilesValue) {
            expectThat(file).isContentEqualsTo(text)
        }
    }

    @Test
    fun oneMainFileRestoreTest() {
        val file = createAndSave("file")
        val bunchFile = createAndSave("file.b")

        switchToBranch("b")
        writeTextAndCommitChanges(file, "new text")
        saveFileText(bunchFile, file)
        addFileAfterRestore(file, bunchFile)

        restore()
    }

    @Test
    fun oneBunchFileRestoreTest() {
        val mainFile = createAndAddFile("file")
        val file = createAndAddFile("file.b")
        createAndAddBunchFile(mainFile, "c")

        switchToBranch("c")
        writeTextAndCommitChanges(file, "new text")
        addFileAfterRestore(file, file)
        saveFileText(file)

        restore()

    }

    @Test
    fun oneFileWithoutBunchFilesRestoreTest() {
        val file = createAndAddFile("file")
        val otherFile = createAndAddFile("otherFile")
        createAndAddBunchFile(otherFile, "b")

        switchToBranch("b")
        writeTextAndCommitChanges(file, "new text")
        addFileAfterRestore(file, file)
        saveFileText(file)

        restore()
    }

    @Test
    fun noBackupOneFileRestoreTest() {
        isBackupNeeded = false
        val singleFile = createAndAddFile("file")
        val file = createAndAddFile("otherFile")
        val bunchFile = createAndAddBunchFile(file, "b")

        switchToBranch("b")
        writeAndSave(singleFile)
        commitCurrentChanges()

        touch(file)
        saveFileText(bunchFile, file)
        commitCurrentChanges()
        addFileAfterRestore(file, bunchFile)
        addFileAfterRestore(singleFile, singleFile)

        restore(backup = false)
    }

    @Test
    fun severalTypesInOneCommitRestoreTest() {
        val singleFile = createAndAddFile("file")
        val file = createAndAddFile("otherFile")
        val bunchFile = createAndAddBunchFile(file, "b")
        val otherBunchFile = createAndAddBunchFile(file, "c")

        switchToBranch("b")
        writeAndSave(singleFile)
        touch(file)
        saveFileText(bunchFile, file)
        writeAndSave(otherBunchFile)
        commitCurrentChanges()

        addFileAfterRestore(otherBunchFile, otherBunchFile)
        addFileAfterRestore(file, bunchFile)
        addFileAfterRestore(singleFile, singleFile)

        restore()
    }

    @Test
    fun severalFilesInCommitRestoreTest() {
        val files = (1..10).map { createAndAddFile("file$it") }
        val bunchFiles = files.map { createAndAddBunchFile(it, "d") }

        switchToBranch("d")
        for (file in files) {
            touch(file)
        }
        commitCurrentChanges()

        for ((file, bunchFile) in files.zip(bunchFiles)) {
            addFileAfterRestore(file, bunchFile)
            saveFileText(bunchFile, file)
        }

        restore()
    }

    @Test
    fun severalCommitsRestoreTest() {
        val files = (1..5).map { createAndAddFile("file$it") }
        val bunchFiles = files.map { createAndAddBunchFile(it, "d") }
        val otherBunchFiles = files.map { createAndAddBunchFile(it, "b") }

        switchToBranch("d")
        for (file in files) {
            touch(file)
            commitCurrentChanges()
        }
        for ((file, bunchFile) in files.zip(bunchFiles)) {
            addFileAfterRestore(file, bunchFile)
            saveFileText(bunchFile, file)
        }
        for (file in otherBunchFiles) {
            writeAndSave(file)
            commitCurrentChanges()
            addFileAfterRestore(file, file)
        }

        restore()
    }

    @Test
    fun customHashWithoutExtensionRestoreTest() {
        noCheck = true
        expectThrows<BunchException> { restore(getLastCommit()) }
    }

    @Test
    fun customExtensionRestoreTest() {
        restoreExtension = "c"
        val file = createAndAddFile("file")
        createAndAddBunchFile(file, "b")
        val otherBunchFile = createAndAddBunchFile(file, "c")

        switchToBranch("b")
        touch(file)
        commitCurrentChanges()

        addFileAfterRestore(file, otherBunchFile)
        saveFileText(otherBunchFile, file)

        restore(extension = "c")
    }

    @Test
    fun noSwitchInHistoryRestoreTest() {
        noCheck = true
        expectThrows<BunchException> { restore() }
    }

    @Test
    fun customUntilRefRestoreTest() {
        val file = createAndAddFile("file")
        val singleFile = createAndAddFile("file.txt")
        val bunchFile = createAndAddBunchFile(file, "b")
        saveAllFiles()
        val initCommit = commitCurrentChanges()

        val readme = getFile("readme")
        val fileText = readme.readText()
        touch(readme)
        saveFileText(readme, fileText)
        val droppedCommit = commitCurrentChanges()

        touch(singleFile)
        saveFileText(singleFile)
        addFileAfterRestore(singleFile, singleFile)
        commitCurrentChanges()

        touch(file)
        saveFileText(bunchFile, file)
        addFileAfterRestore(file, bunchFile)
        commitCurrentChanges()

        switchCommit = droppedCommit
        restore(commit = initCommit, extension = "b")
    }
}