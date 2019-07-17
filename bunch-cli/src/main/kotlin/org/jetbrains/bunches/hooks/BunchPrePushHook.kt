package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.git.readCommit
import org.jetbrains.bunches.git.readCommits
import java.util.*

fun checkBeforePush(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("Invalid arguments number $args")
        return
    }
    val repoPath = args[0]
    var message = ""

    val scanner = Scanner(System.`in`)
    while (scanner.hasNext()) {
        val localRef = scanner.next()
        val localSha = scanner.next()
        val remoteRef = scanner.next()
        val remoteSha = scanner.next()
        val commits =
            if (remoteSha == "0000000000000000000000000000000000000000") {
                readCommit(repoPath, localSha)
            } else {
                readCommits(repoPath, localSha, remoteSha)
            }
        if (commits.any { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }) {
            showOptionalMessage(
                "Impossible to commit with $GENERATED_COMMIT_MARK in history",
                arrayOf("Ok"),
                "Ok"
            )
            println(1)
            return
        }

        val localMessage = checkCommitInterval(commits)
        if (localMessage.isNotEmpty()) {
            message += "In push $localRef to $remoteRef\n$localMessage\n"
        }
    }

    val result = if (message.isNotEmpty()) {
        showOptionalMessage("$message Check it before push?\n", arrayOf("Yes", "No"), "Yes")
    } else {
        0
    }
    println(
        if (result == 0) {
            1
        } else {
            0
        }
    )
}