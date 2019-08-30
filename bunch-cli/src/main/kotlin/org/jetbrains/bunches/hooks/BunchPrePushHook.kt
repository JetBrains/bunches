package org.jetbrains.bunches.hooks

import org.jetbrains.bunches.git.readCommits

data class PushInfo(val localRef: String, val localSha: String, val remoteRef: String, val remoteSha: String)

private const val NON_EXISTENT_COMMIT = "0000000000000000000000000000000000000000"
private const val estimatedMaxCommitCount = 50
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
    }

    for (info in branchesInfo) {
        val commits =
            if (info.remoteSha == NON_EXISTENT_COMMIT) {
                readCommits(repoPath) { this.setMaxCount(estimatedMaxCommitCount) }
            } else {
                readCommits(repoPath, info.localSha, info.remoteSha)
            }
        if (commits.any { (it.message ?: "").contains(GENERATED_COMMIT_MARK) }) {
            exitWithMessage(1, "Impossible to push with $GENERATED_COMMIT_MARK in history")
        }
    }

    exitWithMessage(0)
}