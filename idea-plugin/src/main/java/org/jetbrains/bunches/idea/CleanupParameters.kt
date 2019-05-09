package org.jetbrains.bunches.idea

data class CleanupParameters(
    val extension: String,
    val commitTitle: String,
    val isNoCommit: Boolean
)