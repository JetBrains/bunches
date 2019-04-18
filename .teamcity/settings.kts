import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.ui.create
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2018.2"

project {
    description = "Bunch Tool"

    vcsRoot(BunchTool)

    buildTypes.addAll(listOf(
            Composite,
            Main
    ))

    cleanup {
        preventDependencyCleanup = false
    }
}

object Composite : BuildType({
    id("Composite")
    name = "Composite"

    type = BuildTypeSettings.Type.COMPOSITE

    vcs {
        showDependenciesChanges = true
    }

    dependencies {
        dependency(RelativeId("Main")) {
            snapshot {
            }

            artifacts {
                artifactRules = "*.zip"
            }
        }
    }
})

object Main : BuildType({
    name = "Main"

    vcs {
        root(BunchTool)
    }

    steps {
        gradle {
            name = "Clean"
            tasks = "clean"
        }

        gradle {
            name = "Build Cli"
            tasks = ":bunch-cli:build"
        }

        gradle {
            name = "Wait"
            tasks = "wait"
        }

        gradle {
            name = "Build Idea Plugin"
            tasks = ":idea-plugin:build"
        }

        gradle {
            name = "Build All"
            tasks = "build"
            buildFile = ""
            gradleWrapperPath = ""
        }
    }

    artifactRules = """
        bunch-cli/build/distributions/*.zip
        idea-plugin/build/distributions/*.zip
    """.trimIndent()
})

object BunchTool : GitVcsRoot({
    name = "Bunch Tool"
    url = "file:///C:/Projects/bunches"
})