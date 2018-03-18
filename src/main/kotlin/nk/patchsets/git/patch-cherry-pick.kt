package nk.patchsets.git

data class Settings(
        val gitPath: String,
        val branch: String,
        val untilHash: String,
        val suffix: String
)

private val localSettings = Settings(
        gitPath = "patches",
        branch = "172",
        untilHash = "3ccf254d42b9c1dff90f5ff722436387c97755ad",
        suffix = "172"
)

private val kotlinSettings = Settings(
        gitPath = "C:/Projects/kotlin",
        branch = "origin/master_172",
        untilHash = "27fa8e964c858832e233fa16f51d6627d5ce380b",
        suffix = "172"
)

fun main(args: Array<String>) {
    with (kotlinSettings) {
        val commits = readCommits(gitPath, branch, untilHash)

        for (commit in commits.reversed()) {
            println("Apply: ${commit.hash} ${commit.title}")

            reCommitPatched(gitPath, commit, suffix)
        }
    }
}