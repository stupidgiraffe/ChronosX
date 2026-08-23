package dev.chronosx.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.chronosx.core.TimeMode
import dev.chronosx.core.TimeRule

@Entity(tableName = "time_rules")
data class TimeRuleEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean,
    val mode: String,
    val offsetMillis: Long,
    val fixedEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

fun TimeRuleEntity.toDomain(): TimeRule = TimeRule(
    packageName = packageName,
    enabled = enabled,
    mode = runCatching { TimeMode.valueOf(mode) }.getOrDefault(TimeMode.REAL_TIME),
    offsetMillis = offsetMillis,
    fixedEpochMillis = fixedEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun TimeRule.toEntity(): TimeRuleEntity = TimeRuleEntity(
    packageName = packageName,
    enabled = enabled,
    mode = mode.name,
    offsetMillis = offsetMillis,
    fixedEpochMillis = fixedEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
