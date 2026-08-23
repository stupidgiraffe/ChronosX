package dev.chronosx.core

/**
 * Immutable, versioned per-package temporal policy.
 *
 * Wall-clock, monotonic-clock, and default-zone policies deliberately remain separate. A Unix
 * epoch is meaningful for a wall clock but never for a boot-relative monotonic clock.
 */
data class TimeRule(
    val packageName: String,
    val enabled: Boolean = false,
    val mode: TimeMode = TimeMode.REAL_TIME,
    val offsetMillis: Long = 0L,
    val fixedEpochMillis: Long = 0L,
    val zoneMode: ZoneMode = ZoneMode.DEVICE_DEFAULT,
    val zoneId: String? = null,
    val monotonicMode: MonotonicMode = MonotonicMode.PRESERVE,
    val monotonicOffsetMillis: Long = 0L,
    val processPolicy: ProcessPolicy = ProcessPolicy.ALL_PROCESSES,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val ruleRevision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    val isVirtualized: Boolean
        get() = enabled && mode != TimeMode.REAL_TIME

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2

        fun disabled(packageName: String): TimeRule = TimeRule(packageName = packageName)
    }
}
