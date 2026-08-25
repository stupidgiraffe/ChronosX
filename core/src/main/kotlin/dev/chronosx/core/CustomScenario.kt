package dev.chronosx.core

import java.nio.charset.StandardCharsets
import java.util.Base64

/** A user-authored, reusable Lab scenario. Built-in scenarios remain immutable templates. */
data class CustomScenario(
    val id: String,
    val title: String,
    val description: String,
    val category: ScenarioCategory,
    val profile: TemporalProfile,
    val expectedObservation: String,
    val controlledFixture: ControlledFixture? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toLabScenario(): LabScenario = LabScenario(
        id = id,
        title = title,
        description = description,
        category = category,
        profile = profile,
        expectedObservation = expectedObservation,
        controlledFixture = controlledFixture,
    )

    companion object {
        fun fromTemplate(
            template: LabScenario,
            id: String,
            nowEpochMillis: Long,
        ): CustomScenario = CustomScenario(
            id = id,
            title = "${template.title} custom",
            description = template.description,
            category = template.category,
            profile = template.profile.copy(
                id = "custom-$id",
                name = "${template.profile.name} custom",
            ),
            expectedObservation = template.expectedObservation,
            controlledFixture = template.controlledFixture,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }
}

sealed interface CustomScenarioDecodeResult {
    data class Decoded(val scenario: CustomScenario) : CustomScenarioDecodeResult
    data class Invalid(val message: String) : CustomScenarioDecodeResult
}

/** Portable persisted/export format for custom Lab scenarios and run snapshots. */
object CustomScenarioCodec {
    private const val HEADER = "chronosx-custom-scenario-v1"

    fun encode(scenario: CustomScenario): String = buildString {
        appendLine(HEADER)
        appendLine("id=${encodeText(scenario.id)}")
        appendLine("title=${encodeText(scenario.title)}")
        appendLine("description=${encodeText(scenario.description)}")
        appendLine("category=${scenario.category.name}")
        appendLine("expectedObservation=${encodeText(scenario.expectedObservation)}")
        appendLine("fixtureId=${encodeText(scenario.controlledFixture?.id.orEmpty())}")
        appendLine("fixtureKind=${scenario.controlledFixture?.responseKind?.name.orEmpty()}")
        appendLine("fixtureDelayMillis=${scenario.controlledFixture?.delayMillis ?: 0L}")
        appendLine("createdAt=${scenario.createdAtEpochMillis}")
        appendLine("updatedAt=${scenario.updatedAtEpochMillis}")
        appendLine("profile=${encodeText(TemporalProfileCodec.encode(scenario.profile))}")
    }.trimEnd()

    fun decode(text: String): CustomScenarioDecodeResult = runCatching {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported custom scenario format." }
        val values = lines.drop(1).associate { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Malformed custom scenario entry." }
            line.substring(0, separator) to line.substring(separator + 1)
        }
        val profileResult = TemporalProfileCodec.decode(decodeText(values.getValue("profile")))
        val profile = (profileResult as? ProfileImportResult.Imported)?.profile
            ?: error((profileResult as? ProfileImportResult.Invalid)?.reason ?: "Invalid scenario profile.")
        val fixtureId = decodeText(values.getValue("fixtureId"))
        val fixtureKind = values.getValue("fixtureKind")
        CustomScenario(
            id = decodeText(values.getValue("id")),
            title = decodeText(values.getValue("title")),
            description = decodeText(values.getValue("description")),
            category = enumValueOf(values.getValue("category")),
            profile = profile,
            expectedObservation = decodeText(values.getValue("expectedObservation")),
            controlledFixture = fixtureId.takeIf { it.isNotBlank() }?.let {
                ControlledFixture(
                    id = it,
                    responseKind = enumValueOf(fixtureKind),
                    delayMillis = values.getValue("fixtureDelayMillis").toLong(),
                )
            },
            createdAtEpochMillis = values.getValue("createdAt").toLong(),
            updatedAtEpochMillis = values.getValue("updatedAt").toLong(),
        )
    }.fold(
        onSuccess = CustomScenarioDecodeResult::Decoded,
        onFailure = { CustomScenarioDecodeResult.Invalid(it.message ?: "Invalid custom scenario.") },
    )

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
