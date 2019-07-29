@file:JvmName("BunchCleanup")

package org.jetbrains.bunches.cleanup

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.git.*
import java.io.File

const val EXT_PATTERN = "{ext}"
const val DEFAULT_CLEANUP_COMMIT_TITLE = "~~~~ cleanup $EXT_PATTERN ~~~~"

const val CLEANUP_DESCRIPTION = "Removes bunch file from repository directory."

data class Settings(
    val repoPath: String,
    val bunchPath: String,
    val extension: String?,
    val commitTitle: String? = DEFAULT_CLEANUP_COMMIT_TITLE,
    val isNoCommit: Boolean
)

class CleanUpCommand : BunchSubCommand(
    name = "cleanup",
    help = CLEANUP_DESCRIPTION
) {
    val repoPath by repoPathOption()
    val extension by option(
        "--ext",
        help = "Particular extension for remove. " +
                "All files with extensions found in '$BUNCH_FILE_NAME' file will be removed if not set."
    )

    private val isNoCommit by option("--no-commit", help = "Do not commit changes. -m option will be ignored").flag()

    private val commitTitle by option(
        "-m",
        help = "Title for the cleanup commit. " +
                "\"$DEFAULT_CLEANUP_COMMIT_TITLE\" is used by default."
    ).default(DEFAULT_CLEANUP_COMMIT_TITLE)

    override fun run() {
        val settings = Settings(
            repoPath = repoPath.toString(),
            bunchPath = repoPath.toString(),
            extension = extension,
            commitTitle = commitTitle,
            isNoCommit = isNoCommit
        )

        process { cleanup(settings) }
    }
}

fun main(args: Array<String>) {
    CleanUpCommand().main(args)
}

fun cleanup(settings: Settings) {
    if (hasUncommittedChanges(settings.repoPath) && !settings.isNoCommit) {
        exitWithError("Can not commit changes for cleanup with uncommitted changes.")
    }

    val extensions = if (settings.extension != null) {
        setOf(settings.extension)
    } else {
        readExtensionsFromFile(settings.bunchPath)?.toSet()
    }?.map { ".$it" }

    if (extensions == null) exitWithError()

    val root = File(settings.repoPath)

    if (!isGitRoot(root)) {
        exitWithError("Repository directory with branch is expected")
    }

    val gitignoreParseResult = parseGitIgnore(root)

    val filesWithExtensions = root
        .walkTopDown()
        .onEnter { dir -> !shouldIgnoreDir(dir, root, gitignoreParseResult) }
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

    if (settings.isNoCommit) {
        return
    }

    val extValue = if (settings.extension != null) " ${settings.extension}" else ""
    val commitTitle = settings.commitTitle!!.replace(EXT_PATTERN, extValue)
    commitChanges(settings.repoPath, changedFiles, commitTitle)
}
