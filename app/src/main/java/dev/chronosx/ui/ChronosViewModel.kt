package dev.chronosx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.chronosx.AppContainer
import dev.chronosx.data.DebugLogEntity
import dev.chronosx.data.DevicePostureSnapshot
import dev.chronosx.data.FrameworkStatus
import dev.chronosx.data.InstalledApplication
import dev.chronosx.data.RuleApplyResult
import dev.chronosx.data.RunningTarget
import dev.chronosx.data.ScenarioRunEntity
import dev.chronosx.data.SyncResult
import dev.chronosx.core.LabScenario
import dev.chronosx.core.ScenarioRunStatus
import dev.chronosx.core.TemporalProfile
import dev.chronosx.core.TemporalProfileCodec
import dev.chronosx.core.TimeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChronosViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(ChronosUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            container.timeRuleRepository.rules.collectLatest { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
        viewModelScope.launch {
            container.debugLogRepository.entries.collectLatest { entries ->
                _uiState.update { it.copy(logs = entries) }
            }
        }
        viewModelScope.launch {
            container.frameworkBridge.status.collectLatest { status ->
                _uiState.update { it.copy(frameworkStatus = status) }
            }
        }
        viewModelScope.launch {
            container.scenarioRunRepository.runs.collectLatest { runs ->
                _uiState.update { it.copy(scenarioRuns = runs) }
            }
        }
        refreshDiagnostics()
    }

    fun loadInstalledApplications() {
        if (_uiState.value.appsLoaded || _uiState.value.appsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(appsLoading = true) }
            val applications = withContext(Dispatchers.IO) {
                container.installedAppsRepository.listUserApplications()
            }
            _uiState.update {
                it.copy(
                    applications = applications,
                    appsLoading = false,
                    appsLoaded = true,
                )
            }
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshingDiagnostics = true) }
            withContext(Dispatchers.IO) {
                container.frameworkBridge.refreshStatus()
                val targets = container.frameworkBridge.runningTargets()
                val posture = container.devicePostureCollector.collect(container.frameworkBridge.status.value)
                _uiState.update { it.copy(activeTargets = targets, devicePosture = posture) }
            }
            _uiState.update { it.copy(refreshingDiagnostics = false) }
        }
    }

    fun saveRule(rule: TimeRule) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.timeRuleRepository.save(rule)
            }
            val message = when (result) {
                is RuleApplyResult.Applied -> {
                    container.debugLogRepository.info(
                        "Rule",
                        "Applied revision ${result.rule.ruleRevision} (${result.rule.mode}) for ${result.rule.packageName}.",
                    )
                    "${result.rule.packageName} revision ${result.rule.ruleRevision} is active."
                }

                is RuleApplyResult.StoredLocally -> {
                    container.debugLogRepository.warn("Rule", result.message)
                    result.message
                }

                is RuleApplyResult.Rejected -> {
                    container.debugLogRepository.error("Rule", result.message)
                    result.message
                }
            }
            _messages.emit(message)
            refreshDiagnostics()
        }
    }

    fun deleteRule(packageName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.timeRuleRepository.delete(packageName)
            }
            container.debugLogRepository.info("Rule", "Removed the rule for $packageName.")
            _messages.emit(
                when (result) {
                    dev.chronosx.data.FrameworkActionResult.Success -> "Rule removed."
                    else -> "Rule removed locally; framework sync is pending."
                },
            )
            refreshDiagnostics()
        }
    }

    fun synchronizeSavedRules() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.timeRuleRepository.synchronizeAll()
            }
            logSynchronization(result)
            val suffix = if (result.failures.isEmpty()) "" else " ${result.failures.size} need attention."
            _messages.emit("Synchronized ${result.synchronizedRules} saved rules.$suffix")
            refreshDiagnostics()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.debugLogRepository.clear() }
        }
    }

    fun ruleFor(packageName: String): TimeRule =
        _uiState.value.rules.firstOrNull { it.packageName == packageName } ?: TimeRule.disabled(packageName)

    fun runScenario(packageName: String, scenario: LabScenario) {
        viewModelScope.launch {
            val execution = withContext(Dispatchers.IO) {
                container.scenarioRunRepository.run(scenario, packageName)
            }
            val source = when (execution.status) {
                ScenarioRunStatus.FAILED, ScenarioRunStatus.OBSERVED_FAIL -> "Scenario failed"
                ScenarioRunStatus.PENDING_FRAMEWORK -> "Scenario pending"
                else -> "Scenario"
            }
            container.debugLogRepository.info(source, "${scenario.id}: ${execution.message}")
            _messages.emit(execution.message)
            refreshDiagnostics()
        }
    }

    fun applyProfile(packageName: String, profile: TemporalProfile) {
        saveRule(profile.applyTo(packageName))
    }

    fun applyTomorrow(packageName: String) {
        val current = ruleFor(packageName)
        saveRule(
            current.copy(
                enabled = true,
                mode = dev.chronosx.core.TimeMode.OFFSET,
                offsetMillis = 86_400_000L,
            ),
        )
    }

    fun profileExport(packageName: String): String =
        TemporalProfileCodec.encode(
            TemporalProfile.fromRule(
                name = "${packageName} profile",
                description = "Exported from ChronosX Manager.",
                rule = ruleFor(packageName),
            ),
        )

    private suspend fun logSynchronization(result: SyncResult) {
        if (result.failures.isEmpty()) {
            container.debugLogRepository.info("Framework", "Synchronized ${result.synchronizedRules} rules.")
        } else {
            container.debugLogRepository.warn("Framework", result.failures.joinToString(separator = " | "))
        }
    }
}

data class ChronosUiState(
    val rules: List<TimeRule> = emptyList(),
    val applications: List<InstalledApplication> = emptyList(),
    val appsLoading: Boolean = false,
    val appsLoaded: Boolean = false,
    val logs: List<DebugLogEntity> = emptyList(),
    val frameworkStatus: FrameworkStatus = FrameworkStatus.disconnected(),
    val activeTargets: List<RunningTarget> = emptyList(),
    val devicePosture: DevicePostureSnapshot = DevicePostureSnapshot.unavailable(),
    val scenarioRuns: List<ScenarioRunEntity> = emptyList(),
    val refreshingDiagnostics: Boolean = false,
)

class ChronosViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(ChronosViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return ChronosViewModel(container) as T
    }
}
