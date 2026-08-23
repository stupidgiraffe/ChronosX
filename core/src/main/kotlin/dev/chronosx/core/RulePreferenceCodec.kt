package dev.chronosx.core

/** Wire contract used by the manager and injected process through libxposed remote prefs. */
object RulePreferenceCodec {
    const val GROUP = "chronosx.rules.v2"

    fun read(packageName: String, reader: PreferenceReader): TimeRule {
        val base = keyPrefix(packageName)
        return TimeRule(
            packageName = packageName,
            enabled = reader.boolean("${base}enabled", false),
            mode = reader.string("${base}mode", TimeMode.REAL_TIME.name)
                .toTimeModeOrDefault(),
            offsetMillis = reader.long("${base}offsetMillis", 0L),
            fixedEpochMillis = reader.long("${base}fixedEpochMillis", 0L),
            zoneMode = reader.string("${base}zoneMode", ZoneMode.DEVICE_DEFAULT.name)
                .toEnumOrDefault(ZoneMode.DEVICE_DEFAULT),
            zoneId = reader.string("${base}zoneId", "").ifBlank { null },
            monotonicMode = reader.string("${base}monotonicMode", MonotonicMode.PRESERVE.name)
                .toEnumOrDefault(MonotonicMode.PRESERVE),
            monotonicOffsetMillis = reader.long("${base}monotonicOffsetMillis", 0L),
            processPolicy = reader.string("${base}processPolicy", ProcessPolicy.ALL_PROCESSES.name)
                .toEnumOrDefault(ProcessPolicy.ALL_PROCESSES),
            schemaVersion = reader.long("${base}schemaVersion", TimeRule.CURRENT_SCHEMA_VERSION.toLong()).toInt(),
            ruleRevision = reader.long("${base}ruleRevision", 0L),
            updatedAtEpochMillis = reader.long("${base}updatedAtEpochMillis", 0L),
        )
    }

    fun encode(rule: TimeRule): Map<String, Any> {
        val base = keyPrefix(rule.packageName)
        return mapOf(
            "${base}enabled" to rule.enabled,
            "${base}mode" to rule.mode.name,
            "${base}offsetMillis" to rule.offsetMillis,
            "${base}fixedEpochMillis" to rule.fixedEpochMillis,
            "${base}zoneMode" to rule.zoneMode.name,
            "${base}zoneId" to rule.zoneId.orEmpty(),
            "${base}monotonicMode" to rule.monotonicMode.name,
            "${base}monotonicOffsetMillis" to rule.monotonicOffsetMillis,
            "${base}processPolicy" to rule.processPolicy.name,
            "${base}schemaVersion" to rule.schemaVersion.toLong(),
            "${base}ruleRevision" to rule.ruleRevision,
            "${base}updatedAtEpochMillis" to rule.updatedAtEpochMillis,
        )
    }

    fun keysFor(packageName: String): Set<String> {
        val base = keyPrefix(packageName)
        return setOf(
            "${base}enabled",
            "${base}mode",
            "${base}offsetMillis",
            "${base}fixedEpochMillis",
            "${base}zoneMode",
            "${base}zoneId",
            "${base}monotonicMode",
            "${base}monotonicOffsetMillis",
            "${base}processPolicy",
            "${base}schemaVersion",
            "${base}ruleRevision",
            "${base}updatedAtEpochMillis",
        )
    }

    private fun keyPrefix(packageName: String): String = "rule.$packageName."

    private fun String?.toTimeModeOrDefault(): TimeMode =
        runCatching { TimeMode.valueOf(this.orEmpty()) }.getOrDefault(TimeMode.REAL_TIME)

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(defaultValue: T): T =
        runCatching { enumValueOf<T>(this.orEmpty()) }.getOrDefault(defaultValue)
}

interface PreferenceReader {
    fun boolean(key: String, defaultValue: Boolean): Boolean
    fun long(key: String, defaultValue: Long): Long
    fun string(key: String, defaultValue: String): String
}
