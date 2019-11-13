@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchCherryPick")

package org.jetbrains.bunches.cp

import com.github.ajalt.clikt.parameters.arguments.argument
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.git.checkAndExitIfNeeded
import org.jetbrains.bunches.git.reCommitChanges
import org.jetbrains.bunches.git.readCommits
import org.jetbrains.bunches.git.uncommittedChanges

data class Settings(
    val gitPath: String,
    val sinceHash: String,
    val untilHash: String,
    val suffix: String
)

fun main(args: Array<String>) {
    CherryPickCommand().main(args)
}

const val CHERRY_PICK_DESCRIPTION =
    "Cherry-picks commit to the current branch with auto-creating bunch files with given suffix." +
            " If bunch files already exist commits will override previous content to make files equals after applying."

val CHERRY_PICK_EXAMPLE =
    """
    Example:
    bunch cp -C C:/Projects/kotlin my-feature-branch c1125219e44aed60b8ba033ddae5b8f5c748052e 182
    """.trimIndent()

const val CP_SINCE = "since-ref"
const val CP_UNTIL = "until-ref"
const val CP_SX = "extension"

class CherryPickCommand : BunchSubCommand(
    name = "cp",
    help = CHERRY_PICK_DESCRIPTION,
    epilog = CHERRY_PICK_EXAMPLE
) {
    val repoPath by repoPathOption()
    private val sinceRef by argument("<$CP_SINCE>", help = "Reference to most recent commit that should be ported.")

    private val untilRef by argument(
        "<$CP_UNTIL>",
        help = "Parent of the last commit that should be ported (hash of \"==== switch 182 ====\" for instance)."
    )

    val extension by argument("<$CP_SX>", help = "Suffix for ported files.")

    override fun run() {
        val settings = Settings(
            gitPath = repoPath.toString(),
            sinceHash = sinceRef,
            untilHash = untilRef,
            suffix = extension
        )

        process { cherryPick(settings) }
    }
}

fun cherryPick(settings: Settings) {
    uncommittedChanges(settings.gitPath).checkAndExitIfNeeded {
        exitWithError("Can not create new commit in cp with uncommitted changes.")
    }

    with(settings) {
        val commits = readCommits(
            repositoryPath = gitPath,
            sinceRevString = sinceHash,
            untilRevString = untilHash
        )

        for (commit in commits.reversed()) {
            println("Apply: ${commit.hash} ${commit.title}")
            reCommitChanges(gitPath, commit.fileActions, commit, suffix)
        }
    }
}