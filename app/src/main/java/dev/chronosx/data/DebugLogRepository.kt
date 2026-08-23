package dev.chronosx.data

import kotlinx.coroutines.flow.Flow

class DebugLogRepository(private val dao: DebugLogDao) {
    val entries: Flow<List<DebugLogEntity>> = dao.observeLatest()

    suspend fun info(source: String, message: String) = append("INFO", source, message)

    suspend fun warn(source: String, message: String) = append("WARN", source, message)

    suspend fun error(source: String, message: String) = append("ERROR", source, message)

    suspend fun clear() = dao.clear()

    private suspend fun append(level: String, source: String, message: String) {
        dao.insert(
            DebugLogEntity(
                timestampEpochMillis = System.currentTimeMillis(),
                level = level,
                source = source,
                message = message,
            ),
        )
    }
}
