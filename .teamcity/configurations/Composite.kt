package configurations

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.v2018_2.RelativeId

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
