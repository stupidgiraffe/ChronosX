package dev.chronosx.core

import java.time.Instant

enum class ScenarioCategory {
    BOUNDARY_TIME,
    TIMEZONE_AND_DST,
    EXPIRY_AND_TTL,
    PROCESS_LIFECYCLE,
    MONOTONIC_CORRECTNESS,
    HYBRID_POLICY,
}

enum class FixtureResponseKind {
    VALID,
    EXPIRED,
    STALE,
    DENIED,
    RETRYABLE_FAILURE,
    MALFORMED_CONTRACT,
}

/** A fixture understood by the loopback-only ChronosX Lab server. */
data class ControlledFixture(
    val id: String,
    val responseKind: FixtureResponseKind,
    val delayMillis: Long = 0L,
)

/** A runnable, reusable scenario for an authorized target or mock application. */
data class LabScenario(
    val id: String,
    val title: String,
    val description: String,
    val category: ScenarioCategory,
    val profile: TemporalProfile,
    val expectedObservation: String,
    val controlledFixture: ControlledFixture? = null,
)

/** Built-in scenarios are presets, not hardcoded product logic. */
object ScenarioCatalog {
    val all: List<LabScenario> = listOf(
        LabScenario(
            id = "boundary-tomorrow",
            title = "Tomorrow boundary",
            description = "Moves wall time forward by one day while preserving interval clocks.",
            category = ScenarioCategory.BOUNDARY_TIME,
            profile = TemporalProfile(
                id = "tomorrow",
                name = "Tomorrow",
                description = "Wall clock +24 hours.",
                mode = TimeMode.OFFSET,
                offsetMillis = DAY_MILLIS,
            ),
            expectedObservation = "Local date-based checks observe tomorrow; timeout clocks remain physical.",
        ),
        LabScenario(
            id = "client-expiry-skew",
            title = "Client expiry skew",
            description = "Exercises local expiry and cache handling with a two-hour wall-clock advance.",
            category = ScenarioCategory.EXPIRY_AND_TTL,
            profile = TemporalProfile(
                id = "expiry-skew",
                name = "Expiry +2 hours",
                description = "Wall clock +2 hours.",
                mode = TimeMode.OFFSET,
                offsetMillis = 2L * HOUR_MILLIS,
            ),
            expectedObservation = "A test app should refresh, expire, or safely reconcile its local cache.",
            controlledFixture = ControlledFixture("expired", FixtureResponseKind.EXPIRED),
        ),
        LabScenario(
            id = "timezone-dst-transition",
            title = "DST transition",
            description = "Pins a known daylight-saving transition in Los Angeles.",
            category = ScenarioCategory.TIMEZONE_AND_DST,
            profile = TemporalProfile(
                id = "dst-los-angeles",
                name = "Los Angeles DST",
                description = "2026 spring DST boundary with a virtual default zone.",
                mode = TimeMode.FIXED_TIME,
                fixedEpochMillis = Instant.parse("2026-03-08T09:30:00Z").toEpochMilli(),
                zoneMode = ZoneMode.VIRTUAL_DEFAULT,
                zoneId = "America/Los_Angeles",
            ),
            expectedObservation = "Zone-aware APIs agree on the local date, offset, and transition behavior.",
        ),
        LabScenario(
            id = "leap-day-rollover",
            title = "Leap-day rollover",
            description = "Pins a leap-day wall-clock instant for date validation.",
            category = ScenarioCategory.BOUNDARY_TIME,
            profile = TemporalProfile(
                id = "leap-day",
                name = "Leap day",
                description = "2028-02-29 12:00 UTC.",
                mode = TimeMode.FIXED_TIME,
                fixedEpochMillis = Instant.parse("2028-02-29T12:00:00Z").toEpochMilli(),
                zoneMode = ZoneMode.VIRTUAL_DEFAULT,
                zoneId = "UTC",
            ),
            expectedObservation = "Date calculations preserve leap-day semantics without corrupting monotonic clocks.",
        ),
        LabScenario(
            id = "multiprocess-consistency",
            title = "Multi-process consistency",
            description = "Applies the same rule across eligible processes for a package.",
            category = ScenarioCategory.PROCESS_LIFECYCLE,
            profile = TemporalProfile(
                id = "multiprocess",
                name = "Multi-process +1 day",
                description = "Wall clock +24 hours across package processes.",
                mode = TimeMode.OFFSET,
                offsetMillis = DAY_MILLIS,
                processPolicy = ProcessPolicy.ALL_PROCESSES,
            ),
            expectedObservation = "UI, worker, and service processes report the same rule revision.",
        ),
        LabScenario(
            id = "monotonic-preservation",
            title = "Monotonic preservation",
            description = "Pins wall time while retaining physical elapsed and uptime clocks.",
            category = ScenarioCategory.MONOTONIC_CORRECTNESS,
            profile = TemporalProfile(
                id = "fixed-preserve-monotonic",
                name = "Fixed wall / physical monotonic",
                description = "Fixed wall clock with untouched duration clocks.",
                mode = TimeMode.FIXED_TIME,
                fixedEpochMillis = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli(),
                monotonicMode = MonotonicMode.PRESERVE,
            ),
            expectedObservation = "Wall time is fixed while timeouts and animation durations remain live.",
        ),
        LabScenario(
            id = "hybrid-policy-expired",
            title = "Hybrid policy disagreement",
            description = "Combines local time skew with an explicit expired fixture from the Lab server.",
            category = ScenarioCategory.HYBRID_POLICY,
            profile = TemporalProfile(
                id = "hybrid-expired",
                name = "Hybrid expiry",
                description = "Client +2 hours with an expired controlled fixture.",
                mode = TimeMode.OFFSET,
                offsetMillis = 2L * HOUR_MILLIS,
            ),
            expectedObservation = "A test app should expose and safely resolve client/server policy disagreement.",
            controlledFixture = ControlledFixture("expired", FixtureResponseKind.EXPIRED),
        ),
    )

    fun byId(id: String): LabScenario? = all.firstOrNull { it.id == id }

    private const val HOUR_MILLIS = 3_600_000L
    private const val DAY_MILLIS = 86_400_000L
}
