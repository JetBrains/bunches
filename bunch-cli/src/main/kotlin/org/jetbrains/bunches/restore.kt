@file:JvmName("BunchRestore")

package org.jetbrains.bunches.restore

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithError
import org.jetbrains.bunches.git.*
import org.jetbrains.bunches.isBunchFile
import org.jetbrains.bunches.switch.RESTORE_COMMIT_TITLE
import org.jetbrains.bunches.switch.findLastSwitchCommit
import org.jetbrains.bunches.withoutBunchFiles

data class Settings(
    val repoPath: String,
    val untilRef: String,
    val extension: String,
    val backup: Boolean,
    val bunch: Boolean
)

fun main(args: Array<String>) {
    RestoreCommand().main(args)
}

const val GENERATED_CP_BRANCH = "bunches_backup_branch_for_"
const val RESTORE_DESCRIPTION =
    "Move all commit changes to given branch. " +
            "Change will be redirected to .<branch> file if affected file isn`t a bunch file" +
            " and already has at least one bunch file. " +
            "Old commits will be saved to new branch named with \"$GENERATED_CP_BRANCH\" prefix."

class RestoreCommand : BunchSubCommand(
    name = "restore",
    help = RESTORE_DESCRIPTION
) {
    val repoPath by repoPathOption()

    private val untilRef: String by argument(
        "<until-ref>",
        help = "Lowest common ancestor of backup branch and current (usually parent of switch commit)." +
                " First commit after this will be dropped. Default value is the parent of the most recent switch commit."
    ).default("")

    private val extension by option(
        "--ext", help = "Branch to move all commits in the interval." +
                " Default value will be taken from latest switch commit."
    )

    private val bunch by option(
        "--all", "-a", help = "Create bunch files for all files without bunch extension."
    ).flag()

    private val backup by option("--no-backup", help = "Do not create backup git branch with old commits").flag()

    override fun run() {
        val settings = getSettings(repoPath.toString(), untilRef, extension, !backup, bunch)
        process { doRestore(settings) }
    }
}

internal fun getSettings(repoPath: String, untilRef: String, extension: String?, backup: Boolean, bunch: Boolean): Settings {
    return if (untilRef.isBlank()) {
        val lastCommit = findLastSwitchCommit(repoPath)
            ?: exitWithError("No until ref or switch commit found")
        Settings(
            repoPath = repoPath,
            untilRef = lastCommit.hash + "~",
            extension = extension
                ?: parseExtensionFromCommit(lastCommit.title!!)
                ?: exitWithError("Failed to parse branch from switch commit"),
            backup = backup,
            bunch = bunch
        )
    } else {
        Settings(
            repoPath = repoPath,
            untilRef = untilRef,
            extension = extension ?: exitWithError("No extension given for custom until ref."),
            backup = backup,
            bunch = bunch
        )
    }
}

internal fun doRestore(settings: Settings) {
    uncommittedChanges(settings.repoPath).checkAndExitIfNeeded {
        exitWithError("Can not do restore with uncommitted changes.")
    }

    with(settings) {
        val git = Git(configureRepository(repoPath))
        val currentBranch = git.repository.branch
        val extensions = readExtensionsFromFile(repoPath).result
        val commits = readCommits(repoPath, currentBranch, untilRef).dropLast(1)

        if (backup) {
            val backupBranch = GENERATED_CP_BRANCH + currentBranch
            git.branchCreate().setStartPoint(currentBranch).setName(backupBranch).call()
        }

        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(untilRef).call()
        for (commit in commits.reversed()) {
            val actions = commit.fileActions.map {
                val path = it.newPath ?: return@map it
                val fileName = newChangesPath(path, extensions, repoPath, bunch, extension)
                it.copy(newPath = fileName)
            }

            println("Copy: ${commit.hash} ${commit.title}")
            reCommitChanges(repoPath, actions, commit, prefix = "changes in $extension")
        }
    }
}

private fun newChangesPath(path: String,
                           extensions: List<String>,
                           repoPath: String,
                           bunch: Boolean,
                           extension: String): String {
    return if (isBunchFile(path, extensions) || (!bunch && withoutBunchFiles(path, extensions, repoPath))) {
        path
    } else {
        "$path.$extension"
    }
}

internal fun parseExtensionFromCommit(message: String): String? {
    val switchCommitRegex = RESTORE_COMMIT_TITLE.replace("{target}", "(\\w+)")
    return Regex(switchCommitRegex).findAll(message).toList().singleOrNull()?.groupValues?.get(1)
}