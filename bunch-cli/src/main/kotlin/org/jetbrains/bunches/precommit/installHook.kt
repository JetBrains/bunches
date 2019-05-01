@file:JvmName("InstallGook")

package org.jetbrains.bunches.precommit

import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import java.io.File

fun installHook(args: Array<String>) {
    if (args.isEmpty())
        exitWithUsageError("""
            Usage: <git-path>

            Installs git hook that checks forgotten bunch files

            <git-path>   - Directory with repository (parent directory for .git).
            """.trimIndent())

    if (!File(args[0]).exists())
        exitWithError("Directory ${args[0]} doesn't exist")

    val dotGitPath = args[0] + "/.git"
    if (!File(dotGitPath).exists())
        exitWithError("Directory ${args[0]} is not a repository")

    val hookPath = "$dotGitPath/hooks/pre-commit"
    if (File(hookPath).exists())
        exitWithError("Precommit hook already exists")

    val bunchPath = File(::installHook::class.java.protectionDomain.codeSource.location.toURI()).parentFile.parentFile.canonicalPath + "/bin/bunch"
    File(hookPath).writeText("""
        #!/bin/sh

        '$bunchPath precommit < /dev/tty'
        exit $?
    """.trimIndent())

    println("Hook was successfully installed")
}
