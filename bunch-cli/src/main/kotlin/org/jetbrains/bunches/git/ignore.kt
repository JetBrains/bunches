package org.jetbrains.bunches.git

import java.io.File

fun isGradleDir(dir: File) = dir.name == ".gradle"

fun isGitDir(dir: File) = dir.name == ".git"

private fun isOutDir(dir: File) = dir.isDirectory && dir.name == "out"

private fun isGradleBuildDir(dir: File): Boolean {
    if (dir.name != "build") return false
    if (File(dir.parentFile, "build.gradle.kts").exists()) return true
    if (File(dir.parentFile, "build.gradle").exists()) return true

    return false
}

// Git root directory contains ".git" sub-directory.
// Git worktree directory contains ".git" file.
// Both cases are covered by this function.
fun isGitRoot(dir: File) = File(dir, ".git").exists()

fun isNestedGitRoot(dir: File, baseGitRoot: File) =
    isGitRoot(dir) && dir != baseGitRoot

fun shouldIgnoreDir(dir: File, baseGitRoot: File) =
    isGitDir(dir) || isOutDir(dir) || isGradleBuildDir(dir) || isGradleDir(dir) || isNestedGitRoot(dir, baseGitRoot)
