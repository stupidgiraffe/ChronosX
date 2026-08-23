package dev.chronosx.module

import android.os.SystemClock
import dev.chronosx.core.TimeMode
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Central registry for every supported time surface.
 *
 * Each hook is independently installed and guarded. One platform-specific hook failure therefore
 * leaves the rest of the time engine available and never prevents the target application from
 * launching. New surfaces are added by implementing [ChronosHook] and registering it below.
 */
internal object HookRegistry {
    private val hooks: List<ChronosHook> = listOf(
        SystemCurrentTimeMillisHook,
        SystemNanoTimeHook,
        DateConstructorHook,
        CalendarGetInstanceHook,
        InstantNowHook,
        LocalDateNowHook,
        LocalDateTimeNowHook,
        ClockSystemUtcHook,
        ElapsedRealtimeHook,
        UptimeMillisHook,
    )

    fun installAll(
        module: XposedModule,
        runtime: ProcessRuleRuntime,
        logger: ModuleLogger,
    ): HookInstallReport {
        val installed = mutableListOf<HookId>()
        val failed = mutableListOf<HookFailure>()

        hooks.forEach { hook ->
            runCatching { hook.install(module, runtime, logger) }
                .onSuccess {
                    installed += hook.id
                    logger.debug("Installed ${hook.id.wireName}.")
                }
                .onFailure { error ->
                    failed += HookFailure(hook.id, error.message ?: error.javaClass.simpleName)
                    logger.warn("Could not install ${hook.id.wireName}; preserving native behavior.", error)
                }
        }
        return HookInstallReport(installed, failed)
    }
}

internal interface ChronosHook {
    val id: HookId

    fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger)
}

internal enum class HookId(val wireName: String) {
    SYSTEM_CURRENT_TIME_MILLIS("System.currentTimeMillis"),
    SYSTEM_NANO_TIME("System.nanoTime"),
    DATE_CONSTRUCTOR("Date.<init>()"),
    CALENDAR_GET_INSTANCE("Calendar.getInstance"),
    INSTANT_NOW("Instant.now"),
    LOCAL_DATE_NOW("LocalDate.now"),
    LOCAL_DATE_TIME_NOW("LocalDateTime.now"),
    CLOCK_SYSTEM_UTC("Clock.systemUTC"),
    ELAPSED_REALTIME("SystemClock.elapsedRealtime"),
    UPTIME_MILLIS("SystemClock.uptimeMillis"),
}

internal data class HookInstallReport(
    val installed: List<HookId>,
    val failures: List<HookFailure>,
)

internal data class HookFailure(
    val id: HookId,
    val reason: String,
)

private object SystemCurrentTimeMillisHook : ChronosHook {
    override val id = HookId.SYSTEM_CURRENT_TIME_MILLIS

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installLongStaticHook(module, System::class.java.getDeclaredMethod("currentTimeMillis"), id, runtime, logger) {
            runtime.virtualEpochMillis(it)
        }
    }
}

private object SystemNanoTimeHook : ChronosHook {
    override val id = HookId.SYSTEM_NANO_TIME

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installLongStaticHook(module, System::class.java.getDeclaredMethod("nanoTime"), id, runtime, logger) {
            runtime.virtualMonotonicNanos(it)
        }
    }
}

private object DateConstructorHook : ChronosHook {
    override val id = HookId.DATE_CONSTRUCTOR

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val constructor = Date::class.java.getDeclaredConstructor()
        module.protectiveHook(constructor, id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

            runtime.withConstructionBypass { chain.proceed() }
            val date = chain.thisObject as? Date ?: return@protectiveHook null
            runCatching { date.time = runtime.virtualEpochMillis(date.time) }
                .onFailure { logger.warn("Date constructor result could not be virtualized.", it) }
            null
        }
    }
}

private object CalendarGetInstanceHook : ChronosHook {
    override val id = HookId.CALENDAR_GET_INSTANCE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val signatures = listOf(
            emptyArray<Class<*>>(),
            arrayOf(TimeZone::class.java),
            arrayOf(Locale::class.java),
            arrayOf(TimeZone::class.java, Locale::class.java),
        )

        signatures.forEachIndexed { index, signature ->
            val method = Calendar::class.java.getDeclaredMethod("getInstance", *signature)
            module.protectiveHook(method, id, suffix = index.toString()) { chain ->
                if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

                val calendar = runtime.withConstructionBypass { chain.proceed() } as? Calendar
                    ?: return@protectiveHook null
                runCatching { calendar.timeInMillis = runtime.virtualEpochMillis(calendar.timeInMillis) }
                    .onFailure { logger.warn("Calendar result could not be virtualized.", it) }
                calendar
            }
        }
    }
}

private object InstantNowHook : ChronosHook {
    override val id = HookId.INSTANT_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val method = Instant::class.java.getDeclaredMethod("now")
        module.protectiveHook(method, id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

            val instant = runtime.withConstructionBypass { chain.proceed() } as? Instant
                ?: return@protectiveHook null
            transformOrOriginal(instant, logger, "Instant.now") { runtime.virtualInstant(instant) }
        }
    }
}

private object LocalDateNowHook : ChronosHook {
    override val id = HookId.LOCAL_DATE_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val method = LocalDate::class.java.getDeclaredMethod("now")
        module.protectiveHook(method, id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

            val original = runtime.withConstructionBypass { chain.proceed() }
            transformOrOriginal(original, logger, "LocalDate.now") {
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
        }
    }
}

private object LocalDateTimeNowHook : ChronosHook {
    override val id = HookId.LOCAL_DATE_TIME_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val method = LocalDateTime::class.java.getDeclaredMethod("now")
        module.protectiveHook(method, id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

            val original = runtime.withConstructionBypass { chain.proceed() }
            transformOrOriginal(original, logger, "LocalDateTime.now") {
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            }
        }
    }
}

private object ClockSystemUtcHook : ChronosHook {
    override val id = HookId.CLOCK_SYSTEM_UTC

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val method = Clock::class.java.getDeclaredMethod("systemUTC")
        module.protectiveHook(method, id) { chain ->
            val original = chain.proceed()
            if (runtime.shouldBypassHooks() || !runtime.rule().isVirtualized) {
                original
            } else {
                ChronosVirtualClock(runtime, ZoneOffset.UTC)
            }
        }
    }
}

private object ElapsedRealtimeHook : ChronosHook {
    override val id = HookId.ELAPSED_REALTIME

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installLongStaticHook(module, SystemClock::class.java.getDeclaredMethod("elapsedRealtime"), id, runtime, logger) {
            runtime.virtualMonotonicMillis(it)
        }
    }
}

private object UptimeMillisHook : ChronosHook {
    override val id = HookId.UPTIME_MILLIS

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installLongStaticHook(module, SystemClock::class.java.getDeclaredMethod("uptimeMillis"), id, runtime, logger) {
            runtime.virtualMonotonicMillis(it)
        }
    }
}

private fun installLongStaticHook(
    module: XposedModule,
    executable: Executable,
    id: HookId,
    runtime: ProcessRuleRuntime,
    logger: ModuleLogger,
    transform: (Long) -> Long,
) {
    module.protectiveHook(executable, id) { chain ->
        if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

        val original = chain.proceed()
        val value = original as? Long ?: return@protectiveHook original
        transformOrOriginal(value, logger, id.wireName) { transform(value) }
    }
}

private fun XposedModule.protectiveHook(
    executable: Executable,
    id: HookId,
    suffix: String = "",
    interceptor: (XposedInterface.Chain) -> Any?,
) {
    hook(executable)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .setId("chronosx.${id.name.lowercase()}$suffix")
        .intercept { chain -> interceptor(chain) }
}

private fun <T> transformOrOriginal(
    original: T,
    logger: ModuleLogger,
    surface: String,
    transform: () -> T,
): T = runCatching(transform)
    .onFailure { logger.warn("$surface transformation failed; returning the original value.", it) }
    .getOrDefault(original)

/** A UTC/zone-aware clock backed by the process rule and a bypassed physical wall clock. */
private class ChronosVirtualClock(
    private val runtime: ProcessRuleRuntime,
    private val zone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock =
        if (this.zone == zone) this else ChronosVirtualClock(runtime, zone)

    override fun instant(): Instant = Instant.ofEpochMilli(runtime.virtualNowEpochMillis())

    override fun millis(): Long = runtime.virtualNowEpochMillis()
}
