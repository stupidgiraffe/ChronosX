package dev.chronosx.core

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
                    localDate = "2027-01-15",
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
}
