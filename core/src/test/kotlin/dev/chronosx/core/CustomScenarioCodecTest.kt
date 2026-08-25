package dev.chronosx.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomScenarioCodecTest {
    @Test
    fun `custom scenario keeps full manual policy and fixture`() {
        val original = CustomScenario(
            id = "custom-1",
            title = "Quarter-end Tokyo",
            description = "Manual zone rollover.",
            category = ScenarioCategory.TIMEZONE_AND_DST,
            profile = TemporalProfile(
                id = "profile-1",
                name = "Quarter end",
                description = "Fixed Tokyo instant.",
                mode = TimeMode.FIXED_TIME,
                fixedEpochMillis = 1_800_000_000_000L,
                zoneMode = ZoneMode.VIRTUAL_DEFAULT,
                zoneId = "Asia/Tokyo",
                monotonicMode = MonotonicMode.OFFSET,
                monotonicOffsetMillis = 25L,
                processPolicy = ProcessPolicy.ALL_PROCESSES,
            ),
            expectedObservation = "All date surfaces agree on the local date.",
            controlledFixture = ControlledFixture("quarter-end", FixtureResponseKind.STALE, 400L),
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L,
        )

        val decoded = CustomScenarioCodec.decode(CustomScenarioCodec.encode(original))

        assertTrue(decoded is CustomScenarioDecodeResult.Decoded)
        assertEquals(original, (decoded as CustomScenarioDecodeResult.Decoded).scenario)
    }

    @Test
    fun `lab scenario snapshot is immutable and decodable`() {
        val original = ScenarioCatalog.byId("hybrid-policy-expired")!!

        val decoded = LabScenarioCodec.decode(LabScenarioCodec.encode(original))

        assertTrue(decoded is LabScenarioDecodeResult.Decoded)
        assertEquals(original, (decoded as LabScenarioDecodeResult.Decoded).scenario)
    }
}
