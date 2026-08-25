package dev.chronosx.labsdk

import android.content.Intent
import dev.chronosx.core.BenchmarkProtocol
import dev.chronosx.core.ControlledFixture
import dev.chronosx.core.FixtureResponseKind

/** Parsed launch metadata for an authorized mock or customer-owned target application. */
data class ScenarioLaunchConfiguration(
    val runId: String,
    val runToken: String,
    val scenarioId: String,
    val ruleRevision: Long,
    val controlledFixture: ControlledFixture?,
) {
    companion object {
        fun fromIntent(intent: Intent): ScenarioLaunchConfiguration? {
            val runId = intent.getStringExtra(BenchmarkProtocol.EXTRA_RUN_ID).orEmpty()
            val runToken = intent.getStringExtra(BenchmarkProtocol.EXTRA_RUN_TOKEN).orEmpty()
            val scenarioId = intent.getStringExtra(BenchmarkProtocol.EXTRA_SCENARIO_ID).orEmpty()
            if (runId.isBlank() || runToken.isBlank() || scenarioId.isBlank()) return null
            val fixtureId = intent.getStringExtra(BenchmarkProtocol.EXTRA_FIXTURE_ID)
            val fixture = fixtureId?.takeIf { it.isNotBlank() }?.let { id ->
                val kind = intent.getStringExtra(BenchmarkProtocol.EXTRA_FIXTURE_RESPONSE_KIND)
                    ?.let { value -> runCatching { FixtureResponseKind.valueOf(value) }.getOrNull() }
                    ?: FixtureResponseKind.VALID
                ControlledFixture(
                    id = id,
                    responseKind = kind,
                    delayMillis = intent.getLongExtra(BenchmarkProtocol.EXTRA_FIXTURE_DELAY_MILLIS, 0L),
                )
            }
            return ScenarioLaunchConfiguration(
                runId = runId,
                runToken = runToken,
                scenarioId = scenarioId,
                ruleRevision = intent.getLongExtra(BenchmarkProtocol.EXTRA_RULE_REVISION, 0L),
                controlledFixture = fixture,
            )
        }
    }

    /** Loopback-only fixture route exposed by ChronosX Lab server for owned mock/staging builds. */
    fun loopbackFixtureUrl(port: Int = 8787): String? = controlledFixture?.let { fixture ->
        "http://127.0.0.1:$port/v1/time-policy?fixture=${fixture.id}" +
            "&kind=${fixture.responseKind.name}&delayMillis=${fixture.delayMillis}"
    }
}
