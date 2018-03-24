@file:Suppress("PackageDirectoryMismatch")

package nk.patchsets.git.restore

import nk.patchsets.git.ChangeType
import nk.patchsets.git.FileChange
import nk.patchsets.git.commitChanges
import java.io.File

val rule = "173->172->171->as30"
val repoPath = "patches"
val commitTitile = "==== switch ===="

fun main(args: Array<String>) {
    val suffixes = rule.split("->")

    val originBranchExtension = suffixes.first()
    val donorExtensionsPrioritized = suffixes.subList(1, suffixes.size).reversed().toSet()

    val root = File(".")

    if (!root.exists() || !root.isDirectory) {
        System.err.println("Repository directory with branch is expected")
    }

    val changedFiles = HashSet<FileChange>()

    val affectedOriginFiles: Set<File> = root
            .walkTopDown()
            .filter { child -> child.extension in donorExtensionsPrioritized }
            .mapTo(HashSet(), { child -> File(child.parentFile, child.nameWithoutExtension)})

    for (originFile in affectedOriginFiles) {
        val originFileModificationType: ChangeType
        if (originFile.exists()) {
            if (originFile.isDirectory) {
                System.err.println("Patch specific directories are not supported: ${originFile}")
                return
            }

            val branchCopyFile = originFile.toPatchFile(originBranchExtension)

            if (branchCopyFile.exists()) {
                System.err.println("Can't store copy of origin file, because branch file is already exist: ${branchCopyFile}")
                return
            }

            originFile.copyTo(branchCopyFile)
            changedFiles.add(FileChange(ChangeType.ADD, branchCopyFile))

            originFile.delete()
            originFileModificationType = ChangeType.MODIFY
        } else {
            originFileModificationType = ChangeType.ADD
        }

        val targetFile = donorExtensionsPrioritized
                .asSequence()
                .map { extension -> originFile.toPatchFile(extension) }
                .first { it.exists() }

        if (targetFile.isDirectory) {
            System.err.println("Patch specific directories are not supported: ${targetFile}")
            return
        }

        targetFile.copyTo(originFile)
        changedFiles.add(FileChange(originFileModificationType, originFile))
    }

    commitChanges(repoPath, changedFiles, commitTitile)
}

private fun File.toPatchFile(extension: String) = File(parentFile, "$name.$extension")