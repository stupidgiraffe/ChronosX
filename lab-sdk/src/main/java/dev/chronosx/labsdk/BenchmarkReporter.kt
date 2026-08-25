package dev.chronosx.labsdk

import android.content.Context
import android.content.Intent
import dev.chronosx.core.BenchmarkProtocol
import dev.chronosx.core.DateCapabilityMatrix
import dev.chronosx.core.DateCapabilityMatrixCodec

/**
 * Optional helper for customer-owned or mock targets to submit a self-reported Lab assertion.
 * The target package is explicit, so results are delivered only to the installed ChronosX Manager.
 */
class BenchmarkReporter(private val context: Context) {
    fun report(result: BenchmarkResult) {
        val intent = Intent(BenchmarkProtocol.ACTION_RESULT)
            .setPackage(BenchmarkProtocol.TARGET_PACKAGE)
            .putExtra(BenchmarkProtocol.EXTRA_RUN_ID, result.runId)
            .putExtra(BenchmarkProtocol.EXTRA_RUN_TOKEN, result.runToken)
            .putExtra(BenchmarkProtocol.EXTRA_SOURCE_PACKAGE, context.packageName)
            .putExtra(BenchmarkProtocol.EXTRA_RULE_REVISION, result.ruleRevision)
            .putExtra(BenchmarkProtocol.EXTRA_PASSED, result.passed)
            .putExtra(BenchmarkProtocol.EXTRA_PROCESS_NAME, result.processName)
            .putExtra(BenchmarkProtocol.EXTRA_OBSERVED_ZONE_ID, result.observedZoneId)
            .putExtra(BenchmarkProtocol.EXTRA_OBSERVED_SURFACES, result.observedSurfaces)
            .putExtra(BenchmarkProtocol.EXTRA_MESSAGE, result.message)

        result.dateCapabilityMatrix?.let { matrix ->
            intent.putExtra(
                BenchmarkProtocol.EXTRA_DATE_CAPABILITY_MATRIX,
                DateCapabilityMatrixCodec.encode(matrix),
            )
        }

        result.observedWallEpochMillis?.let {
            intent.putExtra(BenchmarkProtocol.EXTRA_OBSERVED_WALL_EPOCH_MILLIS, it)
        }
        context.sendBroadcast(intent)
    }
}

data class BenchmarkResult(
    val runId: String,
    val runToken: String,
    val ruleRevision: Long,
    val passed: Boolean,
    val processName: String? = null,
    val observedWallEpochMillis: Long? = null,
    val observedZoneId: String? = null,
    val observedSurfaces: String? = null,
    val dateCapabilityMatrix: DateCapabilityMatrix? = null,
    val message: String? = null,
)
