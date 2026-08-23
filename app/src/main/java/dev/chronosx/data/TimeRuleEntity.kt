package dev.chronosx.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.chronosx.core.MonotonicMode
import dev.chronosx.core.ProcessPolicy
import dev.chronosx.core.TimeMode
import dev.chronosx.core.TimeRule
import dev.chronosx.core.ZoneMode

@Entity(tableName = "time_rules")
data class TimeRuleEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean,
    val mode: String,
    val offsetMillis: Long,
    val fixedEpochMillis: Long,
    val zoneMode: String,
    val zoneId: String?,
    val monotonicMode: String,
    val monotonicOffsetMillis: Long,
    val processPolicy: String,
    val schemaVersion: Int,
    val ruleRevision: Long,
    val updatedAtEpochMillis: Long,
)

fun TimeRuleEntity.toDomain(): TimeRule = TimeRule(
    packageName = packageName,
    enabled = enabled,
    mode = runCatching { TimeMode.valueOf(mode) }.getOrDefault(TimeMode.REAL_TIME),
    offsetMillis = offsetMillis,
    fixedEpochMillis = fixedEpochMillis,
    zoneMode = runCatching { ZoneMode.valueOf(zoneMode) }.getOrDefault(ZoneMode.DEVICE_DEFAULT),
    zoneId = zoneId,
    monotonicMode = runCatching { MonotonicMode.valueOf(monotonicMode) }.getOrDefault(MonotonicMode.PRESERVE),
    monotonicOffsetMillis = monotonicOffsetMillis,
    processPolicy = runCatching { ProcessPolicy.valueOf(processPolicy) }.getOrDefault(ProcessPolicy.ALL_PROCESSES),
    schemaVersion = schemaVersion,
    ruleRevision = ruleRevision,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun TimeRule.toEntity(): TimeRuleEntity = TimeRuleEntity(
    packageName = packageName,
    enabled = enabled,
    mode = mode.name,
    offsetMillis = offsetMillis,
    fixedEpochMillis = fixedEpochMillis,
    zoneMode = zoneMode.name,
    zoneId = zoneId,
    monotonicMode = monotonicMode.name,
    monotonicOffsetMillis = monotonicOffsetMillis,
    processPolicy = processPolicy.name,
    schemaVersion = schemaVersion,
    ruleRevision = ruleRevision,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
