package org.jetbrains.bunches.idea.actions

data class SwitchParameters(
    val repoPath: String,
    val branch: String,
    val stepByStep: Boolean,
    val doCleanup: Boolean,
    val commitMessage: String
)