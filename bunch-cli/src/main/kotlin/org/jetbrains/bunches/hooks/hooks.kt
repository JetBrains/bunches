@file:JvmName("BunchHook")

package org.jetbrains.bunches.hooks

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithError
import java.io.File

enum class HookAction {
    INSTALL {
        override val action = "install"
        override val help = "install hooks and save old hook if necessary." +
                " It such hook already exits it will be replaced with current version.\n"
    },
    UNINSTALL {
        override val action = "uninstall"
        override val help = "uninstall hook and restore old hook if it exists."
    },
    UPDATE {
        override val action = "update"
        override val help = "update hook if it exists."
    };

    abstract val action: String
    abstract val help: String
}

class HooksSettings(
    val path: String,
    val type: HookTypeOption,
    val action: HookAction
)

class HookTypeOption(private val hookType: HookType?) {
    companion object {
        val allType = HookTypeOption(null)
    }

    fun getTypeList(): List<HookType> {
        return if (hookType != null) {
            listOf(hookType)
        } else {
            HookType.values().toList()
        }
    }
}

class HooksCommand : BunchSubCommand(
    name = "hooks",
    help = "Git hooks management."
) {
    val repoPath by repoPathOption()

    private val action: HookAction by argument(
        "action",
        help = "Action:\n${HookAction.values().joinToString("\n") { "${it.action} -  ${it.help}" }}."
    )
        .choice(HookAction.values().map { it.action to it }.toMap())

    private val hookType: HookTypeOption by argument(
        "hook-type", help = "Type: ${HookType.values().joinToString { it.hookName }}. All used by default."
    ).choice(HookType.values()
        .associate { it.hookName to HookTypeOption(it) }.plus("all" to HookTypeOption.allType)
    )
        .default(HookTypeOption.allType)

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
    val dotGitPath = File(settings.path, ".git")
    for (type in settings.type.getTypeList()) {
        try {
            when (settings.action) {
                HookAction.INSTALL -> installHook(type, dotGitPath)
                HookAction.UNINSTALL -> uninstallHook(type, dotGitPath)
                HookAction.UPDATE -> updateHook(type, dotGitPath)
            }
        } catch (exception: LocalBunchException) {
            println(exception.message)
        }
    }
}

fun updateHook(type: HookType, dotGitPath: File) {
    val hooksDirectory = File(dotGitPath, "hooks")
    val hookPath = File(hooksDirectory, type.hookName)
    if (!hookPath.exists() || !checkHookMarker(hookPath.readText(), type)) {
        exitWithLocalError("No ${type.hookName} hook found")
    }

    val bunchExecutableFile = findExecutableFileFromZip()
        ?: findExecutableFileFromProject()
        ?: exitWithError("Can't find executable file")

    val oldHookPath = hookCodeParams(hookPath.readText()).oldHookPath
    hookPath.writeText(type.getHookCodeTemplate(bunchExecutableFile, oldHookPath, dotGitPath.parentFile))
    println("Successfully updated ${type.hookName} hook")
}

fun installHook(type: HookType, dotGitPath: File) {
    val hooksDirectory = File(dotGitPath, "hooks")
    val hookPath = File(hooksDirectory, type.hookName).absolutePath

    val oldHookNewName: String
    val hooksPath = File(dotGitPath, "hooks")

    if (File(hookPath).exists()) {
        if (checkHookMarker(File(hookPath).readText(), type)) {
            println("Bunch file ${type.hookName} hook found, it will be reinstalled")
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
                    oldHookNewName = readLine() ?: exitWithLocalError("New name for ${type.hookName} was not provided")
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
                exitWithLocalError("Couldn't rename existing ${type.hookName} hook")
        }
    } else {
        oldHookNewName = ""
    }

    val hookFile = File(hookPath).absoluteFile
    if (!hookFile.exists() && !hookFile.createNewFile()) {
        exitWithLocalError("Failed to create ${type.hookName} hook file")
    }
    if (!hookFile.setExecutable(true)) {
        exitWithLocalError("Failed to make ${type.hookName} hook executable")
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
        exitWithLocalError("${type.hookName} hook is not found")
    }

    val hookCode = hookFile.readText()

    if (!checkHookMarker(hookCode, type)) {
        exitWithLocalError("${type.hookName} hook exists but this is not the bunch tool hook")
    }

    val hookParams = hookCodeParams(hookCode)
    if (!hookFile.delete()) {
        exitWithLocalError("Couldn't delete ${type.hookName} hook file")
    }

    if (hookParams.oldHookPath == ":")
        println("Successfully uninstalled ${type.hookName} hook")
    else {
        val oldHookFile = File(hookParams.oldHookPath)
        if (oldHookFile.renameTo(File(hooksPath, type.hookName)))
            println("Successfully uninstalled. Old ${type.hookName} hook was reverted.")
        else
            exitWithLocalError("Successfully uninstalled. Old ${type.hookName} hook wasn't restored.")
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

private fun exitWithLocalError(message: String? = null): Nothing {
    throw LocalBunchException(message)
}

private class LocalBunchException(message: String? = null) : BunchException(message)