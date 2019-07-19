package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.git.readCommits

data class PushInfo(val localRef: String, val localSha: String, val remoteRef: String, val remoteSha: String)

private const val NON_EXISTENT_COMMIT = "0000000000000000000000000000000000000000"

internal fun checkBeforePush(args: Array<String>) {
    val repoPath = args[0]
    val mode = args[3] == "1"
    var message = ""

    val pushArgs = args.drop(4)
    val groups = pushArgs.groupBy {
        val index = pushArgs.indexOf(it)
        index / 4
    }

    for ((_, current) in groups) {
        val info = PushInfo(current[0], current[1], current[2], current[3])
        val commits =
            if (info.remoteSha == NON_EXISTENT_COMMIT) {
                readCommits(repoPath, info.localSha)
            } else {
                readCommits(repoPath, info.localSha, info.remoteSha)
            }
        if (commits.any { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }) {
            if (mode) {
                showOptionalMessage(
                    "Sorry, impossible to push with $GENERATED_COMMIT_MARK in history",
                    arrayOf("Ok"),
                    "Ok"
                )
            } else {
                println("Sorry, impossible to push with $GENERATED_COMMIT_MARK in history")
            }
            exitWithCode(1, mode)
            return
        }

        val localMessage = checkCommitInterval(commits)
        if (localMessage.isNotEmpty()) {
            message += "In push ${info.localRef} to ${info.remoteRef}\n$localMessage\n"
        }
    }

    if (message.isEmpty()) {
        exitWithCode(0, mode)
        return
    }
    val result = if (showInfo("${message}Check it before push?\n", mode)) 1 else 0
    exitWithCode(result, mode)
    return
}