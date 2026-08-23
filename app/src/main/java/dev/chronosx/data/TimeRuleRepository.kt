package dev.chronosx.data

import dev.chronosx.core.PackageTargetPolicy
import dev.chronosx.core.TimeRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimeRuleRepository(
    private val ruleDao: TimeRuleDao,
    private val frameworkBridge: FrameworkBridge,
) {
    val rules: Flow<List<TimeRule>> = ruleDao.observeAll().map { entries -> entries.map(TimeRuleEntity::toDomain) }

    suspend fun ruleFor(packageName: String): TimeRule =
        ruleDao.get(packageName)?.toDomain() ?: TimeRule.disabled(packageName)

    suspend fun save(rule: TimeRule): RuleApplyResult {
        val assessment = PackageTargetPolicy.assess(rule.packageName)
        if (assessment is dev.chronosx.core.TargetAssessment.Rejected) {
            return RuleApplyResult.Rejected(assessment.reason)
        }

        val previous = ruleDao.get(rule.packageName)
        val persisted = rule.copy(
            schemaVersion = TimeRule.CURRENT_SCHEMA_VERSION,
            ruleRevision = (previous?.ruleRevision ?: 0L) + 1L,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        ruleDao.upsert(persisted.toEntity())

        val remote = frameworkBridge.writeRule(persisted)
        if (remote != FrameworkActionResult.Success) {
            return RuleApplyResult.StoredLocally(remote.userMessage())
        }

        val scopeResult = if (persisted.enabled) {
            frameworkBridge.requestScope(persisted.packageName)
        } else {
            frameworkBridge.removeScope(persisted.packageName)
        }

        return when (scopeResult) {
            FrameworkActionResult.Success -> RuleApplyResult.Applied(persisted)
            else -> RuleApplyResult.StoredLocally(scopeResult.userMessage())
        }
    }

    suspend fun synchronizeAll(): SyncResult {
        val savedRules = ruleDao.getAllOnce()
        var synchronized = 0
        val failures = mutableListOf<String>()

        for (entity in savedRules) {
            val rule = entity.toDomain()
            when (val remote = frameworkBridge.writeRule(rule)) {
                FrameworkActionResult.Success -> {
                    val scope = if (rule.enabled) {
                        frameworkBridge.requestScope(rule.packageName)
                    } else {
                        frameworkBridge.removeScope(rule.packageName)
                    }
                    if (scope == FrameworkActionResult.Success) {
                        synchronized += 1
                    } else {
                        failures += "${rule.packageName}: ${scope.userMessage()}"
                    }
                }

                else -> failures += "${rule.packageName}: ${remote.userMessage()}"
            }
        }
        return SyncResult(synchronized, failures)
    }

    suspend fun delete(packageName: String): FrameworkActionResult {
        val previous = ruleDao.get(packageName)
        val disabled = TimeRule.disabled(packageName).copy(
            ruleRevision = (previous?.ruleRevision ?: 0L) + 1L,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        ruleDao.upsert(disabled.toEntity())
        val remote = frameworkBridge.writeRule(disabled)
        frameworkBridge.removeScope(packageName)
        ruleDao.delete(packageName)
        return remote
    }
}

private fun FrameworkActionResult.userMessage(): String = when (this) {
    FrameworkActionResult.Success -> "Applied."
    FrameworkActionResult.Unavailable -> "Saved locally. Connect a libxposed API 102 framework to apply it."
    is FrameworkActionResult.Failed -> "Saved locally. $message"
}

sealed interface RuleApplyResult {
    data class Applied(val rule: TimeRule) : RuleApplyResult
    data class StoredLocally(val message: String) : RuleApplyResult
    data class Rejected(val message: String) : RuleApplyResult
}

data class SyncResult(
    val synchronizedRules: Int,
    val failures: List<String>,
)
