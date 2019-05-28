package org.jetbrains.bunches.precommit

fun hookCodeFromTemplate(bunchExecutablePath: String, oldHookPath: String): String {
    return """
        #!/bin/sh

        #bunch tool pre-commit hook

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

fun checkHookCode(hookCode: String): Boolean = hookCode.lines()[2] == "#bunch tool pre-commit hook"


data class HookParams(val bunchExecutablePath: String, val oldHookPath: String)

fun hookCodeParams(hookCode: String): HookParams {
    val hookCodeLines = hookCode.lines()
    val bunchExecutablePath = hookCodeLines[14].
        drop("""eval "'""".length).
        dropLast("""' precommit ${'$'}files < /dev/tty"""".length)

    val oldHookPath = if(hookCodeLines[4] != ":")
        hookCodeLines[4].substring(1, hookCodeLines[4].length - 1) else ":"

    return HookParams(bunchExecutablePath, oldHookPath)
}