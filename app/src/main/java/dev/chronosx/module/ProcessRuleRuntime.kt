package dev.chronosx.module

import android.content.SharedPreferences
import android.os.SystemClock
import dev.chronosx.core.MonotonicAnchor
import dev.chronosx.core.PreferenceReader
import dev.chronosx.core.RulePreferenceCodec
import dev.chronosx.core.TimeEngine
import dev.chronosx.core.TimeRule
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe, process-local view of one package's remote rule.
 *
 * Clock hooks call into this object on application threads. Configuration is delivered by the
 * framework's remote [SharedPreferences], so snapshots are atomically replaced rather than being
 * mutated field-by-field. The small bypass scopes prevent hooks from virtualizing ChronosX's own
 * source-clock reads or a nested time API while it is being normalized.
 */
internal class ProcessRuleRuntime(
    private val packageName: String,
    private val preferences: SharedPreferences,
    private val logger: ModuleLogger,
) {
    private val bypassDepth = ThreadLocal.withInitial { 0 }
    private val constructionDepth = ThreadLocal.withInitial { 0 }
    private val interestedKeys = RulePreferenceCodec.keysFor(packageName)

    private val snapshot = AtomicReference(
        RuntimeSnapshot(
            rule = TimeRule.disabled(packageName),
            anchor = TimeEngine.createMonotonicAnchor(
                rule = TimeRule.disabled(packageName),
                sourceMillis = 0L,
                sourceNanos = 0L,
            ),
        ),
    )

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in interestedKeys) refresh()
    }

    fun start() {
        refresh()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun rule(): TimeRule = snapshot.get().rule

    fun shouldBypassHooks(): Boolean = (bypassDepth.get() ?: 0) > 0 || (constructionDepth.get() ?: 0) > 0

    fun virtualEpochMillis(realEpochMillis: Long): Long =
        TimeEngine.epochMillis(snapshot.get().rule, realEpochMillis)

    fun virtualMonotonicMillis(realMonotonicMillis: Long): Long {
        val current = snapshot.get()
        return TimeEngine.monotonicMillis(current.rule, realMonotonicMillis, current.anchor)
    }

    fun virtualMonotonicNanos(realMonotonicNanos: Long): Long {
        val current = snapshot.get()
        return TimeEngine.monotonicNanos(current.rule, realMonotonicNanos, current.anchor)
    }

    fun virtualNowEpochMillis(): Long = virtualEpochMillis(sourceEpochMillis())

    fun virtualInstant(realInstant: Instant): Instant {
        val current = snapshot.get().rule
        val virtualMillis = TimeEngine.epochMillis(current, realInstant.toEpochMilli())
        return when {
            !current.enabled || current.mode != dev.chronosx.core.TimeMode.OFFSET ->
                Instant.ofEpochMilli(virtualMillis)

            else -> Instant.ofEpochMilli(virtualMillis)
                .plusNanos((realInstant.nano % NANOS_PER_MILLI).toLong())
        }
    }

    /** Runs a source-clock operation without re-entering ChronosX hooks. */
    fun <T> withBypass(block: () -> T): T {
        bypassDepth.set((bypassDepth.get() ?: 0) + 1)
        return try {
            block()
        } finally {
            decrement(bypassDepth)
        }
    }

    /**
     * Java's higher-level date APIs commonly delegate to System.currentTimeMillis(). While the
     * API itself is hooked we keep its nested source call real, then transform the finished value
     * once. This avoids applying an offset twice.
     */
    fun <T> withConstructionBypass(block: () -> T): T {
        constructionDepth.set((constructionDepth.get() ?: 0) + 1)
        return try {
            block()
        } finally {
            decrement(constructionDepth)
        }
    }

    private fun refresh() {
        val newRule = runCatching {
            RulePreferenceCodec.read(packageName, SharedPreferencesReader(preferences))
        }.getOrElse { error ->
            logger.warn("Rejected malformed remote rule; using real time.", error)
            TimeRule.disabled(packageName)
        }

        val newAnchor = withBypass {
            TimeEngine.createMonotonicAnchor(
                rule = newRule,
                sourceMillis = SystemClock.elapsedRealtime(),
                sourceNanos = System.nanoTime(),
            )
        }
        snapshot.set(RuntimeSnapshot(newRule, newAnchor))
        logger.debug(
            "Rule refreshed: enabled=${newRule.enabled}, mode=${newRule.mode}, " +
                "updatedAt=${newRule.updatedAtEpochMillis}.",
        )
    }

    private fun sourceEpochMillis(): Long = withBypass { System.currentTimeMillis() }

    private fun decrement(depth: ThreadLocal<Int>) {
        val next = (depth.get() ?: 0) - 1
        if (next <= 0) depth.remove() else depth.set(next)
    }

    private data class RuntimeSnapshot(
        val rule: TimeRule,
        val anchor: MonotonicAnchor,
    )

    private class SharedPreferencesReader(
        private val preferences: SharedPreferences,
    ) : PreferenceReader {
        override fun boolean(key: String, defaultValue: Boolean): Boolean =
            preferences.getBoolean(key, defaultValue)

        override fun long(key: String, defaultValue: Long): Long =
            preferences.getLong(key, defaultValue)

        override fun string(key: String, defaultValue: String): String =
            preferences.getString(key, defaultValue) ?: defaultValue
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000
    }
}
