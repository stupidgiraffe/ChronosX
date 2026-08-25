package dev.chronosx.core

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Process-local hook telemetry published by the scoped runtime to framework remote preferences. */
data class RuntimeTelemetry(
    val packageName: String,
    val processName: String,
    val ruleRevision: Long,
    val phase: RuntimeTelemetryPhase,
    val installedSurfaces: Set<String> = emptySet(),
    val failedSurfaces: Map<String, String> = emptyMap(),
    val observedSurfaces: Set<String> = emptySet(),
    val updatedAtEpochMillis: Long = 0L,
    val message: String? = null,
) {
    fun surfaceState(surface: HookSurface): RuntimeSurfaceState = when (surface.wireName) {
        in observedSurfaces -> RuntimeSurfaceState.OBSERVED
        in failedSurfaces -> RuntimeSurfaceState.FAILED
        in installedSurfaces -> RuntimeSurfaceState.INSTALLED
        else -> RuntimeSurfaceState.NOT_REPORTED
    }
}

enum class RuntimeTelemetryPhase {
    RULE_LOADED,
    HOOKS_INSTALLED,
    OBSERVING,
    FAILED,
}

enum class RuntimeSurfaceState {
    OBSERVED,
    INSTALLED,
    FAILED,
    NOT_REPORTED,
}

/**
 * Versioned remote-preference contract. Package and process names are URL-safe encoded so names
 * containing a process separator cannot collide with preference keys.
 */
object RuntimeTelemetryCodec {
    const val GROUP = "chronosx.runtime.v1"

    fun read(
        packageName: String,
        processName: String,
        reader: PreferenceReader,
    ): RuntimeTelemetry? {
        val base = keyPrefix(packageName, processName)
        val revision = reader.long("${base}ruleRevision", -1L)
        if (revision < 0L) return null
        return RuntimeTelemetry(
            packageName = packageName,
            processName = processName,
            ruleRevision = revision,
            phase = reader.string("${base}phase", RuntimeTelemetryPhase.RULE_LOADED.name)
                .toTelemetryPhaseOrDefault(),
            installedSurfaces = decodeSet(reader.string("${base}installed", "")),
            failedSurfaces = decodeMap(reader.string("${base}failed", "")),
            observedSurfaces = decodeSet(reader.string("${base}observed", "")),
            updatedAtEpochMillis = reader.long("${base}updatedAtEpochMillis", 0L),
            message = reader.string("${base}message", "").ifBlank { null },
        )
    }

    fun encode(telemetry: RuntimeTelemetry): Map<String, Any> {
        val base = keyPrefix(telemetry.packageName, telemetry.processName)
        return mapOf(
            "${base}ruleRevision" to telemetry.ruleRevision,
            "${base}phase" to telemetry.phase.name,
            "${base}installed" to encodeSet(telemetry.installedSurfaces),
            "${base}failed" to encodeMap(telemetry.failedSurfaces),
            "${base}observed" to encodeSet(telemetry.observedSurfaces),
            "${base}updatedAtEpochMillis" to telemetry.updatedAtEpochMillis,
            "${base}message" to telemetry.message.orEmpty(),
        )
    }

    fun knownProcesses(packageName: String, reader: PreferenceReader): Set<String> =
        decodeSet(reader.string("${packagePrefix(packageName)}processes", ""))

    fun knownProcessesKey(packageName: String): String = "${packagePrefix(packageName)}processes"

    fun keysFor(packageName: String, processName: String): Set<String> {
        val base = keyPrefix(packageName, processName)
        return setOf(
            "${base}ruleRevision",
            "${base}phase",
            "${base}installed",
            "${base}failed",
            "${base}observed",
            "${base}updatedAtEpochMillis",
            "${base}message",
        )
    }

    fun encodeKnownProcesses(processNames: Set<String>): String = encodeSet(processNames)

    private fun keyPrefix(packageName: String, processName: String): String =
        "${packagePrefix(packageName)}${token(processName)}."

    private fun packagePrefix(packageName: String): String = "runtime.${token(packageName)}."

    private fun encodeSet(values: Set<String>): String =
        values.sorted().joinToString(",") { token(it) }

    private fun decodeSet(value: String): Set<String> =
        value.split(',').asSequence().filter { it.isNotBlank() }.map(::untoken).toSet()

    private fun encodeMap(values: Map<String, String>): String =
        values.toSortedMap().entries.joinToString(",") { (key, value) -> "${token(key)}:${token(value)}" }

    private fun decodeMap(value: String): Map<String, String> =
        value.split(',').asSequence().filter { it.isNotBlank() }.associate { item ->
            val separator = item.indexOf(':')
            require(separator > 0) { "Malformed runtime telemetry failure." }
            untoken(item.substring(0, separator)) to untoken(item.substring(separator + 1))
        }

    private fun token(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun untoken(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private fun String?.toTelemetryPhaseOrDefault(): RuntimeTelemetryPhase =
        runCatching { RuntimeTelemetryPhase.valueOf(this.orEmpty()) }
            .getOrDefault(RuntimeTelemetryPhase.RULE_LOADED)
}
