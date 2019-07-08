@file:JvmName("BunchReduce")

package org.jetbrains.bunches.reduce

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.bunches.file.readUpdatePairsFromFile
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.general.partial
import org.jetbrains.bunches.general.process
import org.jetbrains.bunches.git.*
import org.jetbrains.bunches.restore.toBunchFile
import java.io.File
import java.nio.file.Paths

enum class ReduceAction {
    PRINT,
    DELETE,
    COMMIT
}

data class Settings(val repoPath: String, val bunchPath: String, val action: ReduceAction, val commitMessage: String)

fun main(args: Array<String>) {
    ReduceCommand().main(args)
}

const val REDUCE_DESCRIPTION = "Check repository for unneeded files with the same content."
const val DEFAULT_REDUCE_COMMIT_TITLE = "~~~~ reduce ~~~~"
val REDUCE_EXAMPLE =
    """
    Example:
    bunch reduce C:/Projects/kotlin
    """.trimIndent()

val ACTION_HELP =
    """
    Action that should be applied to redundant files. "commit" is used by default.
        print - print the list in console
        delete - only delete files
        commit - delete files and commit them
    """.trimIndent()

val ACTIONS = mapOf("--print" to ReduceAction.PRINT, "--delete" to ReduceAction.DELETE, "--commit" to ReduceAction.COMMIT)

class ReduceCommand : CliktCommand(name = "reduce", help = REDUCE_DESCRIPTION, epilog = REDUCE_EXAMPLE) {
    val config by requireObject<Map<String, Boolean>>()
    val repoPath by option("-C", help = "Directory with repository (parent directory for .git).")
        .path(exists = true, fileOkay = false)
        .default(Paths.get(".").toAbsolutePath().normalize())
    val action by option(help = ACTION_HELP).switch(ACTIONS).default(ReduceAction.COMMIT)
    val commitTitle by option("-m", help = "Commit message for \"commit\" action. $DEFAULT_REDUCE_COMMIT_TITLE is used by default.")
        .default(DEFAULT_REDUCE_COMMIT_TITLE)
    override fun run() {
        val settings = Settings(
            repoPath = repoPath.toString(),
            bunchPath = repoPath.toString(),
            action = action,
            commitMessage = commitTitle
        )
        process(config.getValue("VERBOSE"), ::doReduce.partial(settings))
    }
}

private data class UpdatePair(val from: String, val to: String)

fun getReducibleFiles(repoPath: String, bunchPath: String) : ArrayList<File> {
    val root = File(repoPath)
    if (!isGitRoot(root)) {
        exitWithError("Repository directory with branch is expected")
    }

	val gitignoreParseResult = parseGitIgnore(root)
    val (base, rules) = readUpdatePairsFromFile(bunchPath) ?: exitWithError()

    if (rules.isEmpty()) {
        exitWithError()
    }

    val extensions = rules.map { it.last() }.toSet() + base

    val filesWithDonorExtensions = root
        .walkTopDown()
        .onEnter { dir -> !shouldIgnoreDir(dir, root, gitignoreParseResult) }
        .filter { child -> child.extension in extensions }
        .toList()

    val affectedOriginFiles: Set<File> =
        filesWithDonorExtensions.mapTo(HashSet(), { child -> File(child.parentFile, child.nameWithoutExtension) })

    val reduceFiles = ArrayList<File>()
    for (affectedOriginFile in affectedOriginFiles) {
        val contentMap: Map<String, String?> = extensions.map { extension ->
            val file = if (extension == base) affectedOriginFile else affectedOriginFile.toBunchFile(extension)
            val content: String? =
                if (file.exists()) file.readText().replace(Regex("\\s*", RegexOption.MULTILINE), "") else null
            extension to content
        }.toMap()

        val checkedPairs = HashSet<UpdatePair>()
        for (rule in rules) {
            var fromExtension = rule.first()

            for (toExtension in rule.drop(1)) {
                val fromContent = contentMap[fromExtension] ?: continue
                val toContent = contentMap[toExtension] ?: continue

                if (checkedPairs.add(UpdatePair(fromExtension, toExtension))) {
                    if (toContent == fromContent) {
                        reduceFiles.add(affectedOriginFile.toBunchFile(toExtension))
                    }
                }

                fromExtension = toExtension
            }
        }
    }
    return reduceFiles
}

fun doReduce(settings: Settings) {
    val files = getReducibleFiles(settings.repoPath, settings.bunchPath)

    if (settings.action == ReduceAction.PRINT) {
        files.sort()
        for (file in files) {
            println(file)
        }
    }

    assert(settings.action == ReduceAction.DELETE || settings.action == ReduceAction.COMMIT)

    val changedFiles = ArrayList<FileChange>()
    for (file in files) {
        file.delete()
        changedFiles.add(FileChange(ChangeType.REMOVE, file))
    }

    if (settings.action == ReduceAction.COMMIT) {
        commitChanges(settings.repoPath, changedFiles, settings.commitMessage)
    }
}



