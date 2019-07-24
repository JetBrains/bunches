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
            <type>     - Type of hook to install (${HookType.values().joinToString { it.hookName }}) 
            
            """.trimIndent()
        )
    }
    val gitPath = if (args.size == 1) File("").canonicalPath else args[1]

    val type = parseType(args[0]) ?: exitWithError("Unknown hook type")

    if (!File(gitPath).absoluteFile.exists()) {
        exitWithError("Directory $gitPath doesn't exist")
    }

    val dotGitPath = File(gitPath, ".git")
    if (!dotGitPath.isDirectory) {
        exitWithError("Directory $gitPath is not a repository")
    }

    installHook(type, dotGitPath)

    println("Bunch ${type.hookName} hook has been successfully installed")
}

fun installHook(type: HookType, dotGitPath: File) {
    val hooksDirectory = File(dotGitPath, "hooks")
    val hookPath = File(hooksDirectory, type.hookName).absolutePath

    val oldHookNewName: String
    val hooksPath = File(dotGitPath, "hooks")

    if (File(hookPath).exists()) {
        if (checkHookMarker(File(hookPath).readText(), type)) {
            println("Bunch file checking hook found, it will be reinstalled")
            val oldHookName = File(hookCodeParams(File(hookPath).readText()).oldHookPath).name
            oldHookNewName = if (oldHookName == ":") "" else oldHookName
        } else {
            println(
                """
            Other hook was found
            Do you want the new name to be generated (1) or rename it by yourself? (2)
            Type 1 or 2
            To cancel installation press ctrl+c
            """.trimIndent()
            )

            when (readLine()) {
                "2" -> {
                    oldHookNewName = readLine() ?: exitWithError("New name was not provided")
                }
                else -> {
                    val tempFile = createTempFile(type.hookName, "", File("$dotGitPath/hooks"))
                    oldHookNewName = tempFile.relativeTo(hooksPath).path
                    tempFile.delete()
                }
            }

            if (File(hookPath).renameTo(File(hooksPath, oldHookNewName)))
                println("Old hook was renamed to $oldHookNewName and will still be called")
            else
                exitWithError("Couldn't rename existing hook")
        }
    } else {
        oldHookNewName = ""
    }

    val hookFile = File(hookPath).absoluteFile
    if (!hookFile.exists() && !hookFile.createNewFile()) {
        exitWithError("Failed to create hook file")
    }
    if (!hookFile.setExecutable(true)) {
        exitWithError("Failed to make hook executable")
    }

    val bunchExecutableFile = findExecutableFileFromZip()
        ?: findExecutableFileFromProject()
        ?: exitWithError("Can't find executable file")

    val oldHookPath = if (oldHookNewName != "")
        "'${File(hooksPath, oldHookNewName).absolutePath}'"
    else
        ":"

    hookFile.writeText(type.getHookCodeTemplate(bunchExecutableFile, oldHookPath, dotGitPath.parentFile))
}

fun findExecutableFileFromZip(): File? {
    val jarURI = ::installHookCommand::class.java.protectionDomain.codeSource.location.toURI()
    val installationDir = File(jarURI).parentFile.parentFile
    val bunchExecutableFile = File(installationDir, "bin/bunch")
    if (bunchExecutableFile.exists()) {
        return bunchExecutableFile
    }
    return null
}

fun findExecutableFileFromProject(): File? {
    val bunchExecutableFile = File("build/install/bunch-cli/bin/", "bunch")
    if (bunchExecutableFile.exists()) {
        return bunchExecutableFile
    }
    return null
}