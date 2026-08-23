package dev.chronosx.core

import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZonePolicyTest {
    @Test
    fun `virtual default zone resolves a valid IANA identifier`() {
        val rule = TimeRule(
            packageName = "com.example.target",
            enabled = true,
            zoneMode = ZoneMode.VIRTUAL_DEFAULT,
            zoneId = "Asia/Tokyo",
        )

        assertEquals(ZoneId.of("Asia/Tokyo"), TimeEngine.zoneId(rule, ZoneId.of("UTC")))
    }

    @Test
    fun `malformed virtual zone falls back to the physical default`() {
        val physical = ZoneId.of("UTC")
        val rule = TimeRule(
            packageName = "com.example.target",
            enabled = true,
            zoneMode = ZoneMode.VIRTUAL_DEFAULT,
            zoneId = "not/a-zone",
        )

        assertEquals(physical, TimeEngine.zoneId(rule, physical))
    }
}
