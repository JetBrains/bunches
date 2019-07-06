@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchCherryPick")

package org.jetbrains.bunches.cp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.bunches.general.partial
import org.jetbrains.bunches.general.process
import org.jetbrains.bunches.git.reCommitPatched
import org.jetbrains.bunches.git.readCommits
import java.nio.file.Paths

data class Settings(
    val gitPath: String,
    val branch: String,
    val untilHash: String,
    val suffix: String
)

fun main(args: Array<String>) {
    CherryPickCommand().main(args)
}

const val CHERRY_PICK_DESCRIPTION =
    "cherry-picks commit to the current branch with auto-creating bunch files with given suffix."

val CHERRY_PICK_EXAMPLE =
    """
    Example:
    bunch cp -C C:/Projects/kotlin my-feature-branch c1125219e44aed60b8ba033ddae5b8f5c748052e 182
    """.trimIndent()

const val CP_SINCE = "since-ref"
const val CP_UNTIL = "until-ref"
const val CP_SX = "extension"

class CherryPickCommand : CliktCommand(name = "cp", help = CHERRY_PICK_DESCRIPTION, epilog = CHERRY_PICK_EXAMPLE) {
    val config by requireObject<Map<String, Boolean>>()
    val repoPath by option("-C", help = "Directory with repository (parent directory for .git).")
        .path(exists = true, fileOkay = false)
        .default(Paths.get(".").toAbsolutePath().normalize())
    val sinceRef by argument(name = "<$CP_SINCE>",  help = "Reference to most recent commit that should be ported.")
    val untilRef by argument(name = "<$CP_UNTIL>", help = "Parent of the last commit that should be ported (hash of \"==== switch 182 ====\" for instance).")
    val extension by argument(name = "<$CP_SX>", help = "Suffix for ported files.")
    override fun run() {
        val settings = Settings(repoPath.toString(), sinceRef, untilRef, extension)
        process(config.getValue("VERBOSE"), ::cherryPick.partial(settings))
    }
}

fun cherryPick(settings: Settings) {
    with(settings) {
        val commits = readCommits(gitPath, branch, untilHash)

        for (commit in commits.reversed()) {
            println("Apply: ${commit.hash} ${commit.title}")

            reCommitPatched(gitPath, commit, suffix)
        }
    }
}
