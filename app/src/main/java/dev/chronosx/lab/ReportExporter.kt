package dev.chronosx.lab

import dev.chronosx.data.FrameworkStatus
import dev.chronosx.data.DevicePostureSnapshot
import dev.chronosx.data.ScenarioRunEntity

/** Produces portable JSON and Markdown evidence without introducing an app-wide JSON dependency. */
object ReportExporter {
    fun json(run: ScenarioRunEntity, framework: FrameworkStatus, posture: DevicePostureSnapshot): String = buildString {
        append("{\n")
        jsonField("reportVersion", "1", raw = true, trailing = true)
        jsonField("runId", run.runId, trailing = true)
        jsonField("scenarioId", run.scenarioId, trailing = true)
        jsonField("scenarioTitle", run.scenarioTitle, trailing = true)
        jsonField("targetPackage", run.targetPackage, trailing = true)
        jsonField("ruleRevision", run.ruleRevision.toString(), raw = true, trailing = true)
        jsonField("fixtureId", run.fixtureId, trailing = true)
        jsonField("status", run.status, trailing = true)
        jsonField("summary", run.summary, trailing = true)
        jsonField("startedAtEpochMillis", run.startedAtEpochMillis.toString(), raw = true, trailing = true)
        jsonField("completedAtEpochMillis", run.completedAtEpochMillis?.toString(), raw = true, trailing = true)
        jsonField("observedWallEpochMillis", run.observedWallEpochMillis?.toString(), raw = true, trailing = true)
        jsonField("observedZoneId", run.observedZoneId, trailing = true)
        jsonField("observedProcessName", run.observedProcessName, trailing = true)
        jsonField("observedSurfaces", run.observedSurfaces, trailing = true)
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
        appendLine("- Summary: ${run.summary}")
        appendLine("- Framework: ${framework.frameworkName ?: "unavailable"} API ${framework.apiVersion ?: "—"}")
        appendLine("- Device posture: build=${posture.buildType}, root indicators=${posture.rootIndicators.size}, debugger=${posture.debuggerConnected}, emulator likely=${posture.emulatorLikely}")
        run.observedZoneId?.let { appendLine("- Observed zone: $it") }
        run.observedProcessName?.let { appendLine("- Observed process: $it") }
        run.observedSurfaces?.let { appendLine("- Observed surfaces: $it") }
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
