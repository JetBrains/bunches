@file:JvmName("InstallHook")

package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.general.process
import java.io.File

fun main(args: Array<String>) {
    process(args, ::installHookCommand)
}

fun installHookCommand(args: Array<String>) {
    if (args.size > 2 || args.isEmpty()) {
        exitWithUsageError(
            """
            Usage: <type> [<git-path>]

            Installs git hook that checks forgotten bunch files

            <git-path> - Directory with repository (parent directory for .git).
            <type>     - Type of hook to install (commit or rebase) 
            
            """.trimIndent()
        )
    }
    val gitPath = if (args.size == 1) File("").canonicalPath else args[1]

    val type = parseType(args[0]) ?: exitWithError("Unknown hook type")

    if (!File(gitPath).absoluteFile.exists()) {
        exitWithError("Directory $gitPath doesn't exist")
    }

    val dotGitPath = "$gitPath/.git"
    if (!File(dotGitPath).isDirectory) {
        exitWithError("Directory $gitPath is not a repository")
    }

    val hookPath = "$dotGitPath/hooks/$type"
    installHook(hookPath, type, dotGitPath)

    println("Bunch $type hook has been successfully installed")
}

fun installHook(hookPath: String, type: HookType, dotGitPath: String) {
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
                val tempFile = createTempFile(type.hookName, "", File("$dotGitPath/hooks"))
                oldHookNewName = tempFile.relativeTo(File("$dotGitPath/hooks")).path
                tempFile.delete()
            }
            "2" -> {
                oldHookNewName = readLine() ?: exitWithError("New name was not provided")
            }
            else -> {
                val tempFile = createTempFile(type.hookName, "", File("$dotGitPath/hooks"))
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
    val jarURI = ::installHookCommand::class.java.protectionDomain.codeSource.location.toURI()
    val installationDir = File(jarURI).parentFile.parentFile
    var bunchExecutableFile = File(installationDir, "bin/bunch")
    if (!bunchExecutableFile.exists()) {
        println("\"Can't find executable file `$bunchExecutableFile`\"")
        println("Trying to pretend to be bunch tool project")
    }
//    Some crutch
    if (!File("build/install/bunch-cli/bin/", "bunch").exists()) {
        exitWithError("Can't find executable file `$bunchExecutableFile`")
    }
    bunchExecutableFile = File("build/install/bunch-cli/bin/", "bunch")

    val oldHookPath = if (oldHookNewName != "")
        "'$dotGitPath/hooks/$oldHookNewName'"
    else
        ":"

    val bunchExecutablePath = bunchExecutableFile.canonicalPath
    hookFile.writeText(type.getHookCodeTemplate(bunchExecutablePath, oldHookPath, dotGitPath))
}