@file:JvmName("BunchCleanup")
package org.jetbrains.bunches.cleanup

import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.*
import java.io.File

const val EXT_PATTERN = "{ext}"
const val DEFAULT_CLEANUP_COMMIT_TITLE = "~~~~ cleanup $EXT_PATTERN ~~~~"
const val NO_COMMIT_ = "--no-commit"
const val EXT__ = "--ext="

data class Settings(
        val repoPath: String,
        val extension: String?,
        val commitTitle: String? = DEFAULT_CLEANUP_COMMIT_TITLE,
        val isNoCommit: Boolean)

fun main(args: Array<String>) {
    cleanup(args)
}

const val CLEANUP_DESCRIPTION = "Removes bunch file from repository directory."

fun cleanup(args: Array<String>) {
    if (args.size !in 1..3) {
        exitWithUsageError("""
            Usage: <git-path> [$EXT__<file-extension>] [<commit-title>|$NO_COMMIT_]

            $CLEANUP_DESCRIPTION

            <git-path>             - Directory with repository (parent directory for .git).
            $EXT__<file-extension> - Particular extension for remove.
                                     All files with extensions found in '$BUNCH_FILE_NAME' file will be removed if not set.
            <commit-title>         - Title for the cleanup commit. "$DEFAULT_CLEANUP_COMMIT_TITLE" is used by default.
            $NO_COMMIT_            - Do not commit changes.

            Example:
            bunch cleanup C:/Projects/kotlin
            """.trimIndent())
    }

    val repoPath = args[0]
    val extension = args.getOrNull(1)?.takeIf { it.startsWith(EXT__) }?.substringAfter(EXT__)
    val commitIndex = if (extension == null) 1 else 2

    val commitTitleOrNoCommit = args.getOrNull(commitIndex)
    if (commitIndex == 1 && args.size == 3) {
        exitWithUsageError("Unknown parameter: '${args[2]}'")
    }

    val isNoCommit = commitTitleOrNoCommit == NO_COMMIT_
    val commitTitle = if (!isNoCommit) commitTitleOrNoCommit ?: DEFAULT_CLEANUP_COMMIT_TITLE else null

    val settings = Settings(
            repoPath = repoPath,
            extension = extension,
            commitTitle = commitTitle,
            isNoCommit = isNoCommit
    )

    cleanup(settings)
}

fun cleanup(settings: Settings) {
    val extensions = if (settings.extension != null) {
        setOf(settings.extension)
    } else {
        readExtensionsFromFile(settings.repoPath)?.toSet()
    }?.map { ".$it" }

    if (extensions == null) exitWithError()

    val root = File(settings.repoPath)

    if (!isGitRoot(root)) {
        exitWithError("Repository directory with branch is expected")
    }

    val filesWithExtensions = root
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isGradleDir(dir) || isNestedGitRoot(dir, root)) }
            .filter { child -> extensions.any { child.name.endsWith(it) } }
            .toList()

    val changedFiles = ArrayList<FileChange>()
    for (cleanupFile in filesWithExtensions) {
        if (cleanupFile.isDirectory) {
            exitWithError("Bunch directories are not supported: $cleanupFile")
        }

        cleanupFile.delete()
        changedFiles.add(FileChange(ChangeType.REMOVE, cleanupFile))
    }

    if (!settings.isNoCommit) {
        val extValue = if (settings.extension != null) " ${settings.extension}" else ""
        val commitTitle = settings.commitTitle!!.replace(EXT_PATTERN, extValue)
        commitChanges(settings.repoPath, changedFiles, commitTitle)
    }
}
