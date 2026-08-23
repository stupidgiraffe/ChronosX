package dev.chronosx.data

import android.content.Context
import android.content.Intent
import dev.chronosx.core.BenchmarkProtocol

/** Launches an installed target with optional Lab metadata. It never alters package data. */
class TargetLauncher(private val context: Context) {
    fun launch(packageName: String, runId: String, scenarioId: String, ruleRevision: Long): LaunchResult =
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return LaunchResult.Unavailable("No launch activity was found for $packageName.")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BenchmarkProtocol.EXTRA_RUN_ID, runId)
            intent.putExtra(BenchmarkProtocol.EXTRA_SCENARIO_ID, scenarioId)
            intent.putExtra(BenchmarkProtocol.EXTRA_RULE_REVISION, ruleRevision)
            context.startActivity(intent)
            LaunchResult.Launched
        }.getOrElse { LaunchResult.Failed(it.message ?: "Target launch failed.") }
}

sealed interface LaunchResult {
    data object Launched : LaunchResult
    data class Unavailable(val message: String) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}
