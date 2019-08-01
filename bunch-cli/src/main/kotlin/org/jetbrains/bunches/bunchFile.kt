package org.jetbrains.bunches.file

import org.jetbrains.bunches.BunchException
import org.jetbrains.bunches.general.exitWithError
import java.io.File

const val BUNCH_FILE_NAME = ".bunch"

fun readRuleFromFile(endSuffix: String, path: String): String {
    val file = File(path, BUNCH_FILE_NAME)
    if (!file.exists()) {
        throw BunchException(
            "Can't build rule for restore branch from '$endSuffix'. File '${file.canonicalPath}' doesn't exist"
        )
    }

    val branchRules = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }

    val currentBranchSuffix = branchRules.firstOrNull()
    if (currentBranchSuffix == null) {
        throw BunchException("First line in '${file.canonicalPath}' should contain current branch name")
    }

    if (currentBranchSuffix == endSuffix) {
        return endSuffix
    }

    val requestedRule = branchRules.find { it == endSuffix || it.startsWith(endSuffix + "_") }
    if (requestedRule == null) {
        throw BunchException("Can't find rule for '$endSuffix' in file '${file.canonicalPath}'")
    }

    val ruleTargetToCurrent = (requestedRule + "_$currentBranchSuffix").split("_")

    return ruleTargetToCurrent.reversed().joinToString(separator = "_")
}

class Result<T: Any> private constructor(private val _message: String?, val resultIfSuccess: T?) {
    fun isError() = _message != null

    val result: T get() {
        if (isError()) throw IllegalStateException("Can't get result. Error: $message")
        return resultIfSuccess ?: throw IllegalStateException("Invalid result state")
    }

    val message: String get() {
        if (!isError()) throw IllegalStateException("Can't get error message for success result")
        return _message ?: throw IllegalStateException("Invalid result state")
    }

    companion object {
        fun <T: Any> error(message: String) = Result<T>(message, null)
        fun <T: Any> success(result: T) = Result<T>(null, result)
    }
}

fun <T: Any> Result<T>.resultWithExit(): T {
    if (!isError()) {
        return result
    } else {
        exitWithError(message)
    }
}

data class UpdateInfo(val base: String, val rules: List<List<String>>)

fun readUpdatePairsFromFile(path: String): Result<UpdateInfo> {
    val file = File(path, BUNCH_FILE_NAME)
    if (!file.exists()) {
        return Result.error("File '${file.canonicalPath}' doesn't exist.")
    }

    val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }

    val currentBranchSuffix = lines.firstOrNull()
    if (currentBranchSuffix == null) {
        return Result.error("First line in '${file.canonicalPath}' should contain current branch name")
    }

    val rules = ArrayList<List<String>>()

    val branchRules = lines.drop(1)
    for (branchRule in branchRules) {
        rules.add((branchRule + "_$currentBranchSuffix").split("_").reversed())
    }

    return Result.success(UpdateInfo(currentBranchSuffix, rules))
}

fun readExtensionsFromFile(rootPath: String): Result<List<String>> {
    return readExtensionsFromFileImpl(File(rootPath, BUNCH_FILE_NAME))
}

fun readExtensionsFromFile(dir: File): Result<List<String>> {
    return readExtensionsFromFileImpl(File(dir, BUNCH_FILE_NAME))
}

private fun readExtensionsFromFileImpl(file: File): Result<List<String>> {
    if (!file.exists()) {
        return Result.error("Can't get extensions list from file. File '${file.canonicalPath}' doesn't exist.")
    }

    val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    val extensions = lines.map { it.split('_').first() }

    if (extensions.isEmpty()) {
        return Result.error("Can't find any extensions in '${file.canonicalPath}'")
    }

    return Result.success(extensions)
}