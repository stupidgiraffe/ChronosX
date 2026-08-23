package dev.chronosx.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DebugLogDao {
    @Query("SELECT * FROM debug_logs ORDER BY timestampEpochMillis DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int = 250): Flow<List<DebugLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DebugLogEntity)

    @Query("DELETE FROM debug_logs")
    suspend fun clear()
}
