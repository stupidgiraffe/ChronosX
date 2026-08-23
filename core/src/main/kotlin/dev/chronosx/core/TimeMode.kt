package dev.chronosx.core

/** Defines how a target process should perceive wall-clock time. */
enum class TimeMode {
    /** Pass through the device clock unchanged. */
    REAL_TIME,

    /** Add [TimeRule.offsetMillis] to device time. */
    OFFSET,

    /** Return [TimeRule.fixedEpochMillis] for wall-clock APIs. */
    FIXED_TIME,
}
