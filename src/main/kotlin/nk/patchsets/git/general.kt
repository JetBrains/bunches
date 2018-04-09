@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package nk.patchsets.git.general

import nk.patchsets.git.cleanup.cleanup
import nk.patchsets.git.cp.cherryPick
import nk.patchsets.git.restore.restore

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("""
            Usage: cp|restore <args>

            cp - for commits cherry pick
            restore - for restore branch state
            cleanup - removes all files with prefixes found in .bunch file

            Example:
            <program> restore . 173_as31_as32
            """.trimIndent())

        return
    }

    val command = args[0]
    val commandArgs = args.toList().drop(1).toTypedArray()

    when (command) {
        "cp" -> cherryPick(commandArgs)
        "restore" -> restore(commandArgs)
        "cleanup" -> cleanup(commandArgs)
        else -> {
            System.err.println("Unknown command: $command")
            return
        }
    }
}