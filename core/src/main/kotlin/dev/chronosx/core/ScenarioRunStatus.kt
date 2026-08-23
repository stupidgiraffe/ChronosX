package dev.chronosx.core

/** Lifecycle of a locally recorded authorized Lab scenario. */
enum class ScenarioRunStatus {
    PREPARING,
    PENDING_FRAMEWORK,
    APPLIED,
    LAUNCHED,
    OBSERVED_PASS,
    OBSERVED_FAIL,
    FAILED,
}
