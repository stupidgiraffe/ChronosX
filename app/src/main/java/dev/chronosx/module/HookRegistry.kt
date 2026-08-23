package dev.chronosx.module

import android.os.SystemClock
import dev.chronosx.core.HookSurface
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Versioned, independently guarded registry for ChronosX's documented Java and Android clock
 * surfaces. Each surface is represented by [HookSurface] in core so the manager, documentation,
 * and runtime share one support contract.
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
        OffsetDateTimeNowHook,
        ZonedDateTimeNowHook,
        ClockSystemUtcHook,
        ClockSystemDefaultZoneHook,
        ClockSystemZoneHook,
        TimeZoneDefaultHook,
        ZoneIdDefaultHook,
        ElapsedRealtimeHook,
        ElapsedRealtimeNanosHook,
        UptimeMillisHook,
        UptimeNanosHook,
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

internal enum class HookId(val surface: HookSurface) {
    SYSTEM_CURRENT_TIME_MILLIS(HookSurface.SYSTEM_CURRENT_TIME_MILLIS),
    SYSTEM_NANO_TIME(HookSurface.SYSTEM_NANO_TIME),
    DATE_CONSTRUCTOR(HookSurface.DATE_CONSTRUCTOR),
    CALENDAR_GET_INSTANCE(HookSurface.CALENDAR_GET_INSTANCE),
    INSTANT_NOW(HookSurface.INSTANT_NOW),
    LOCAL_DATE_NOW(HookSurface.LOCAL_DATE_NOW),
    LOCAL_DATE_TIME_NOW(HookSurface.LOCAL_DATE_TIME_NOW),
    OFFSET_DATE_TIME_NOW(HookSurface.OFFSET_DATE_TIME_NOW),
    ZONED_DATE_TIME_NOW(HookSurface.ZONED_DATE_TIME_NOW),
    CLOCK_SYSTEM_UTC(HookSurface.CLOCK_SYSTEM_UTC),
    CLOCK_SYSTEM_DEFAULT_ZONE(HookSurface.CLOCK_SYSTEM_DEFAULT_ZONE),
    CLOCK_SYSTEM_ZONE(HookSurface.CLOCK_SYSTEM_ZONE),
    TIME_ZONE_DEFAULT(HookSurface.TIME_ZONE_DEFAULT),
    ZONE_ID_DEFAULT(HookSurface.ZONE_ID_DEFAULT),
    ELAPSED_REALTIME(HookSurface.ELAPSED_REALTIME),
    ELAPSED_REALTIME_NANOS(HookSurface.ELAPSED_REALTIME_NANOS),
    UPTIME_MILLIS(HookSurface.UPTIME_MILLIS),
    UPTIME_NANOS(HookSurface.UPTIME_NANOS),
    ;

    val wireName: String get() = surface.wireName
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
        module.protectiveHook(Date::class.java.getDeclaredConstructor(), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

            runtime.withConstructionBypass { chain.proceed() }
            val date = chain.thisObject as? Date ?: return@protectiveHook null
            runCatching {
                date.time = runtime.virtualEpochMillis(date.time)
                runtime.observeSurface(id.wireName)
            }.onFailure { logger.warn("Date constructor result could not be virtualized.", it) }
            null
        }
    }
}

private object CalendarGetInstanceHook : ChronosHook {
    override val id = HookId.CALENDAR_GET_INSTANCE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val signatures = listOf(
            CalendarSignature(emptyArray(), usesDefaultZone = true),
            CalendarSignature(arrayOf<Class<*>>(TimeZone::class.java), usesDefaultZone = false),
            CalendarSignature(arrayOf<Class<*>>(Locale::class.java), usesDefaultZone = true),
            CalendarSignature(arrayOf<Class<*>>(TimeZone::class.java, Locale::class.java), usesDefaultZone = false),
        )
        signatures.forEachIndexed { index, signature ->
            val method = Calendar::class.java.getDeclaredMethod("getInstance", *signature.parameters)
            module.protectiveHook(method, id, suffix = index.toString()) { chain ->
                if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

                val calendar = runtime.withConstructionBypass { chain.proceed() } as? Calendar
                    ?: return@protectiveHook null
                runCatching {
                    if (signature.usesDefaultZone) {
                        calendar.timeZone = runtime.virtualDefaultTimeZone(calendar.timeZone)
                    }
                    calendar.timeInMillis = runtime.virtualEpochMillis(calendar.timeInMillis)
                    runtime.observeSurface(id.wireName)
                }.onFailure { logger.warn("Calendar result could not be virtualized.", it) }
                calendar
            }
        }
    }

    private data class CalendarSignature(
        val parameters: Array<Class<*>>,
        val usesDefaultZone: Boolean,
    )
}

private object InstantNowHook : ChronosHook {
    override val id = HookId.INSTANT_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(Instant::class.java.getDeclaredMethod("now"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? Instant
                ?: return@protectiveHook null
            transformOrOriginal(original, logger, id.wireName) {
                runtime.observeSurface(id.wireName)
                runtime.virtualInstant(original)
            }
        }
    }
}

private object LocalDateNowHook : ChronosHook {
    override val id = HookId.LOCAL_DATE_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(LocalDate::class.java.getDeclaredMethod("now"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? LocalDate
                ?: return@protectiveHook null
            transformOrOriginal(original, logger, id.wireName) {
                runtime.observeSurface(id.wireName)
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                    .atZone(runtime.virtualDefaultZone())
                    .toLocalDate()
            }
        }
    }
}

private object LocalDateTimeNowHook : ChronosHook {
    override val id = HookId.LOCAL_DATE_TIME_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(LocalDateTime::class.java.getDeclaredMethod("now"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? LocalDateTime
                ?: return@protectiveHook null
            transformOrOriginal(original, logger, id.wireName) {
                runtime.observeSurface(id.wireName)
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                    .atZone(runtime.virtualDefaultZone())
                    .toLocalDateTime()
            }
        }
    }
}

private object OffsetDateTimeNowHook : ChronosHook {
    override val id = HookId.OFFSET_DATE_TIME_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(OffsetDateTime::class.java.getDeclaredMethod("now"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? OffsetDateTime
                ?: return@protectiveHook null
            transformOrOriginal(original, logger, id.wireName) {
                runtime.observeSurface(id.wireName)
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                    .atZone(runtime.virtualDefaultZone())
                    .toOffsetDateTime()
            }
        }
    }
}

private object ZonedDateTimeNowHook : ChronosHook {
    override val id = HookId.ZONED_DATE_TIME_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(ZonedDateTime::class.java.getDeclaredMethod("now"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? ZonedDateTime
                ?: return@protectiveHook null
            transformOrOriginal(original, logger, id.wireName) {
                runtime.observeSurface(id.wireName)
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                    .atZone(runtime.virtualDefaultZone())
            }
        }
    }
}

private object ClockSystemUtcHook : ChronosHook {
    override val id = HookId.CLOCK_SYSTEM_UTC

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(Clock::class.java.getDeclaredMethod("systemUTC"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() }
            if (!runtime.hasVirtualWallOrZone()) {
                original
            } else {
                runtime.observeSurface(id.wireName)
                ChronosVirtualClock(runtime, ZoneOffset.UTC)
            }
        }
    }
}

private object ClockSystemDefaultZoneHook : ChronosHook {
    override val id = HookId.CLOCK_SYSTEM_DEFAULT_ZONE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(Clock::class.java.getDeclaredMethod("systemDefaultZone"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() }
            if (!runtime.hasVirtualWallOrZone()) {
                original
            } else {
                runtime.observeSurface(id.wireName)
                ChronosVirtualClock(runtime, runtime.virtualDefaultZone())
            }
        }
    }
}

private object ClockSystemZoneHook : ChronosHook {
    override val id = HookId.CLOCK_SYSTEM_ZONE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(Clock::class.java.getDeclaredMethod("system", ZoneId::class.java), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() }
            val clock = original as? Clock ?: return@protectiveHook original
            if (!runtime.hasVirtualWallOrZone()) {
                original
            } else {
                runtime.observeSurface(id.wireName)
                ChronosVirtualClock(runtime, clock.zone)
            }
        }
    }
}

private object TimeZoneDefaultHook : ChronosHook {
    override val id = HookId.TIME_ZONE_DEFAULT

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(TimeZone::class.java.getDeclaredMethod("getDefault"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? TimeZone
                ?: return@protectiveHook null
            if (!runtime.hasVirtualDefaultZone()) {
                original
            } else {
                runtime.observeSurface(id.wireName)
                runtime.virtualDefaultTimeZone(original)
            }
        }
    }
}

private object ZoneIdDefaultHook : ChronosHook {
    override val id = HookId.ZONE_ID_DEFAULT

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(ZoneId::class.java.getDeclaredMethod("systemDefault"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? ZoneId
                ?: return@protectiveHook null
            if (!runtime.hasVirtualDefaultZone()) {
                original
            } else {
                runtime.observeSurface(id.wireName)
                runtime.virtualDefaultZone(original)
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

private object ElapsedRealtimeNanosHook : ChronosHook {
    override val id = HookId.ELAPSED_REALTIME_NANOS

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installLongStaticHook(module, SystemClock::class.java.getDeclaredMethod("elapsedRealtimeNanos"), id, runtime, logger) {
            runtime.virtualMonotonicNanos(it)
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

private object UptimeNanosHook : ChronosHook {
    override val id = HookId.UPTIME_NANOS

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installLongStaticHook(module, SystemClock::class.java.getDeclaredMethod("uptimeNanos"), id, runtime, logger) {
            runtime.virtualMonotonicNanos(it)
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
        transformOrOriginal(value, logger, id.wireName) {
            runtime.observeSurface(id.wireName)
            transform(value)
        }
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

/** A wall-clock-backed Clock that preserves the zone semantics requested by the calling API. */
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
