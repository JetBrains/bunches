import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.project
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.v2018_2.version

version = "2018.2"

project {
    description = "Bunch Tool"

    vcsRoot(BunchTool)

    buildType(Main)

    cleanup {
        preventDependencyCleanup = false
    }
}

object Main : BuildType({
    name = "Main"
    buildNumberPattern = "1.0.%build.counter%"

    vcs {
        root(BunchTool)
    }

    steps {
        gradle {
            name = "Build All"
            tasks = "clean build -Pversion=%build.number%"
            buildFile = ""
            gradleWrapperPath = ""
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:<default>
                +:rr/*
            """.trimIndent()
        }
    }

    artifactRules = """
        bunch-cli/build/distributions/*.zip
        idea-plugin/build/distributions/*.zip
    """.trimIndent()
})

object BunchTool : GitVcsRoot({
    name = "Bunch Tool"
    url = "https://github.com/JetBrains/bunches.git"
})