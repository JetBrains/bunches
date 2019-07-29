package org.jetbrains.bunches.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class BunchGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        checkGradleVersion()

        val tasks = getTasks(ProjectPropertyProvider(project))

        @Suppress("UnstableApiUsage")
        project.tasks.register("bunch") {
            it.doLast {
                println("In order to execute bunch tool run gradlew <task name>, where <task name> is one of:")
                for (task in tasks) {
                    println("${task.name} - ${task.description}")
                }
            }
        }

        for (task in tasks) {
            @Suppress("UnstableApiUsage")
            project.tasks.register(task.name) {
                it.doLast(task.launch)
            }
        }
    }

    private fun checkGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version("4.9")) {
            throw GradleException("Unsupported gradle version. At least 4.9 is required.")
        }
    }
}

class ProjectPropertyProvider(private val project: Project) : PropertyProvider {
    override fun hasProperty(name: String): Boolean = project.hasProperty(name)

    override fun getProperty(name: String): String = getProperty(name)
}
