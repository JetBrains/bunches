package configurations

import configurations.steps.publishArtifacts
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.ui.add
import vcsRoots.BunchToolVcsRoot

object Main : BuildType({
    name = "Main"

    val bunchCLIArtifactRule = "bunch-cli/build/distributions/*.zip"
    val bunchIdeaPluginArtifactRule = "idea-plugin/build/distributions/*.zip"

    vcs {
        root(BunchToolVcsRoot)
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

        publishArtifacts(
                stepName = "Publish Cli",
                artifactPath = bunchCLIArtifactRule
        )

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

    features {
        add {
            perfmon {
            }
        }
    }

    artifactRules = listOf(bunchCLIArtifactRule, bunchIdeaPluginArtifactRule).joinToString("\n")
})
