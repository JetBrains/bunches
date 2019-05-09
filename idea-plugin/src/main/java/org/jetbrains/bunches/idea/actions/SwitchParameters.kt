package org.jetbrains.bunches.idea.actions

data class SwitchParameters(
    val branch: String,
    val stepByStep: Boolean,
    val doCleanup: Boolean,
    val commitMessage: String
)