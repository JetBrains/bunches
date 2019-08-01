@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchStats")

package org.jetbrains.bunches.stats

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.bunches.check.isDeletedBunchFile
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.file.resultWithExit
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.parseGitIgnore
import org.jetbrains.bunches.git.shouldIgnoreDir
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

enum class Kind {
    DIR,
    LS
}

data class Settings(val path: String, val kind: Kind)

fun main(args: Array<String>) {
    StatCommand().main(args)
}

class StatCommand : BunchSubCommand(
    name = "stats",
    help = "Show statistics about bunch files in repository.",
    epilog =
        """
        Example:
        bunch stats C:/Projects/kotlin
        """.trimIndent()
) {
    val repoPath: Path by option("-C", help = PATH_DESCRIPTION)
        .path(exists = true, fileOkay = false)
        .default(Paths.get(".").toAbsolutePath().normalize())

    private val kind by option(
        help = """
            Kind of statistics. `dir` is used by default.
            `dir` shows information about single directory.
            `ls` gets an overview for all sub-directories.
        """.trimIndent())
        .switch(mapOf("--dir" to Kind.DIR, "--ls" to Kind.LS))
        .default(Kind.DIR)

    private val path: Path? by argument(
        name = "path",
        help = PATH_DESCRIPTION)
        .path(exists = true, fileOkay = false)
        .optional()

    override fun run() {
        val settings = Settings(
            path = (path ?: repoPath).toString(),
            kind = kind
        )

        process { doStats(settings) }
    }

    companion object {
        const val PATH_DESCRIPTION = "Subdirectory of the the repository where statistics should be evaluated."
    }
}

private fun File.parents(): Sequence<File> = generateSequence(this.absoluteFile) { it.parentFile }
private fun findGitRoot(dir: File) = dir.parents().firstOrNull { File(it, ".git").exists() }

fun doStats(settings: Settings) {
    when (settings.kind) {
        Kind.DIR -> doDirStats(settings.path)
        Kind.LS -> doLsStats(settings.path)
    }
}

fun doDirStats(path: String) {
    val (statsDir, gitRoot) = fetchStatsDirs(path)
    val extensions = readExtensionsFromFile(gitRoot).resultWithExit()

    val gitignoreParseResult = parseGitIgnore(gitRoot)

    val bunchFiles = statsDir
        .walkTopDown()
        .onEnter { dir -> !shouldIgnoreDir(dir, gitRoot, gitignoreParseResult) }
        .filter { child -> child.extension in extensions }
        .toList()

    val groupedFiles = bunchFiles.groupBy { it.extension }

    val affectedOriginFiles: Set<File> =
        bunchFiles.mapTo(HashSet()) { child -> File(child.parentFile, child.nameWithoutExtension) }

    printDirResults(statsDir, affectedOriginFiles, bunchFiles, extensions, groupedFiles)
}

fun doLsStats(path: String) {
    val (statsDir, gitRoot) = fetchStatsDirs(path)
    val extensions = readExtensionsFromFile(gitRoot).resultWithExit()

    val gitignoreParseResult = parseGitIgnore(gitRoot)

    var count = 0
    var total = 0

    statsDir
        .walkTopDown()
        .onEnter { dir ->
            val ignoreDir = shouldIgnoreDir(dir, gitRoot, gitignoreParseResult)
            if (ignoreDir) {
                val lsName = printLSDirName(dir, statsDir)
                if (lsName != null) {
                    println("%6s %s".format("ignore", lsName))
                    count = 0
                }
            }
            !ignoreDir
        }
        .onLeave { dir ->
            val lsName = printLSDirName(dir, statsDir)
            if (lsName != null) {
                println("%6d %s".format(count, lsName))
                count = 0
            }
        }
        .onEach { child ->
            if (child.extension in extensions) {
                count++
                total++
            }
        }.forEach { _ -> }

    println()
    println("Total: $total")
}

private fun printLSDirName(dir: File, statsDir: File) =
    when {
        dir.parentFile == statsDir -> {
            dir.name
        }
        dir == statsDir -> {
            "<root>"
        }
        else -> null
    }

private data class StatsDirs(val statsDir: File, val gitDir: File)

private fun fetchStatsDirs(path: String): StatsDirs {
    val statsDir = File(path).absoluteFile
    val gitRoot = findGitRoot(statsDir) ?: exitWithUsageError("Couldn't find git root directory")
    return StatsDirs(statsDir, gitRoot)
}

private fun printDirResults(
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

