package org.jetbrains.bunches.gradle

import org.gradle.api.*
import org.gradle.util.GradleVersion
import org.jetbrains.bunches.general.*

class BunchGradlePlugin : Plugin<Project> {
    override fun apply(project: Project?) {
        if (!isGradleVersionAtLeast(4, 9)) {
            throw GradleException("Unsupported gradle version. At least 4.9 is required.")
        }

        // By default the System.exit is invoked. We just throw exception in order to fail the build
        exitHook = {
            throw GradleException(it.first!!)
        }

        val tasks = getTasksAndDescriptions(ProjectPropertyProvider(project!!), { x -> main(x) })
        project.tasks.register("bunch") {
            it.doLast {
                println("In order to execute bunch tool run gradlew <task name>, where <task name> is one of:")
                for (task in tasks) {
                    if (task.value.second.isEmpty()) {
                        println(task.key)
                    } else {
                        println("${task.key} - ${task.value.second}")
                    }
                }
            }
        }
        for (task in tasks) {
            project.tasks.register(task.key) {
                it.doLast(task.value.first)
            }
        }
    }

    private fun isGradleVersionAtLeast(major: Int, minor: Int) = GradleVersion.current() >= GradleVersion.version("$major.$minor")

}

class ProjectPropertyProvider(private val project: Project) : PropertyProvider {
    override fun hasProperty(name: String): Boolean = project.hasProperty(name)

    override fun getProperty(name: String): String = getProperty(name)

}
