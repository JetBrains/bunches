@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package org.jetbrains.bunches.general

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.BunchParametersException
import org.jetbrains.bunches.ManifestReader
import org.jetbrains.bunches.cleanup.CleanUpCommand
import org.jetbrains.bunches.cp.CherryPickCommand
import org.jetbrains.bunches.restore.SwitchCommand
import kotlin.system.exitProcess

fun exitWithUsageError(message: String): Nothing {
    throw BunchParametersException(message)
}

fun exitWithError(message: String? = null): Nothing {
    throw BunchException(message)
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

val commands = listOf(CleanUpCommand(), CherryPickCommand(), SwitchCommand())

fun <A, B> Function1<A, B>.partial(a: A): () -> B {
    return {invoke(a)}
}

fun main(args: Array<String>) {
    BunchCli().subcommands(commands).main(args)
}

class BunchCli : CliktCommand(name = "bunch") {
    init {
        versionOption(getVersion())
    }
    val verbose by option("--verbose").flag()
    val config by findObject { mutableMapOf<String, Boolean>() }
    override fun run() {
        config["VERBOSE"] = verbose
    }
}

//fun doMain(args: Array<String>) {
//    if (args.isEmpty()) {
//        exitWithUsageError(
//            """
//            Usage: switch|cp|cleanup|check|reduce|stats|--version <args>
//
//            switch  - $SWITCH_DESCRIPTION
//            cp      - $CHERRY_PICK_DESCRIPTION
//            cleanup - $CLEANUP_DESCRIPTION
//            check   - $CHECK_DESCRIPTION
//            reduce  - $REDUCE_DESCRIPTION
//            stats   - $STATS_DESCRIPTION
//
//            Example:
//            bunch switch . as32
//            """.trimIndent()
//        )
//    }
//
//    val command = args[0]
//    val commandArgs = args.toList().drop(1).toTypedArray()
//    when (command) {
//        "cp" -> cherryPick(commandArgs)
//        "restore" -> restore(commandArgs)
//        "switch" -> restore(commandArgs)
//        "cleanup" -> cleanup(commandArgs)
//        "check" -> check(commandArgs)
//        "reduce" -> reduce(commandArgs)
//        "stats" -> stats(commandArgs)
//        "installHook" -> installHook(commandArgs)
//        "uninstallHook" -> uninstallHook(commandArgs)
//        "precommit" -> precommitHook(commandArgs)
//
//        "--version" -> printVersion()
//
//        else -> throw BunchParametersException("Unknown command: $command")
//    }
//}

private fun getVersion() = ManifestReader.readAttribute("Bundle-Version") ?: "unknown"

