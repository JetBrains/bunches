@file:JvmName("InstallHook")

package org.jetbrains.bunches.precommit

import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.general.process
import java.io.File

fun main(args: Array<String>) {
    process(args, ::installHook)
}

fun installHook(args: Array<String>) {
    if (args.isEmpty()) {
        exitWithUsageError(
            """
            Usage: <git-path>

            Installs git hook that checks forgotten bunch files

            <git-path>   - Directory with repository (parent directory for .git).
            """.trimIndent()
        )
    }

    val gitPath = args[0]
    if (!File(gitPath).exists()) {
        exitWithError("Directory $gitPath doesn't exist")
    }

    val dotGitPath = "$gitPath/.git"
    if (!File(dotGitPath).exists()) {
        exitWithError("Directory $gitPath is not a repository")
    }

    val hookPath = "$dotGitPath/hooks/pre-commit"
    if (File(hookPath).exists()) {
        exitWithError("Pre-commit hook already exists")
    }

    // Directories structure: app/lib/jar
    val jarURI = ::installHook::class.java.protectionDomain.codeSource.location.toURI()
    val installationDir = File(jarURI).parentFile.parentFile

    val bunchExecutableFile = File(installationDir, "bin/bunch")
    if (!bunchExecutableFile.exists()) {
        exitWithError("Can't find executable file `$bunchExecutableFile`")
    }

    val bunchExecutablePath = bunchExecutableFile.canonicalPath
    File(hookPath).writeText(
        """
        #!/bin/sh

        if [[ -t 1 ]]
        then
            files="${'$'}(git diff --cached --name-only | while read file ; do echo -n "'${'$'}file' "; done)"
            eval "'$bunchExecutablePath' precommit ${'$'}files < /dev/tty"
            exit $?
        else
            exit 0
        fi
        """.trimIndent()
    )

    println("Bunch pre-commit hook has been successfully installed")
}
