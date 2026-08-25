package dev.chronosx.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TimeRuleEntity::class,
        DebugLogEntity::class,
        ScenarioRunEntity::class,
        CustomScenarioEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class ChronosDatabase : RoomDatabase() {
    abstract fun timeRuleDao(): TimeRuleDao
    abstract fun debugLogDao(): DebugLogDao
    abstract fun scenarioRunDao(): ScenarioRunDao
    abstract fun customScenarioDao(): CustomScenarioDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scenario_runs ADD COLUMN scenarioSnapshot TEXT")
                database.execSQL("ALTER TABLE scenario_runs ADD COLUMN observedDateMatrix TEXT")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS custom_scenarios (" +
                        "id TEXT NOT NULL, title TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, " +
                        "payload TEXT NOT NULL, PRIMARY KEY(id))",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scenario_runs ADD COLUMN runToken TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
