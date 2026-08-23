package dev.chronosx.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TimeRuleEntity::class, DebugLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ChronosDatabase : RoomDatabase() {
    abstract fun timeRuleDao(): TimeRuleDao
    abstract fun debugLogDao(): DebugLogDao
}
