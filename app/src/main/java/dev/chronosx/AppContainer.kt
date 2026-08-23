package dev.chronosx

import android.content.Context
import androidx.room.Room
import dev.chronosx.data.ChronosDatabase
import dev.chronosx.data.DebugLogRepository
import dev.chronosx.data.FrameworkBridge
import dev.chronosx.data.InstalledAppsRepository
import dev.chronosx.data.TimeRuleRepository

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        ChronosDatabase::class.java,
        "chronosx.db",
    ).build()

    val frameworkBridge = FrameworkBridge()
    val installedAppsRepository = InstalledAppsRepository(context.applicationContext)
    val debugLogRepository = DebugLogRepository(database.debugLogDao())
    val timeRuleRepository = TimeRuleRepository(
        ruleDao = database.timeRuleDao(),
        frameworkBridge = frameworkBridge,
    )
}
