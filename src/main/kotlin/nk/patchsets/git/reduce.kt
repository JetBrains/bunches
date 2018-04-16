package nk.patchsets.git.reduce

import nk.patchsets.git.file.readUpdatePairsFromFile
import nk.patchsets.git.restore.isGitDir
import nk.patchsets.git.restore.isGradleBuildDir
import nk.patchsets.git.restore.isGradleDir
import nk.patchsets.git.restore.toBunchFile
import java.io.File

data class Settings(val repoPath: String)

fun main(args: Array<String>) {
    reduce(args)
}

const val REDUCE_DESCRIPTION = "Check repository for unneeded files with the same content."

fun reduce(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("""
            Usage: <git-path>

            $REDUCE_DESCRIPTION

            <git-path> - Directory with repository (parent directory for .git)

            Example:
            bunch reduce C:/Projects/kotlin
            """.trimIndent())

        return
    }

    val settings = Settings(
            repoPath = args[0]
    )

    doReduce(settings)
}

private data class UpdatePair(val from: String, val to: String)

fun doReduce(settings: Settings) {
    val root = File(settings.repoPath)
    if (!root.exists() || !root.isDirectory) {
        System.err.println("Repository directory with branch is expected")
    }

    val (base, rules) = readUpdatePairsFromFile(settings.repoPath) ?: return
    if (rules.isEmpty()) {
        return
    }

    val extensions = rules.map { it.last() }.toSet() + base

    val filesWithDonorExtensions = root
            .walkTopDown()
            .onEnter { dir -> !(isGitDir(dir) || isGradleBuildDir(dir) || isGradleDir(dir)) }
            .filter { child -> child.extension in extensions }
            .toList()

    val affectedOriginFiles: Set<File> =
            filesWithDonorExtensions.mapTo(HashSet(), { child -> File(child.parentFile, child.nameWithoutExtension) })

    for (affectedOriginFile in affectedOriginFiles) {
        val contentMap: Map<String, String?> = extensions.map { extension ->
            val file = if (extension == base) affectedOriginFile else affectedOriginFile.toBunchFile(extension)
            val content: String? = if (file.exists()) file.readText().replace(Regex("\\s*", RegexOption.MULTILINE), "") else null
            extension to content
        }.toMap()

        val checkedPairs = HashSet<UpdatePair>()
        for (rule in rules) {
            var fromExtension = rule.first()

            for (toExtension in rule.drop(1)) {
                if (!checkedPairs.add(UpdatePair(fromExtension, toExtension))) continue

                val fromContent = contentMap[fromExtension] ?: continue
                val toContent = contentMap[toExtension] ?: continue

                if (toContent == fromContent) {
                    println(affectedOriginFile.toBunchFile(toExtension))
                }

                fromExtension = toExtension
            }
        }
    }
}



