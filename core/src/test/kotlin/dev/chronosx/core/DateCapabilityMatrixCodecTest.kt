package dev.chronosx.core

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DateCapabilityMatrixCodecTest {
    @Test
    fun `date capability matrix round trips all date evidence`() {
        val original = DateCapabilityMatrix(
            capturedAtEpochMillis = 1_800_000_000_000L,
            defaultZoneId = "Asia/Tokyo",
            observations = listOf(
                DateSurfaceObservation(
                    surface = "LocalDate.now",
                    state = DateObservationState.OBSERVED,
                    observedEpochMillis = 1_800_000_000_000L,
                    referenceEpochMillis = 1_800_000_000_000L,
                    localDate = "2027-01-15",
                    localYear = 2027,
                    localMonth = 1,
                    localDayOfMonth = 15,
                    localDayOfWeek = "FRIDAY",
                    zoneId = "Asia/Tokyo",
                ),
                DateSurfaceObservation(
                    surface = "JapaneseDate.now",
                    state = DateObservationState.ERROR,
                    detail = "Unsupported chronology",
                ),
            ),
        )

        val decoded = DateCapabilityMatrixCodec.decode(DateCapabilityMatrixCodec.encode(original))

        assertTrue(decoded is DateCapabilityMatrixDecodeResult.Decoded)
        assertEquals(original, (decoded as DateCapabilityMatrixDecodeResult.Decoded).matrix)
    }

    @Test
    fun `invalid date capability matrix fails closed`() {
        assertTrue(DateCapabilityMatrixCodec.decode("unknown") is DateCapabilityMatrixDecodeResult.Invalid)
    }

    @Test
    fun `version one date matrix remains readable`() {
        val legacy = """
            chronosx-date-matrix-v1
            capturedAt=1
            defaultZone=QXNpYS9Ub2t5bw
            observation=TG9jYWxEYXRlLm5vdw\tOBSERVED\t1\tMjAyOC0wMi0yOQ\tQXNpYS9Ub2t5bw\tbGVnYWN5
        """.trimIndent().replace("\\t", "\t")

        val decoded = DateCapabilityMatrixCodec.decode(legacy)

        assertTrue(decoded is DateCapabilityMatrixDecodeResult.Decoded)
        val observation = (decoded as DateCapabilityMatrixDecodeResult.Decoded).matrix.observations.single()
        assertEquals(1L, observation.referenceEpochMillis)
        assertEquals("2028-02-29", observation.localDate)
        assertEquals(null, observation.localYear)
    }

    @Test
    fun `analyzer flags a date surface that diverges from its virtual reference epoch`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val referenceEpoch = Instant.parse("2028-02-29T12:00:00Z").toEpochMilli()
        val expected = Instant.ofEpochMilli(referenceEpoch).atZone(zone).toLocalDate()
        val matrix = DateCapabilityMatrix(
            capturedAtEpochMillis = referenceEpoch,
            defaultZoneId = zone.id,
            observations = listOf(
                DateSurfaceObservation(
                    surface = "Calendar.getInstance",
                    state = DateObservationState.OBSERVED,
                    observedEpochMillis = referenceEpoch,
                    referenceEpochMillis = referenceEpoch,
                    localDate = expected.toString(),
                    localYear = expected.year,
                    localMonth = expected.monthValue,
                    localDayOfMonth = expected.dayOfMonth,
                    localDayOfWeek = expected.dayOfWeek.name,
                    zoneId = zone.id,
                ),
                DateSurfaceObservation(
                    surface = "LocalDate.now",
                    state = DateObservationState.OBSERVED,
                    referenceEpochMillis = referenceEpoch,
                    localDate = "2026-08-26",
                    localYear = 2026,
                    localMonth = 8,
                    localDayOfMonth = 26,
                    localDayOfWeek = "WEDNESDAY",
                    zoneId = zone.id,
                ),
            ),
        )

        val analysis = DateCapabilityAnalyzer.analyze(matrix)

        assertEquals(listOf("Calendar.getInstance"), analysis.matchingSurfaces)
        assertEquals(1, analysis.divergentSurfaces.size)
        assertTrue(DateComponent.YEAR in analysis.divergentSurfaces.single().mismatchedComponents)
        assertTrue(DateComponent.DAY_OF_WEEK in analysis.divergentSurfaces.single().mismatchedComponents)
    }
}
