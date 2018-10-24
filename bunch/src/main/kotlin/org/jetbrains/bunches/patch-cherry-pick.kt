@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchCherryPick")

package org.jetbrains.bunches.cp

import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.reCommitPatched
import org.jetbrains.bunches.git.readCommits

data class Settings(
        val gitPath: String,
        val branch: String,
        val untilHash: String,
        val suffix: String
)

fun main(args: Array<String>) {
    cherryPick(args)
}

const val CHERRY_PICK_DESCRIPTION = "cherry-picks commit to the current branch with auto-creating bunch files with given suffix."

fun cherryPick(args: Array<String>) {
    if (args.size != 4) {
        exitWithUsageError("""
            Usage: <git-path> <since-ref> <until-ref> <suffix>

            $CHERRY_PICK_DESCRIPTION

            <git-path>  - Directory with repository (parent directory for .git folder).
            <since-ref> - Reference to most recent commit that should be ported.
            <until-ref> - Parent of the last commit that should be ported (hash of "==== switch 182 ====" for instance).
            <suffix>    - Suffix for ported files.

            Example:
            bunch cp C:/Projects/kotlin my-feature-branch c1125219e44aed60b8ba033ddae5b8f5c748052e 182
            """.trimIndent())
    }

    val settings = Settings(args[0], args[1], args[2], args[3])
    with(settings) {
        val commits = readCommits(gitPath, branch, untilHash)

        for (commit in commits.reversed()) {
            println("Apply: ${commit.hash} ${commit.title}")

            reCommitPatched(gitPath, commit, suffix)
        }
    }
}
