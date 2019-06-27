package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.general.process
import java.io.File

fun main(args: Array<String>) {
    process(args, ::uninstallHook)
}

fun uninstallHook(args: Array<String>) {
    if (args.size > 2 || args.isEmpty()) {
        exitWithUsageError(
            """
            Usage: <type> [<git-path>]

            Uninstalls git hook that checks forgotten bunch files

            <git-path>   -- Directory with repository base (parent directory for .git directory).
            <type>       -- Type of hook to install (commit or rebase) 
            """.trimIndent()
        )
    }

    val type = when (args[0]) {
        "commit" -> "pre-commit"
        "rebase" -> "pre-rebase"
        else -> exitWithError("Unknown hook type")
    }

    val gitPath = if (args.size == 1) File("").canonicalPath else args[1]
    if (!File(gitPath).exists()) {
        exitWithError("Directory `$gitPath` doesn't exist")
    }

    val dotGitPath = "$gitPath/.git"
    if (!File(dotGitPath).isDirectory) {
        exitWithError("Directory `$gitPath` is not a repository.")
    }

    val hookFile = File("$dotGitPath/hooks/$type")
    if (!hookFile.exists()) {
        exitWithError("$type hook is not found")
    }

    val hookCode = hookFile.readText()

    if (!checkHookCode(hookCode, type)) {
        exitWithError("$type hook exists but this is not the bunch tool hook")
    }

    val hookParams = hookCodeParams(hookCode)
    if (!hookFile.delete()) {
        exitWithError("Couldn't delete hook file")
    }

    if (hookParams.oldHookPath == ":")
        println("Successfully uninstalled hook")
    else {
        val oldHookFile = File(hookParams.oldHookPath)
        if (oldHookFile.renameTo(File("$dotGitPath/hooks/$type")))
            println("Successfully uninstalled. Old $type hook was reverted.")
        else
            exitWithError("Successfully uninstalled. Old $type hook wasn't restored.")
    }
}