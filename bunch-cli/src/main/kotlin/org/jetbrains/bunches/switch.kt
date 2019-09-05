@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchSwitch")

package org.jetbrains.bunches.switch

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.eclipse.jgit.ignore.IgnoreNode
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.file.readRuleFromFile
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.git.*
import java.io.File

const val RESTORE_COMMIT_TITLE = "~~~~ switch {target} ~~~~"
const val RESTORE_BACKUP_COMMIT_TITLE = "~~~~ backup files ~~~~"
const val RESTORE_CLEANUP_COMMIT_TITLE = "~~~~ restore cleanup ~~~~"
const val NO_GIT_EXCEPTION_TEXT = "Repository directory with branch is expected"
const val NO_DIRECTORIES_SUPPORT = "Bunching for directories is not supported:"
const val SAVED_VALUE_EXISTS = "Can't store copy of the origin file, because branch file is already exist:"

const val STEP_ = "--step"
const val checkingCommitCount = 50

data class Settings(
    val repoPath: String,
    val bunchPath: String,
    val rule: String,
    val commitTitle: String = RESTORE_COMMIT_TITLE,
    val step: Boolean,
    val doCleanup: Boolean
)

typealias SwitchSettings = Settings

fun main(args: Array<String>) {
    SwitchCommand().main(args)
}

const val SWITCH_DESCRIPTION =
    "Restores state of files for the particular branch by replacing base files with bunch counterparts. " +
            "Old files will be saved to main bunch (according to $BUNCH_FILE_NAME) files."

val SWITCH_EXAMPLE =
    """
    Example:
    bunch switch -C C:/Projects/kotlin as32
    """.trimIndent()

const val SW_BRANCHES_ = "branches-rule"

class SwitchCommand : BunchSubCommand(
    name = "switch",
    help = SWITCH_DESCRIPTION,
    epilog = SWITCH_EXAMPLE
) {
    val repoPath by repoPathOption()
    private val rule by argument(
        name = "<$SW_BRANCHES_>",
        help = """Set of file suffixes separated with `_` showing what files should be affected and priority
            of application. If only target branch is given file <git-path>/$BUNCH_FILE_NAME will be checked for
            pattern.""".trimIndent()
    )

    private val stepByStep by option(
        "--step",
        help = "Do switch step by step with intermediate commits after applying each step."
    ).flag()

    private val cleanUp by option(
        "--cleanup",
        help = "Remove bunch files after branch restore (executes 'cleanup' command with default arguments)."
    ).flag()

    private val commitTitle by option(
        "-m",
        help = """Title for the switch commit. \"$RESTORE_COMMIT_TITLE\" is used by default. {target} pattern
            in message will be replaced with the target branch suffix.""".trimIndent()
    ).default(RESTORE_COMMIT_TITLE)

    override fun run() {
        val settings = Settings(
            repoPath = repoPath.toString(),
            bunchPath = repoPath.toString(),
            rule = rule,
            commitTitle = commitTitle,
            step = stepByStep,
            doCleanup = cleanUp
        )

        process { doSwitch(settings) }
    }
}

fun doSwitch(settings: Settings) {
    uncommittedChanges(settings.repoPath).checkAndExitIfNeeded {
        exitWithError("Can not do switch with uncommitted changes.")
    }

    checkLastCommitsNotContainSwitches(settings.repoPath)

    val parameterRuleStr = settings.rule
    val rule = if (parameterRuleStr.contains('_')) {
        parameterRuleStr
    } else {
        // Short rule format with destination bunch only
        readRuleFromFile(parameterRuleStr, settings.bunchPath)
    }

    val suffixes = rule.split("_")
    if (suffixes.isEmpty()) {
        exitWithError("Don't know how to switch to `$parameterRuleStr`")
    }

    if (suffixes.size != 1) {
        if (settings.step) {
            doStepByStepSwitch(suffixes, settings.repoPath, settings.commitTitle)
        } else {
            doOneStepSwitch(suffixes, settings.repoPath, settings.commitTitle)
        }
    }

    if (settings.doCleanup) {
        cleanup(
            org.jetbrains.bunches.cleanup.Settings(
                settings.repoPath, settings.bunchPath, extension = null, commitTitle = RESTORE_CLEANUP_COMMIT_TITLE, isNoCommit = false
            )
        )
    }
}

fun doStepByStepSwitch(suffixes: List<String>, repoPath: String, commitTitle: String) {
    val originBranchExtension = suffixes.first()
    val donorExtensionsInStepByStepOrder = suffixes.subList(1, suffixes.size).toSet()

    val root = File(repoPath)

    if (!isGitRoot(root)) {
        exitWithError(NO_GIT_EXCEPTION_TEXT)
    }

    val gitIgnoreParseResult = parseGitIgnore(root)

    val filesWithDonorExtensions = getFilesWithExtensions(root, gitIgnoreParseResult, donorExtensionsInStepByStepOrder)

    val affectedOriginFiles: Set<File> =
        filesWithDonorExtensions.mapTo(HashSet()) { child -> File(child.parentFile, child.nameWithoutExtension) }

    run {
        val backupChanges = HashSet<FileChange>()
        for (originFile in affectedOriginFiles) {
            val baseCopiedFile = originFile.toBunchFile(originBranchExtension)
            if (baseCopiedFile.exists()) {
                exitWithError("$SAVED_VALUE_EXISTS $baseCopiedFile")
            }

            if (originFile.exists()) {
                if (originFile.isDirectory) {
                    exitWithError("$NO_DIRECTORIES_SUPPORT $originFile")
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

        if (backupChanges.isEmpty()) {
            exitWithError("No bunch files for switch found")
        }

        commitChanges(
            repoPath,
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
                exitWithError("$NO_DIRECTORIES_SUPPORT $targetFile")
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

        if (branchChanges.isNotEmpty()) {
            commitChanges(
                repoPath,
                branchChanges,
                commitTitle.replace("{target}", extension),
                noVerify = true
            )
        }
    }
}

fun doOneStepSwitch(suffixes: List<String>, repoPath: String, commitTitle: String) {
    val originBranchExtension = suffixes.first()
    val donorExtensionsPrioritized = suffixes.subList(1, suffixes.size).reversed().toSet()

    val root = File(repoPath)

    if (!isGitRoot(root)) {
        exitWithError(NO_GIT_EXCEPTION_TEXT)
    }

    val gitIgnoreParseResult = parseGitIgnore(root)

    val changedFiles = HashSet<FileChange>()

    val filesWithDonorExtensions = getFilesWithExtensions(root, gitIgnoreParseResult, donorExtensionsPrioritized)

    val affectedOriginFiles: Set<File> =
        filesWithDonorExtensions.mapTo(HashSet()) { child -> File(child.parentFile, child.nameWithoutExtension) }

    for (originFile in affectedOriginFiles) {
        val originFileModificationType: ChangeType

        val baseCopiedFile = originFile.toBunchFile(originBranchExtension)
        if (baseCopiedFile.exists()) {
            exitWithError("$SAVED_VALUE_EXISTS $baseCopiedFile")
        }

        if (originFile.exists()) {
            if (originFile.isDirectory) {
                exitWithError("$NO_DIRECTORIES_SUPPORT $originFile")
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
            .map { extension ->
                originFile.toBunchFile(extension) }
            .first {
                it.exists()
            }

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

    if (changedFiles.isEmpty()) {
        exitWithError("No bunch files for switch found")
    }

    commitChanges(
        repoPath,
        changedFiles,
        commitTitle.replace("{target}", suffixes.last()),
        noVerify = true
    )
}

fun File.toBunchFile(extension: String) = File(parentFile, "$name.$extension")

private fun getFilesWithExtensions(root: File, gitIgnoreParseResult: IgnoreNode?, extensions: Set<String>) = root
    .walkTopDown()
    .onEnter { dir -> !shouldIgnoreDir(dir, root, gitIgnoreParseResult) }
    .filter { child -> child.extension in extensions }
    .toList()

fun findLastSwitchCommit(repoPath: String): CommitInfo? {
    val lastCommits = readCommits(repoPath) { this.setMaxCount(checkingCommitCount) }
    val switchCommitsRegex = Regex(RESTORE_COMMIT_TITLE.replace("{target}", ".+"))
    for (commit in lastCommits) {
        val title = commit.title ?: continue
        if (title.contains(switchCommitsRegex)) {
            return commit
        }
    }
    return null
}

private fun checkLastCommitsNotContainSwitches(repoPath: String) {
    val lastCommit = findLastSwitchCommit(repoPath) ?: return
    exitWithError("Can not do switch with other switch in history: ${lastCommit.title} ${lastCommit.hash}")
}