package dev.chronosx.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TimeRuleEntity::class, DebugLogEntity::class, ScenarioRunEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ChronosDatabase : RoomDatabase() {
    abstract fun timeRuleDao(): TimeRuleDao
    abstract fun debugLogDao(): DebugLogDao
    abstract fun scenarioRunDao(): ScenarioRunDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE time_rules ADD COLUMN zoneMode TEXT NOT NULL DEFAULT 'DEVICE_DEFAULT'")
                database.execSQL("ALTER TABLE time_rules ADD COLUMN zoneId TEXT")
                database.execSQL("ALTER TABLE time_rules ADD COLUMN monotonicMode TEXT NOT NULL DEFAULT 'PRESERVE'")
                database.execSQL("ALTER TABLE time_rules ADD COLUMN monotonicOffsetMillis INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE time_rules ADD COLUMN processPolicy TEXT NOT NULL DEFAULT 'ALL_PROCESSES'")
                database.execSQL("ALTER TABLE time_rules ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 2")
                database.execSQL("ALTER TABLE time_rules ADD COLUMN ruleRevision INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS scenario_runs (" +
                        "runId TEXT NOT NULL, scenarioId TEXT NOT NULL, scenarioTitle TEXT NOT NULL, " +
                        "targetPackage TEXT NOT NULL, ruleRevision INTEGER NOT NULL, fixtureId TEXT, " +
                        "status TEXT NOT NULL, summary TEXT NOT NULL, startedAtEpochMillis INTEGER NOT NULL, " +
                        "completedAtEpochMillis INTEGER, observedAtEpochMillis INTEGER, observedWallEpochMillis INTEGER, " +
                        "observedZoneId TEXT, observedProcessName TEXT, observedSurfaces TEXT, " +
                        "PRIMARY KEY(runId))",
                )
            }
        }
    }
}
