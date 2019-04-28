@file:JvmName("InstallGook")

package org.jetbrains.bunches.precommit

import org.jetbrains.bunches.general.exitWithUsageError
import java.io.File

fun installHook(args: Array<String>) {
    if(args.isEmpty())
        exitWithUsageError("""
            Usage: <git-path>

            Installs git hook thath checks forgotten bunch files

            <git-path>   - Directory with repository (parent directory for .git).
            """.trimIndent())

    val bunchPath = File(::installHook::class.java.protectionDomain.codeSource.location.file).parent + "/bin/bunch"
}