package dev.chronosx.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class RulePreferenceCodecTest {
    @Test
    fun `encoded configuration round trips through the remote preference contract`() {
        val original = TimeRule(
            packageName = "com.example.clocklab",
            enabled = true,
            mode = TimeMode.FIXED_TIME,
            offsetMillis = -1L,
            fixedEpochMillis = 1_798_804_800_000L,
            zoneMode = ZoneMode.VIRTUAL_DEFAULT,
            zoneId = "Asia/Tokyo",
            monotonicMode = MonotonicMode.OFFSET,
            monotonicOffsetMillis = 123L,
            processPolicy = ProcessPolicy.MAIN_PROCESS_ONLY,
            ruleRevision = 17L,
            updatedAtEpochMillis = 1_700_000_000_000L,
        )
        val values = RulePreferenceCodec.encode(original)
        val decoded = RulePreferenceCodec.read(original.packageName, MapPreferenceReader(values))

        assertEquals(original, decoded)
        assertTrue(RulePreferenceCodec.keysFor(original.packageName).all(values::containsKey))
    }

    @Test
    fun `unknown persisted mode fails closed to real time`() {
        val rule = RulePreferenceCodec.read(
            "com.example.clocklab",
            MapPreferenceReader(mapOf("rule.com.example.clocklab.mode" to "NOT_A_MODE")),
        )

        assertEquals(TimeMode.REAL_TIME, rule.mode)
    }

    private class MapPreferenceReader(private val values: Map<String, Any>) : PreferenceReader {
        override fun boolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun long(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun string(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue
    }
}
