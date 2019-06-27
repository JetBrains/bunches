package org.jetbrains.bunches.hooks

private const val BUNCH_PRE_COMMIT_HOOK_COMMENT_MARKER = "#bunch tool pre-commit hook"
private const val BUNCH_EXECUTABLE_PATH_COMMENT_MARKER = "#executable"
private const val BUNCH_PRE_REBASE_HOOK_COMMENT_MARKER = "#bunch tool pre-rebase hook"
private const val OLD_HOOK_PATH_COMMENT_MARKER = "#old"

fun preCommitHookCodeFromTemplate(bunchExecutablePath: String, oldHookPath: String): String {
    return """
        #!/bin/sh

        $BUNCH_PRE_COMMIT_HOOK_COMMENT_MARKER
        $BUNCH_EXECUTABLE_PATH_COMMENT_MARKER '$bunchExecutablePath'
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
            eval "'$bunchExecutablePath' checkCommit ${'$'}files < /dev/tty"
            exit $?
        else
            exit 0
        fi
        """.trimIndent()
}

fun preRebaseCodeWithBashCommand(bunchExecutablePath: String, oldHookPath: String): String {
    return """
        #!/bin/bash

        $BUNCH_PRE_REBASE_HOOK_COMMENT_MARKER
        $BUNCH_EXECUTABLE_PATH_COMMENT_MARKER '$bunchExecutablePath'
        $OLD_HOOK_PATH_COMMENT_MARKER $oldHookPath
        
        two=${'$'}2
        if [ -z ${'$'}2 ]
        then
	        two=${'$'}(git branch | grep \* | cut -d ' ' -f2)
        fi

        result=$('$bunchExecutablePath' checkRebase ${'$'}1 ${'$'}two)

        exit "${'$'}result"
        
        """.trimIndent()
}

fun checkHookCode(hookCode: String, type: String): Boolean {
    return hookCode.lines().any {
        it.trim() == when (type) {
            "pre-commit" -> BUNCH_PRE_COMMIT_HOOK_COMMENT_MARKER
            "pre-rebase" -> BUNCH_PRE_REBASE_HOOK_COMMENT_MARKER
            else -> return false
        }
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