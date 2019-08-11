@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("BunchStatsLog")

package org.jetbrains.bunches.stats.log

import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LogCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.revwalk.filter.AndRevFilter
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter
import org.eclipse.jgit.revwalk.filter.MaxCountRevFilter
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.util.GitDateParser
import org.jetbrains.bunches.file.readExtensionsFromFile
import org.jetbrains.bunches.file.resultWithExit
import org.jetbrains.bunches.general.BunchSubCommand
import org.jetbrains.bunches.general.exitWithUsageError
import org.jetbrains.bunches.git.readCommits
import java.text.ParseException
import java.util.*

data class Settings(val repoPath: String, val startDate: Date?, val amount: Int?)

fun main(args: Array<String>) {
    LogCommand().main(args)
}

private const val DEFAULT_PROCESSED_COMMITS_NUMBER = 5

class LogCommand : BunchSubCommand(
    name = "log",
    help = "Show log of commits with addition or deletion of bunch files.",
    epilog =
    """
            Example:
            bunch log --since 1.day.2.weeks.ago
            """.trimIndent()
) {
    private val repoPath by repoPathOption()

    private val startDate by option(
        "--since",
        "--after",
        help = "Show commits from a period of time"
    ).convert {
        try {
            GitDateParser.parse(it, null)
        } catch (parseException: ParseException) {
            exitWithUsageError("Date should be in git --since format. Use spaces only inside \"\"")
        }
    }

    private val amount by option(
        "--last",
        help = "Shows log for last n commits"
    )
        .int()
        .restrictTo(1)


    override fun run() {
        val settings = Settings(
            repoPath = repoPath.toString(),
            startDate = startDate,
            amount = amount ?: if (startDate == null) DEFAULT_PROCESSED_COMMITS_NUMBER else null
        )

        process { doLogStats(settings) }
    }
}

private fun getGitLogFilter(settings: Settings): LogCommand.(Git) -> LogCommand {
    val filterList: MutableList<RevFilter> = mutableListOf(RevFilter.ALL)
    if (settings.startDate != null) {
        filterList.add(CommitTimeRevFilter.after(settings.startDate))
    }
    if (settings.amount != null) {
        filterList.add(MaxCountRevFilter.create(settings.amount))
    }
    val filter = AndRevFilter.create(filterList)
    return { this.setRevFilter(filter) }
}

private fun doLogStats(settings: Settings) {
    val extensions = readExtensionsFromFile(settings.repoPath)
        .resultWithExit()
        .toSet()
        .map { ".$it" }

    fun isBunchFile(filename: String?): Boolean =
        extensions.any { filename?.endsWith(it) ?: false }

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