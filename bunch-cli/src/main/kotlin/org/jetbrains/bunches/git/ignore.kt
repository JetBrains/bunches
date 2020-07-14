package org.jetbrains.bunches.git

import org.eclipse.jgit.ignore.IgnoreNode
import java.io.File

fun isGitDir(dir: File) = dir.name == ".git"

fun parseGitIgnore(baseGitRoot: File): IgnoreNode? {
    val gitignoreFile = File(baseGitRoot, ".gitignore")
    if (!gitignoreFile.exists()) {
        return null
    }

    gitignoreFile.inputStream().use {
        val ignore = IgnoreNode()
        ignore.parse(it)
        return ignore
    }
}

// If '.gitignore' file doesn't exist 'ignore' will be null and '==' returns false
// We skip '.gitignore' check in this case
fun checkIgnoreList(dir: File, baseGitRoot: File, ignore: IgnoreNode?): Boolean {
    if (ignore == null) return false
    val repoPath = dir.relativeTo(baseGitRoot).path.replace(File.separatorChar, '/')
    return ignore.isIgnored(repoPath, dir.isDirectory) == IgnoreNode.MatchResult.IGNORED
}

// Git root directory contains ".git" sub-directory.
// Git worktree directory contains ".git" file.
// Both cases are covered by this function.
fun isGitRoot(dir: File) = File(dir, ".git").exists()

fun isNestedGitRoot(dir: File, baseGitRoot: File) =
    isGitRoot(dir) && dir != baseGitRoot

fun shouldIgnoreDir(dir: File, baseGitRoot: File, ignore: IgnoreNode?) =
    isGitDir(dir) || isNestedGitRoot(dir, baseGitRoot) || checkIgnoreList(dir, baseGitRoot, ignore)
