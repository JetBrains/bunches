package org.jetbrains.bunches.precommit

private const val BUNCH_HOOK_COMMENT_MARKER = "#bunch tool pre-commit hook"
private const val BUNCH_EXECUTABLE_PATH_COMMENT_MARKER = "#executable"
private const val OLD_HOOK_PATH_COMMENT_MARKER = "#old"

fun hookCodeFromTemplate(bunchExecutablePath: String, oldHookPath: String): String {
    return """
        #!/bin/sh

        $BUNCH_HOOK_COMMENT_MARKER
        $BUNCH_EXECUTABLE_PATH_COMMENT_MARKER '$bunchExecutablePath'
        $OLD_HOOK_PATH_COMMENT_MARKER $oldHookPath
        
        $oldHookPath
        exitCode=${'$'}?
        if [[ "${'$'}exitCode" -ne 0 ]]
        then
            exit ${'$'}exitCode
        fi

        if [[ -t 1 ]]
        then
            files="${'$'}(git diff --cached --name-only | while read file ; do echo -n "'${'$'}file' "; done)"
            eval "'$bunchExecutablePath' precommit ${'$'}files < /dev/tty"
            exit $?
        else
            exit 0
        fi
        """.trimIndent()
}

fun checkHookCode(hookCode: String): Boolean = hookCode.lines().any { it.trim() == BUNCH_HOOK_COMMENT_MARKER }

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

    val bunchExecutablePath = extractPathWithPrefix(hookCodeLines, BUNCH_EXECUTABLE_PATH_COMMENT_MARKER)  ?: "."
    val oldHookPath = extractPathWithPrefix(hookCodeLines, OLD_HOOK_PATH_COMMENT_MARKER) ?: ":"

    return HookParams(bunchExecutablePath, oldHookPath)
}