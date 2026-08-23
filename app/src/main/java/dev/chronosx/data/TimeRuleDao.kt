package dev.chronosx.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeRuleDao {
    @Query("SELECT * FROM time_rules ORDER BY enabled DESC, packageName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TimeRuleEntity>>

    @Query("SELECT * FROM time_rules ORDER BY enabled DESC, packageName COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<TimeRuleEntity>

    @Query("SELECT * FROM time_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): TimeRuleEntity?

    @Upsert
    suspend fun upsert(rule: TimeRuleEntity)

    @Query("DELETE FROM time_rules WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
