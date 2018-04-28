package nk.patchsets.git.reduce

import nk.patchsets.git.ChangeType
import nk.patchsets.git.FileChange
import nk.patchsets.git.commitChanges
import nk.patchsets.git.file.readUpdatePairsFromFile
import nk.patchsets.git.general.exitWithError
import nk.patchsets.git.general.exitWithUsageError
import nk.patchsets.git.restore.*
import java.io.File

enum class ReduceAction {
    PRINT,
    DELETE,
    COMMIT
}

data class Settings(val repoPath: String, val action: ReduceAction, val commitMessage: String)

fun main(args: Array<String>) {
    reduce(args)
}

const val REDUCE_DESCRIPTION = "Check repository for unneeded files with the same content."
const val DEFAULT_REDUCE_COMMIT_TITLE = "~~~~ reduce ~~~~"

fun reduce(args: Array<String>) {
    if (args.size !in 1..3) {
        exitWithUsageError("""
            Usage: <git-path> [<action: print|delete|commit>] [<commit message>]

            $REDUCE_DESCRIPTION

            <git-path>       - Directory with repository (parent directory for .git)
            <action>         - Action that should be applied to redundant files. "commit" is used by default.
                                 print - print the list in console
                                 delete - only delete files
                                 commit - delete files and commit them
            <commit message> - Commit message for "commit" action. "~~~~ reduce ~~~~" is used by default.

            Example:
            bunch reduce C:/Projects/kotlin
            """.trimIndent())
    }

    val actionFromParam = args.getOrNull(1)?.let {
        when (it) {
            "print" -> ReduceAction.PRINT
            "delete" -> ReduceAction.DELETE
            "commit" -> ReduceAction.COMMIT
            else -> {
                if (args.size == 3) {
                    exitWithUsageError("Unknown action: '$it'")
                }

                null
            }
        }
    }

    if (args.size == 3 && actionFromParam != ReduceAction.COMMIT) {
        exitWithUsageError("Commit message is ignored for '$actionFromParam'")
    }

    val commitMessageFromParam = args.getOrNull(if (actionFromParam == null) 1 else 2)

    val settings = Settings(
        repoPath = args[0],
        action = actionFromParam ?: ReduceAction.COMMIT,
        commitMessage = commitMessageFromParam ?: DEFAULT_REDUCE_COMMIT_TITLE)

    doReduce(settings)
}

private data class UpdatePair(val from: String, val to: String)

fun doReduce(settings: Settings) {
    val root = File(settings.repoPath)
    if (!root.exists() || !root.isDirectory) {
        exitWithError("Repository directory with branch is expected")
    }

    val (base, rules) = readUpdatePairsFromFile(settings.repoPath) ?: exitWithError()
    if (rules.isEmpty()) {
        exitWithError()
    }

    val extensions = rules.map { it.last() }.toSet() + base

    val filesWithDonorExtensions = root
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isOutDir(dir, root) || isGradleBuildDir(dir) || isGradleDir(dir)) }
            .filter { child -> child.extension in extensions }
            .toList()

    val affectedOriginFiles: Set<File> =
            filesWithDonorExtensions.mapTo(HashSet(), { child -> File(child.parentFile, child.nameWithoutExtension) })

    val reduceFiles = ArrayList<File>()
    for (affectedOriginFile in affectedOriginFiles) {
        val contentMap: Map<String, String?> = extensions.map { extension ->
            val file = if (extension == base) affectedOriginFile else affectedOriginFile.toBunchFile(extension)
            val content: String? = if (file.exists()) file.readText().replace(Regex("\\s*", RegexOption.MULTILINE), "") else null
            extension to content
        }.toMap()

        val checkedPairs = HashSet<UpdatePair>()
        for (rule in rules) {
            var fromExtension = rule.first()

            for (toExtension in rule.drop(1)) {
                if (!checkedPairs.add(UpdatePair(fromExtension, toExtension))) continue

                val fromContent = contentMap[fromExtension] ?: continue
                val toContent = contentMap[toExtension] ?: continue

                if (toContent == fromContent) {
                    reduceFiles.add(affectedOriginFile.toBunchFile(toExtension))
                }

                fromExtension = toExtension
            }
        }
    }

    if (settings.action == ReduceAction.PRINT) {
        reduceFiles.sort()
        for (file in reduceFiles) {
            println(file)
        }
        return
    }

    assert(settings.action == ReduceAction.DELETE || settings.action == ReduceAction.COMMIT)

    val changedFiles = ArrayList<FileChange>()
    for (file in reduceFiles) {
        file.delete()
        changedFiles.add(FileChange(ChangeType.REMOVE, file))
    }

    if (settings.action == ReduceAction.COMMIT) {
        commitChanges(settings.repoPath, changedFiles, settings.commitMessage)
    }
}



