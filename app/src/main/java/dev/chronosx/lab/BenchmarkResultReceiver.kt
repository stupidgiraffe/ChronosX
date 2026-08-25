package dev.chronosx.lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.chronosx.ChronosXApplication
import dev.chronosx.core.BenchmarkProtocol
import dev.chronosx.core.DateCapabilityMatrixCodec
import dev.chronosx.core.DateCapabilityMatrixDecodeResult
import dev.chronosx.data.BenchmarkObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives optional, explicit benchmark evidence from an authorized target or mock app. */
class BenchmarkResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BenchmarkProtocol.ACTION_RESULT) return
        val runId = intent.getStringExtra(BenchmarkProtocol.EXTRA_RUN_ID).orEmpty()
        if (runId.isBlank()) return

        val pending = goAsync()
        val application = context.applicationContext as? ChronosXApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                application?.container?.scenarioRunRepository?.recordObservation(
                    BenchmarkObservation(
                        runId = runId,
                        runToken = intent.getStringExtra(BenchmarkProtocol.EXTRA_RUN_TOKEN).orEmpty(),
                        sourcePackage = intent.getStringExtra(BenchmarkProtocol.EXTRA_SOURCE_PACKAGE)
                            ?: "unknown",
                        processName = intent.getStringExtra(BenchmarkProtocol.EXTRA_PROCESS_NAME),
                        ruleRevision = intent.getLongExtra(BenchmarkProtocol.EXTRA_RULE_REVISION, 0L),
                        passed = intent.getBooleanExtra(BenchmarkProtocol.EXTRA_PASSED, false),
                        observedWallEpochMillis = intent.takeIf {
                            it.hasExtra(BenchmarkProtocol.EXTRA_OBSERVED_WALL_EPOCH_MILLIS)
                        }?.getLongExtra(BenchmarkProtocol.EXTRA_OBSERVED_WALL_EPOCH_MILLIS, 0L),
                        observedZoneId = intent.getStringExtra(BenchmarkProtocol.EXTRA_OBSERVED_ZONE_ID),
                        observedSurfaces = intent.getStringExtra(BenchmarkProtocol.EXTRA_OBSERVED_SURFACES),
                        dateCapabilityMatrix = intent
                            .getStringExtra(BenchmarkProtocol.EXTRA_DATE_CAPABILITY_MATRIX)
                            ?.let(DateCapabilityMatrixCodec::decode)
                            ?.let { result ->
                                (result as? DateCapabilityMatrixDecodeResult.Decoded)?.matrix
                            },
                        message = intent.getStringExtra(BenchmarkProtocol.EXTRA_MESSAGE),
                    ),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
