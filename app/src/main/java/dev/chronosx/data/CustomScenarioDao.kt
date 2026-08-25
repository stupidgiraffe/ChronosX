package dev.chronosx.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomScenarioDao {
    @Query("SELECT * FROM custom_scenarios ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<CustomScenarioEntity>>

    @Upsert
    suspend fun upsert(scenario: CustomScenarioEntity)

    @Query("DELETE FROM custom_scenarios WHERE id = :id")
    suspend fun delete(id: String)
}
