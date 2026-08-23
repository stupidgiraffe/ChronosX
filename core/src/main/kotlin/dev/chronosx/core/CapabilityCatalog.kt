package dev.chronosx.core

/** Public support contract for every documented runtime surface. */
enum class HookSurface(
    val wireName: String,
    val domain: ClockDomain,
    val minimumAndroidApi: Int,
    val semantics: String,
) {
    SYSTEM_CURRENT_TIME_MILLIS("System.currentTimeMillis", ClockDomain.WALL, 27, "real / offset / fixed"),
    DATE_CONSTRUCTOR("Date.<init>()", ClockDomain.WALL, 27, "real / offset / fixed"),
    CALENDAR_GET_INSTANCE("Calendar.getInstance", ClockDomain.WALL, 27, "real / offset / fixed"),
    INSTANT_NOW("Instant.now", ClockDomain.WALL, 27, "real / offset / fixed"),
    LOCAL_DATE_NOW("LocalDate.now", ClockDomain.WALL, 27, "virtual wall clock and default zone"),
    LOCAL_DATE_TIME_NOW("LocalDateTime.now", ClockDomain.WALL, 27, "virtual wall clock and default zone"),
    OFFSET_DATE_TIME_NOW("OffsetDateTime.now", ClockDomain.WALL, 27, "virtual wall clock and default zone"),
    ZONED_DATE_TIME_NOW("ZonedDateTime.now", ClockDomain.WALL, 27, "virtual wall clock and default zone"),
    CLOCK_SYSTEM_UTC("Clock.systemUTC", ClockDomain.WALL, 27, "rule-backed UTC clock"),
    CLOCK_SYSTEM_DEFAULT_ZONE("Clock.systemDefaultZone", ClockDomain.WALL, 27, "rule-backed default-zone clock"),
    CLOCK_SYSTEM_ZONE("Clock.system(ZoneId)", ClockDomain.WALL, 27, "rule-backed explicit-zone clock"),
    TIME_ZONE_DEFAULT("TimeZone.getDefault", ClockDomain.ZONE, 27, "physical or virtual default zone"),
    ZONE_ID_DEFAULT("ZoneId.systemDefault", ClockDomain.ZONE, 27, "physical or virtual default zone"),
    SYSTEM_NANO_TIME("System.nanoTime", ClockDomain.MONOTONIC, 27, "physical by default; explicit offset only"),
    ELAPSED_REALTIME("SystemClock.elapsedRealtime", ClockDomain.MONOTONIC, 27, "physical by default; explicit offset only"),
    ELAPSED_REALTIME_NANOS("SystemClock.elapsedRealtimeNanos", ClockDomain.MONOTONIC, 27, "physical by default; explicit offset only"),
    UPTIME_MILLIS("SystemClock.uptimeMillis", ClockDomain.MONOTONIC, 27, "physical by default; explicit offset only"),
    UPTIME_NANOS("SystemClock.uptimeNanos", ClockDomain.MONOTONIC, 35, "physical by default; explicit offset only"),
}

enum class ClockDomain {
    WALL,
    MONOTONIC,
    ZONE,
}

enum class CapabilityStatus {
    SUPPORTED,
    PARTIAL,
    EXPERIMENTAL,
    UNSUPPORTED,
}
