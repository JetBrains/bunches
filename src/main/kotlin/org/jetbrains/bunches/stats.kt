@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchStats")
package org.jetbrains.bunches.stats

import org.jetbrains.bunches.check.isDeletedBunchFile
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.restore.isGitDir
import org.jetbrains.bunches.restore.isGradleBuildDir
import org.jetbrains.bunches.restore.isGradleDir
import org.jetbrains.bunches.restore.isOutDir
import java.io.File

data class Settings(val path: String)

fun main(args: Array<String>) {
    stats(args)
}

const val STATS_DESCRIPTION = "Show statistics about bunch files in repository."

fun stats(args: Array<String>) {
    if (args.size !in 1..1) {
        exitWithUsageError("""
            Usage: <git-path>

            $STATS_DESCRIPTION

            <git-path>  - Directory to process. Should be within .git repository as repository root will be used
                          to spot bunches file with extensions.

            Example:
            bunch stats C:/Projects/kotlin
            """.trimIndent())
    }

    val settings = Settings(args[0])

    doStats(settings)
}

private fun File.parents(): Sequence<File> = generateSequence(this.absoluteFile) { it.parentFile }
private fun findGitRoot(dir: File) = dir.parents().firstOrNull { File(it, ".git").exists() }

fun doStats(settings: Settings) {
    val statsDir = File(settings.path)
    when {
        !statsDir.exists() -> exitWithUsageError("$statsDir directory doesn't exist")
        !statsDir.isDirectory -> exitWithUsageError("$statsDir is not directory")
    }

    val gitRoot = findGitRoot(statsDir) ?: exitWithUsageError("Couldn't find git root directory")
    val extensions = readExtensionsFromFile(gitRoot) ?: exitWithError()

    val bunchFiles = statsDir
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isOutDir(dir, gitRoot) || isGradleBuildDir(dir) || isGradleDir(dir)) }
            .filter { child -> child.extension in extensions }
            .toList()

    val groupedFiles = bunchFiles.groupBy { it.extension }

    val affectedOriginFiles: Set<File> =
            bunchFiles.mapTo(HashSet()) { child -> File(child.parentFile, child.nameWithoutExtension) }

    printResults(statsDir, affectedOriginFiles, bunchFiles, extensions, groupedFiles)
}

private fun printResults(
    statsDir: File,
    affectedOriginFiles: Set<File>,
    bunchFiles: List<File>,
    extensions: List<String>,
    groupedFiles: Map<String, List<File>>
) {
    val tablePatternTitle = "%-6s|%6s|%6s|%6s"
    val tablePatternRow = "%-6s|%6d|%6d|%6d"

    println("Directory: ${statsDir.absoluteFile}")
    println("Number of affected origin files: ${affectedOriginFiles.size}")
    println()

    println(tablePatternTitle.format("Ext", "exists", "del", "total"))

    val all = bunchFiles.size
    val allDeleted = bunchFiles.count { isDeletedBunchFile(it) }
    println(tablePatternRow.format("all", all - allDeleted, allDeleted, all))

    for (extension in extensions) {
        val currentExtensionFiles = groupedFiles[extension] ?: listOf()
        val deleted = currentExtensionFiles.count { isDeletedBunchFile(it) }
        val total = currentExtensionFiles.size

        println(tablePatternRow.format(extension, total - deleted, deleted, total))
    }
}

