@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package nk.patchsets.git.general

import nk.patchsets.git.check.check
import nk.patchsets.git.cleanup.cleanup
import nk.patchsets.git.cp.cherryPick
import nk.patchsets.git.reduce.reduce
import nk.patchsets.git.restore.restore

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("""
            Usage: cp|restore|cleanup|check|reduce <args>

            cp - for commits cherry pick
            restore - for restore branch state
            cleanup - removes all files with prefixes found in .bunch file
            check - check commits for forgotten bunch files updates
            reduce - scan repository for unneeded files

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
        "check" -> check(commandArgs)
        "reduce" -> reduce(commandArgs)
        else -> {
            System.err.println("Unknown command: $command")
            return
        }
    }
}