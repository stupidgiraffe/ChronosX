package dev.chronosx.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debug_logs")
data class DebugLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampEpochMillis: Long,
    val level: String,
    val source: String,
    val message: String,
)
