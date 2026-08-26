package dev.chronosx.labsdk

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.TimeZone as IcuTimeZone
import dev.chronosx.core.DateCapabilityMatrix
import dev.chronosx.core.DateObservationState
import dev.chronosx.core.DateSurfaceObservation
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.chrono.JapaneseDate
import java.time.chrono.MinguoDate
import java.time.chrono.ThaiBuddhistDate
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Samples documented and diagnostic date surfaces in an authorized mock or customer-owned test
 * target. The probe records explicit year, month, day, and weekday components, then supplies a
 * virtual reference epoch for APIs that only return calendar values.
 *
 * This is evidence, not a claim about arbitrary applications: a target explicitly calls this
 * probe and sends the resulting matrix back through [BenchmarkReporter].
 */
object DateCapabilityProbe {
    fun capture(): DateCapabilityMatrix {
        val defaultZone = ZoneId.systemDefault()
        val capturedAt = System.currentTimeMillis()
        return DateCapabilityMatrix(
            capturedAtEpochMillis = capturedAt,
            defaultZoneId = defaultZone.id,
            observations = buildList {
                epoch("System.currentTimeMillis", defaultZone) { System.currentTimeMillis() }
                epoch("Date.<init>()", defaultZone) { Date().time }
                epoch("Calendar.getInstance", defaultZone) { Calendar.getInstance().timeInMillis }
                epoch("Calendar.getInstance(TimeZone)", defaultZone) {
                    Calendar.getInstance(TimeZone.getTimeZone(defaultZone.id)).timeInMillis
                }
                epoch("Calendar.Builder.build", defaultZone) { Calendar.Builder().build().timeInMillis }
                epoch("GregorianCalendar.<init>()", defaultZone) { GregorianCalendar().timeInMillis }
                epoch("GregorianCalendar(TimeZone)", defaultZone) {
                    GregorianCalendar(TimeZone.getTimeZone(defaultZone.id)).timeInMillis
                }
                epoch("android.icu.util.Calendar.getInstance", defaultZone) { IcuCalendar.getInstance().timeInMillis }
                epoch("android.icu.util.Calendar.getInstance(TimeZone)", defaultZone) {
                    IcuCalendar.getInstance(IcuTimeZone.getTimeZone(defaultZone.id)).timeInMillis
                }
                epoch("Instant.now", defaultZone) { Instant.now().toEpochMilli() }
                epoch("Instant.now(Clock.systemUTC())", defaultZone) { Instant.now(Clock.systemUTC()).toEpochMilli() }
                epoch("Clock.systemUTC().millis", defaultZone) { Clock.systemUTC().millis() }
                epoch("Clock.systemDefaultZone().millis", defaultZone) { Clock.systemDefaultZone().millis() }
                epoch("Clock.system(ZoneId).millis", defaultZone) { Clock.system(defaultZone).millis() }

                date("LocalDate.now", defaultZone) { LocalDate.now().toDateParts() }
                date("LocalDate.now(ZoneId)", defaultZone) { LocalDate.now(defaultZone).toDateParts() }
                date("LocalDate.now(Clock.systemDefaultZone())", defaultZone) {
                    LocalDate.now(Clock.systemDefaultZone()).toDateParts()
                }
                date("LocalDateTime.now", defaultZone) { LocalDateTime.now().toLocalDate().toDateParts() }
                date("LocalDateTime.now(ZoneId)", defaultZone) {
                    LocalDateTime.now(defaultZone).toLocalDate().toDateParts()
                }
                date("LocalDateTime.now(Clock.systemDefaultZone())", defaultZone) {
                    LocalDateTime.now(Clock.systemDefaultZone()).toLocalDate().toDateParts()
                }

                epochAndDate("OffsetDateTime.now", defaultZone) {
                    OffsetDateTime.now().let { value ->
                        value.toInstant().toEpochMilli() to value.toLocalDate().toDateParts()
                    }
                }
                epochAndDate("OffsetDateTime.now(ZoneId)", defaultZone) {
                    OffsetDateTime.now(defaultZone).let { value ->
                        value.toInstant().toEpochMilli() to value.toLocalDate().toDateParts()
                    }
                }
                epochAndDate("OffsetDateTime.now(Clock.systemDefaultZone())", defaultZone) {
                    OffsetDateTime.now(Clock.systemDefaultZone()).let { value ->
                        value.toInstant().toEpochMilli() to value.toLocalDate().toDateParts()
                    }
                }
                epochAndDate("ZonedDateTime.now", defaultZone) {
                    ZonedDateTime.now().let { value ->
                        value.toInstant().toEpochMilli() to value.toLocalDate().toDateParts()
                    }
                }
                epochAndDate("ZonedDateTime.now(ZoneId)", defaultZone) {
                    ZonedDateTime.now(defaultZone).let { value ->
                        value.toInstant().toEpochMilli() to value.toLocalDate().toDateParts()
                    }
                }
                epochAndDate("ZonedDateTime.now(Clock.systemDefaultZone())", defaultZone) {
                    ZonedDateTime.now(Clock.systemDefaultZone()).let { value ->
                        value.toInstant().toEpochMilli() to value.toLocalDate().toDateParts()
                    }
                }

                date("Year.now", defaultZone) { Year.now().toDateParts() }
                date("Year.now(ZoneId)", defaultZone) { Year.now(defaultZone).toDateParts() }
                date("YearMonth.now", defaultZone) { YearMonth.now().toDateParts() }
                date("YearMonth.now(ZoneId)", defaultZone) { YearMonth.now(defaultZone).toDateParts() }
                date("MonthDay.now", defaultZone) { MonthDay.now().toDateParts() }
                date("MonthDay.now(ZoneId)", defaultZone) { MonthDay.now(defaultZone).toDateParts() }
                date("JapaneseDate.now", defaultZone) {
                    LocalDate.ofEpochDay(JapaneseDate.now().toEpochDay()).toDateParts()
                }
                date("JapaneseDate.now(ZoneId)", defaultZone) {
                    LocalDate.ofEpochDay(JapaneseDate.now(defaultZone).toEpochDay()).toDateParts()
                }
                date("HijrahDate.now", defaultZone) {
                    LocalDate.ofEpochDay(HijrahDate.now().toEpochDay()).toDateParts()
                }
                date("HijrahDate.now(ZoneId)", defaultZone) {
                    LocalDate.ofEpochDay(HijrahDate.now(defaultZone).toEpochDay()).toDateParts()
                }
                date("MinguoDate.now", defaultZone) {
                    LocalDate.ofEpochDay(MinguoDate.now().toEpochDay()).toDateParts()
                }
                date("MinguoDate.now(ZoneId)", defaultZone) {
                    LocalDate.ofEpochDay(MinguoDate.now(defaultZone).toEpochDay()).toDateParts()
                }
                date("ThaiBuddhistDate.now", defaultZone) {
                    LocalDate.ofEpochDay(ThaiBuddhistDate.now().toEpochDay()).toDateParts()
                }
                date("ThaiBuddhistDate.now(ZoneId)", defaultZone) {
                    LocalDate.ofEpochDay(ThaiBuddhistDate.now(defaultZone).toEpochDay()).toDateParts()
                }
                zone("TimeZone.getDefault") { TimeZone.getDefault().id }
                zone("ZoneId.systemDefault") { ZoneId.systemDefault().id }
                zone("android.icu.util.TimeZone.getDefault") { IcuTimeZone.getDefault().id }
            },
        )
    }

    private fun MutableList<DateSurfaceObservation>.epoch(
        surface: String,
        zone: ZoneId,
        block: () -> Long,
    ) {
        val result = runCatching(block)
        add(
            result.fold(
                onSuccess = { epoch -> epochObservation(surface, epoch, epoch, zone) },
                onFailure = { error -> failure(surface, zone, error) },
            ),
        )
    }

    private fun MutableList<DateSurfaceObservation>.epochAndDate(
        surface: String,
        zone: ZoneId,
        block: () -> Pair<Long, DateParts>,
    ) {
        val result = runCatching(block)
        add(
            result.fold(
                onSuccess = { (epoch, parts) ->
                    observation(
                        surface = surface,
                        zone = zone,
                        observedEpochMillis = epoch,
                        referenceEpochMillis = epoch,
                        parts = parts,
                    )
                },
                onFailure = { error -> failure(surface, zone, error) },
            ),
        )
    }

    private fun MutableList<DateSurfaceObservation>.date(
        surface: String,
        zone: ZoneId,
        block: () -> DateParts,
    ) {
        // The actual date call falls somewhere between these two readings. Using their midpoint
        // avoids biasing the comparison to the instant after the call, which otherwise produces
        // a false divergence when a sample happens exactly at a local-date boundary.
        val referenceBeforeEpochMillis = System.currentTimeMillis()
        val result = runCatching(block)
        val referenceAfterEpochMillis = System.currentTimeMillis()
        val referenceEpochMillis = referenceBeforeEpochMillis +
            ((referenceAfterEpochMillis - referenceBeforeEpochMillis) / 2)
        add(
            result.fold(
                onSuccess = { parts ->
                    observation(
                        surface = surface,
                        zone = zone,
                        referenceEpochMillis = referenceEpochMillis,
                        parts = parts,
                    )
                },
                onFailure = { error -> failure(surface, zone, error, referenceEpochMillis) },
            ),
        )
    }

    private fun MutableList<DateSurfaceObservation>.zone(
        surface: String,
        block: () -> String,
    ) {
        val result = runCatching(block)
        add(
            result.fold(
                onSuccess = { zoneId ->
                    DateSurfaceObservation(
                        surface = surface,
                        state = DateObservationState.OBSERVED,
                        zoneId = zoneId,
                    )
                },
                onFailure = { error -> failure(surface, null, error) },
            ),
        )
    }

    private fun epochObservation(
        surface: String,
        epochMillis: Long,
        referenceEpochMillis: Long,
        zone: ZoneId,
    ): DateSurfaceObservation = observation(
        surface = surface,
        zone = zone,
        observedEpochMillis = epochMillis,
        referenceEpochMillis = referenceEpochMillis,
        parts = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toDateParts(),
    )

    private fun observation(
        surface: String,
        zone: ZoneId,
        observedEpochMillis: Long? = null,
        referenceEpochMillis: Long? = null,
        parts: DateParts,
    ): DateSurfaceObservation = DateSurfaceObservation(
        surface = surface,
        state = DateObservationState.OBSERVED,
        observedEpochMillis = observedEpochMillis,
        referenceEpochMillis = referenceEpochMillis,
        localDate = parts.display,
        localYear = parts.year,
        localMonth = parts.month,
        localDayOfMonth = parts.dayOfMonth,
        localDayOfWeek = parts.dayOfWeek,
        zoneId = zone.id,
    )

    private fun failure(
        surface: String,
        zone: ZoneId?,
        error: Throwable,
        referenceEpochMillis: Long? = null,
    ): DateSurfaceObservation = DateSurfaceObservation(
        surface = surface,
        state = DateObservationState.ERROR,
        referenceEpochMillis = referenceEpochMillis,
        zoneId = zone?.id,
        detail = error.message ?: error.javaClass.simpleName,
    )

    private data class DateParts(
        val display: String,
        val year: Int? = null,
        val month: Int? = null,
        val dayOfMonth: Int? = null,
        val dayOfWeek: String? = null,
    )

    private fun LocalDate.toDateParts(): DateParts = DateParts(
        display = toString(),
        year = year,
        month = monthValue,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek.name,
    )

    private fun Year.toDateParts(): DateParts = DateParts(display = toString(), year = value)

    private fun YearMonth.toDateParts(): DateParts = DateParts(
        display = toString(),
        year = year,
        month = monthValue,
    )

    private fun MonthDay.toDateParts(): DateParts = DateParts(
        display = toString(),
        month = monthValue,
        dayOfMonth = dayOfMonth,
    )
}
