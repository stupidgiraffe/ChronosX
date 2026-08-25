package dev.chronosx

import android.content.Context
import androidx.room.Room
import dev.chronosx.data.ChronosDatabase
import dev.chronosx.data.CustomScenarioRepository
import dev.chronosx.data.DebugLogRepository
import dev.chronosx.data.DevicePostureCollector
import dev.chronosx.data.FrameworkBridge
import dev.chronosx.data.InstalledAppsRepository
import dev.chronosx.data.ScenarioRunRepository
import dev.chronosx.data.TargetLauncher
import dev.chronosx.data.TimeRuleRepository

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        ChronosDatabase::class.java,
        "chronosx.db",
    ).addMigrations(
        ChronosDatabase.MIGRATION_1_2,
        ChronosDatabase.MIGRATION_2_3,
        ChronosDatabase.MIGRATION_3_4,
    ).build()

    val frameworkBridge = FrameworkBridge()
    val installedAppsRepository = InstalledAppsRepository(context.applicationContext)
    val devicePostureCollector = DevicePostureCollector(context.applicationContext)
    val debugLogRepository = DebugLogRepository(database.debugLogDao())
    val timeRuleRepository = TimeRuleRepository(
        ruleDao = database.timeRuleDao(),
        frameworkBridge = frameworkBridge,
    )
    val scenarioRunRepository = ScenarioRunRepository(
        dao = database.scenarioRunDao(),
        timeRuleRepository = timeRuleRepository,
        targetLauncher = TargetLauncher(context.applicationContext),
    )
    val customScenarioRepository = CustomScenarioRepository(database.customScenarioDao())
}
