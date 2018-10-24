package org.jetbrains.bunches.file

import java.io.File

fun readRuleFromFile(endSuffix: String, path: String): String? {
    val file = File(path, ".bunch")
    if (!file.exists()) {
        System.err.println("Can't build rule for restore branch from '$endSuffix'. File '${file.canonicalPath}' doesn't exist ")
        return null
    }

    val branchRules = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }

    val currentBranchSuffix = branchRules.firstOrNull()
    if (currentBranchSuffix == null) {
        System.err.println("First line in '${file.canonicalPath}' should contain current branch name")
        return null
    }

    if (currentBranchSuffix == endSuffix) {
        return endSuffix
    }

    val requestedRule = branchRules.find { it == endSuffix || it.startsWith(endSuffix + "_") }
    if (requestedRule == null) {
        System.err.println("Can't find rule for '$endSuffix' in file '${file.canonicalPath}'")
        return null
    }

    val ruleTargetToCurrent = (requestedRule + "_$currentBranchSuffix").split("_")

    return ruleTargetToCurrent.reversed().joinToString(separator = "_")
}

data class UpdateInfo(val base: String, val rules: List<List<String>>)

fun readUpdatePairsFromFile(path: String): UpdateInfo? {
    val file = File(path, ".bunch")
    if (!file.exists()) {
        System.err.println("File '${file.canonicalPath}' doesn't exist ")
        return null
    }

    val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }

    val currentBranchSuffix = lines.firstOrNull()
    if (currentBranchSuffix == null) {
        System.err.println("First line in '${file.canonicalPath}' should contain current branch name")
        return null
    }

    val rules = ArrayList<List<String>>()

    val branchRules = lines.drop(1)
    for (branchRule in branchRules) {
        rules.add((branchRule + "_$currentBranchSuffix").split("_").reversed())
    }

    return UpdateInfo(currentBranchSuffix, rules)
}

fun readExtensionsFromFile(rootPath: String): List<String>? {
    return readExtensionsFromFileImpl(File(rootPath, ".bunch"))
}

fun readExtensionsFromFile(dir: File): List<String>? {
    return readExtensionsFromFileImpl(File(dir, ".bunch"))
}

private fun readExtensionsFromFileImpl(file: File): List<String>? {
    if (!file.exists()) {
        System.err.println("Can't get extensions list from file. File '${file.canonicalPath}' doesn't exist ")
        return null
    }

    val lines = file.readLines().map { it.trim() }.filter { !it.isEmpty() }
    val extensions = lines.map { it.split('_').first() }

    if (extensions.isEmpty()) {
        System.err.println("Can't find any extensions in '${file.canonicalPath}'")
        return null
    }

    return extensions
}