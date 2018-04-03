@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package nk.patchsets.git.general

import nk.patchsets.git.cp.cherryPick
import nk.patchsets.git.restore.restore

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("""
            Usage: cp|restore <args>

            cp - for commits cherry pick
            restore - for restore branch state

            Example:
            <program> restore C:/Projects/kotlin 173->172->171->as30
            """.trimIndent())

        return
    }

    val command = args[0]
    val commandArgs = args.sliceArray(1..args.size)

    when (command) {
        "cp" -> cherryPick(commandArgs)
        "restore" -> restore(commandArgs)
        else -> {
            System.err.println("Unknown command: $command")
            return
        }
    }
}