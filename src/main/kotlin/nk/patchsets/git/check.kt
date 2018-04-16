package nk.patchsets.git.check

import nk.patchsets.git.file.readExtensionFromFile
import nk.patchsets.git.readCommits
import java.io.File

data class Settings(val repoPath: String, val sinceRef: String, val untilRef: String, val extensions: String?)

fun main(args: Array<String>) {
    check(args)
}

const val CHECK_DESCRIPTION = "Check if commits have forgotten bunch files according to the HEAD of the given directory."

fun check(args: Array<String>) {
    if (args.size !in 3..4) {
        System.err.println("""
            Usage: <git-path> <since-ref> <until-ref> [<extensions>]

            $CHECK_DESCRIPTION

            <git-path>   - Directory with repository (parent directory for .git).
            <since-ref>  - Reference to the most recent commit that should be checked.
            <until-ref>  - Parent of the last commit that should be checked.
            <extensions> - Set of extensions to check with '_' separator. '.bunch' file will be used if
                           the option is missing.

            Example:
            bunch check C:/Projects/kotlin HEAD 377572896b7dc09a5e2aa6af29825ffe07f71e58
            """.trimIndent())

        return
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
    val extensions = settings.extensions?.split('_') ?: readExtensionFromFile(settings.repoPath) ?: return

    val commits = readCommits(settings.repoPath, null, settings.untilRef)
    var problemCommitsFound = false

    println("Found commits:")
    for (commit in commits) {
        println(commit.title)
    }
    println()

    println("Result:")
    for (commit in commits) {
        val affectedPaths = commit.fileActions.mapNotNullTo(HashSet()) { it.newPath }

        val forgottenFilesPaths = ArrayList<String>()
        for (fileAction in commit.fileActions) {
            val newPath = fileAction.newPath ?: continue
            if (File(newPath).extension in extensions) continue

            for (extension in extensions) {
                val bunchFilePath = "$newPath.$extension"
                if (bunchFilePath !in affectedPaths && File(settings.repoPath, bunchFilePath).exists()) {
                    forgottenFilesPaths.add(bunchFilePath)
                }
            }
        }

        if (!forgottenFilesPaths.isEmpty()) {
            problemCommitsFound = true

            println("${commit.hash} ${commit.title}")
            for (forgottenPath in forgottenFilesPaths) {
                println("    $forgottenPath")
            }
            println()
        }
    }

    if (!problemCommitsFound) {
        println("${commits.size} commits have been checked. No problem commits found.")
    }
}

