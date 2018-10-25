@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchSwitch")

package org.jetbrains.bunches.restore

import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.file.readRuleFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.ChangeType
import org.jetbrains.bunches.git.FileChange
import org.jetbrains.bunches.git.commitChanges
import org.jetbrains.bunches.git.shouldIgnoreDir
import java.io.File

const val RESTORE_COMMIT_TITLE = "~~~~ switch {target} ~~~~"
const val RESTORE_BACKUP_COMMIT_TITLE = "~~~~ backup files ~~~~"
const val RESTORE_CLEANUP_COMMIT_TITLE = "~~~~ restore cleanup ~~~~"

const val STEP_ = "--step"

data class Settings(
        val repoPath: String,
        val rule: String,
        val commitTitle: String = RESTORE_COMMIT_TITLE,
        val step: Boolean,
        val doCleanup: Boolean)

fun main(args: Array<String>) {
    restore(args)
}

private const val CLEAN_UP = "--cleanup"

const val SWITCH_DESCRIPTION = "Restores state of files for the particular branch by replacing base files with bunch counterparts."
const val SWITCH_COMMIT_TITLE = """<commit-title>    - Title for the switch commit. "$RESTORE_COMMIT_TITLE" is used by default. {target} pattern
                                in message will be replaced with the target branch suffix."""
const val SWITCH_BRANCHES_RULE = """<branches-rule>   - Set of file suffixes separated with `_` showing what files should be affected and priority
                                of application. If only target branch is given file <git-path>/.bunch will be checked for
                                pattern."""

fun restore(args: Array<String>) {
    if (args.size != 4 && args.size != 3 && args.size != 2) {
        exitWithUsageError("""
            Usage: <git-path> <branches-rule> [$STEP_] [$CLEAN_UP] [<commit-title>]

            $SWITCH_DESCRIPTION

            <git-path>        - Directory with repository (parent directory for .git).

            $SWITCH_BRANCHES_RULE

            $STEP_            - Do switch step by step with intermediate commits after applying each step

            $CLEAN_UP         - Remove bunch files after branch restore (executes 'cleanup' command with default arguments).

            $SWITCH_COMMIT_TITLE

            Example:
            bunch switch C:/Projects/kotlin as32
            """.trimIndent())
    }

    var commitTitle: String? = null
    var doCleanup: Boolean? = null
    var stepByStep: Boolean? = null

    fun readOptionArg(value: String?) {
        if (value == null) return
        when (value) {
            STEP_ -> {
                if (stepByStep != null) {
                    exitWithUsageError("Reassign of $STEP_ parameter")
                }

                stepByStep = true
            }
            CLEAN_UP -> {
                if (doCleanup != null) {
                    exitWithUsageError("Reassign of $CLEAN_UP parameter")
                }

                doCleanup = true
            }
            else -> {
                if (commitTitle != null) {
                    exitWithUsageError("Reassign of <commit-title> parameter")
                }

                commitTitle = value
            }
        }
    }

    readOptionArg(args.getOrNull(2))
    readOptionArg(args.getOrNull(3))
    readOptionArg(args.getOrNull(4))

    val settings = Settings(
            repoPath = args[0],
            rule = args[1],
            commitTitle = commitTitle ?: RESTORE_COMMIT_TITLE,
            step = stepByStep ?: false,
            doCleanup = doCleanup ?: false
    )

    val suffixes = getRuleSuffixes(settings)
    if (suffixes.isEmpty()) {
        exitWithError()
    }

    if (suffixes.size != 1) {
        if (settings.step) {
            doStepByStepSwitch(suffixes, settings)
        } else {
            doSwitch(suffixes, settings)
        }
    }

    if (settings.doCleanup) {
        cleanup(org.jetbrains.bunches.cleanup.Settings(
                settings.repoPath, extension = null, commitTitle =  RESTORE_CLEANUP_COMMIT_TITLE, isNoCommit = false))
    }
}

private fun doStepByStepSwitch(suffixes: List<String>, settings: Settings) {
    val originBranchExtension = suffixes.first()
    val donorExtensionsInStepByStepOrder = suffixes.subList(1, suffixes.size).toSet()

    val root = File(settings.repoPath)

    if (!root.exists() || !root.isDirectory) {
        exitWithError("Repository directory with branch is expected")
    }

    val filesWithDonorExtensions = root
            .walkTopDown()
            .onEnter { dir -> !shouldIgnoreDir(dir) }
            .filter { child -> child.extension in donorExtensionsInStepByStepOrder }
            .toList()

    val affectedOriginFiles: Set<File> =
            filesWithDonorExtensions.mapTo(HashSet()) { child -> File(child.parentFile, child.nameWithoutExtension) }

    run {
        val backupChanges = HashSet<FileChange>()
        for (originFile in affectedOriginFiles) {
            val baseCopiedFile = originFile.toBunchFile(originBranchExtension)
            if (baseCopiedFile.exists()) {
                exitWithError("Can't store copy of the origin file, because branch file is already exist: $baseCopiedFile")
            }

            if (originFile.exists()) {
                if (originFile.isDirectory) {
                    exitWithError("Bunching for directories is not supported: $originFile")
                }

                originFile.copyTo(baseCopiedFile)
                backupChanges.add(
                        FileChange(
                                ChangeType.ADD,
                                baseCopiedFile
                        )
                )
            } else {
                // File was absent and going to be introduced.
                // Create empty bunch files to show it's going to be removed in old branch.
                baseCopiedFile.createNewFile()
                backupChanges.add(
                        FileChange(
                                ChangeType.ADD,
                                baseCopiedFile
                        )
                )
            }
        }

        commitChanges(
                settings.repoPath,
                backupChanges,
                RESTORE_BACKUP_COMMIT_TITLE
        )
    }

    for (extension in donorExtensionsInStepByStepOrder) {
        val branchChanges = HashSet<FileChange>()
        for (originFile in affectedOriginFiles) {
            val targetFile = originFile.toBunchFile(extension)
            if (!targetFile.exists()) {
                continue
            }

            if (targetFile.isDirectory) {
                exitWithError("Bunching for directories is not supported: $targetFile")
            }

            val isOriginExist = originFile.exists()

            val isTargetRemoved = targetFile.readText().trim().isEmpty()
            if (!isTargetRemoved) {
                targetFile.copyTo(originFile, true)
                branchChanges.add(FileChange(if (isOriginExist) ChangeType.MODIFY else ChangeType.ADD, originFile))
            } else {
                if (isOriginExist) {
                    originFile.delete()
                    branchChanges.add(FileChange(ChangeType.REMOVE, originFile))
                } else {
                    // That mean the bunch file is redundant, but do nothing
                }
            }
        }

        if (!branchChanges.isEmpty()) {
            commitChanges(
                    settings.repoPath,
                    branchChanges,
                    settings.commitTitle.replace("{target}", extension)
            )
        }
    }
}

private fun doSwitch(suffixes: List<String>, settings: Settings) {
    val originBranchExtension = suffixes.first()
    val donorExtensionsPrioritized = suffixes.subList(1, suffixes.size).reversed().toSet()

    val root = File(settings.repoPath)

    if (!root.exists() || !root.isDirectory) {
        exitWithError("Repository directory with branch is expected")
    }

    val changedFiles = HashSet<FileChange>()

    val filesWithDonorExtensions = root
            .walkTopDown()
            .onEnter { dir -> !shouldIgnoreDir(dir) }
            .filter { child -> child.extension in donorExtensionsPrioritized }
            .toList()

    val affectedOriginFiles: Set<File> =
            filesWithDonorExtensions.mapTo(HashSet()) { child -> File(child.parentFile, child.nameWithoutExtension) }

    for (originFile in affectedOriginFiles) {
        val originFileModificationType: ChangeType

        val baseCopiedFile = originFile.toBunchFile(originBranchExtension)
        if (baseCopiedFile.exists()) {
            exitWithError("Can't store copy of the origin file, because branch file is already exist: $baseCopiedFile")
        }

        if (originFile.exists()) {
            if (originFile.isDirectory) {
                exitWithError("Patch specific directories are not supported: $originFile")
            }

            originFile.copyTo(baseCopiedFile)
            changedFiles.add(
                FileChange(
                    ChangeType.ADD,
                    baseCopiedFile
                )
            )

            originFile.delete()
            originFileModificationType = ChangeType.MODIFY
        } else {
            // File was absent and going to be introduced.
            // Create empty bunch files to show it's going to be removed in old branch.
            baseCopiedFile.createNewFile()
            changedFiles.add(
                FileChange(
                    ChangeType.ADD,
                    baseCopiedFile
                )
            )

            originFileModificationType = ChangeType.ADD
        }

        val targetFile = donorExtensionsPrioritized
                .asSequence()
                .map { extension -> originFile.toBunchFile(extension) }
                .first { it.exists() }

        if (targetFile.isDirectory) {
            exitWithError("Patch specific directories are not supported: $targetFile")
        }

        val isTargetRemoved = targetFile.readText().trim().isEmpty()
        if (!isTargetRemoved) {
            targetFile.copyTo(originFile)
            changedFiles.add(FileChange(originFileModificationType, originFile))
        } else {
            when (originFileModificationType) {
                ChangeType.ADD -> {
                    // Original file was copied, but there's nothing to add instead. Do nothing.
                }
                ChangeType.MODIFY -> {
                    changedFiles.add(
                        FileChange(
                            ChangeType.REMOVE,
                            originFile
                        )
                    )
                }
                ChangeType.REMOVE -> {
                    throw IllegalStateException("REMOVE state isn't expected for original file modification")
                }
            }
        }
    }

    commitChanges(
        settings.repoPath,
        changedFiles,
        settings.commitTitle.replace("{target}", suffixes.last())
    )
}

fun File.toBunchFile(extension: String) = File(parentFile, "$name.$extension")

fun getRuleSuffixes(settings: Settings): List<String> {
    val suffixes = settings.rule.split("_")
    return when {
        suffixes.isEmpty() -> {
            System.err.println("There should be at target branch in pattern: ${settings.rule}")
            emptyList()
        }

        suffixes.size == 1 -> {
            val ruleFromFile = readRuleFromFile(suffixes.first(), settings.repoPath) ?: return emptyList()
            return ruleFromFile.split("_")
        }

        else -> suffixes
    }
}