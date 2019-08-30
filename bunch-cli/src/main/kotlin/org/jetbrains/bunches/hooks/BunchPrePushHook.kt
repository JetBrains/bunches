package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.git.readCommits

data class PushInfo(val localRef: String, val localSha: String, val remoteRef: String, val remoteSha: String)

internal fun checkBeforePush(args: Array<String>) {
    val repoPath = args[0]

    // $1 -- Name of the remote to which the push is being done
    // $2 -- URL to which the push is being done
    val pushArgs = args.drop(3)

    // Information about the commits which are being pushed is supplied as lines to the standard input in the form:
    // <local ref> <local sha1> <remote ref> <remote sha1>
    val branchesInfo = pushArgs.chunked(4).map {
        PushInfo(
            localRef = it[0],
            localSha = it[1],
            remoteRef = it[2],
            remoteSha = it[3]
        )
    }.filter { it.remoteRef.split("/").last() == "master" }

    for (info in branchesInfo) {
        val commits = readCommits(repoPath, info.localSha, info.remoteSha)
        if (commits.any { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }) {
            exitWithMessage(1, "Impossible to push to master with $GENERATED_COMMIT_MARK in history")
        }
    }

    exitWithMessage(0)
}