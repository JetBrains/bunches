package org.jetbrains.bunches.idea.actions

data class CleanupParameters(
    val extension: String,
    val commitTitle: String,
    val isNoCommit: Boolean
)