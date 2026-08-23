package dev.chronosx.core

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

    /**
     * Establishes a monotonic timeline for APIs such as elapsedRealtime and nanoTime.
     * Fixed wall time deliberately continues advancing after the anchor to avoid breaking
     * loops, timeouts, and schedulers that rely on monotonic clocks.
     */
    fun createMonotonicAnchor(
        rule: TimeRule,
        sourceMillis: Long,
        sourceNanos: Long,
    ): MonotonicAnchor = when {
        !rule.enabled || rule.mode == TimeMode.REAL_TIME ->
            MonotonicAnchor(sourceMillis, sourceMillis, sourceNanos, sourceNanos)

        rule.mode == TimeMode.OFFSET -> {
            val offsetNanos = saturatedMultiply(rule.offsetMillis, NANOS_PER_MILLI)
            MonotonicAnchor(
                sourceMillis = sourceMillis,
                virtualMillis = saturatedAdd(sourceMillis, rule.offsetMillis),
                sourceNanos = sourceNanos,
                virtualNanos = saturatedAdd(sourceNanos, offsetNanos),
            )
        }

        else -> MonotonicAnchor(
            sourceMillis = sourceMillis,
            virtualMillis = rule.fixedEpochMillis,
            sourceNanos = sourceNanos,
            virtualNanos = saturatedMultiply(rule.fixedEpochMillis, NANOS_PER_MILLI),
        )
    }

    fun monotonicMillis(
        rule: TimeRule,
        realMonotonicMillis: Long,
        anchor: MonotonicAnchor,
    ): Long = when {
        !rule.enabled || rule.mode == TimeMode.REAL_TIME -> realMonotonicMillis
        rule.mode == TimeMode.OFFSET -> saturatedAdd(realMonotonicMillis, rule.offsetMillis)
        else -> saturatedAdd(anchor.virtualMillis, saturatedSubtract(realMonotonicMillis, anchor.sourceMillis))
    }

    fun monotonicNanos(
        rule: TimeRule,
        realMonotonicNanos: Long,
        anchor: MonotonicAnchor,
    ): Long = when {
        !rule.enabled || rule.mode == TimeMode.REAL_TIME -> realMonotonicNanos
        rule.mode == TimeMode.OFFSET -> saturatedAdd(
            realMonotonicNanos,
            saturatedMultiply(rule.offsetMillis, NANOS_PER_MILLI),
        )

        else -> saturatedAdd(anchor.virtualNanos, saturatedSubtract(realMonotonicNanos, anchor.sourceNanos))
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
    val virtualMillis: Long,
    val sourceNanos: Long,
    val virtualNanos: Long,
)
