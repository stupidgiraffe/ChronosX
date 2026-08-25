package dev.chronosx.module

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import dev.chronosx.core.PackageTargetPolicy
import dev.chronosx.core.PreferenceReader
import dev.chronosx.core.RulePreferenceCodec
import dev.chronosx.core.RuntimeTelemetry
import dev.chronosx.core.RuntimeTelemetryCodec
import dev.chronosx.core.RuntimeTelemetryPhase
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 entry point.
 *
 * The framework loads this class only in ChronosX's dynamic scope. The manager requests that scope
 * after persisting a per-package rule, while this entry independently verifies the remote rule and
 * refuses system, malformed, or stale targets before any hook is registered.
 */
class ChronosXModule : XposedModule() {
    private lateinit var logger: ModuleLogger
    private var processName: String = ""
    private var frameworkSupported = false
    private var activated = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
        logger = ModuleLogger(this, param.processName)
        val api = runCatching { apiVersion }.getOrDefault(0)
        val supportsRemoteRules = runCatching {
            frameworkProperties and XposedInterface.PROP_CAP_REMOTE != 0L
        }.getOrDefault(false)

        frameworkSupported = api >= XposedInterface.API_102 && supportsRemoteRules
        if (frameworkSupported) {
            logger.info("Loaded with API $api and remote configuration support.")
        } else {
            logger.warn(
                "Inactive: ChronosX requires libxposed API 102 and remote preferences " +
                    "(api=$api, remote=$supportsRemoteRules).",
            )
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!frameworkSupported) return
        if (!param.isFirstPackage) {
            logger.warn("Ignoring a secondary package in a shared process: ${param.packageName}.")
            publishSharedProcessConflict(param.packageName)
            return
        }
        if (activated) return

        val packageName = param.packageName
        if (param.applicationInfo.flags and SYSTEM_APPLICATION_FLAGS != 0) {
            logger.warn("Ignoring system application package: $packageName.")
            return
        }
        if (!PackageTargetPolicy.isTargetable(packageName)) {
            logger.warn("Ignoring non-targetable package: $packageName.")
            return
        }

        try {
            val preferences = getRemotePreferences(RulePreferenceCodec.GROUP)
            val telemetryPreferences = getRemotePreferences(RuntimeTelemetryCodec.GROUP)
            val runtime = ProcessRuleRuntime(
                packageName = packageName,
                processName = processName,
                preferences = preferences,
                runtimePreferences = telemetryPreferences,
                logger = logger,
            )
            runtime.start()
            if (!runtime.rule().enabled) {
                logger.info("No enabled rule for $packageName; no hooks registered.")
                return
            }
            if (!runtime.isEligibleProcess()) {
                logger.info("Rule for $packageName excludes process $processName; no hooks registered.")
                return
            }

            val report = HookRegistry.installAll(this, runtime, logger)
            runtime.recordHookInstallation(report)
            activated = report.installed.isNotEmpty()
            if (activated) {
                val failures = report.failures.joinToString { it.id.wireName }
                logger.info(
                    "Activated ${report.installed.size} hook surfaces for $packageName" +
                        if (failures.isBlank()) "." else "; unavailable: $failures.",
                )
            } else {
                logger.error("No hook surfaces could be registered for $packageName.")
            }
        } catch (error: Throwable) {
            // Lifecycle callback exceptions are already guarded by libxposed, but this provides a
            // clear, package-scoped diagnostic and ensures no partially initialized state escapes.
            logger.error("Activation failed for $packageName; leaving application behavior unchanged.", error)
        }
    }

    /**
     * A single Linux process cannot safely carry two independently scoped package policies. Make
     * that limitation visible to the affected rule instead of silently treating it as inactive.
     */
    private fun publishSharedProcessConflict(packageName: String) {
        if (!PackageTargetPolicy.isTargetable(packageName)) return
        val rule = runCatching {
            val rulePreferences = getRemotePreferences(RulePreferenceCodec.GROUP)
            RulePreferenceCodec.read(packageName, SharedPreferencesReader(rulePreferences))
        }.onFailure { error ->
            logger.warn("Could not read shared-process rule for $packageName.", error)
        }.getOrNull() ?: return
        if (!rule.enabled) return

        runCatching {
            val telemetryPreferences = getRemotePreferences(RuntimeTelemetryCodec.GROUP)
            val reader = SharedPreferencesReader(telemetryPreferences)
            val knownProcesses = RuntimeTelemetryCodec.knownProcesses(packageName, reader) + processName
            val telemetry = RuntimeTelemetry(
                packageName = packageName,
                processName = processName,
                ruleRevision = rule.ruleRevision,
                phase = RuntimeTelemetryPhase.FAILED,
                updatedAtEpochMillis = rule.updatedAtEpochMillis,
                message = "Shared process conflict: ${paramProcessOwner()} already owns this process. " +
                    "ChronosX will not merge package rules into one process.",
            )
            val editor = telemetryPreferences.edit() ?: error("Runtime telemetry editor is unavailable.")
            RuntimeTelemetryCodec.encode(telemetry).forEach { (key, value) ->
                when (value) {
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    else -> error("Unsupported telemetry value for $key")
                }
            }
            editor.putString(
                RuntimeTelemetryCodec.knownProcessesKey(packageName),
                RuntimeTelemetryCodec.encodeKnownProcesses(knownProcesses),
            )
            check(editor.commit()) { "Runtime telemetry commit failed." }
        }.onFailure { error ->
            logger.warn("Could not publish shared-process conflict for $packageName.", error)
        }
    }

    private fun paramProcessOwner(): String = processName.substringBefore(':')

    private class SharedPreferencesReader(
        private val preferences: SharedPreferences,
    ) : PreferenceReader {
        override fun boolean(key: String, defaultValue: Boolean): Boolean =
            preferences.getBoolean(key, defaultValue)

        override fun long(key: String, defaultValue: Long): Long =
            preferences.getLong(key, defaultValue)

        override fun string(key: String, defaultValue: String): String =
            preferences.getString(key, defaultValue) ?: defaultValue
    }

    private companion object {
        val SYSTEM_APPLICATION_FLAGS =
            ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    }
}
