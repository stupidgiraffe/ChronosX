package dev.chronosx.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class TimeEngineTest {
    @Test
    fun `offset mode adds one day`() {
        val rule = TimeRule(
            packageName = "com.example.demo",
            enabled = true,
            mode = TimeMode.OFFSET,
            offsetMillis = 86_400_000L,
        )

        assertEquals(186_400_000L, TimeEngine.epochMillis(rule, 100_000_000L))
    }

    @Test
    fun `offset mode supports yesterday`() {
        val rule = TimeRule(
            packageName = "com.example.demo",
            enabled = true,
            mode = TimeMode.OFFSET,
            offsetMillis = -86_400_000L,
        )

        assertEquals(13_600_000L, TimeEngine.epochMillis(rule, 100_000_000L))
    }

    @Test
    fun `fixed mode ignores the real wall clock`() {
        val rule = TimeRule(
            packageName = "com.example.demo",
            enabled = true,
            mode = TimeMode.FIXED_TIME,
            fixedEpochMillis = 1_798_804_800_000L,
        )

        assertEquals(1_798_804_800_000L, TimeEngine.epochMillis(rule, 0L))
        assertEquals(1_798_804_800_000L, TimeEngine.epochMillis(rule, Long.MAX_VALUE))
    }

    @Test
    fun `disabled rule always passes through real time`() {
        val rule = TimeRule(
            packageName = "com.example.demo",
            enabled = false,
            mode = TimeMode.FIXED_TIME,
            fixedEpochMillis = 0L,
        )

        assertEquals(123L, TimeEngine.epochMillis(rule, 123L))
    }

    @Test
    fun `overflow saturates instead of wrapping`() {
        val rule = TimeRule(
            packageName = "com.example.demo",
            enabled = true,
            mode = TimeMode.OFFSET,
            offsetMillis = 10L,
        )

        assertEquals(Long.MAX_VALUE, TimeEngine.epochMillis(rule, Long.MAX_VALUE - 2L))
    }

    @Test
    fun `fixed monotonic timeline keeps advancing`() {
        val rule = TimeRule(
            packageName = "com.example.demo",
            enabled = true,
            mode = TimeMode.FIXED_TIME,
            fixedEpochMillis = 1_000L,
        )
        val anchor = TimeEngine.createMonotonicAnchor(rule, sourceMillis = 250L, sourceNanos = 250_000_000L)

        assertEquals(1_000L, TimeEngine.monotonicMillis(rule, 250L, anchor))
        assertEquals(1_025L, TimeEngine.monotonicMillis(rule, 275L, anchor))
        assertEquals(1_025_000_000L, TimeEngine.monotonicNanos(rule, 275_000_000L, anchor))
    }
}
