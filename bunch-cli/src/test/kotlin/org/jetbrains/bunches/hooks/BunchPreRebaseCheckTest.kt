package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.BunchBaseTest
import org.jetbrains.bunches.containsExactly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.succeeded
import java.io.File

class BunchPreRebaseCheckTest : BunchBaseTest() {
    private val firstBranch = "first"
    private val addedList = mutableListOf<String>()
    private val deletedList = mutableListOf<String>()
    private val secondBranch = "second"
    private val file = createAndAddFile("file")

    private fun File.markAdded() {
        addedList.add(relativePath())
    }

    private fun File.markDeleted() {
        deletedList.add(relativePath())
    }

    private fun preRebaseCheck(): PreRebaseCheckResult {
        return PreRebaseCheckResult.compute(firstBranch, secondBranch, directory.absolutePath)
    }

    @AfterEach
    fun check() {
        val isOk = addedList.isEmpty() && deletedList.isEmpty()
        expectCatching { preRebaseCheck() }.succeeded().containsExactly(isOk, addedList, deletedList)
    }

    private fun inFirstBranch(toRun: () -> Any) {
        checkoutFirst()
        toRun.invoke()
        commitCurrentChanges()
        gitManager.checkout("master")
    }

    private fun inSecondBranch(toRun: () -> Any) {
        checkoutSecond()
        toRun.invoke()
        commitCurrentChanges()
        gitManager.checkout("master")
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

    @BeforeEach
    fun initBunchFile() {
        configureBunchFile("123", "124", "125", "126")
    }

    @Test
    fun createdBunchFilePreRebaseTest() {
        commitAndCreateForkHere()

        inFirstBranch {
            createAndAddBunchFile(file, "124")
        }
    }

    @Test
    fun modifiedMainFilePreRebaseTest() {
        createAndAddBunchFile(file, "123")
        commitAndCreateForkHere()
        inFirstBranch {
            touch(file)
        }
    }

    @Test
    fun modifiedMainFileAndAddedBunchFilePreRebaseTest() {
        commitAndCreateForkHere()

        inFirstBranch {
            touch(file)
        }

        inSecondBranch {
            createAndAddBunchFile(file, "123").markDeleted()
        }
    }

    @Test
    fun modifiedMainFileInSecondBranchAndAddedBunchFileInFirstPreRebaseTest() {
        commitAndCreateForkHere()
        inFirstBranch {
            touch(file)
        }

        inSecondBranch {
            createAndAddBunchFile(file, "123").markDeleted()
        }
    }

    @Test
    fun addedBunchFileInBothBranchesPreRebaseTest() {
        commitAndCreateForkHere()

        inFirstBranch {
            touch(file)
            createAndAddBunchFile(file, "124").markAdded()
        }

        inSecondBranch {
            touch(file)
            createAndAddBunchFile(file, "125").markDeleted()
        }
    }

    @Test
    fun addedBunchFileWithoutMainModificationPreRebaseTest() {
        commitAndCreateForkHere()

        inFirstBranch {
            createAndAddBunchFile(file, "124")
        }

        inSecondBranch {
            createAndAddBunchFile(file, "125")
        }
    }

    @Test
    fun createdInBothBranchesBunchFilePreRebaseTest() {
        commitAndCreateForkHere()

        inFirstBranch {
            createAndAddBunchFile(file, "125")
        }

        inSecondBranch {
            createAndAddBunchFile(file, "125")
        }
    }

    @Test
    fun severalBunchFilesAddedPreRebaseTest() {
        commitAndCreateForkHere()
        val extensions = (1..50).map { it.toString() }
        getFile(".bunch").writeText(extensions.joinToString(System.lineSeparator()))
        inFirstBranch {
            extensions.forEach {
                createAndAddBunchFile(file, it).markAdded()
            }
        }

        inSecondBranch {
            touch(file)
        }
    }

    @Test
    fun severalMainFilesPreRebaseTest() {
        val files = (1..100).map { createAndAddFile("file$it.txt") }
        commitAndCreateForkHere()

        inFirstBranch {
            files.forEach {
                createAndAddBunchFile(it, "124").markAdded()
            }
            gitManager.gitLog()
        }

        inSecondBranch {
            files.forEach {
                touch(it)
            }
            gitManager.gitLog()
        }
    }

    @Test
    fun forgottenFilesInInnerFolderPreRebaseTest() {
        val dir = getFile("files")
        dir.mkdir()
        val innerFile = File(dir, file.name)
        innerFile.createNewFile()
        gitManager.gitAdd(innerFile)
        commitAndCreateForkHere()

        inFirstBranch {
            createAndAddBunchFile(innerFile, "124")
        }

        inSecondBranch {
            touch(file)
        }
    }

    @Test
    fun forgottenFilesInInnerFolderProblemPreRebaseTest() {
        val dir = getFile("files")
        dir.mkdir()
        val innerFile = File(dir, file.name)
        innerFile.createNewFile()
        gitManager.gitAdd(innerFile)
        commitAndCreateForkHere()

        inFirstBranch {
            createAndAddBunchFile(innerFile, "124").markAdded()
        }

        inSecondBranch {
            touch(file)
            touch(innerFile)
        }
    }
}