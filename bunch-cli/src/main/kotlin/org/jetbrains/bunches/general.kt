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
import org.jetbrains.bunches.restore.SwitchCommand
import org.jetbrains.bunches.stats.StatCommand
import java.nio.file.Paths
import kotlin.system.exitProcess

fun exitWithUsageError(message: String): Nothing {
    throw BunchParametersException(message)
}

fun exitWithError(message: String? = null): Nothing {
    throw BunchException(message)
}

inline fun process(args: Array<String>, f: () -> Unit) {
    process(args.contains("--verbose"), f)
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
            HooksCommand()
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
) : CliktCommand(help, epilog, name) {
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

