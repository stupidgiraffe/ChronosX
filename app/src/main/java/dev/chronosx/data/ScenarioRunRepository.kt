package dev.chronosx.data

import dev.chronosx.core.LabScenario
import dev.chronosx.core.ScenarioRunStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ScenarioRunRepository(
    private val dao: ScenarioRunDao,
    private val timeRuleRepository: TimeRuleRepository,
    private val targetLauncher: TargetLauncher,
) {
    val runs: Flow<List<ScenarioRunEntity>> = dao.observeLatest()

    suspend fun run(scenario: LabScenario, packageName: String): ScenarioRunExecution {
        val applyResult = timeRuleRepository.save(scenario.profile.applyTo(packageName))
        val persistedRule = timeRuleRepository.ruleFor(packageName)
        val runId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val base = ScenarioRunEntity(
            runId = runId,
            scenarioId = scenario.id,
            scenarioTitle = scenario.title,
            targetPackage = packageName,
            ruleRevision = persistedRule.ruleRevision,
            fixtureId = scenario.controlledFixture?.id,
            status = ScenarioRunStatus.PREPARING.name,
            summary = "Preparing rule revision ${persistedRule.ruleRevision}.",
            startedAtEpochMillis = now,
            completedAtEpochMillis = null,
            observedAtEpochMillis = null,
            observedWallEpochMillis = null,
            observedZoneId = null,
            observedProcessName = null,
            observedSurfaces = null,
        )
        dao.upsert(base)

        if (applyResult is RuleApplyResult.Rejected) {
            dao.updateStatus(runId, ScenarioRunStatus.FAILED.name, applyResult.message, System.currentTimeMillis())
            return ScenarioRunExecution(runId, ScenarioRunStatus.FAILED, applyResult.message)
        }

        if (applyResult is RuleApplyResult.StoredLocally) {
            dao.updateStatus(runId, ScenarioRunStatus.PENDING_FRAMEWORK.name, applyResult.message, null)
            return ScenarioRunExecution(runId, ScenarioRunStatus.PENDING_FRAMEWORK, applyResult.message)
        }

        val applyMessage = when (applyResult) {
            is RuleApplyResult.Applied -> "Rule revision ${applyResult.rule.ruleRevision} applied."
            is RuleApplyResult.StoredLocally -> error("Handled above")
            is RuleApplyResult.Rejected -> error("Handled above")
        }
        dao.updateStatus(runId, ScenarioRunStatus.APPLIED.name, applyMessage, null)

        return when (val launch = targetLauncher.launch(packageName, runId, scenario.id, persistedRule.ruleRevision)) {
            LaunchResult.Launched -> {
                val summary = "$applyMessage Target launch requested; await an authorized benchmark observation."
                dao.updateStatus(runId, ScenarioRunStatus.LAUNCHED.name, summary, null)
                ScenarioRunExecution(runId, ScenarioRunStatus.LAUNCHED, summary)
            }

            is LaunchResult.Unavailable -> {
                dao.updateStatus(runId, ScenarioRunStatus.APPLIED.name, "$applyMessage ${launch.message}", null)
                ScenarioRunExecution(runId, ScenarioRunStatus.APPLIED, "$applyMessage ${launch.message}")
            }

            is LaunchResult.Failed -> {
                dao.updateStatus(runId, ScenarioRunStatus.FAILED.name, "$applyMessage ${launch.message}", System.currentTimeMillis())
                ScenarioRunExecution(runId, ScenarioRunStatus.FAILED, "$applyMessage ${launch.message}")
            }
        }
    }

    suspend fun recordObservation(observation: BenchmarkObservation) {
        val now = System.currentTimeMillis()
        val status = if (observation.passed) ScenarioRunStatus.OBSERVED_PASS else ScenarioRunStatus.OBSERVED_FAIL
        val summary = buildString {
            append(if (observation.passed) "Benchmark passed" else "Benchmark failed")
            append(" from ").append(observation.sourcePackage)
            observation.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }
        dao.recordObservation(
            runId = observation.runId,
            status = status.name,
            summary = summary,
            completedAt = now,
            observedAt = now,
            observedWall = observation.observedWallEpochMillis,
            observedZone = observation.observedZoneId,
            processName = observation.processName,
            surfaces = observation.observedSurfaces,
        )
    }
}

data class ScenarioRunExecution(
    val runId: String,
    val status: ScenarioRunStatus,
    val message: String,
)
