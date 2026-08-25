package dev.chronosx.core

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Immutable, portable snapshot format used in run evidence. A scenario run keeps this snapshot
 * even if its source custom scenario is later edited or deleted.
 */
object LabScenarioCodec {
    private const val HEADER = "chronosx-lab-scenario-v1"

    fun encode(scenario: LabScenario): String = buildString {
        appendLine(HEADER)
        appendLine("id=${encodeText(scenario.id)}")
        appendLine("title=${encodeText(scenario.title)}")
        appendLine("description=${encodeText(scenario.description)}")
        appendLine("category=${scenario.category.name}")
        appendLine("expectedObservation=${encodeText(scenario.expectedObservation)}")
        appendLine("fixtureId=${encodeText(scenario.controlledFixture?.id.orEmpty())}")
        appendLine("fixtureKind=${scenario.controlledFixture?.responseKind?.name.orEmpty()}")
        appendLine("fixtureDelayMillis=${scenario.controlledFixture?.delayMillis ?: 0L}")
        appendLine("profile=${encodeText(TemporalProfileCodec.encode(scenario.profile))}")
    }.trimEnd()

    fun decode(text: String): LabScenarioDecodeResult = runCatching {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported Lab scenario format." }
        val values = lines.drop(1).associate { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Malformed Lab scenario entry." }
            line.substring(0, separator) to line.substring(separator + 1)
        }
        val profileResult = TemporalProfileCodec.decode(decodeText(values.getValue("profile")))
        val profile = (profileResult as? ProfileImportResult.Imported)?.profile
            ?: error((profileResult as? ProfileImportResult.Invalid)?.reason ?: "Invalid scenario profile.")
        val fixtureId = decodeText(values.getValue("fixtureId"))
        LabScenario(
            id = decodeText(values.getValue("id")),
            title = decodeText(values.getValue("title")),
            description = decodeText(values.getValue("description")),
            category = enumValueOf(values.getValue("category")),
            profile = profile,
            expectedObservation = decodeText(values.getValue("expectedObservation")),
            controlledFixture = fixtureId.takeIf { it.isNotBlank() }?.let { id ->
                ControlledFixture(
                    id = id,
                    responseKind = enumValueOf(values.getValue("fixtureKind")),
                    delayMillis = values.getValue("fixtureDelayMillis").toLong(),
                )
            },
        )
    }.fold(
        onSuccess = LabScenarioDecodeResult::Decoded,
        onFailure = { LabScenarioDecodeResult.Invalid(it.message ?: "Invalid Lab scenario.") },
    )

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}

sealed interface LabScenarioDecodeResult {
    data class Decoded(val scenario: LabScenario) : LabScenarioDecodeResult
    data class Invalid(val message: String) : LabScenarioDecodeResult
}
