package org.jetbrains.bunches.gradle

import org.gradle.api.Task
import org.jetbrains.bunches.check.CHECK_DESCRIPTION
import org.jetbrains.bunches.check.CH_SINCE
import org.jetbrains.bunches.check.CH_UNTIL
import org.jetbrains.bunches.cleanup.CLEANUP_DESCRIPTION
import org.jetbrains.bunches.cp.*
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.reduce.REDUCE_DESCRIPTION
import org.jetbrains.bunches.reduce.RE_A_
import org.jetbrains.bunches.restore.SWITCH_DESCRIPTION
import org.jetbrains.bunches.restore.SW_BRANCHES_
import org.jetbrains.bunches.restore.SW_COMMIT_T
import org.jetbrains.bunches.stats.STATS_DESCRIPTION
import org.jetbrains.bunches.stats.STATS_DIR
import org.jetbrains.bunches.stats.STATS_LS
import java.io.File

private fun getProperty(propertyName: String, p: PropertyProvider): String? {
    if (!p.hasProperty(propertyName)) {
        return null
    }

    return p.getProperty(propertyName)
}

private fun getMandatoryProperty(propertyName: String, p: PropertyProvider): String {
    return getProperty(propertyName, p)
            ?: exitWithUsageError("Could not find required parameter $propertyName. Add parameter '-P$propertyName'")
}

class TaskDescription(
        val name: String,
        val description: String,
        val launch: (Task) -> Unit) {
    init {
        require(description.isNotEmpty())
    }
}

fun getTasks(
        p: PropertyProvider,
        launcher: (Array<String>) -> Unit
): List<TaskDescription> {
    val path = File(".").absolutePath

    val tasks = ArrayList<TaskDescription>()
    fun addTask(name: String,
                description: String = name,
                launch: (Task) -> Unit) {
        tasks.add(TaskDescription(name, description, launch))
    }

    addTask("bunch-stats", STATS_DESCRIPTION) {
        arrayOf("stats", path)
    }

    addTask("bunch-stats-dir", "$STATS_DESCRIPTION ($STATS_DIR)") {
        launcher(arrayOf("stats", "dir", path))
    }

    addTask("bunch-stats-ls", "$STATS_DESCRIPTION ($STATS_LS)") {
        launcher(arrayOf("stats", "ls", path))
    }

    addTask("bunch-cp", CHERRY_PICK_DESCRIPTION) {
        cherryPick(arrayOf(
                path,
                getMandatoryProperty(CP_SINCE, p),
                getMandatoryProperty(CP_UNTIL, p),
                getMandatoryProperty(CP_SX, p)
        ))
    }

    addTask("bunch-switch", SWITCH_DESCRIPTION) {
        // TODO support clean option
        // TODO support step option
        val commitTitle = getProperty(SW_COMMIT_T, p)
        var params = arrayOf("switch", path, getMandatoryProperty(SW_BRANCHES_, p))
        if (commitTitle != null) {
            params += commitTitle
        }
        launcher(params)
    }

    // List available bunches and add task for each bunch
    readExtensionsFromFile(path)?.forEach { bunch ->
        addTask("switch-$bunch", "Switch to bunch $bunch") {
            launcher(arrayOf("switch", path, bunch))
        }
    }

    // TODO support args
    addTask("bunch-cleanup", CLEANUP_DESCRIPTION) {
        launcher(arrayOf("cleanup", path))
    }

    // TODO support more args
    addTask("bunch-check", CHECK_DESCRIPTION) {
        org.jetbrains.bunches.check.check(arrayOf(
                path,
                getMandatoryProperty(CH_SINCE, p),
                getMandatoryProperty(CH_UNTIL, p)
        ))
    }

    // TODO support more args
    addTask("reduce", REDUCE_DESCRIPTION) {
        var params = arrayOf("reduce", path)
        val action = getProperty(RE_A_, p)
        if (action != null) {
            params += action
        }
        launcher(params)
    }

    return tasks
}

interface PropertyProvider {
    fun hasProperty(name: String): Boolean
    fun getProperty(name: String): String
}
