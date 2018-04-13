package nk.patchsets.git.cleanup

import nk.patchsets.git.ChangeType
import nk.patchsets.git.FileChange
import nk.patchsets.git.commitChanges
import nk.patchsets.git.file.readExtensionFromFile
import nk.patchsets.git.restore.isGitDir
import nk.patchsets.git.restore.isGradleDir
import java.io.File

const val EXT_PATTERN = "{ext}"
const val DEFAULT_CLEANUP_COMMIT_TITLE = "~~~~ cleanup$EXT_PATTERN ~~~~"
const val NO_COMMIT_ = "--no-commit"
const val EXT_ = "-ext="

data class Settings(
        val repoPath: String,
        val extension: String?,
        val commitTitle: String? = DEFAULT_CLEANUP_COMMIT_TITLE,
        val isNoCommit: Boolean)

fun main(args: Array<String>) {
    cleanup(args)
}

fun cleanup(args: Array<String>) {
    if (!(args.size in 1..3)) {
        System.err.println("""
            Usage: <git-path> ($EXT_<file-extension>)? (<commit-title>|$NO_COMMIT_)?

            <git-path>            - Directory with repository (parent directory for .git folder).
            $EXT_<file-extension> - Particular extension for remove.
                                    All files with extensions found in .bunch file will be removed if not set.
            <commit-title>        - Title for cleanup commit. "$DEFAULT_CLEANUP_COMMIT_TITLE" is used by default.
            $NO_COMMIT_           - option to cancel cleanup commit.

            Example:
            <program> C:/Projects/kotlin $NO_COMMIT_
            """.trimIndent())

        return
    }

    val repoPath = args[0]
    val extension = args.getOrNull(1)?.takeIf { it.startsWith(EXT_) }?.substringAfter(EXT_)
    val commitIndex = if (extension == null) 1 else 2

    val commitTitleOrNoCommit = args.getOrNull(commitIndex)
    if (commitIndex == 1 && args.size == 3) {
        System.err.println("Unknown parameter: '${args[2]}'")
        return
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
        readExtensionFromFile(settings.repoPath)?.toSet()
    }?.map { ".$it" }

    if (extensions == null) return

    val root = File(settings.repoPath)
    val filesWithExtensions = root
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isGradleDir(dir)) }
            .filter { child -> extensions.any { child.name.endsWith(it) } }
            .toList()

    val changedFiles = ArrayList<FileChange>()
    for (cleanupFile in filesWithExtensions) {
        if (cleanupFile.isDirectory) {
            System.err.println("Bunch directories are not supported: $cleanupFile")
            return
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
