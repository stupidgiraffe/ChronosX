package dev.chronosx.core

/** Immutable per-package virtualization configuration. */
data class TimeRule(
    val packageName: String,
    val enabled: Boolean = false,
    val mode: TimeMode = TimeMode.REAL_TIME,
    val offsetMillis: Long = 0L,
    val fixedEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    val isVirtualized: Boolean
        get() = enabled && mode != TimeMode.REAL_TIME

    companion object {
        fun disabled(packageName: String): TimeRule = TimeRule(packageName = packageName)
    }
}
