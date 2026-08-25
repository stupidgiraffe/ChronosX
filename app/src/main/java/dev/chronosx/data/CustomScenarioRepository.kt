package dev.chronosx.data

import dev.chronosx.core.CustomScenario
import dev.chronosx.core.CustomScenarioCodec
import dev.chronosx.core.CustomScenarioDecodeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists editable Lab scenarios without coupling the database to the profile schema. */
class CustomScenarioRepository(
    private val dao: CustomScenarioDao,
) {
    val scenarios: Flow<List<CustomScenario>> = dao.observeAll().map { entries ->
        entries.mapNotNull { entry ->
            (CustomScenarioCodec.decode(entry.payload) as? CustomScenarioDecodeResult.Decoded)?.scenario
        }
    }

    suspend fun save(scenario: CustomScenario) {
        require(scenario.id.isNotBlank()) { "Scenario ID is required." }
        require(scenario.title.isNotBlank()) { "Scenario title is required." }
        dao.upsert(
            CustomScenarioEntity(
                id = scenario.id,
                title = scenario.title.trim(),
                updatedAtEpochMillis = scenario.updatedAtEpochMillis,
                payload = CustomScenarioCodec.encode(scenario),
            ),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)
}
