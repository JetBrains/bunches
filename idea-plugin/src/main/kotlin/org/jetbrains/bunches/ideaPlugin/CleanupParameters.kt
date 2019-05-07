package org.jetbrains.bunches.ideaPlugin

data class CleanupParameters(
        val extension: String,
        val commitTitle: String,
        val isNoCommit: Boolean
)