package dev.chronosx.lab

import dev.chronosx.data.FrameworkStatus
import dev.chronosx.data.DevicePostureSnapshot
import dev.chronosx.data.ScenarioRunEntity
import dev.chronosx.core.DateCapabilityMatrixCodec
import dev.chronosx.core.DateCapabilityMatrixDecodeResult

/** Produces portable JSON and Markdown evidence without introducing an app-wide JSON dependency. */
object ReportExporter {
    fun json(run: ScenarioRunEntity, framework: FrameworkStatus, posture: DevicePostureSnapshot): String = buildString {
        append("{\n")
        jsonField("reportVersion", "2", raw = true, trailing = true)
        jsonField("runId", run.runId, trailing = true)
        jsonField("scenarioId", run.scenarioId, trailing = true)
        jsonField("scenarioTitle", run.scenarioTitle, trailing = true)
        jsonField("targetPackage", run.targetPackage, trailing = true)
        jsonField("ruleRevision", run.ruleRevision.toString(), raw = true, trailing = true)
        jsonField("fixtureId", run.fixtureId, trailing = true)
        jsonField("scenarioSnapshot", run.scenarioSnapshot, trailing = true)
        jsonField("status", run.status, trailing = true)
        jsonField("summary", run.summary, trailing = true)
        jsonField("startedAtEpochMillis", run.startedAtEpochMillis.toString(), raw = true, trailing = true)
        jsonField("completedAtEpochMillis", run.completedAtEpochMillis?.toString(), raw = true, trailing = true)
        jsonField("observedWallEpochMillis", run.observedWallEpochMillis?.toString(), raw = true, trailing = true)
        jsonField("observedZoneId", run.observedZoneId, trailing = true)
        jsonField("observedProcessName", run.observedProcessName, trailing = true)
        jsonField("observedSurfaces", run.observedSurfaces, trailing = true)
        append("  \"dateCapabilityMatrix\": ").append(dateMatrixJson(run.observedDateMatrix)).append(",\n")
        append("  \"framework\": {\n")
        append("    \"connected\": ${framework.connected},\n")
        append("    \"name\": ${jsonValue(framework.frameworkName)},\n")
        append("    \"version\": ${jsonValue(framework.frameworkVersion)},\n")
        append("    \"apiVersion\": ${framework.apiVersion ?: "null"},\n")
        append("    \"remotePreferencesAvailable\": ${framework.remotePreferencesAvailable}\n")
        append("  },\n")
        append("  \"devicePosture\": {\n")
        append("    \"buildFingerprint\": ${jsonValue(posture.buildFingerprint)},\n")
        append("    \"buildType\": ${jsonValue(posture.buildType)},\n")
        append("    \"testKeysPresent\": ${posture.testKeysPresent},\n")
        append("    \"rootIndicators\": ${jsonValue(posture.rootIndicators.joinToString())},\n")
        append("    \"debuggerConnected\": ${posture.debuggerConnected},\n")
        append("    \"frameworkConnected\": ${posture.frameworkConnected},\n")
        append("    \"emulatorLikely\": ${posture.emulatorLikely},\n")
        append("    \"attestationStatus\": ${jsonValue(posture.attestationStatus)}\n")
        append("  }\n")
        append('}')
    }

    fun markdown(run: ScenarioRunEntity, framework: FrameworkStatus, posture: DevicePostureSnapshot): String = buildString {
        appendLine("# ChronosX Lab report")
        appendLine()
        appendLine("- Run: `${run.runId}`")
        appendLine("- Scenario: ${run.scenarioTitle} (`${run.scenarioId}`)")
        appendLine("- Target: `${run.targetPackage}`")
        appendLine("- Rule revision: ${run.ruleRevision}")
        appendLine("- Status: ${run.status}")
        appendLine("- Fixture: ${run.fixtureId ?: "none"}")
        appendLine("- Scenario snapshot: ${if (run.scenarioSnapshot == null) "not available" else "recorded"}")
        appendLine("- Summary: ${run.summary}")
        appendLine("- Framework: ${framework.frameworkName ?: "unavailable"} API ${framework.apiVersion ?: "—"}")
        appendLine("- Device posture: build=${posture.buildType}, root indicators=${posture.rootIndicators.size}, debugger=${posture.debuggerConnected}, emulator likely=${posture.emulatorLikely}")
        run.observedZoneId?.let { appendLine("- Observed zone: $it") }
        run.observedProcessName?.let { appendLine("- Observed process: $it") }
        run.observedSurfaces?.let { appendLine("- Observed surfaces: $it") }
        appendDateMatrix(run.observedDateMatrix)
        appendLine()
        appendLine("> Benchmark observations are supplied by an authorized test target and are self-reported evidence.")
    }

    private fun StringBuilder.jsonField(name: String, value: String?, raw: Boolean = false, trailing: Boolean) {
        append("  \"").append(name).append("\": ")
        append(if (raw && value != null) value else jsonValue(value))
        if (trailing) append(',')
        append('\n')
    }

    private fun jsonValue(value: String?): String = value?.let { "\"${escape(it)}\"" } ?: "null"

    private fun dateMatrixJson(encoded: String?): String {
        val matrix = encoded
            ?.let(DateCapabilityMatrixCodec::decode)
            ?.let { it as? DateCapabilityMatrixDecodeResult.Decoded }
            ?.matrix
            ?: return "null"
        return buildString {
            append("{\"capturedAtEpochMillis\":${matrix.capturedAtEpochMillis},")
            append("\"defaultZoneId\":").append(jsonValue(matrix.defaultZoneId)).append(",")
            append("\"observations\":[")
            matrix.observations.forEachIndexed { index, observation ->
                if (index > 0) append(',')
                append('{')
                append("\"surface\":").append(jsonValue(observation.surface)).append(',')
                append("\"state\":").append(jsonValue(observation.state.name)).append(',')
                append("\"observedEpochMillis\":")
                    .append(observation.observedEpochMillis?.toString() ?: "null").append(',')
                append("\"localDate\":").append(jsonValue(observation.localDate)).append(',')
                append("\"zoneId\":").append(jsonValue(observation.zoneId)).append(',')
                append("\"detail\":").append(jsonValue(observation.detail))
                append('}')
            }
            append("]}")
        }
    }

    private fun StringBuilder.appendDateMatrix(encoded: String?) {
        val matrix = encoded
            ?.let(DateCapabilityMatrixCodec::decode)
            ?.let { it as? DateCapabilityMatrixDecodeResult.Decoded }
            ?.matrix
            ?: return
        val observed = matrix.observations.count { it.state.name == "OBSERVED" }
        val errors = matrix.observations.count { it.state.name == "ERROR" }
        appendLine("- Date capability matrix: $observed/${matrix.observations.size} sampled, $errors errors, default zone=${matrix.defaultZoneId}")
        matrix.observations.forEach { observation ->
            appendLine(
                "  - ${observation.surface}: ${observation.state.name.lowercase()}" +
                    (observation.localDate?.let { ", date=$it" } ?: "") +
                    (observation.zoneId?.let { ", zone=$it" } ?: "") +
                    (observation.detail?.let { ", detail=$it" } ?: ""),
            )
        }
    }

    private fun escape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
