package dev.chronosx.core

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Explicit evidence returned by an authorized benchmark target after it samples date-related
 * APIs. The manager records this data as benchmark evidence; it never infers it from injection.
 */
data class DateCapabilityMatrix(
    val capturedAtEpochMillis: Long,
    val defaultZoneId: String,
    val observations: List<DateSurfaceObservation>,
)

data class DateSurfaceObservation(
    val surface: String,
    val state: DateObservationState,
    val observedEpochMillis: Long? = null,
    val localDate: String? = null,
    val zoneId: String? = null,
    val detail: String? = null,
)

enum class DateObservationState {
    OBSERVED,
    UNAVAILABLE,
    ERROR,
}

sealed interface DateCapabilityMatrixDecodeResult {
    data class Decoded(val matrix: DateCapabilityMatrix) : DateCapabilityMatrixDecodeResult
    data class Invalid(val message: String) : DateCapabilityMatrixDecodeResult
}

/** Dependency-free wire codec for broadcasts, reports, and durable Lab run snapshots. */
object DateCapabilityMatrixCodec {
    private const val HEADER = "chronosx-date-matrix-v1"
    private const val SEPARATOR = "\t"

    fun encode(matrix: DateCapabilityMatrix): String = buildString {
        appendLine(HEADER)
        appendLine("capturedAt=${matrix.capturedAtEpochMillis}")
        appendLine("defaultZone=${encodeText(matrix.defaultZoneId)}")
        matrix.observations.forEach { observation ->
            append("observation=")
            append(encodeText(observation.surface))
            append(SEPARATOR)
            append(observation.state.name)
            append(SEPARATOR)
            append(observation.observedEpochMillis?.toString().orEmpty())
            append(SEPARATOR)
            append(encodeText(observation.localDate.orEmpty()))
            append(SEPARATOR)
            append(encodeText(observation.zoneId.orEmpty()))
            append(SEPARATOR)
            append(encodeText(observation.detail.orEmpty()))
            appendLine()
        }
    }.trimEnd()

    fun decode(text: String): DateCapabilityMatrixDecodeResult = runCatching {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported date capability matrix." }
        val properties = lines.drop(1).filter { it.startsWith("capturedAt=") || it.startsWith("defaultZone=") }
            .associate { line ->
                val separator = line.indexOf('=')
                line.substring(0, separator) to line.substring(separator + 1)
            }
        val observations = lines.drop(1)
            .filter { it.startsWith("observation=") }
            .map { line -> decodeObservation(line.removePrefix("observation=")) }
        DateCapabilityMatrix(
            capturedAtEpochMillis = properties.getValue("capturedAt").toLong(),
            defaultZoneId = decodeText(properties.getValue("defaultZone")),
            observations = observations,
        )
    }.fold(
        onSuccess = DateCapabilityMatrixDecodeResult::Decoded,
        onFailure = { DateCapabilityMatrixDecodeResult.Invalid(it.message ?: "Invalid date capability matrix.") },
    )

    private fun decodeObservation(value: String): DateSurfaceObservation {
        val fields = value.split(SEPARATOR)
        require(fields.size == 6) { "Malformed date capability observation." }
        return DateSurfaceObservation(
            surface = decodeText(fields[0]),
            state = enumValueOf(fields[1]),
            observedEpochMillis = fields[2].ifBlank { null }?.toLong(),
            localDate = decodeText(fields[3]).ifBlank { null },
            zoneId = decodeText(fields[4]).ifBlank { null },
            detail = decodeText(fields[5]).ifBlank { null },
        )
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
