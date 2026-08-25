package dev.chronosx.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioRunDao {
    @Query("SELECT * FROM scenario_runs ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int = 100): Flow<List<ScenarioRunEntity>>

    @Query("SELECT * FROM scenario_runs WHERE runId = :runId LIMIT 1")
    suspend fun get(runId: String): ScenarioRunEntity?

    @Upsert
    suspend fun upsert(run: ScenarioRunEntity)

    @Query(
        "UPDATE scenario_runs SET status = :status, summary = :summary, completedAtEpochMillis = :completedAt " +
            "WHERE runId = :runId",
    )
    suspend fun updateStatus(runId: String, status: String, summary: String, completedAt: Long?)

    @Query(
        "UPDATE scenario_runs SET status = :status, summary = :summary, completedAtEpochMillis = :completedAt, " +
        "observedAtEpochMillis = :observedAt, observedWallEpochMillis = :observedWall, " +
            "observedZoneId = :observedZone, observedProcessName = :processName, observedSurfaces = :surfaces, " +
            "observedDateMatrix = :dateCapabilityMatrix " +
            "WHERE runId = :runId",
    )
    suspend fun recordObservation(
        runId: String,
        status: String,
        summary: String,
        completedAt: Long,
        observedAt: Long,
        observedWall: Long?,
        observedZone: String?,
        processName: String?,
        surfaces: String?,
        dateCapabilityMatrix: String?,
    )
}
