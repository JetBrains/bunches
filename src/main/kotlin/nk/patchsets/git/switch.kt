@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchSwitch")

package nk.patchsets.git.restore

import nk.patchsets.git.ChangeType
import nk.patchsets.git.FileChange
import nk.patchsets.git.cleanup.cleanup
import nk.patchsets.git.commitChanges
import nk.patchsets.git.file.readRuleFromFile
import java.io.File

const val RESTORE_COMMIT_TITLE = "~~~~ switch {target} ~~~~"
const val RESTORE_CLEANUP_COMMIT_TITLE = "~~~~ restore cleanup ~~~~"

data class Settings(
        val repoPath: String,
        val rule: String,
        val commitTitle: String = RESTORE_COMMIT_TITLE,
        val doCleanup: Boolean)

fun main(args: Array<String>) {
    restore(args)
}

private const val CLEAN_UP = "--cleanup"

const val SWITCH_DESCRIPTION = "Restores state of files for the particular branch by replacing base files with bunch counterparts."

fun restore(args: Array<String>) {
    if (args.size != 4 && args.size != 3 && args.size != 2) {
        System.err.println("""
            Usage: <git-path> <branches-rule> [<commit-title>] [$CLEAN_UP]

            $SWITCH_DESCRIPTION

            <git-path>        - Directory with repository (parent directory for .git).
            <branches-rule>   - Set of file suffixes separated with `_` showing what files should be affected and priority
                                of application. If only target branch is given file <git-path>/.bunch will be checked for
                                pattern.
            <commit-title>    - Title for the switch commit. "$RESTORE_COMMIT_TITLE" is used by default. {target} pattern
                                in message will be replaced with the target branch suffix.
            $CLEAN_UP         - Remove bunch files after branch restore (executes 'cleanup' command with default arguments).

            Example:
            bunch switch C:/Projects/kotlin as32
            """.trimIndent())

        return
    }

    if (args.size == 4 && args[3] != CLEAN_UP) {
        System.err.println("Last parameter should be $CLEAN_UP or absent")
        return
    }

    val settings = Settings(
            repoPath = args[0],
            rule = args[1],
            commitTitle = args.getOrNull(2)?.takeIf { it != CLEAN_UP } ?: RESTORE_COMMIT_TITLE,
            doCleanup = CLEAN_UP == (args.getOrNull(3) ?: args.getOrNull(2))
    )

    val suffixes = getRuleSuffixes(settings)
    if (suffixes.isEmpty()) {
        return
    }

    if (suffixes.size != 1) {
        doRestore(suffixes, settings)
    }

    if (settings.doCleanup) {
        cleanup(nk.patchsets.git.cleanup.Settings(
                settings.repoPath, extension = null, commitTitle =  RESTORE_CLEANUP_COMMIT_TITLE, isNoCommit = false))
    }
}

private fun doRestore(suffixes: List<String>, settings: Settings) {
    val originBranchExtension = suffixes.first()
    val donorExtensionsPrioritized = suffixes.subList(1, suffixes.size).reversed().toSet()

    val root = File(settings.repoPath)

    if (!root.exists() || !root.isDirectory) {
        System.err.println("Repository directory with branch is expected")
    }

    val changedFiles = HashSet<FileChange>()

    val filesWithDonorExtensions = root
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isOutDir(dir, root) || isGradleBuildDir(dir) || isGradleDir(dir)) }
            .filter { child -> child.extension in donorExtensionsPrioritized }
            .toList()

    val affectedOriginFiles: Set<File> =
            filesWithDonorExtensions.mapTo(HashSet(), { child -> File(child.parentFile, child.nameWithoutExtension) })

    for (originFile in affectedOriginFiles) {
        val originFileModificationType: ChangeType

        if (originFile.exists()) {
            if (originFile.isDirectory) {
                System.err.println("Patch specific directories are not supported: ${originFile}")
                return
            }

            val branchCopyFile = originFile.toBunchFile(originBranchExtension)

            if (branchCopyFile.exists()) {
                System.err.println("Can't store copy of the origin file, because branch file is already exist: ${branchCopyFile}")
                return
            }
            originFile.copyTo(branchCopyFile)
            changedFiles.add(FileChange(ChangeType.ADD, branchCopyFile))

            originFile.delete()
            originFileModificationType = ChangeType.MODIFY
        } else {
            originFileModificationType = ChangeType.ADD
        }

        val targetFile = donorExtensionsPrioritized
                .asSequence()
                .map { extension -> originFile.toBunchFile(extension) }
                .first { it.exists() }

        if (targetFile.isDirectory) {
            System.err.println("Patch specific directories are not supported: $targetFile")
            return
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
                    changedFiles.add(FileChange(ChangeType.REMOVE, originFile))
                }
                ChangeType.REMOVE -> {
                    throw IllegalStateException("REMOVE state isn't expected for original file modification")
                }
            }
        }
    }

    commitChanges(settings.repoPath, changedFiles, settings.commitTitle.replace("{target}", suffixes.last()))
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

fun isGradleDir(dir: File) = dir.name == ".gradle"
fun isGitDir(dir: File) = dir.name == ".git"

fun isOutDir(dir: File, baseDir: File): Boolean {
    if (dir.name != "out") return false
    return dir.parentFile == baseDir
}

fun isGradleBuildDir(dir: File): Boolean {
    if (dir.name != "build") return false
    if (File(dir.parentFile, "build.gradle.kts").exists()) return true
    if (File(dir.parentFile, "build.gradle").exists()) return true

    return false
}