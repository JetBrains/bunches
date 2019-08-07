package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.BunchBaseTest
import org.jetbrains.bunches.containsExactlyActions
import org.jetbrains.bunches.git.ChangeType
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.FileChange
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File

class BunchFriendlyCommitsTest : BunchBaseTest() {
    private val modifiedFiles = mutableMapOf<String, MutableList<FileChange>>()
    private val addedFiles = mutableMapOf<String, MutableList<FileChange>>()

    private val firstBranch = "other"
    private val secondBranch = "current"
    private val file = createAndAddFile("file")

    private val hashRegex = "[0-9a-f]{5,40}"
    private val superUniqueString = "<><><><>"
    private val addedRegex = generatedAddedCommitMessage(".*", superUniqueString).toRealBrackets()
    private val modRegex = generatedModifiedCommitMessage(".*", superUniqueString).toRealBrackets()

    @BeforeEach
    fun initBunchFile() {
        configureBunchFile("123", "124", "125", "126")
    }

    private fun checkoutFirst() {
        gitManager.checkout(firstBranch)
    }

    private fun checkoutSecond() {
        gitManager.checkout(secondBranch)
    }

    private fun commitAndCreateForkHere() {
        commitCurrentChanges()
        gitManager.crateBranch(firstBranch)
        gitManager.crateBranch(secondBranch)
    }

    private fun String.toRealBrackets(): String {
        return this.replace("[", "\\[")
            .replace("]", "\\]").replace(superUniqueString, hashRegex)
    }

    private fun addModifiedFile(file: File, hash: String?) {
        modifiedFiles.getOrPut(hash!!, { mutableListOf() }).add(FileChange(ChangeType.MODIFY, file))
    }

    private fun addAddedFile(file: File, hash: String?) {
        addedFiles.getOrPut(hash!!, { mutableListOf() }).add(FileChange(ChangeType.ADD, file))
    }

    @AfterEach
    fun check() {
        val masterCommits = getAllCommits("master")
        val firstCommits = getAllCommits(firstBranch)
        checkoutSecond()
        val secondCommits = getAllCommits()

        val result = PreRebaseCheckResult.compute(firstBranch, secondBranch, directory.absolutePath)
        expectThat(result).get { isOk() }.isFalse()
        result.createCommits()
        val newCommits = getAllCommits()
        expectThat(getAllCommits(firstBranch)).isEqualTo(firstCommits)
        expectThat(getAllCommits("master")).isEqualTo(masterCommits)
        expectThat(newCommits).contains(secondCommits)
        checkFriendlyCommits(newCommits.minus(secondCommits))
    }

    private fun getCommitHash(title: String?): String {
        expectThat(title).isNotNull()
        val hash = title!!.substringAfterLast("] ").substringBefore(" $GENERATED_COMMIT_MARK")
        expectThat(hash).matches(Regex(hashRegex))
        return hash
    }

    private fun checkFriendlyCommits(commits: List<CommitInfo>) {
        commits.forEach {
            writeToOutput(it.title + ":")
            it.fileActions.forEach { action ->
                writeToOutput("" + action.changeType + " in " + action.newPath)
            }
            writeToOutput()
        }

        expect {
            commits.all { it.title?.matches(Regex(addedRegex)) ?: false || it.title?.matches(Regex(modRegex)) ?: false }
        }

        for (commit in commits.filter { it.title?.matches(Regex(addedRegex)) ?: false }) {
            val hash = getCommitHash(commit.title)
            val commitActions = addedFiles[hash]!!
            expectThat(commit).containsExactlyActions(directory, commitActions)
        }

        for (commit in commits.filter { it.title?.matches(Regex(modRegex)) ?: false }) {
            val hash = getCommitHash(commit.title)
            val commitActions = modifiedFiles[hash]!!
            expectThat(commit).containsExactlyActions(directory, commitActions)
        }
    }

    @Test
    fun oneAddingCommitPreRebaseTest() {
        commitAndCreateForkHere()

        checkoutFirst()
        val bunchFile = createAndAddBunchFile(file, "124")
        val commit = commitCurrentChanges()
        addAddedFile(bunchFile, getCommitHash(commit))
        commitCurrentChanges()

        checkoutSecond()
        touch(file)
        commitCurrentChanges()
    }

    @Test
    fun oneModificationCommitPreRebaseTest() {
        commitAndCreateForkHere()

        checkoutFirst()
        touch(file)
        val commit = commitCurrentChanges()

        checkoutSecond()
        touch(file)
        val bunchFile = createAndAddBunchFile(file, "124")
        addModifiedFile(bunchFile, commit.hash)
        commitCurrentChanges()
    }

    @Test
    fun severalCreatedBunchFilesPreRebaseTest() {
        commitAndCreateForkHere()

        checkoutFirst()
        touch(file)
        val commit = commitCurrentChanges()

        checkoutSecond()
        touch(file)
        val bunchFiles = listOf(
            createAndAddBunchFile(file, "124"),
            createAndAddBunchFile(file, "125"),
            createAndAddBunchFile(file, "126")
        )
        commitCurrentChanges("changes in second")
        bunchFiles.forEach { addModifiedFile(it, commit.hash) }
    }

    @Test
    fun severalNewBunchFilesPreRebaseTest() {
        commitAndCreateForkHere()

        checkoutFirst()
        val bunchFiles = listOf(
            createAndAddBunchFile(file, "124"),
            createAndAddBunchFile(file, "125"),
            createAndAddBunchFile(file, "126")
        )
        val commit = commitCurrentChanges()
        bunchFiles.forEach { addAddedFile(it, commit.hash) }

        checkoutSecond()
        touch(file)
        commitCurrentChanges()
    }

    @Test
    fun addedAndModifiedFilesPreRebaseTest() {
        commitAndCreateForkHere()

        checkoutFirst()
        touch(file)
        val bunchFile = createAndAddBunchFile(file, "124")
        val commit = commitCurrentChanges()
        addAddedFile(bunchFile, commit.hash)

        checkoutSecond()
        touch(file)
        val firstBunchFile = createAndAddBunchFile(file, "125")
        commitCurrentChanges()
        addModifiedFile(firstBunchFile, commit.hash)
    }

    @Test
    fun severalCommitsWithModificationPreRebaseTest() {
        commitAndCreateForkHere()

        checkoutSecond()
        touch(file)
        val firstBunchFile = createAndAddBunchFile(file, "125")
        commitCurrentChanges()

        checkoutFirst()
        repeat(10) {
            touch(file)
            val commit = commitCurrentChanges()
            addModifiedFile(firstBunchFile, commit.hash)
        }
    }

    @Test
    fun severalCommitsWithAddingPreRebaseTest() {
        val extensions = (1..10).map { it.toString() }
        configureBunchFile(extensions)
        commitAndCreateForkHere()

        checkoutSecond()
        touch(file)
        commitCurrentChanges()

        checkoutFirst()
        extensions.forEach {
            val bunchFile = createAndAddBunchFile(file, it)
            val commit = commitCurrentChanges()
            addAddedFile(bunchFile, commit.hash)
        }
    }
}