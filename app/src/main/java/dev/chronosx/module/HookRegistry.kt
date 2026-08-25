package dev.chronosx.module

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.TimeZone as IcuTimeZone
import android.os.SystemClock
import dev.chronosx.core.ClockDomain
import dev.chronosx.core.HookSurface
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.chrono.JapaneseDate
import java.time.chrono.MinguoDate
import java.time.chrono.ThaiBuddhistDate
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
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
        GregorianCalendarConstructorHook,
        IcuCalendarGetInstanceHook,
        InstantNowHook,
        LocalDateNowHook,
        LocalDateNowZoneHook,
        LocalDateTimeNowHook,
        LocalDateTimeNowZoneHook,
        OffsetDateTimeNowHook,
        OffsetDateTimeNowZoneHook,
        ZonedDateTimeNowHook,
        ZonedDateTimeNowZoneHook,
        YearNowHook,
        YearMonthNowHook,
        MonthDayNowHook,
        JapaneseDateNowHook,
        HijrahDateNowHook,
        MinguoDateNowHook,
        ThaiBuddhistDateNowHook,
        ClockSystemUtcHook,
        ClockSystemDefaultZoneHook,
        ClockSystemZoneHook,
        TimeZoneDefaultHook,
        ZoneIdDefaultHook,
        IcuTimeZoneDefaultHook,
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
    GREGORIAN_CALENDAR_CONSTRUCTOR(HookSurface.GREGORIAN_CALENDAR_CONSTRUCTOR),
    ICU_CALENDAR_GET_INSTANCE(HookSurface.ICU_CALENDAR_GET_INSTANCE),
    INSTANT_NOW(HookSurface.INSTANT_NOW),
    LOCAL_DATE_NOW(HookSurface.LOCAL_DATE_NOW),
    LOCAL_DATE_NOW_ZONE(HookSurface.LOCAL_DATE_NOW_ZONE),
    LOCAL_DATE_TIME_NOW(HookSurface.LOCAL_DATE_TIME_NOW),
    LOCAL_DATE_TIME_NOW_ZONE(HookSurface.LOCAL_DATE_TIME_NOW_ZONE),
    OFFSET_DATE_TIME_NOW(HookSurface.OFFSET_DATE_TIME_NOW),
    OFFSET_DATE_TIME_NOW_ZONE(HookSurface.OFFSET_DATE_TIME_NOW_ZONE),
    ZONED_DATE_TIME_NOW(HookSurface.ZONED_DATE_TIME_NOW),
    ZONED_DATE_TIME_NOW_ZONE(HookSurface.ZONED_DATE_TIME_NOW_ZONE),
    YEAR_NOW(HookSurface.YEAR_NOW),
    YEAR_MONTH_NOW(HookSurface.YEAR_MONTH_NOW),
    MONTH_DAY_NOW(HookSurface.MONTH_DAY_NOW),
    JAPANESE_DATE_NOW(HookSurface.JAPANESE_DATE_NOW),
    HIJRAH_DATE_NOW(HookSurface.HIJRAH_DATE_NOW),
    MINGUO_DATE_NOW(HookSurface.MINGUO_DATE_NOW),
    THAI_BUDDHIST_DATE_NOW(HookSurface.THAI_BUDDHIST_DATE_NOW),
    CLOCK_SYSTEM_UTC(HookSurface.CLOCK_SYSTEM_UTC),
    CLOCK_SYSTEM_DEFAULT_ZONE(HookSurface.CLOCK_SYSTEM_DEFAULT_ZONE),
    CLOCK_SYSTEM_ZONE(HookSurface.CLOCK_SYSTEM_ZONE),
    TIME_ZONE_DEFAULT(HookSurface.TIME_ZONE_DEFAULT),
    ZONE_ID_DEFAULT(HookSurface.ZONE_ID_DEFAULT),
    ICU_TIME_ZONE_DEFAULT(HookSurface.ICU_TIME_ZONE_DEFAULT),
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

/** Covers constructors that create a fresh current-time GregorianCalendar rather than using the factory. */
private object GregorianCalendarConstructorHook : ChronosHook {
    override val id = HookId.GREGORIAN_CALENDAR_CONSTRUCTOR

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val signatures = listOf(
            CalendarConstructorSignature(emptyArray(), usesDefaultZone = true),
            CalendarConstructorSignature(arrayOf<Class<*>>(TimeZone::class.java), usesDefaultZone = false),
            CalendarConstructorSignature(arrayOf<Class<*>>(Locale::class.java), usesDefaultZone = true),
            CalendarConstructorSignature(
                arrayOf<Class<*>>(TimeZone::class.java, Locale::class.java),
                usesDefaultZone = false,
            ),
        )
        signatures.forEachIndexed { index, signature ->
            val constructor = GregorianCalendar::class.java.getDeclaredConstructor(*signature.parameters)
            module.protectiveHook(constructor, id, suffix = index.toString()) { chain ->
                if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

                runtime.withConstructionBypass { chain.proceed() }
                val calendar = chain.thisObject as? GregorianCalendar ?: return@protectiveHook null
                runCatching {
                    if (signature.usesDefaultZone) {
                        calendar.timeZone = runtime.virtualDefaultTimeZone(calendar.timeZone)
                    }
                    calendar.timeInMillis = runtime.virtualEpochMillis(calendar.timeInMillis)
                    runtime.observeSurface(id.wireName)
                }.onFailure { logger.warn("GregorianCalendar constructor result could not be virtualized.", it) }
                null
            }
        }
    }
}

/** Android ICU uses separate classes from java.util, so it must be covered independently. */
private object IcuCalendarGetInstanceHook : ChronosHook {
    override val id = HookId.ICU_CALENDAR_GET_INSTANCE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        val signatures = listOf(
            CalendarConstructorSignature(emptyArray(), usesDefaultZone = true),
            CalendarConstructorSignature(arrayOf<Class<*>>(IcuTimeZone::class.java), usesDefaultZone = false),
            CalendarConstructorSignature(arrayOf<Class<*>>(Locale::class.java), usesDefaultZone = true),
            CalendarConstructorSignature(
                arrayOf<Class<*>>(IcuTimeZone::class.java, Locale::class.java),
                usesDefaultZone = false,
            ),
        )
        signatures.forEachIndexed { index, signature ->
            val method = IcuCalendar::class.java.getDeclaredMethod("getInstance", *signature.parameters)
            module.protectiveHook(method, id, suffix = index.toString()) { chain ->
                if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()

                val calendar = runtime.withConstructionBypass { chain.proceed() } as? IcuCalendar
                    ?: return@protectiveHook null
                runCatching {
                    if (signature.usesDefaultZone) {
                        calendar.timeZone = IcuTimeZone.getTimeZone(runtime.virtualDefaultZone().id)
                    }
                    calendar.timeInMillis = runtime.virtualEpochMillis(calendar.timeInMillis)
                    runtime.observeSurface(id.wireName)
                }.onFailure { logger.warn("Android ICU Calendar result could not be virtualized.", it) }
                calendar
            }
        }
    }
}

private data class CalendarConstructorSignature(
    val parameters: Array<Class<*>>,
    val usesDefaultZone: Boolean,
)

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

private object LocalDateNowZoneHook : ChronosHook {
    override val id = HookId.LOCAL_DATE_NOW_ZONE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(LocalDate::class.java.getDeclaredMethod("now", ZoneId::class.java), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? LocalDate
                ?: return@protectiveHook null
            val requestedZone = chain.getArg(0) as? ZoneId ?: return@protectiveHook original
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallClock()) return@protectiveHook original
            transformOrOriginal(original, logger, id.wireName) {
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis()).atZone(requestedZone).toLocalDate()
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

private object LocalDateTimeNowZoneHook : ChronosHook {
    override val id = HookId.LOCAL_DATE_TIME_NOW_ZONE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(LocalDateTime::class.java.getDeclaredMethod("now", ZoneId::class.java), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? LocalDateTime
                ?: return@protectiveHook null
            val requestedZone = chain.getArg(0) as? ZoneId ?: return@protectiveHook original
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallClock()) return@protectiveHook original
            transformOrOriginal(original, logger, id.wireName) {
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis()).atZone(requestedZone).toLocalDateTime()
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

private object OffsetDateTimeNowZoneHook : ChronosHook {
    override val id = HookId.OFFSET_DATE_TIME_NOW_ZONE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(OffsetDateTime::class.java.getDeclaredMethod("now", ZoneId::class.java), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? OffsetDateTime
                ?: return@protectiveHook null
            val requestedZone = chain.getArg(0) as? ZoneId ?: return@protectiveHook original
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallClock()) return@protectiveHook original
            transformOrOriginal(original, logger, id.wireName) {
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis()).atZone(requestedZone).toOffsetDateTime()
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

private object ZonedDateTimeNowZoneHook : ChronosHook {
    override val id = HookId.ZONED_DATE_TIME_NOW_ZONE

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(ZonedDateTime::class.java.getDeclaredMethod("now", ZoneId::class.java), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? ZonedDateTime
                ?: return@protectiveHook null
            val requestedZone = chain.getArg(0) as? ZoneId ?: return@protectiveHook original
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallClock()) return@protectiveHook original
            transformOrOriginal(original, logger, id.wireName) {
                Instant.ofEpochMilli(runtime.virtualNowEpochMillis()).atZone(requestedZone)
            }
        }
    }
}

private object YearNowHook : ChronosHook {
    override val id = HookId.YEAR_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, Year::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            Year.of(it.year)
        }
        installZoneDerivedHook(module, Year::class.java.getDeclaredMethod("now", ZoneId::class.java), id, runtime, logger) {
            date, _ -> Year.of(date.year)
        }
    }
}

private object YearMonthNowHook : ChronosHook {
    override val id = HookId.YEAR_MONTH_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, YearMonth::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            YearMonth.of(it.year, it.month)
        }
        installZoneDerivedHook(module, YearMonth::class.java.getDeclaredMethod("now", ZoneId::class.java), id, runtime, logger) {
            date, _ -> YearMonth.of(date.year, date.month)
        }
    }
}

private object MonthDayNowHook : ChronosHook {
    override val id = HookId.MONTH_DAY_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, MonthDay::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            MonthDay.of(it.month, it.dayOfMonth)
        }
        installZoneDerivedHook(module, MonthDay::class.java.getDeclaredMethod("now", ZoneId::class.java), id, runtime, logger) {
            date, _ -> MonthDay.of(date.month, date.dayOfMonth)
        }
    }
}

private object JapaneseDateNowHook : ChronosHook {
    override val id = HookId.JAPANESE_DATE_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, JapaneseDate::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            JapaneseDate.from(it)
        }
        installZoneDerivedHook(module, JapaneseDate::class.java.getDeclaredMethod("now", ZoneId::class.java), id, runtime, logger) {
            date, _ -> JapaneseDate.from(date)
        }
    }
}

private object HijrahDateNowHook : ChronosHook {
    override val id = HookId.HIJRAH_DATE_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, HijrahDate::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            HijrahDate.from(it)
        }
        installZoneDerivedHook(module, HijrahDate::class.java.getDeclaredMethod("now", ZoneId::class.java), id, runtime, logger) {
            date, _ -> HijrahDate.from(date)
        }
    }
}

private object MinguoDateNowHook : ChronosHook {
    override val id = HookId.MINGUO_DATE_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, MinguoDate::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            MinguoDate.from(it)
        }
        installZoneDerivedHook(module, MinguoDate::class.java.getDeclaredMethod("now", ZoneId::class.java), id, runtime, logger) {
            date, _ -> MinguoDate.from(date)
        }
    }
}

private object ThaiBuddhistDateNowHook : ChronosHook {
    override val id = HookId.THAI_BUDDHIST_DATE_NOW

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        installDefaultZoneDerivedHook(module, ThaiBuddhistDate::class.java.getDeclaredMethod("now"), id, runtime, logger) {
            ThaiBuddhistDate.from(it)
        }
        installZoneDerivedHook(
            module,
            ThaiBuddhistDate::class.java.getDeclaredMethod("now", ZoneId::class.java),
            id,
            runtime,
            logger,
        ) { date, _ -> ThaiBuddhistDate.from(date) }
    }
}

private object ClockSystemUtcHook : ChronosHook {
    override val id = HookId.CLOCK_SYSTEM_UTC

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(Clock::class.java.getDeclaredMethod("systemUTC"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() }
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallClock()) {
                original
            } else {
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
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallOrZone()) {
                original
            } else {
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
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualWallClock()) {
                original
            } else {
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
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualDefaultZone()) {
                original
            } else {
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
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualDefaultZone()) {
                original
            } else {
                runtime.virtualDefaultZone(original)
            }
        }
    }
}

private object IcuTimeZoneDefaultHook : ChronosHook {
    override val id = HookId.ICU_TIME_ZONE_DEFAULT

    override fun install(module: XposedModule, runtime: ProcessRuleRuntime, logger: ModuleLogger) {
        module.protectiveHook(IcuTimeZone::class.java.getDeclaredMethod("getDefault"), id) { chain ->
            if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
            val original = runtime.withConstructionBypass { chain.proceed() } as? IcuTimeZone
                ?: return@protectiveHook null
            runtime.observeSurface(id.wireName)
            if (!runtime.hasVirtualDefaultZone()) {
                original
            } else {
                transformOrOriginal(original, logger, id.wireName) {
                    IcuTimeZone.getTimeZone(runtime.virtualDefaultZone().id)
                }
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

private fun installDefaultZoneDerivedHook(
    module: XposedModule,
    executable: Executable,
    id: HookId,
    runtime: ProcessRuleRuntime,
    logger: ModuleLogger,
    transform: (LocalDate) -> Any,
) {
    module.protectiveHook(executable, id) { chain ->
        if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
        val original = runtime.withConstructionBypass { chain.proceed() } ?: return@protectiveHook null
        runtime.observeSurface(id.wireName)
        if (!runtime.hasVirtualWallOrZone()) return@protectiveHook original
        transformOrOriginal(original, logger, id.wireName) {
            val date = Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                .atZone(runtime.virtualDefaultZone())
                .toLocalDate()
            transform(date)
        }
    }
}

private fun installZoneDerivedHook(
    module: XposedModule,
    executable: Executable,
    id: HookId,
    runtime: ProcessRuleRuntime,
    logger: ModuleLogger,
    transform: (LocalDate, ZoneId) -> Any,
) {
    module.protectiveHook(executable, id, suffix = "-zone") { chain ->
        if (runtime.shouldBypassHooks()) return@protectiveHook chain.proceed()
        val original = runtime.withConstructionBypass { chain.proceed() } ?: return@protectiveHook null
        val requestedZone = chain.getArg(0) as? ZoneId ?: return@protectiveHook original
        runtime.observeSurface(id.wireName)
        if (!runtime.hasVirtualWallClock()) return@protectiveHook original
        transformOrOriginal(original, logger, id.wireName) {
            val date = Instant.ofEpochMilli(runtime.virtualNowEpochMillis())
                .atZone(requestedZone)
                .toLocalDate()
            transform(date, requestedZone)
        }
    }
}

private fun XposedModule.protectiveHook(
    executable: Executable,
    id: HookId,
    suffix: String = "",
    interceptor: (XposedInterface.Chain) -> Any?,
) {
    if (id.surface.domain != ClockDomain.MONOTONIC) {
        // ART may inline static clock factories. A best-effort deoptimization makes supported
        // date/zone surfaces observable on more release builds; hook installation remains safe
        // if a framework or device declines it.
        runCatching { deoptimize(executable) }
    }
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
