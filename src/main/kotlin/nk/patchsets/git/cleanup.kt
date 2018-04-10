package nk.patchsets.git.cleanup

import nk.patchsets.git.ChangeType
import nk.patchsets.git.FileChange
import nk.patchsets.git.commitChanges
import nk.patchsets.git.file.readExtensionFromFile
import nk.patchsets.git.restore.isGitDir
import nk.patchsets.git.restore.isGradleDir
import java.io.File

const val DEFAULT_CLEANUP_COMMIT_TITLE = "==== cleanup ===="
const val NO_COMMIT_ = "--no-commit"

data class Settings(val repoPath: String, val commitTitle: String? = DEFAULT_CLEANUP_COMMIT_TITLE, val isNoCommit: Boolean)

fun main(args: Array<String>) {
    cleanup(args)
}

fun cleanup(args: Array<String>) {
    if (args.size != 1 && args.size != 2) {
        System.err.println("""
            Usage: <git-path> <commit-title>? $NO_COMMIT_?

            <git-path>      - Directory with repository (parent directory for .git folder).
            <commit-title>  - Title for cleanup commit. "$DEFAULT_CLEANUP_COMMIT_TITLE" is used by default.
            $NO_COMMIT_     - option to cancel cleanup commit.

            Example:
            <program> C:/Projects/kotlin $NO_COMMIT_
            """.trimIndent())

        return
    }

    val isNoCommit = args.size == 2 && args[1] == NO_COMMIT_
    val settings = if (!isNoCommit) {
        Settings(
                repoPath = args[0],
                commitTitle = args.getOrNull(1) ?: DEFAULT_CLEANUP_COMMIT_TITLE,
                isNoCommit = false
        )
    } else {
        Settings(
                repoPath = args[0],
                commitTitle = null,
                isNoCommit = true
        )
    }

    cleanup(settings)
}

fun cleanup(settings: Settings) {
    val extensions = readExtensionFromFile(settings.repoPath)?.toSet() ?: return

    val root = File(settings.repoPath)
    val filesWithExtensions = root
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isGradleDir(dir)) }
            .filter { child -> child.extension in extensions }
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
        commitChanges(settings.repoPath, changedFiles, settings.commitTitle!!)
    }
}
