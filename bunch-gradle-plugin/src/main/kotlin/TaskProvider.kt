package org.jetbrains.bunches.gradle

import org.gradle.api.Task
import org.jetbrains.bunches.check.CHECK_DESCRIPTION
import org.jetbrains.bunches.check.CHECK_SINCE_REF
import org.jetbrains.bunches.check.CHECK_UNTIL_REF
import org.jetbrains.bunches.cleanup.CLEANUP_DESCRIPTION
import org.jetbrains.bunches.cp.*
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.reduce.REDUCE_ACTION
import org.jetbrains.bunches.reduce.REDUCE_COMMIT_MESSAGE
import org.jetbrains.bunches.reduce.REDUCE_DESCRIPTION
import org.jetbrains.bunches.restore.*
import org.jetbrains.bunches.stats.STATS_DESCRIPTION
import org.jetbrains.bunches.stats.STATS_DIR
import org.jetbrains.bunches.stats.STATS_LS
import java.io.File
import java.util.*


private fun getProperty(helpMessage: String, p: PropertyProvider): String? {
    return try {
        getMandatoryProperty(helpMessage, p)
    } catch (_: Exception) {
        null
    }
}

private fun getMandatoryProperty(helpMessage: String, p: PropertyProvider): String {
    if (!helpMessage.matches(Regex("<.+>.+"))) {
        exitWithError("Internal error: help message [$helpMessage] has wrong format.")
    }
    val propertyName = helpMessage.substring(1, helpMessage.indexOf(">"))
    if (!p.hasProperty(propertyName)) {
        exitWithUsageError("Could not find required parameter $propertyName. Add parameter '-P${helpMessage.replace("<", "").replace(">", "")}'")
    }
    return p.getProperty(propertyName)
}

fun getTasksAndDescriptions(p: PropertyProvider, launcher: (Array<String>) -> Unit): Map<String, Pair<(Task) -> (Unit), String>> {
    val tasks = TreeMap<String, Pair<(Task) -> (Unit), String>>()
    val path = File(".").absolutePath
    tasks["bunch-stats"] = Pair({ _ -> launcher(arrayOf("stats", path)) }, STATS_DESCRIPTION)
    tasks["bunch-stats-dir"] = Pair({ _ -> launcher(arrayOf("stats", "dir", path)) }, "$STATS_DESCRIPTION ($STATS_DIR)")
    tasks["bunch-stats-ls"] = Pair({ _ -> launcher(arrayOf("stats", "ls", path)) }, "$STATS_DESCRIPTION ($STATS_LS)")

    // cp cherryPick(commandArgs)
    tasks["bunch-cp"] = Pair({ _ -> cherryPick(arrayOf(path, getMandatoryProperty(CHERRY_SINCE_DESCRIPTION, p), getMandatoryProperty(CHERRY_UNTIL_DESCRIPTION, p), getMandatoryProperty(CHERRY_SUFFIX_DESCRIPTION, p))) }, CHERRY_PICK_DESCRIPTION)

    // "restore" -> restore(commandArgs)
    tasks["bunch-switch"] = Pair({ _ ->
        // TODO support clean option
        // TODO support step option
        val commitTitle = getProperty(SWITCH_COMMIT_TITLE, p)
        var params = arrayOf("restore", path, getMandatoryProperty(SWITCH_BRANCHES_RULE, p))
        if (commitTitle != null) {
            params += commitTitle
        }
        launcher(params)
    }, SWITCH_DESCRIPTION)

    //List available bunches and add task for each bunch
    val bunchesFile = File(path, ".bunch")
    if (bunchesFile.exists()) {
        bunchesFile.forEachLine {
            tasks["switch-$it"] = Pair({ _ -> launcher(arrayOf("restore", path, it)) }, "Switch to bunch $it")
        }
    }

    // "cleanup" -> cleanup(commandArgs)
    //TODO support args
    tasks["bunch-cleanup"] = Pair({ _ -> launcher(arrayOf("cleanup", path)) }, CLEANUP_DESCRIPTION)

    // "check" -> check(commandArgs)
    //TODO support more args
    tasks["bunch-check"] = Pair({ _ -> org.jetbrains.bunches.check.check(arrayOf(path, getMandatoryProperty(CHECK_SINCE_REF, p), getMandatoryProperty(CHECK_UNTIL_REF, p))) }, CHECK_DESCRIPTION)

    // "reduce" -> reduce(commandArgs)
    tasks["reduce"] = Pair({ _ ->
        var params = arrayOf("reduce", path)
        val action = getProperty(REDUCE_ACTION, p)
        val message = getProperty(REDUCE_COMMIT_MESSAGE, p)
        if (action != null) {
            params += action
            if (message != null) {
                params += message
            }
        }
        launcher(params)
    }, REDUCE_DESCRIPTION)
    return tasks
}

interface PropertyProvider {

    fun hasProperty(name: String): Boolean

    fun getProperty(name: String): String
}
