package dev.chronosx.core

/** Controls whether a target sees the physical default zone or an explicit IANA zone. */
enum class ZoneMode {
    DEVICE_DEFAULT,
    VIRTUAL_DEFAULT,
}

/**
 * Monotonic APIs are physical by default. Offset mode is an explicit lab-only test policy and
 * never derives a boot-relative value from a wall-clock epoch.
 */
enum class MonotonicMode {
    PRESERVE,
    OFFSET,
}

/** Defines which processes belonging to an enabled package may consume the rule. */
enum class ProcessPolicy {
    MAIN_PROCESS_ONLY,
    ALL_PROCESSES,
}
