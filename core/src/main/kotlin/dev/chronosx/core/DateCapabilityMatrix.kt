package dev.chronosx.core

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
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

/**
 * A single date-surface sample. [referenceEpochMillis] is the target's virtual wall-clock
 * reference immediately around the call when the surface itself cannot expose an epoch. That
 * makes a local-only API such as [java.time.LocalDate.now] comparable without pretending it
 * returned a timestamp itself.
 */
data class DateSurfaceObservation(
    val surface: String,
    val state: DateObservationState,
    val observedEpochMillis: Long? = null,
    val referenceEpochMillis: Long? = null,
    val localDate: String? = null,
    val localYear: Int? = null,
    val localMonth: Int? = null,
    val localDayOfMonth: Int? = null,
    val localDayOfWeek: String? = null,
    val zoneId: String? = null,
    val detail: String? = null,
)

enum class DateObservationState {
    OBSERVED,
    UNAVAILABLE,
    ERROR,
}

/** A small, manager-side consistency result for a cooperative date-surface benchmark. */
data class DateCapabilityAnalysis(
    val matchingSurfaces: List<String>,
    val divergentSurfaces: List<DateSurfaceDivergence>,
    val nonComparableSurfaces: List<String>,
    val unavailableSurfaces: List<String>,
    val errorSurfaces: List<String>,
) {
    val comparedSurfaceCount: Int get() = matchingSurfaces.size + divergentSurfaces.size
    val isConsistent: Boolean get() = divergentSurfaces.isEmpty()
}

data class DateSurfaceDivergence(
    val surface: String,
    val expected: DateComponents,
    val actual: DateComponents,
    val mismatchedComponents: Set<DateComponent>,
)

data class DateComponents(
    val year: Int? = null,
    val month: Int? = null,
    val dayOfMonth: Int? = null,
    val dayOfWeek: String? = null,
)

enum class DateComponent {
    YEAR,
    MONTH,
    DAY_OF_MONTH,
    DAY_OF_WEEK,
}

/**
 * Compares each date-valued observation to the virtual epoch sampled by the same authorized
 * target. This detects the exact split where a wall-clock hook is observed but a date surface
 * still reports the physical date. It is an internal-consistency check, not proof that an
 * arbitrary third-party app used one of these APIs.
 */
object DateCapabilityAnalyzer {
    fun analyze(matrix: DateCapabilityMatrix): DateCapabilityAnalysis {
        val matching = mutableListOf<String>()
        val divergent = mutableListOf<DateSurfaceDivergence>()
        val nonComparable = mutableListOf<String>()
        val unavailable = mutableListOf<String>()
        val errors = mutableListOf<String>()

        matrix.observations.forEach { observation ->
            when (observation.state) {
                DateObservationState.UNAVAILABLE -> unavailable += observation.surface
                DateObservationState.ERROR -> errors += observation.surface
                DateObservationState.OBSERVED -> {
                    val actual = observation.dateComponents()
                    if (actual == DateComponents()) {
                        nonComparable += observation.surface
                        return@forEach
                    }

                    val zone = observation.zoneId
                        ?.let { zoneId -> runCatching { ZoneId.of(zoneId) }.getOrNull() }
                        ?: runCatching { ZoneId.of(matrix.defaultZoneId) }.getOrDefault(ZoneId.systemDefault())
                    val referenceEpoch = observation.referenceEpochMillis
                        ?: observation.observedEpochMillis
                        ?: matrix.capturedAtEpochMillis
                    val expectedDate = Instant.ofEpochMilli(referenceEpoch).atZone(zone).toLocalDate()
                    val expected = DateComponents(
                        year = expectedDate.year,
                        month = expectedDate.monthValue,
                        dayOfMonth = expectedDate.dayOfMonth,
                        dayOfWeek = expectedDate.dayOfWeek.name,
                    )
                    val mismatches = buildSet {
                        if (actual.year != null && actual.year != expected.year) add(DateComponent.YEAR)
                        if (actual.month != null && actual.month != expected.month) add(DateComponent.MONTH)
                        if (actual.dayOfMonth != null && actual.dayOfMonth != expected.dayOfMonth) {
                            add(DateComponent.DAY_OF_MONTH)
                        }
                        if (actual.dayOfWeek != null && actual.dayOfWeek != expected.dayOfWeek) {
                            add(DateComponent.DAY_OF_WEEK)
                        }
                    }
                    if (mismatches.isEmpty()) {
                        matching += observation.surface
                    } else {
                        divergent += DateSurfaceDivergence(
                            surface = observation.surface,
                            expected = expected,
                            actual = actual,
                            mismatchedComponents = mismatches,
                        )
                    }
                }
            }
        }

        return DateCapabilityAnalysis(
            matchingSurfaces = matching,
            divergentSurfaces = divergent,
            nonComparableSurfaces = nonComparable,
            unavailableSurfaces = unavailable,
            errorSurfaces = errors,
        )
    }

    /** Retains useful comparison for v1 matrices that recorded only a formatted local date. */
    private fun DateSurfaceObservation.dateComponents(): DateComponents {
        val explicit = DateComponents(
            year = localYear,
            month = localMonth,
            dayOfMonth = localDayOfMonth,
            dayOfWeek = localDayOfWeek,
        )
        if (explicit != DateComponents()) return explicit
        val value = localDate ?: return explicit
        parseLocalDateOrNull(value)?.let { date ->
            return DateComponents(date.year, date.monthValue, date.dayOfMonth, date.dayOfWeek.name)
        }
        parseYearMonthOrNull(value)?.let { yearMonth ->
            return DateComponents(year = yearMonth.year, month = yearMonth.monthValue)
        }
        parseMonthDayOrNull(value)?.let { monthDay ->
            return DateComponents(month = monthDay.monthValue, dayOfMonth = monthDay.dayOfMonth)
        }
        parseYearOrNull(value)?.let { year -> return DateComponents(year = year.value) }
        return explicit
    }

    private fun parseLocalDateOrNull(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    private fun parseYearMonthOrNull(value: String): YearMonth? =
        runCatching { YearMonth.parse(value) }.getOrNull()

    private fun parseMonthDayOrNull(value: String): MonthDay? =
        runCatching { MonthDay.parse(value) }.getOrNull()

    private fun parseYearOrNull(value: String): Year? =
        runCatching { Year.parse(value) }.getOrNull()
}

sealed interface DateCapabilityMatrixDecodeResult {
    data class Decoded(val matrix: DateCapabilityMatrix) : DateCapabilityMatrixDecodeResult
    data class Invalid(val message: String) : DateCapabilityMatrixDecodeResult
}

/** Dependency-free wire codec for broadcasts, reports, and durable Lab run snapshots. */
object DateCapabilityMatrixCodec {
    private const val HEADER_V1 = "chronosx-date-matrix-v1"
    private const val HEADER_V2 = "chronosx-date-matrix-v2"
    private const val SEPARATOR = "\t"
    private const val V1_FIELD_COUNT = 6
    private const val V2_FIELD_COUNT = 11

    fun encode(matrix: DateCapabilityMatrix): String = buildString {
        appendLine(HEADER_V2)
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
            append(observation.referenceEpochMillis?.toString().orEmpty())
            append(SEPARATOR)
            append(encodeText(observation.localDate.orEmpty()))
            append(SEPARATOR)
            append(observation.localYear?.toString().orEmpty())
            append(SEPARATOR)
            append(observation.localMonth?.toString().orEmpty())
            append(SEPARATOR)
            append(observation.localDayOfMonth?.toString().orEmpty())
            append(SEPARATOR)
            append(encodeText(observation.localDayOfWeek.orEmpty()))
            append(SEPARATOR)
            append(encodeText(observation.zoneId.orEmpty()))
            append(SEPARATOR)
            append(encodeText(observation.detail.orEmpty()))
            appendLine()
        }
    }.trimEnd()

    fun decode(text: String): DateCapabilityMatrixDecodeResult = runCatching {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()
        require(header == HEADER_V1 || header == HEADER_V2) { "Unsupported date capability matrix." }
        val properties = lines.drop(1).filter { it.startsWith("capturedAt=") || it.startsWith("defaultZone=") }
            .associate { line ->
                val separator = line.indexOf('=')
                line.substring(0, separator) to line.substring(separator + 1)
            }
        val observations = lines.drop(1)
            .filter { it.startsWith("observation=") }
            .map { line -> decodeObservation(line.removePrefix("observation="), header) }
        DateCapabilityMatrix(
            capturedAtEpochMillis = properties.getValue("capturedAt").toLong(),
            defaultZoneId = decodeText(properties.getValue("defaultZone")),
            observations = observations,
        )
    }.fold(
        onSuccess = DateCapabilityMatrixDecodeResult::Decoded,
        onFailure = { DateCapabilityMatrixDecodeResult.Invalid(it.message ?: "Invalid date capability matrix.") },
    )

    private fun decodeObservation(value: String, header: String): DateSurfaceObservation {
        val fields = paddedFields(
            value = value,
            expectedSize = if (header == HEADER_V1) V1_FIELD_COUNT else V2_FIELD_COUNT,
        )
        return if (header == HEADER_V1) {
            DateSurfaceObservation(
                surface = decodeText(fields[0]),
                state = enumValueOf(fields[1]),
                observedEpochMillis = fields[2].toLongOrNull(),
                referenceEpochMillis = fields[2].toLongOrNull(),
                localDate = decodeText(fields[3]).ifBlank { null },
                zoneId = decodeText(fields[4]).ifBlank { null },
                detail = decodeText(fields[5]).ifBlank { null },
            )
        } else {
            DateSurfaceObservation(
                surface = decodeText(fields[0]),
                state = enumValueOf(fields[1]),
                observedEpochMillis = fields[2].toLongOrNull(),
                referenceEpochMillis = fields[3].toLongOrNull(),
                localDate = decodeText(fields[4]).ifBlank { null },
                localYear = fields[5].toIntOrNull(),
                localMonth = fields[6].toIntOrNull(),
                localDayOfMonth = fields[7].toIntOrNull(),
                localDayOfWeek = decodeText(fields[8]).ifBlank { null },
                zoneId = decodeText(fields[9]).ifBlank { null },
                detail = decodeText(fields[10]).ifBlank { null },
            )
        }
    }

    /** Handles empty trailing Base64 fields on Kotlin/JVM implementations consistently. */
    private fun paddedFields(value: String, expectedSize: Int): List<String> {
        val fields = value.split(SEPARATOR)
        require(fields.size in 2..expectedSize) { "Malformed date capability observation." }
        return fields + List(expectedSize - fields.size) { "" }
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
