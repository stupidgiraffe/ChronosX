package dev.chronosx.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable user-authored scenario; [payload] is the versioned core export format. */
@Entity(tableName = "custom_scenarios")
data class CustomScenarioEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAtEpochMillis: Long,
    val payload: String,
)
