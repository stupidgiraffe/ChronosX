package dev.chronosx.core

/** Package-neutral contract shared by the optional Android Lab SDK and ChronosX Manager. */
object BenchmarkProtocol {
    const val ACTION_RESULT = "dev.chronosx.action.BENCHMARK_RESULT"
    const val TARGET_PACKAGE = "dev.chronosx"

    const val EXTRA_RUN_ID = "dev.chronosx.extra.RUN_ID"
    const val EXTRA_SCENARIO_ID = "dev.chronosx.extra.SCENARIO_ID"
    const val EXTRA_RULE_REVISION = "dev.chronosx.extra.RULE_REVISION"
    const val EXTRA_SOURCE_PACKAGE = "dev.chronosx.extra.SOURCE_PACKAGE"
    const val EXTRA_PASSED = "dev.chronosx.extra.PASSED"
    const val EXTRA_PROCESS_NAME = "dev.chronosx.extra.PROCESS_NAME"
    const val EXTRA_OBSERVED_WALL_EPOCH_MILLIS = "dev.chronosx.extra.OBSERVED_WALL_EPOCH_MILLIS"
    const val EXTRA_OBSERVED_ZONE_ID = "dev.chronosx.extra.OBSERVED_ZONE_ID"
    const val EXTRA_OBSERVED_SURFACES = "dev.chronosx.extra.OBSERVED_SURFACES"
    const val EXTRA_MESSAGE = "dev.chronosx.extra.MESSAGE"
}
