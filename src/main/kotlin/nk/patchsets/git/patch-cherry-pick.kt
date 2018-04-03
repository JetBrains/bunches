@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("PatchCherryPick")

package nk.patchsets.git.cp

import nk.patchsets.git.reCommitPatched
import nk.patchsets.git.readCommits

data class Settings(
        val gitPath: String,
        val branch: String,
        val untilHash: String,
        val suffix: String
)

private val localSettings = Settings(
        gitPath = "patches",
        branch = "172",
        untilHash = "3ccf254d42b9c1dff90f5ff722436387c97755ad",
        suffix = "172"
)

private val kotlinSettings = Settings(
        gitPath = "C:/Projects/kotlin",
        branch = "origin/master_182",
        untilHash = "c1125219e44aed60b8ba033ddae5b8f5c748052e",
        suffix = "182"
)

fun main(args: Array<String>) {
    if (args.size != 4) {
        System.err.println("""
            Usage: <git-path> <since-ref> <until-ref> <suffix>

            <git-path> - Directory with repository (parent directory for .git folder)
            <since-ref> - Reference to most recent commit that should be ported
            <until-ref> - Parent of the last commit that should be ported
            <suffix> - Suffix for ported files

            Example:
            <program> C:/Projects/kotlin origin/master_182 c1125219e44aed60b8ba033ddae5b8f5c748052e 182
            """.trimIndent())

        return
    }

    val settings = Settings(args[0], args[1], args[2], args[3])
    with (settings) {
        val commits = readCommits(gitPath, branch, untilHash)

        for (commit in commits.reversed()) {
            println("Apply: ${commit.hash} ${commit.title}")

            reCommitPatched(gitPath, commit, suffix)
        }
    }
}