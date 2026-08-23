package dev.chronosx.core

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Portable, package-independent policy preset. Profiles are intentionally independent from a
 * target package so they can be reused across customer-owned test applications.
 */
data class TemporalProfile(
    val id: String,
    val name: String,
    val description: String,
    val mode: TimeMode = TimeMode.REAL_TIME,
    val offsetMillis: Long = 0L,
    val fixedEpochMillis: Long = 0L,
    val zoneMode: ZoneMode = ZoneMode.DEVICE_DEFAULT,
    val zoneId: String? = null,
    val monotonicMode: MonotonicMode = MonotonicMode.PRESERVE,
    val monotonicOffsetMillis: Long = 0L,
    val processPolicy: ProcessPolicy = ProcessPolicy.ALL_PROCESSES,
) {
    fun applyTo(packageName: String, enabled: Boolean = true): TimeRule = TimeRule(
        packageName = packageName,
        enabled = enabled,
        mode = mode,
        offsetMillis = offsetMillis,
        fixedEpochMillis = fixedEpochMillis,
        zoneMode = zoneMode,
        zoneId = zoneId,
        monotonicMode = monotonicMode,
        monotonicOffsetMillis = monotonicOffsetMillis,
        processPolicy = processPolicy,
    )

    companion object {
        fun fromRule(name: String, description: String, rule: TimeRule): TemporalProfile = TemporalProfile(
            id = "custom-${rule.packageName}",
            name = name,
            description = description,
            mode = rule.mode,
            offsetMillis = rule.offsetMillis,
            fixedEpochMillis = rule.fixedEpochMillis,
            zoneMode = rule.zoneMode,
            zoneId = rule.zoneId,
            monotonicMode = rule.monotonicMode,
            monotonicOffsetMillis = rule.monotonicOffsetMillis,
            processPolicy = rule.processPolicy,
        )
    }
}

/** A dependency-free, versioned export format for profile interchange and review. */
object TemporalProfileCodec {
    private const val HEADER = "chronosx-profile-v1"

    fun encode(profile: TemporalProfile): String = buildString {
        appendLine(HEADER)
        appendLine("id=${encodeText(profile.id)}")
        appendLine("name=${encodeText(profile.name)}")
        appendLine("description=${encodeText(profile.description)}")
        appendLine("mode=${profile.mode.name}")
        appendLine("offsetMillis=${profile.offsetMillis}")
        appendLine("fixedEpochMillis=${profile.fixedEpochMillis}")
        appendLine("zoneMode=${profile.zoneMode.name}")
        appendLine("zoneId=${encodeText(profile.zoneId.orEmpty())}")
        appendLine("monotonicMode=${profile.monotonicMode.name}")
        appendLine("monotonicOffsetMillis=${profile.monotonicOffsetMillis}")
        appendLine("processPolicy=${profile.processPolicy.name}")
    }.trimEnd()

    fun decode(text: String): ProfileImportResult {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != HEADER) return ProfileImportResult.Invalid("Unsupported profile format.")

        val values = lines.drop(1).associate { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return ProfileImportResult.Invalid("Malformed profile entry.")
            line.substring(0, separator) to line.substring(separator + 1)
        }
        return runCatching {
            TemporalProfile(
                id = decodeText(values.required("id")),
                name = decodeText(values.required("name")),
                description = decodeText(values.required("description")),
                mode = enumValueOf<TimeMode>(values.required("mode")),
                offsetMillis = values.required("offsetMillis").toLong(),
                fixedEpochMillis = values.required("fixedEpochMillis").toLong(),
                zoneMode = enumValueOf<ZoneMode>(values.required("zoneMode")),
                zoneId = decodeText(values.required("zoneId")).ifBlank { null },
                monotonicMode = enumValueOf<MonotonicMode>(values.required("monotonicMode")),
                monotonicOffsetMillis = values.required("monotonicOffsetMillis").toLong(),
                processPolicy = enumValueOf<ProcessPolicy>(values.required("processPolicy")),
            )
        }.fold(
            onSuccess = ProfileImportResult::Imported,
            onFailure = { ProfileImportResult.Invalid(it.message ?: "Invalid profile.") },
        )
    }

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: error("Missing profile field: $key")

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}

sealed interface ProfileImportResult {
    data class Imported(val profile: TemporalProfile) : ProfileImportResult
    data class Invalid(val reason: String) : ProfileImportResult
}
