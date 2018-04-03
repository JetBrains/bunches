@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("PatchRestore")

package nk.patchsets.git.restore

import nk.patchsets.git.ChangeType
import nk.patchsets.git.FileChange
import nk.patchsets.git.commitChanges
import java.io.File

class Settings(val repoPath: String, val rule: String, val commitTitle: String = "==== switch ====")

val patchesSettings = Settings(
        repoPath = "patches",
        rule = "173->172->171->as30"
)

val kotlinSettings = Settings(
        repoPath = "C:/Projects/kotlin",
        rule = "173->172->171->as30"
)

fun main(args: Array<String>) {
    if (args.size != 3 && args.size != 2) {
        System.err.println("""
            Usage: <git-path> <branches-rule> <commit-title>?

            <git-path> - Directory with repository (parent directory for .git folder)
            <branches-rule> - Set of file suffixes separated with `->` showing what files should be affected and priority
                              of application.
            <commit-title> - Title for switch commit. "==== switch ====" is used by default.

            Example:
            <program> C:/Projects/kotlin 173->172->171->as30
            """.trimIndent())

        return
    }

    val settings = Settings(
            repoPath = args[0],
            rule = args[2],
            commitTitle = args.getOrElse(2, { "==== switch ====" })
    )

    val suffixes = settings.rule.split("->")

    val originBranchExtension = suffixes.first()
    val donorExtensionsPrioritized = suffixes.subList(1, suffixes.size).reversed().toSet()

    val root = File(settings.repoPath)

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

    commitChanges(settings.repoPath, changedFiles, settings.commitTitle)
}

private fun File.toPatchFile(extension: String) = File(parentFile, "$name.$extension")