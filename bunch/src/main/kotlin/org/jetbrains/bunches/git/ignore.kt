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

fun shouldIgnoreDir(dir: File) = isGitDir(dir) || isOutDir(dir) || isGradleBuildDir(dir) || isGradleDir(dir)
