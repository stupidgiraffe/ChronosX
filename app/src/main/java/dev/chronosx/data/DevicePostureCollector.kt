package dev.chronosx.data

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File

/**
 * Report-only device posture snapshot for authorized resilience testing. It detects observable
 * conditions; it never changes, hides, or attempts to evade any device integrity signal.
 */
class DevicePostureCollector(private val context: Context) {
    fun collect(frameworkStatus: FrameworkStatus): DevicePostureSnapshot {
        val rootIndicators = ROOT_INDICATOR_PATHS.filter { File(it).exists() }
        val testKeys = Build.TAGS?.contains("test-keys", ignoreCase = true) == true
        val emulator = listOf(
            Build.FINGERPRINT,
            Build.MODEL,
            Build.MANUFACTURER,
            Build.BRAND,
            Build.DEVICE,
            Build.PRODUCT,
        ).any { value ->
            value?.contains("generic", ignoreCase = true) == true ||
                value?.contains("emulator", ignoreCase = true) == true ||
                value?.contains("sdk", ignoreCase = true) == true
        }
        return DevicePostureSnapshot(
            buildFingerprint = Build.FINGERPRINT ?: "unknown",
            buildType = Build.TYPE ?: "unknown",
            testKeysPresent = testKeys,
            rootIndicators = rootIndicators,
            debuggerConnected = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            frameworkConnected = frameworkStatus.connected,
            frameworkName = frameworkStatus.frameworkName,
            emulatorLikely = emulator,
            attestationStatus = "Not configured; supply an authorized verifier result through the test backend.",
        )
    }

    private companion object {
        val ROOT_INDICATOR_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk",
            "/data/adb/ksu",
        )
    }
}

data class DevicePostureSnapshot(
    val buildFingerprint: String,
    val buildType: String,
    val testKeysPresent: Boolean,
    val rootIndicators: List<String>,
    val debuggerConnected: Boolean,
    val frameworkConnected: Boolean,
    val frameworkName: String?,
    val emulatorLikely: Boolean,
    val attestationStatus: String,
) {
    companion object {
        fun unavailable() = DevicePostureSnapshot(
            buildFingerprint = "unavailable",
            buildType = "unavailable",
            testKeysPresent = false,
            rootIndicators = emptyList(),
            debuggerConnected = false,
            frameworkConnected = false,
            frameworkName = null,
            emulatorLikely = false,
            attestationStatus = "Not collected.",
        )
    }
}
