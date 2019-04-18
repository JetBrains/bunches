package configurations.steps

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.ant

fun BuildSteps.publishArtifacts(stepName: String, vararg artifactPath: String) {
    ant {
        name = stepName
        mode = antScript {
            content = """
                <project name="Publish artifacts" default="publish">
                <target name="publish">
                ${ artifactPath.joinToString("\n") { """<echo message="##teamcity[publishArtifacts '$it']" />""" }}
                </target>
                </project>
            """.trimIndent()
        }
        antArguments = "-v"
    }
}