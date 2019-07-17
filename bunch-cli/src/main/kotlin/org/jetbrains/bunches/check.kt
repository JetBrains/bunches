@file:JvmName("BunchCheck")

package org.jetbrains.bunches.check

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.file.BUNCH_FILE_NAME
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.readCommits
import java.io.File

data class Settings(val repoPath: String, val sinceRef: String, val untilRef: String, val extensions: String?)

fun main(args: Array<String>) {
    check(args)
}

const val CHECK_DESCRIPTION =
    "Check if commits have forgotten bunch files according to the HEAD of the given directory."

const val CH_SINCE = "since-ref"
const val CH_UNTIL = "until-ref"

fun check(args: Array<String>) {
    if (args.size !in 3..4) {
        exitWithUsageError(
            """
            Usage: <git-path> <since-ref> <until-ref> [<extensions>]

            $CHECK_DESCRIPTION

            <git-path>   - Directory with repository (parent directory for .git).
            <$CH_SINCE>  - Reference to the most recent commit that should be checked.
            <$CH_UNTIL>  - Parent of the last commit that should be checked.
            <extensions> - Set of extensions to check with '_' separator. '$BUNCH_FILE_NAME' file will be used if
                           the option is missing.

            Example:
            bunch check C:/Projects/kotlin HEAD 377572896b7dc09a5e2aa6af29825ffe07f71e58
            """.trimIndent()
        )
    }

    val settings = Settings(
        repoPath = args[0],
        sinceRef = args[1],
        untilRef = args[2],
        extensions = args.getOrNull(3)
    )

    doCheck(settings)
}

fun doCheck(settings: Settings) {
    val extensions = settings.extensions?.split('_') ?: readExtensionsFromFile(settings.repoPath) ?: exitWithError()

    val commits = readCommits(settings.repoPath, settings.sinceRef, settings.untilRef)
    var problemCommitsFound = false

    println("Found commits:")
    for (commit in commits) {
        println(commit.title)
    }
    println()

    println("Result:")
    for (commit in commits) {
        println("Checking commit ${commit.message!!.trim()}...")
        val affectedPaths = commit.fileActions.mapNotNullTo(HashSet()) { it.newPath }

        val forgottenFilesPaths = ArrayList<String>()
        for (fileAction in commit.fileActions) {
            val newPath = fileAction.newPath ?: continue
            if (File(newPath).extension in extensions) continue

            for (extension in extensions) {
                val bunchFilePath = "$newPath.$extension"
                val file = File(settings.repoPath, bunchFilePath)
                if (bunchFilePath !in affectedPaths && file.exists() && !isCommittedAfter(file, commits, commit)) {
                    forgottenFilesPaths.add(bunchFilePath)
                    println(file.name + " didnt modified, maybe forgotten")
                }
            }
        }

        val deletedCache: MutableMap<String, Boolean> = HashMap()
        fun isDeletedWithCache(bunchFilePath: String): Boolean {
            return deletedCache.getOrPut(bunchFilePath) {
                val bunchFile = File(settings.repoPath, bunchFilePath)
                isDeletedBunchFile(bunchFile)
            }
        }

        if (forgottenFilesPaths.isNotEmpty()) {
            problemCommitsFound = true

            println("${commit.hash} ${commitAuthorString(commit)} ${commit.title}")
            for (forgottenPath in forgottenFilesPaths) {
                println("    $forgottenPath ${if (isDeletedWithCache(forgottenPath)) "[deleted]" else ""}")
            }
            println()
        } else {
            println("${commit.message} is OK")
        }
    }

    if (problemCommitsFound) {
        exitWithError()
    }

    println("${commits.size} commits have been checked. No problem commits found.")
}

fun isCommittedAfter(file: File, commits: List<CommitInfo>, commit: CommitInfo): Boolean {
    for (current in commits) {
        if (current == commit) {
            return false
        }
        for (action in current.fileActions) {
            if (action.changeType == DiffEntry.ChangeType.ADD
                && action.newPath != null
                && File(action.newPath).name == file.name) {
                return true
            }
        }
    }
    return true
}

fun isDeletedBunchFile(bunchFile: File): Boolean = bunchFile.exists() && bunchFile.readText().trim().isEmpty()

fun commitAuthorString(commit: CommitInfo): String {
    val committer = commit.committer?.name
    var author = commit.author?.name
    if (author == null && committer != null) {
        author = committer
    }

    return when {
        author == null -> ""
        author == committer || committer == null -> "[$author]"
        else -> "[$author ($committer)]"
    }
}

