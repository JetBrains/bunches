@file:Suppress("PackageDirectoryMismatch")

package nk.patchsets.git.restore

import java.io.File

val rule = "173->172->171->as30"

fun main(args: Array<String>) {
    val suffixes = rule.split("->")

    val originBranchExtension = suffixes.first()
    val donorExtensionsPrioritized = suffixes.subList(1, suffixes.size).reversed().toSet()

    val root = File(".")

    if (!root.exists() || !root.isDirectory) {
        System.err.println("Repository directory with branch is expected")
    }

    val affectedOriginFiles: Set<File> = root
            .walkTopDown()
            .filter { child -> child.extension in donorExtensionsPrioritized }
            .mapTo(HashSet(), { child -> File(child.parentFile, child.nameWithoutExtension)})

    for (originFile in affectedOriginFiles) {
        if (originFile.exists()) {
            val branchCopyFile = originFile.toPatchFile(originBranchExtension)
            originFile.copyRecursively(branchCopyFile)
            originFile.deleteRecursively()
        }

        val targetFile = donorExtensionsPrioritized
                .asSequence()
                .map { extension -> originFile.toPatchFile(extension) }
                .first { it.exists() }

        targetFile.copyRecursively(originFile)
        targetFile.deleteRecursively()
    }
}

private fun File.toPatchFile(extension: String) = File(parentFile, "$name.$extension")