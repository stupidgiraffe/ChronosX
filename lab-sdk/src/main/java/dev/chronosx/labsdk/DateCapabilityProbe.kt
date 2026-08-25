package dev.chronosx.labsdk

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.TimeZone as IcuTimeZone
import dev.chronosx.core.DateCapabilityMatrix
import dev.chronosx.core.DateObservationState
import dev.chronosx.core.DateSurfaceObservation
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

/**
 * Samples the documented date surfaces in an authorized mock or customer-owned test target.
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
                epoch("GregorianCalendar.<init>()", defaultZone) { GregorianCalendar().timeInMillis }
                epoch("android.icu.util.Calendar.getInstance", defaultZone) { IcuCalendar.getInstance().timeInMillis }
                epoch("Instant.now", defaultZone) { Instant.now().toEpochMilli() }
                local("LocalDate.now", defaultZone) { LocalDate.now().toString() }
                local("LocalDate.now(ZoneId)", defaultZone) { LocalDate.now(defaultZone).toString() }
                local("LocalDateTime.now", defaultZone) { LocalDateTime.now().toLocalDate().toString() }
                local("LocalDateTime.now(ZoneId)", defaultZone) {
                    LocalDateTime.now(defaultZone).toLocalDate().toString()
                }
                epochAndDate("OffsetDateTime.now", defaultZone) {
                    OffsetDateTime.now().let { it.toInstant().toEpochMilli() to it.toLocalDate().toString() }
                }
                epochAndDate("OffsetDateTime.now(ZoneId)", defaultZone) {
                    OffsetDateTime.now(defaultZone).let { it.toInstant().toEpochMilli() to it.toLocalDate().toString() }
                }
                epochAndDate("ZonedDateTime.now", defaultZone) {
                    ZonedDateTime.now().let { it.toInstant().toEpochMilli() to it.toLocalDate().toString() }
                }
                epochAndDate("ZonedDateTime.now(ZoneId)", defaultZone) {
                    ZonedDateTime.now(defaultZone).let { it.toInstant().toEpochMilli() to it.toLocalDate().toString() }
                }
                local("Year.now", defaultZone) { Year.now().toString() }
                local("YearMonth.now", defaultZone) { YearMonth.now().toString() }
                local("MonthDay.now", defaultZone) { MonthDay.now().toString() }
                local("JapaneseDate.now", defaultZone) {
                    LocalDate.ofEpochDay(JapaneseDate.now().toEpochDay()).toString()
                }
                local("HijrahDate.now", defaultZone) {
                    LocalDate.ofEpochDay(HijrahDate.now().toEpochDay()).toString()
                }
                local("MinguoDate.now", defaultZone) {
                    LocalDate.ofEpochDay(MinguoDate.now().toEpochDay()).toString()
                }
                local("ThaiBuddhistDate.now", defaultZone) {
                    LocalDate.ofEpochDay(ThaiBuddhistDate.now().toEpochDay()).toString()
                }
                local("android.icu.util.TimeZone.getDefault", defaultZone) { IcuTimeZone.getDefault().id }
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
                onSuccess = { epoch ->
                    DateSurfaceObservation(
                        surface = surface,
                        state = DateObservationState.OBSERVED,
                        observedEpochMillis = epoch,
                        localDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().toString(),
                        zoneId = zone.id,
                    )
                },
                onFailure = { error -> failure(surface, zone, error) },
            ),
        )
    }

    private fun MutableList<DateSurfaceObservation>.epochAndDate(
        surface: String,
        zone: ZoneId,
        block: () -> Pair<Long, String>,
    ) {
        val result = runCatching(block)
        add(
            result.fold(
                onSuccess = { (epoch, date) ->
                    DateSurfaceObservation(
                        surface = surface,
                        state = DateObservationState.OBSERVED,
                        observedEpochMillis = epoch,
                        localDate = date,
                        zoneId = zone.id,
                    )
                },
                onFailure = { error -> failure(surface, zone, error) },
            ),
        )
    }

    private fun MutableList<DateSurfaceObservation>.local(
        surface: String,
        zone: ZoneId,
        block: () -> String,
    ) {
        val result = runCatching(block)
        add(
            result.fold(
                onSuccess = { localDate ->
                    DateSurfaceObservation(
                        surface = surface,
                        state = DateObservationState.OBSERVED,
                        localDate = localDate,
                        zoneId = zone.id,
                    )
                },
                onFailure = { error -> failure(surface, zone, error) },
            ),
        )
    }

    private fun failure(surface: String, zone: ZoneId, error: Throwable): DateSurfaceObservation =
        DateSurfaceObservation(
            surface = surface,
            state = DateObservationState.ERROR,
            zoneId = zone.id,
            detail = error.message ?: error.javaClass.simpleName,
        )
}
