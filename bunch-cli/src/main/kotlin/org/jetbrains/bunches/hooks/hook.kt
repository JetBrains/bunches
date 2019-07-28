@file:JvmName("BunchHook")

package org.jetbrains.bunches.hooks

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithError
import java.io.File

enum class HookAction {
    INSTALL,
    UNINSTALL
}

class HooksSettings(
    val path: String,
    val type: HookType,
    val action: HookAction
)

class HooksCommand : BunchSubCommand(
    name = "hooks",
    help = "Git hooks management."
) {
    val repoPath by repoPathOption()

    private val action: HookAction by argument("action", help = "Action: install or uninstall.")
        .choice("install" to HookAction.INSTALL, "uninstall" to HookAction.UNINSTALL)

    private val hookType: HookType by argument(
        "hook-type", help = "Type: ${HookType.values().joinToString { it.hookName }}."
    )
        .choice(HookType.values().map { it.hookName to it }.toMap())

    override fun run() {
        val settings = HooksSettings(
            path = repoPath.toString(),
            action = action,
            type = hookType
        )

        process { hookCommand(settings) }
    }
}

fun main(args: Array<String>) {
    HooksCommand().main(args)
}

fun hookCommand(settings: HooksSettings) {
    val dotGitPath = File(settings.path, ".git")
    if (!dotGitPath.isDirectory) {
        exitWithError("Directory ${settings.path} is not a repository")
    }

    doHookCommand(settings)
}

fun doHookCommand(settings: HooksSettings) {
    when (settings.action) {
        HookAction.INSTALL -> installHook(settings.type, File(settings.path))
        HookAction.UNINSTALL -> uninstallHook(settings.type, File(settings.path))
    }
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

    println("Bunch ${type.hookName} hook has been successfully installed")
}

fun uninstallHook(type: HookType, dotGitPath: File) {
    val hooksPath = File(dotGitPath, "hooks")
    val hookFile = File(hooksPath, type.hookName)
    if (!hookFile.exists()) {
        exitWithError("${type.hookName} hook is not found")
    }

    val hookCode = hookFile.readText()

    if (!checkHookMarker(hookCode, type)) {
        exitWithError("${type.hookName} hook exists but this is not the bunch tool hook")
    }

    val hookParams = hookCodeParams(hookCode)
    if (!hookFile.delete()) {
        exitWithError("Couldn't delete hook file")
    }

    if (hookParams.oldHookPath == ":")
        println("Successfully uninstalled hook")
    else {
        val oldHookFile = File(hookParams.oldHookPath)
        if (oldHookFile.renameTo(File(hooksPath, type.hookName)))
            println("Successfully uninstalled. Old ${type.hookName} hook was reverted.")
        else
            exitWithError("Successfully uninstalled. Old ${type.hookName} hook wasn't restored.")
    }
}

fun findExecutableFileFromZip(): File? {
    val jarURI = HooksCommand::class.java.protectionDomain.codeSource.location.toURI()
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