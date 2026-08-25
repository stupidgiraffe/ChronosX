package dev.chronosx.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeTelemetryCodecTest {
    @Test
    fun `runtime telemetry survives remote-preference encoding`() {
        val original = RuntimeTelemetry(
            packageName = "com.example.mock",
            processName = "com.example.mock:worker",
            ruleRevision = 6L,
            phase = RuntimeTelemetryPhase.OBSERVING,
            installedSurfaces = setOf("LocalDate.now", "Calendar.getInstance"),
            failedSurfaces = mapOf("JapaneseDate.now" to "missing method"),
            observedSurfaces = setOf("LocalDate.now"),
            updatedAtEpochMillis = 1_800_000_000_000L,
            message = "Observed date hook.",
        )
        val values = RuntimeTelemetryCodec.encode(original).toMutableMap()
        values[RuntimeTelemetryCodec.knownProcessesKey(original.packageName)] =
            RuntimeTelemetryCodec.encodeKnownProcesses(setOf(original.processName))
        val reader = MapReader(values)

        assertEquals(original, RuntimeTelemetryCodec.read(original.packageName, original.processName, reader))
        assertEquals(setOf(original.processName), RuntimeTelemetryCodec.knownProcesses(original.packageName, reader))
        assertTrue(RuntimeTelemetryCodec.keysFor(original.packageName, original.processName).isNotEmpty())
    }

    @Test
    fun `invalid telemetry is treated as absent by caller`() {
        val reader = MapReader(mapOf("runtime.bad.ruleRevision" to "not-a-long"))
        assertTrue(RuntimeTelemetryCodec.read("bad", "bad", reader) == null)
    }

    private class MapReader(private val values: Map<String, Any>) : PreferenceReader {
        override fun boolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun long(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun string(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue
    }
}
