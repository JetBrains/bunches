@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("Bunch")

package nk.patchsets.git.general

import nk.patchsets.git.check.CHECK_DESCRIPTION
import nk.patchsets.git.check.check
import nk.patchsets.git.cleanup.CLEANUP_DESCRIPTION
import nk.patchsets.git.cleanup.cleanup
import nk.patchsets.git.cp.CHERRY_PICK_DESCRIPTION
import nk.patchsets.git.cp.cherryPick
import nk.patchsets.git.reduce.REDUCE_DESCRIPTION
import nk.patchsets.git.reduce.reduce
import nk.patchsets.git.restore.SWITCH_DESCRIPTION
import nk.patchsets.git.restore.restore

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("""
            Usage: switch|cp|cleanup|check|reduce <args>

            switch  - $SWITCH_DESCRIPTION
            cp      - $CHERRY_PICK_DESCRIPTION
            cleanup - $CLEANUP_DESCRIPTION
            check   - $CHECK_DESCRIPTION
            reduce  - $REDUCE_DESCRIPTION

            Example:
            bunch switch . as32
            """.trimIndent())

        return
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
        else -> {
            System.err.println("Unknown command: $command")
            return
        }
    }
}