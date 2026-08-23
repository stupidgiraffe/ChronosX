package dev.chronosx.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemporalProfileTest {
    @Test
    fun `profile export round trips independently of a package`() {
        val profile = TemporalProfile(
            id = "tokyo-boundary",
            name = "Tokyo +1 day",
            description = "Tests a Japanese calendar boundary.",
            mode = TimeMode.OFFSET,
            offsetMillis = 86_400_000L,
            zoneMode = ZoneMode.VIRTUAL_DEFAULT,
            zoneId = "Asia/Tokyo",
            monotonicMode = MonotonicMode.PRESERVE,
        )

        val decoded = TemporalProfileCodec.decode(TemporalProfileCodec.encode(profile))

        assertTrue(decoded is ProfileImportResult.Imported)
        val imported = decoded as ProfileImportResult.Imported
        assertEquals(profile, imported.profile)
        assertEquals("com.example.target", imported.profile.applyTo("com.example.target").packageName)
    }

    @Test
    fun `unknown profile header fails closed`() {
        val decoded = TemporalProfileCodec.decode("unknown-profile\nid=x")

        assertTrue(decoded is ProfileImportResult.Invalid)
    }

    @Test
    fun `scenario catalog covers temporal and hybrid policies`() {
        assertTrue(ScenarioCatalog.all.any { it.category == ScenarioCategory.TIMEZONE_AND_DST })
        assertTrue(ScenarioCatalog.all.any { it.controlledFixture?.responseKind == FixtureResponseKind.EXPIRED })
        assertTrue(ScenarioCatalog.all.any { it.category == ScenarioCategory.MONOTONIC_CORRECTNESS })
    }
}
