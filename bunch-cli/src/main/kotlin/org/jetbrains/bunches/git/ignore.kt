package org.jetbrains.bunches.git

import org.eclipse.jgit.ignore.IgnoreNode
import java.io.File
import java.io.FileNotFoundException

fun isGradleDir(dir: File) = dir.name == ".gradle"

fun isGitDir(dir: File) = dir.name == ".git"

private fun isOutDir(dir: File) = dir.isDirectory && dir.name == "out"

private fun isGradleBuildDir(dir: File): Boolean {
    if (dir.name != "build") return false
    if (File(dir.parentFile, "build.gradle.kts").exists()) return true
    if (File(dir.parentFile, "build.gradle").exists()) return true

    return false
}

fun parseGitIgnore(baseGitRoot: File): IgnoreNode? {
    val gitignoreFile = File(baseGitRoot, ".gitignore")
    try {
        gitignoreFile.inputStream().use {
            val ignore = IgnoreNode()
            ignore.parse(it)
            return ignore
        }
    } catch (e: FileNotFoundException) {
        return null
    }
}

// If '.gitignore' file doesn't exist 'ignore' will be null and '==' returns false
// We skip '.gitignore' check in this case
fun checkIgnoreList(dir: File, ignore: IgnoreNode?) =
    ignore?.isIgnored(dir.name, dir.isDirectory) == IgnoreNode.MatchResult.IGNORED

// Git root directory contains ".git" sub-directory.
// Git worktree directory contains ".git" file.
// Both cases are covered by this function.
fun isGitRoot(dir: File) = File(dir, ".git").exists()

fun isNestedGitRoot(dir: File, baseGitRoot: File) =
    isGitRoot(dir) && dir != baseGitRoot

fun shouldIgnoreDir(dir: File, baseGitRoot: File, ignore: IgnoreNode?) =
    isGitDir(dir) || isOutDir(dir) || isGradleBuildDir(dir) || isGradleDir(dir) || checkIgnoreList(dir, ignore) || isNestedGitRoot(dir, baseGitRoot)
