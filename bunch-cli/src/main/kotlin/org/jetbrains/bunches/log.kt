@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchStatsLog")

package org.jetbrains.bunches.stats.log

import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LogCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.file.resultWithExit
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.FileAction
import org.jetbrains.bunches.git.readCommits
import java.util.*
import java.util.Calendar



enum class LogType {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    LASTCOMMITS
}

data class Settings(val repoPath: String, val kind: LogType, val amount: Int)

fun main(args: Array<String>) {
    LogCommand().main(args)
}

class LogCommand : BunchSubCommand(
    name = "log",
    help = "Show log of commits with addition or deletion of bunch files.",
    epilog =
    """
            Example:
            bunch log --week --amount 2
            """.trimIndent()
) {
    val repoPath by repoPathOption()

    private val kind by option(
        help = """
            Kind of log. `week` is used by default.
            `day` shows log for 'amount' of last days.
            `week` shows log for 'amount' of last weeks.
            `month` shows log for 'amount' of last months.
            `year` shows log for 'amount' of last years.
            `last` shows log for last 'amount' commits
        """.trimIndent())
        .switch(mapOf(
            "--day" to LogType.DAY,
            "--week" to LogType.WEEK,
            "--month" to LogType.MONTH,
            "--year" to LogType.YEAR,
            "--last" to LogType.LASTCOMMITS))
        .default(LogType.WEEK)

    private val amount by option(help = "Amount value for chosen kind of log. Default value value is 1.")
        .int()
        .restrictTo(1)
        .default(1)


    override fun run() {
        val settings = Settings(
            repoPath = repoPath.toString(),
            kind = kind,
            amount = amount
        )

        process { doLogStats(settings) }
    }
}

fun getStartDate(calendarPeriod: Int, amount: Int): Date {
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    calendar.add(calendarPeriod, -amount)
    return calendar.time
}

fun LogCommand.addRevFilter(since: Date): LogCommand {
    val revFilter = CommitTimeRevFilter.after(since)
    setRevFilter(revFilter)
    return this
}

fun isBunchAdditionDeletionInCommit(commit: CommitInfo, extensions: List<String>): Boolean =
        commit.fileActions.any { action: FileAction -> (
                action.changeType == DiffEntry.ChangeType.ADD
                || action.changeType == DiffEntry.ChangeType.DELETE
                )
                && action.newPath?.endsWithAnyOf(extensions) ?: false
        }

fun String.endsWithAnyOf(endings: List<String>): Boolean =
    endings.map { this.endsWith(it) }.any { it }

fun doLogStats(settings: Settings) {
    val extensions = readExtensionsFromFile(settings.repoPath)
        .resultWithExit()
        .toSet()
        .map { ".$it" }

    val gitLogFilter =
        when (settings.kind) {
            LogType.LASTCOMMITS -> { it: LogCommand, _: Git -> it.setMaxCount(settings.amount) }
            LogType.DAY -> {
                val startDate = getStartDate(Calendar.DAY_OF_YEAR, settings.amount);
                { it: LogCommand, _: Git -> it.addRevFilter(startDate)}
            }
            LogType.WEEK -> {
                val startDate = getStartDate(Calendar.WEEK_OF_YEAR, settings.amount);
                { it: LogCommand, _: Git -> it.addRevFilter(startDate)}
            }
            LogType.MONTH -> {
                val startDate = getStartDate(Calendar.MONTH, settings.amount);
                { it: LogCommand, _: Git -> it.addRevFilter(startDate)}
            }
            LogType.YEAR -> {
                val startDate = getStartDate(Calendar.YEAR, settings.amount);
                { it: LogCommand, _: Git -> it.addRevFilter(startDate)}
            }
        }

    var commits = readCommits(settings.repoPath, gitLogFilter)
    val amountOfProcessedCommits = commits.size
    commits = commits.filter {
        isBunchAdditionDeletionInCommit(it, extensions)
    }

    println("%d commits processed".format(amountOfProcessedCommits))
    for (commit in commits) {
        val amountOfAddedFiles = commit.fileActions.count {
            it.changeType == DiffEntry.ChangeType.ADD
                    && it.newPath?.endsWithAnyOf(extensions) ?: false
        }
        val amountOfDeletedFiles = commit.fileActions.count {
            it.changeType == DiffEntry.ChangeType.DELETE
                    && it.newPath?.endsWithAnyOf(extensions) ?: false
        }
        val authorName = commit.author?.name ?: "unknown author"
        val commitTime = commit.author?.`when` ?: "unknown time"

        if (amountOfAddedFiles != 0) {
            print("+%d ".format(amountOfAddedFiles))
        }

        if (amountOfDeletedFiles != 0) {
            print("-%d ".format(amountOfDeletedFiles))
        }

        print("%.11s %s %s %s".format(
            commit.hash,
            authorName,
            commitTime,
            commit.message
        ))
    }
}