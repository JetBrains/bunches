@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package org.jetbrains.bunches.general

import org.jetbrains.bunches.ManifestReader
import org.jetbrains.bunches.check.CHECK_DESCRIPTION
import org.jetbrains.bunches.check.check
import org.jetbrains.bunches.cleanup.CLEANUP_DESCRIPTION
import org.jetbrains.bunches.cleanup.cleanup
import org.jetbrains.bunches.cp.CHERRY_PICK_DESCRIPTION
import org.jetbrains.bunches.cp.cherryPick
import org.jetbrains.bunches.reduce.REDUCE_DESCRIPTION
import org.jetbrains.bunches.reduce.reduce
import org.jetbrains.bunches.restore.SWITCH_DESCRIPTION
import org.jetbrains.bunches.restore.restore
import org.jetbrains.bunches.stats.STATS_DESCRIPTION
import org.jetbrains.bunches.stats.stats
import kotlin.system.exitProcess

fun exitWithUsageError(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

fun exitWithError(message: String? = null): Nothing {
    if (message != null) {
        System.err.println(message)
    }
    exitProcess(2)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        exitWithUsageError("""
            Usage: switch|cp|cleanup|check|reduce|stats|--version <args>

            switch  - $SWITCH_DESCRIPTION
            cp      - $CHERRY_PICK_DESCRIPTION
            cleanup - $CLEANUP_DESCRIPTION
            check   - $CHECK_DESCRIPTION
            reduce  - $REDUCE_DESCRIPTION
            stats   - $STATS_DESCRIPTION

            Example:
            bunch switch . as32
            """.trimIndent())
    }

    val command = args[0]
    val commandArgs = args.toList().drop(1).toTypedArray()

    when (command) {
        "cp" -> cherryPick(commandArgs)
        "restore" -> restore(commandArgs)
        "switch" -> restore(commandArgs)
        "cleanup" -> cleanup(commandArgs)
        "check" -> check(commandArgs)
        "reduce" -> reduce(commandArgs)
        "stats" -> stats(commandArgs)

        "--version" -> printVersion()

        else -> {
            exitWithUsageError("Unknown command: $command")
        }
    }
}

private fun printVersion() {
    println(ManifestReader.readAttribute("Bundle-Version") ?: "unknown")
}
