@file:JvmName("BunchCleanup")

package org.jetbrains.bunches.cleanup

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.partial
import org.jetbrains.bunches.general.process
import org.jetbrains.bunches.git.*
import java.io.File
import java.nio.file.Paths

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

class CleanUp : CliktCommand(help = CLEANUP_DESCRIPTION) {
    val config by requireObject<Map<String, Boolean>>()
    val repoPath by option("-C", help = "path to git repository")
        .path(exists = true, fileOkay = false)
        .default(Paths.get(".").toAbsolutePath().normalize())
    val extension by option("--ext", help = "Particular extension for remove. All files with extensions found in '$BUNCH_FILE_NAME' file will be removed if not set.")
    val isNoCommit by option("--no-commit", help = "Do not commit changes.").flag()
    val commitTitle by option("-m", help = "Title for the cleanup commit. \"$DEFAULT_CLEANUP_COMMIT_TITLE\" is used by default.")
        .validate {
            require(!isNoCommit) { "'No-commit' flag is set. Commit will not be created, you can't provide message for it." }
        }
    override fun run() {
        val settings = Settings(
            repoPath = repoPath.toString(),
            bunchPath = repoPath.toString(),
            extension = extension,
            commitTitle = commitTitle,
            isNoCommit = isNoCommit
        )
        process(config.getValue("VERBOSE"), ::cleanup.partial(settings))
    }
}

fun main(args: Array<String>) {
    CleanUp().main(listOf())
}

fun cleanup(settings: Settings) {
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

    if (!settings.isNoCommit) {
        val extValue = if (settings.extension != null) " ${settings.extension}" else ""
        val commitTitle = settings.commitTitle!!.replace(EXT_PATTERN, extValue)
        commitChanges(settings.repoPath, changedFiles, commitTitle)
    }
}
