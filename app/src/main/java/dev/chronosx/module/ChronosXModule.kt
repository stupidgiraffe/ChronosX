package dev.chronosx.module

import android.content.pm.ApplicationInfo
import dev.chronosx.core.PackageTargetPolicy
import dev.chronosx.core.RulePreferenceCodec
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
        if (!frameworkSupported || activated) return
        if (!param.isFirstPackage) {
            logger.warn("Ignoring a secondary package in a shared process: ${param.packageName}.")
            return
        }

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
            val runtime = ProcessRuleRuntime(packageName, processName, preferences, logger)
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

    private companion object {
        val SYSTEM_APPLICATION_FLAGS =
            ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    }
}
