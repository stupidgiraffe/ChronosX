package dev.chronosx.core

/** Wire contract used by the manager and injected process through libxposed remote prefs. */
object RulePreferenceCodec {
    const val GROUP = "chronosx.rules.v1"

    fun read(packageName: String, reader: PreferenceReader): TimeRule {
        val base = keyPrefix(packageName)
        return TimeRule(
            packageName = packageName,
            enabled = reader.boolean("${base}enabled", false),
            mode = reader.string("${base}mode", TimeMode.REAL_TIME.name)
                .toTimeModeOrDefault(),
            offsetMillis = reader.long("${base}offsetMillis", 0L),
            fixedEpochMillis = reader.long("${base}fixedEpochMillis", 0L),
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
            "${base}updatedAtEpochMillis",
        )
    }

    private fun keyPrefix(packageName: String): String = "rule.$packageName."

    private fun String?.toTimeModeOrDefault(): TimeMode =
        runCatching { TimeMode.valueOf(this.orEmpty()) }.getOrDefault(TimeMode.REAL_TIME)
}

interface PreferenceReader {
    fun boolean(key: String, defaultValue: Boolean): Boolean
    fun long(key: String, defaultValue: Long): Long
    fun string(key: String, defaultValue: String): String
}
