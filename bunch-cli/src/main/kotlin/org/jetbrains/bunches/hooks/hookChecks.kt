package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.BunchParametersException
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.process

object InternalHooks {
    @JvmStatic
    fun main(args: Array<String>) {
        process(args) {
            doMain(args)
        }
    }
}

fun doMain(args: Array<String>) {
    if (args.isEmpty()) {
        exitWithError("No args")
    }

    val command = args.first()
    val commandArgs = args.toList().drop(1).toTypedArray()
    when (command) {
        BUNCH_PRE_COMMIT_CHECK_COMMAND -> precommitHook(commandArgs)
        BUNCH_PRE_PUSH_CHECK_COMMAND -> checkBeforePush(commandArgs)
        BUNCH_PRE_REBASE_CHECK_COMMAND -> checkPreRebase(commandArgs)

        else -> throw BunchParametersException("Unknown command: $command")
    }
}