@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package org.jetbrains.bunches.general

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.BunchParametersException
import org.jetbrains.bunches.ManifestReader
import org.jetbrains.bunches.check.CheckCommand
import org.jetbrains.bunches.cleanup.CleanUpCommand
import org.jetbrains.bunches.cp.CherryPickCommand
import org.jetbrains.bunches.hooks.HOOK_LAUNCH_COMMAND
import org.jetbrains.bunches.hooks.HooksCommand
import org.jetbrains.bunches.hooks.InternalHooks
import org.jetbrains.bunches.reduce.ReduceCommand
import org.jetbrains.bunches.restore.RestoreCommand
import org.jetbrains.bunches.stats.StatCommand
import org.jetbrains.bunches.stats.log.LogCommand
import org.jetbrains.bunches.switch.SwitchCommand
import java.nio.file.Paths
import kotlin.system.exitProcess

fun exitWithUsageError(message: String): Nothing {
    throw BunchParametersException(message)
}

fun exitWithError(message: String? = null): Nothing {
    throw BunchException(message)
}

inline fun process(args: Array<String>, f: (args: Array<String>) -> Unit) {
    val isVerbose = args.contains("--verbose")
    process(isVerbose) {
        if (isVerbose) {
            println("Args: ${args.toList()}")
        }
        f(args.filter { it != "--verbose" }.toTypedArray())
    }
}

inline fun process(verbose: Boolean, f: () -> Unit) {
    try {
        f()
    } catch (e: BunchParametersException) {
        printExceptionToSystemError(1, verbose, e)
    } catch (e: BunchException) {
        printExceptionToSystemError(2, verbose, e)
    } catch (e: Throwable) {
        printExceptionToSystemError(3, verbose, e)
    }
}

fun printExceptionToSystemError(errorCode: Int, verbose: Boolean, e: Throwable): Nothing {
    if (verbose) {
        System.err.println(e)
        e.printStackTrace()
    } else {
        System.err.println(e.message ?: "Exit with error")
    }
    exitProcess(errorCode)
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == HOOK_LAUNCH_COMMAND) {
        InternalHooks.main(args.drop(1).toTypedArray())
    }

    BunchCli().main(args)
}

class BunchCli : CliktCommand(name = "bunch") {
    init {
        versionOption(getVersion())
        subcommands(
            CleanUpCommand(),
            CherryPickCommand(),
            SwitchCommand(),
            CheckCommand(),
            ReduceCommand(),
            StatCommand(),
            HooksCommand(),
            LogCommand(),
            RestoreCommand()
        )
    }

    private val verbose by option("--verbose").flag()

    private val config by findObject { mutableMapOf<String, Boolean>() }

    override fun run() {
        config["VERBOSE"] = verbose
    }
}

abstract class BunchSubCommand(
    val help: String = "",
    val epilog: String = "",
    val name: String? = null
): CliCommandWithNewLines(help, epilog, name)  {
    private val config by requireObject<Map<String, Boolean>>()

    fun process(commandAction: () -> Unit) {
        process(config.getValue("VERBOSE"), commandAction)
    }

    companion object {
        fun BunchSubCommand.repoPathOption() =
            option(
                "-C",
                help = "Directory with repository (parent directory for .git). Default is the current directory"
            )
                .path(exists = true, fileOkay = false)
                .default(Paths.get(".").toAbsolutePath().normalize())

        fun toOptionName(name: String) = "-$name"
    }
}

private fun getVersion() = ManifestReader.readAttribute("Bundle-Version") ?: "unknown"

abstract class CliCommandWithNewLines(help: String, epilog: String, name: String?): CliktCommand(help, epilog, name) {
    companion object {
        // clikt help formatter ignore all new lines
        // therefore added long enough string to forcibly add a new line
        private val lineSimulator = "a".repeat(80)
        private val lineSimulatorWithSpaces = " $lineSimulator "
    }

    override fun getFormattedUsage(): String {
        return replaceWithRealLines(super.getFormattedUsage())
    }

    override fun getFormattedHelp(): String {
        return replaceWithRealLines(super.getFormattedHelp())
    }

    private fun replaceWithRealLines(text: String): String {
        val helpLines = text.split(System.lineSeparator())
            .filterNot { it.contains(lineSimulator) }
        return helpLines.joinToString(System.lineSeparator())
    }

    protected fun String.replaceNewLinesWithForcedMarker(): String {
        return this.replace(System.lineSeparator(), lineSimulatorWithSpaces)
    }
}