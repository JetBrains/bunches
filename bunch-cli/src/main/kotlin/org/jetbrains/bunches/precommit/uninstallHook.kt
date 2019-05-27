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

            <git-path>   - Directory with repository (parent directory for .git).
            """.trimIndent()
        )
    }

    val gitPath = if (args.isEmpty()) File("").canonicalPath else args[0]
    if (!File(gitPath).exists()) {
        exitWithError("Directory $gitPath doesn't exist")
    }

    val dotGitPath = "$gitPath/.git"
    if (!File(dotGitPath).isDirectory) {
        exitWithError("Directory $gitPath is not a repository")
    }

    val hookFile = File("$dotGitPath/hooks/pre-commit")
    if (!hookFile.exists()) {
        exitWithError("Pre-commit hook is not found")
    }

    // Directories structure: app/lib/jar
    val jarURI = ::installHook::class.java.protectionDomain.codeSource.location.toURI()
    val installationDir = File(jarURI).parentFile.parentFile

    val bunchExecutableFile = File(installationDir, "bin/bunch")
    if (!bunchExecutableFile.exists()) {
        exitWithError("Can't find executable file `$bunchExecutableFile`")
    }

    val bunchExecutablePath = bunchExecutableFile.canonicalPath

    val hookText = """
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

    if (hookFile.readText() == hookText) {
        if (hookFile.delete()) {
            println("Successfully uninstalled hook")
        } else {
            exitWithError("Couldn't delete hook file")
        }
    } else {
        exitWithError("Precommit hook exists, but is not a forgotten files checker")
    }
}