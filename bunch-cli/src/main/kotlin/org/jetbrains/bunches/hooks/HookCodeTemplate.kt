package org.jetbrains.bunches.hooks

import java.io.File

private const val BUNCH_PRE_COMMIT_HOOK_COMMENT_MARKER = "#bunch tool pre-commit hook"
private const val BUNCH_EXECUTABLE_PATH_COMMENT_MARKER = "#executable"
private const val BUNCH_PRE_REBASE_HOOK_COMMENT_MARKER = "#bunch tool pre-rebase hook"
private const val OLD_HOOK_PATH_COMMENT_MARKER = "#old"

internal const val BUNCH_PRE_COMMIT_CHECK_COMMAND = "checkCommit"
internal const val BUNCH_PRE_REBASE_CHECK_COMMAND = "checkRebase"

internal const val CONSOLE_OUTPUT_MODE = "--terminal"
internal const val IDEA_OUTPUT_MODE = "--idea"

enum class HookType {
    COMMIT {
        override val hookName = "pre-commit"
        override val marker = BUNCH_PRE_COMMIT_HOOK_COMMENT_MARKER
        override fun getHookCodeTemplate(bunchExecutablePath: File, oldHookPath: String, repoPath: File): String {
            return preCommitHookTemplate(bunchExecutablePath, oldHookPath, repoPath)
        }
    },

    REBASE {
        override val hookName = "pre-rebase"
        override val marker = BUNCH_PRE_REBASE_HOOK_COMMENT_MARKER
        override fun getHookCodeTemplate(bunchExecutablePath: File, oldHookPath: String, repoPath: File): String {
            return preRebaseHookTemplate(bunchExecutablePath, oldHookPath, repoPath)
        }
    };

    abstract val hookName : String
    abstract val marker : String
    abstract fun getHookCodeTemplate(bunchExecutablePath: File, oldHookPath: String, repoPath: File): String

    override fun toString(): String {
        return hookName
    }
}

fun parseType(name: String): HookType? {
    return HookType.values().firstOrNull { it.hookName == name }
}

fun preCommitHookTemplate(bunchExecutablePath: File, oldHookPath: String, repoPath: File): String {
    return """
        #!/bin/sh

        $BUNCH_PRE_COMMIT_HOOK_COMMENT_MARKER
        $BUNCH_EXECUTABLE_PATH_COMMENT_MARKER '${bunchExecutablePath.absolutePath}'
        $OLD_HOOK_PATH_COMMENT_MARKER $oldHookPath
        
        $oldHookPath
        exitCode=${'$'}?
        if [[ "${'$'}exitCode" -ne 0 ]]
        then
            exit ${'$'}exitCode
        fi

        if [[ -t 0 ]] || [[ -t 1 ]] || [[ -t 2 ]]
        then
            files="${'$'}(git diff --cached --name-only | while read file ; do echo -n "'${'$'}file' "; done)"
            eval "'${bunchExecutablePath.absolutePath}' $BUNCH_PRE_COMMIT_CHECK_COMMAND ${repoPath.absolutePath} ${'$'}files < /dev/tty"
            exit $?
        else
            exit 0
        fi
        """.trimIndent()
}

fun preRebaseHookTemplate(bunchExecutablePath: File, oldHookPath: String, repoPath: File): String {
    return """
        #!/bin/bash

        $BUNCH_PRE_REBASE_HOOK_COMMENT_MARKER
        $BUNCH_EXECUTABLE_PATH_COMMENT_MARKER '${bunchExecutablePath.absolutePath}'
        $OLD_HOOK_PATH_COMMENT_MARKER $oldHookPath
        
        two=$2
        if [ -z $2 ]
        then
	        two=$(git branch | grep \* | cut -d ' ' -f2)
        fi
        
        mode="$IDEA_OUTPUT_MODE"
        
        if [[ -t 0 ]] || [[ -t 1 ]] || [[ -t 2 ]]
        then
            mode="$CONSOLE_OUTPUT_MODE"    
        fi
        
        '${bunchExecutablePath.absolutePath}' $BUNCH_PRE_REBASE_CHECK_COMMAND ${'$'}1 ${'$'}two ${repoPath.absolutePath} ${'$'}mode
        
        """.trimIndent()
}

fun checkHookMarker(hookCode: String, type: HookType): Boolean {
    return hookCode.lines().any {
        it.trim() == type.marker
    }
}

data class HookParams(val bunchExecutablePath: String, val oldHookPath: String)

private fun extractPathWithPrefix(hookCodeLines: List<String>, commentMarker: String): String? {
    val pathWithQuotes = hookCodeLines.firstOrNull { it.trimStart().startsWith(commentMarker) }
        ?.removePrefix(commentMarker)
        ?.trim() ?: return null

    if (!pathWithQuotes.startsWith("'") || !pathWithQuotes.endsWith("'")) {
        return null
    }

    return pathWithQuotes.removeSurrounding("'")
}

fun hookCodeParams(hookCode: String): HookParams {
    val hookCodeLines = hookCode.lines()

    val bunchExecutablePath = extractPathWithPrefix(hookCodeLines, BUNCH_EXECUTABLE_PATH_COMMENT_MARKER) ?: "."
    val oldHookPath = extractPathWithPrefix(hookCodeLines, OLD_HOOK_PATH_COMMENT_MARKER) ?: ":"

    return HookParams(bunchExecutablePath, oldHookPath)
}