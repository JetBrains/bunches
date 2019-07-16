@file:JvmName("InstallHook")

package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.general.process
import java.io.File

fun main(args: Array<String>) {
    process(args, ::installHook)
}

fun installHook(args: Array<String>) {
    if (args.size > 2 || args.isEmpty()) {
        exitWithUsageError(
            """
            Usage: <type> [<git-path>]

            Installs git hook that checks forgotten bunch files

            <git-path>   -- Directory with repository (parent directory for .git).
            <type>       -- Type of hook to install (commit or rebase) 
            
            """.trimIndent()
        )
    }
    val gitPath = if (args.size == 1) File("").canonicalPath else args[1]

    val type = when (args[0]) {
        "commit" -> "pre-commit"
        "rebase" -> "pre-rebase"
        "push" -> "pre-push"
        else -> exitWithError("Unknown hook type")
    }

    File(gitPath).absoluteFile.setReadable(true)
    println(gitPath + " " + File(gitPath).absolutePath)
    if (!File(gitPath).absoluteFile.exists()) {
        exitWithError("Directory $gitPath doesn't exist")
    }

    val dotGitPath = "$gitPath/.git"
    if (!File(dotGitPath).isDirectory) {
        exitWithError("Directory $gitPath is not a repository")
    }

    val hookPath = "$dotGitPath/hooks/$type"
    installOneHook(hookPath, type, dotGitPath)

    println("Bunch $type hook has been successfully installed")
}

fun installOneHook(hookPath: String, type: String, dotGitPath: String) {
    val oldHookNewName: String
    if (File(hookPath).exists()) {
        if (checkHookCode(File(hookPath).readText(), type))
            exitWithError("Bunch file checking hook is already installed")

        println(
            """
            Other hook was found
            Do you want the new name to be generated (1) or rename it by yourself? (2)
            Type 1 or 2
            To cancel installation press ctrl+c
            """.trimIndent()
        )
        when (readLine()) {
            "1" -> {
                val tempFile = createTempFile(type, "", File("$dotGitPath/hooks"))
                oldHookNewName = tempFile.relativeTo(File("$dotGitPath/hooks")).path
                tempFile.delete()
            }
            "2" -> {
                oldHookNewName = readLine() ?: exitWithError("New name was not provided")
            }
            else -> {
                val tempFile = createTempFile(type, "", File("$dotGitPath/hooks"))
                oldHookNewName = tempFile.relativeTo(File("$dotGitPath/hooks")).path
                tempFile.delete()
            }
        }

        if (File(hookPath).renameTo(File("$dotGitPath/hooks/$oldHookNewName")))
            println("Old hook was renamed to $oldHookNewName and will still be called")
        else
            exitWithError("Couldn't rename existing hook")
    } else {
        oldHookNewName = ""
    }

    val hookFile = File(hookPath).absoluteFile
    if (!hookFile.createNewFile()) {
        exitWithError("Failed to create hook file")
    }
    if (!hookFile.setExecutable(true)) {
        exitWithError("Failed to make hook executable")
    }

//    Directories structure: app/lib/jar
//    val jarURI = ::installHook::class.java.protectionDomain.codeSource.location.toURI()
//    val installationDir = File(jarURI).parentFile.parentFile

    val bunchExecutableFile = File("build/install/bunch-cli/bin/", "bunch")
    if (!bunchExecutableFile.exists()) {
        exitWithError("Can't find executable file `$bunchExecutableFile`")
    }

    val oldHookPath = if (oldHookNewName != "")
        "'$dotGitPath/hooks/$oldHookNewName'"
    else
        ":"

    val bunchExecutablePath = bunchExecutableFile.canonicalPath

    hookFile.writeText(when(type) {
        "pre-commit" -> preCommitHookCodeFromTemplate(bunchExecutablePath, oldHookPath)
        "pre-rebase" -> preRebaseCodeWithBashCommand(bunchExecutablePath, oldHookPath, dotGitPath)
        "pre-push" -> prePushCode(bunchExecutablePath, oldHookPath, dotGitPath)
        else -> ""
    })
}