package org.jetbrains.bunches.precommit

import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.general.process
import java.io.File

fun main(args: Array<String>) {
    process(args, ::uninstallHook)
}

fun uninstallHook(args: Array<String>) {
    if (args.size > 1) {
        exitWithUsageError(
            """
            Usage: <git-path>

            Uninstalls git hook that checks forgotten bunch files

            <git-path>   - Directory with repository base (parent directory for .git directory).
            """.trimIndent()
        )
    }

    val gitPath = if (args.isEmpty()) File("").canonicalPath else args[0]
    if (!File(gitPath).exists()) {
        exitWithError("Directory `$gitPath` doesn't exist")
    }

    val dotGitPath = "$gitPath/.git"
    if (!File(dotGitPath).isDirectory) {
        exitWithError("Directory `$gitPath` is not a repository.")
    }

    val hookFile = File("$dotGitPath/hooks/pre-commit")
    if (!hookFile.exists()) {
        exitWithError("Pre-commit hook is not found")
    }

    val hookCode = hookFile.readText()

    if (!checkHookCode(hookCode)) {
        exitWithError("Precommit hook exists but this is not the bunch tool hook")
    }

    val hookParams = hookCodeParams(hookCode)
    if (!hookFile.delete()) {
        exitWithError("Couldn't delete hook file")
    }

    if (hookParams.oldHookPath == ":")
        println("Successfully uninstalled hook")
    else {
        val oldHookFile = File(hookParams.oldHookPath)
        if (oldHookFile.renameTo(File("$dotGitPath/hooks/pre-commit")))
            println("Successfully uninstalled. Old pre-commit hook was reverted.")
        else
            exitWithError("Successfully uninstalled. Old pre-commit hook wasn't restored.")
    }
}