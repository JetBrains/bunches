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
import org.jetbrains.bunches.general.exitWithError
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

private fun getStartDate(calendarPeriod: Int, amount: Int): Date {
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    calendar.add(calendarPeriod, -amount)
    return calendar.time
}

private fun typeLogToCalendar(type: LogType): Int =
    when(type) {
        LogType.DAY -> Calendar.DAY_OF_YEAR
        LogType.WEEK -> Calendar.WEEK_OF_YEAR
        LogType.MONTH -> Calendar.MONTH
        LogType.YEAR -> Calendar.YEAR
        else -> exitWithError("Can't convert LASTCOMMITS log type to Calendar type.")
    }

private fun getGitLogFilter(settings: Settings): LogCommand.(Git) -> LogCommand =
    when (settings.kind) {
        LogType.LASTCOMMITS -> {
            { this.setMaxCount(settings.amount) }
        }
        else -> {
            val typeOfPeriod = typeLogToCalendar(settings.kind)
            val startDate = getStartDate(typeOfPeriod, settings.amount)
            val revFilter = CommitTimeRevFilter.after(startDate);
            { this.setRevFilter(revFilter)}
        }
    }

private fun doLogStats(settings: Settings) {
    val extensions = readExtensionsFromFile(settings.repoPath)
        .resultWithExit()
        .toSet()
        .map { ".$it" }

    fun isBunchFile(filename: String?): Boolean =
        filename != null && extensions.any { filename.endsWith(it) }

    val gitLogFilter = getGitLogFilter(settings)

    val commits = readCommits(settings.repoPath, gitLogFilter)

    println("%d commits processed".format(commits.size))
    for (commit in commits) {
        val addedCount = commit.fileActions.count {
            it.changeType == DiffEntry.ChangeType.ADD
                    && isBunchFile(it.newPath)
        }
        val deletedCount = commit.fileActions.count {
            it.changeType == DiffEntry.ChangeType.DELETE
                    && isBunchFile(it.newPath)
        }

        if (addedCount == 0 && deletedCount == 0) {
            continue
        }

        val authorName = commit.author?.name ?: "unknown author"
        val commitTime = commit.author?.`when` ?: "unknown time"

        if (addedCount != 0) {
            print("+%d ".format(addedCount))
        }

        if (deletedCount != 0) {
            print("-%d ".format(deletedCount))
        }

        println(
            "%.11s %s %s %s".format(
                commit.hash,
                authorName,
                commitTime,
                commit.title
            )
        )
    }
}