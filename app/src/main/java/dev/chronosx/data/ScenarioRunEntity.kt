package dev.chronosx.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.chronosx.core.ScenarioRunStatus

/** Durable evidence record for one authorized, local ChronosX Lab run. */
@Entity(tableName = "scenario_runs")
data class ScenarioRunEntity(
    @PrimaryKey val runId: String,
    /** Opaque per-run correlation token; never presented as an authentication mechanism. */
    val runToken: String,
    val scenarioId: String,
    val scenarioTitle: String,
    val targetPackage: String,
    val ruleRevision: Long,
    val fixtureId: String?,
    /** Immutable [dev.chronosx.core.LabScenarioCodec] snapshot for reproducible evidence. */
    val scenarioSnapshot: String?,
    val status: String,
    val summary: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val observedAtEpochMillis: Long?,
    val observedWallEpochMillis: Long?,
    val observedZoneId: String?,
    val observedProcessName: String?,
    val observedSurfaces: String?,
    /** Encoded [dev.chronosx.core.DateCapabilityMatrixCodec] data from an authorized target. */
    val observedDateMatrix: String?,
)

val ScenarioRunEntity.runStatus: ScenarioRunStatus
    get() = runCatching { ScenarioRunStatus.valueOf(status) }.getOrDefault(ScenarioRunStatus.FAILED)

data class BenchmarkObservation(
    val runId: String,
    val runToken: String,
    val sourcePackage: String,
    val processName: String?,
    val ruleRevision: Long,
    val passed: Boolean,
    val observedWallEpochMillis: Long?,
    val observedZoneId: String?,
    val observedSurfaces: String?,
    val dateCapabilityMatrix: dev.chronosx.core.DateCapabilityMatrix?,
    val message: String?,
)
