package dev.chronosx.core

import java.time.ZoneId

/**
 * Pure clock arithmetic shared by the manager preview and the injected hook engine.
 * All arithmetic saturates instead of wrapping so a malformed rule cannot move time
 * across the signed-long boundary.
 */
object TimeEngine {
    fun epochMillis(rule: TimeRule, realEpochMillis: Long): Long =
        if (!rule.enabled) {
            realEpochMillis
        } else {
            when (rule.mode) {
                TimeMode.REAL_TIME -> realEpochMillis
                TimeMode.OFFSET -> saturatedAdd(realEpochMillis, rule.offsetMillis)
                TimeMode.FIXED_TIME -> rule.fixedEpochMillis
            }
        }

    /** Captures physical monotonic sources for runtime diagnostics and future test policies. */
    fun createMonotonicAnchor(
        rule: TimeRule,
        sourceMillis: Long,
        sourceNanos: Long,
    ): MonotonicAnchor = MonotonicAnchor(
        sourceMillis = sourceMillis,
        sourceNanos = sourceNanos,
    )

    fun monotonicMillis(
        rule: TimeRule,
        realMonotonicMillis: Long,
        anchor: MonotonicAnchor,
    ): Long = if (rule.enabled && rule.monotonicMode == MonotonicMode.OFFSET) {
        saturatedAdd(realMonotonicMillis, rule.monotonicOffsetMillis)
    } else {
        realMonotonicMillis
    }

    fun monotonicNanos(
        rule: TimeRule,
        realMonotonicNanos: Long,
        anchor: MonotonicAnchor,
    ): Long = if (rule.enabled && rule.monotonicMode == MonotonicMode.OFFSET) {
        saturatedAdd(
            realMonotonicNanos,
            saturatedMultiply(rule.monotonicOffsetMillis, NANOS_PER_MILLI),
        )
    } else {
        realMonotonicNanos
    }

    /** Resolves a malformed or absent requested zone safely to the physical default zone. */
    fun zoneId(rule: TimeRule, physicalDefault: ZoneId): ZoneId = when (rule.zoneMode) {
        ZoneMode.DEVICE_DEFAULT -> physicalDefault
        ZoneMode.VIRTUAL_DEFAULT -> rule.zoneId
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: physicalDefault
    }

    fun saturatedAdd(left: Long, right: Long): Long {
        val result = left + right
        return when {
            right > 0 && result < left -> Long.MAX_VALUE
            right < 0 && result > left -> Long.MIN_VALUE
            else -> result
        }
    }

    fun saturatedSubtract(left: Long, right: Long): Long = when (right) {
        Long.MIN_VALUE -> saturatedAdd(left, Long.MAX_VALUE).let { saturatedAdd(it, 1L) }
        else -> saturatedAdd(left, -right)
    }

    fun saturatedMultiply(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        val result = left * right
        if (result / right == left) return result
        return if ((left xor right) >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}

data class MonotonicAnchor(
    val sourceMillis: Long,
    val sourceNanos: Long,
)
