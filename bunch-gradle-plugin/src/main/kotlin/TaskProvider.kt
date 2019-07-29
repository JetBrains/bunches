package org.jetbrains.bunches.gradle

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.restore.RESTORE_COMMIT_TITLE
import org.jetbrains.bunches.restore.SwitchSettings
import org.jetbrains.bunches.restore.doSwitch
import java.io.File

private fun getProperty(propertyName: String, p: PropertyProvider): String? {
    if (!p.hasProperty(propertyName)) {
        return null
    }

    return p.getProperty(propertyName)
}

@Suppress("unused")
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

private fun gradleExecute(f: () -> Unit) {
    try {
        f()
    } catch (e: Throwable) {
        throw GradleException("Bunch file exit with error", e)
    }
}

fun getTasks(p: PropertyProvider): List<TaskDescription> {
    val path = File(".").absolutePath

    val tasks = ArrayList<TaskDescription>()
    fun addTask(name: String,
                description: String = name,
                launch: (Task) -> Unit) {
        tasks.add(TaskDescription(name, description) { task ->  gradleExecute { launch(task) } })
    }

    // List available bunches and add task for each bunch
    readExtensionsFromFile(path)?.forEach { bunch ->
        addTask("switch-$bunch", "Switch to bunch $bunch") {
            doSwitch(SwitchSettings(
                repoPath = path,
                bunchPath = path,
                rule = bunch,
                commitTitle = RESTORE_COMMIT_TITLE,
                step = false,
                doCleanup = false
            ))
        }
    }

    return tasks
}

interface PropertyProvider {
    fun hasProperty(name: String): Boolean
    fun getProperty(name: String): String
}
