@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchApply")

package org.jetbrains.bunches.apply

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.*
import java.io.File

data class Settings(
    val gitPath: String,
    val branch: String,
    val untilHash: String,
    val suffix: String
)

fun main(args: Array<String>) {
    apply(args)
}

const val APPLY_DESCRIPTION =
    "Apply commit to the current branch selecting specific changes for desired bunch (un-bunch commit)."

const val AP_SINCE = "since-ref"
const val AP_UNTIL = "until-ref"
const val AP_SX = "suffix"

fun apply(args: Array<String>) {
    if (args.size != 4) {
        exitWithUsageError(
            """
            Usage: <git-path> <since-ref> <until-ref> <suffix>

            $APPLY_DESCRIPTION

            <git-path>  - Directory with repository (parent directory for .git folder).
            <$AP_SINCE> - Reference to most recent commit that should be ported.
            <$AP_UNTIL> - Parent of the last commit that should be ported (hash of "==== switch 182 ====" for instance).
            <$AP_SX>    - Suffix for ported files.

            Example:
            bunch apply C:/Projects/kotlin my-feature-branch c1125219e44aed60b8ba033ddae5b8f5c748052e 182
            """.trimIndent()
        )
    }

    val settings = Settings(args[0], args[1], args[2], args[3])
    with(settings) {
        val commits = readCommits(gitPath, branch, untilHash)
        val bunchExtensions = readExtensionsFromFile(settings.gitPath)
            ?: exitWithError("Can't get extension from .bunch file")
        val skipExtensions = bunchExtensions - suffix

        for (commit in commits.reversed()) {
            println("Apply: ${commit.hash} ${commit.title}")

            val baseAndTargetActions = commit.fileActions
                .filter { it.newPath?.pathExtension() !in skipExtensions }
                .groupBy { it.newPath?.pathExtension() == suffix }

            val baseActions = baseAndTargetActions[false] ?: emptyList()
            val targetActions = baseAndTargetActions[true] ?: emptyList()

            val modifiedTargetActions = targetActions
                .filter { it.newPath?.pathExtension() == suffix }
                .map {
                    val modifiedChangeType =
                        if (it.changeType != DiffEntry.ChangeType.DELETE && it.content.trim().isEmpty()) {
                            DiffEntry.ChangeType.DELETE
                        } else {
                            it.changeType
                        }

                    FileAction(modifiedChangeType, it.newPath?.removeSuffix(".$suffix"), it.content)
                }

            val targetPaths = modifiedTargetActions.mapTo(HashSet()) { it.newPath }.filterNotNull()

            val actionsToProcess = modifiedTargetActions + baseActions.filter { it.newPath !in targetPaths }

            val changedFiles = HashSet<FileChange>()
            for (fileAction in actionsToProcess) {
                val path = fileAction.newPath ?: continue
                val file = File(gitPath, path)

                when (fileAction.changeType) {
                    DiffEntry.ChangeType.ADD -> {
                        file.parentFile.mkdirs()
                        file.writeText(fileAction.content)
                        changedFiles.add(FileChange(ChangeType.ADD, file))
                    }

                    DiffEntry.ChangeType.MODIFY -> {
                        file.writeText(fileAction.content)
                        changedFiles.add(FileChange(ChangeType.MODIFY, file))
                    }

                    DiffEntry.ChangeType.DELETE -> {
                        file.delete()
                        changedFiles.add(FileChange(ChangeType.REMOVE, file))
                    }

                    DiffEntry.ChangeType.RENAME -> TODO()
                    DiffEntry.ChangeType.COPY -> TODO()
                }
            }

            commitChanges(
                settings.gitPath,
                changedFiles,
                commit.message ?: commit.title ?: "Apply ${commit.hash}"
            )
        }
    }
}

private fun String.pathExtension() = this.substringAfterLast(".")