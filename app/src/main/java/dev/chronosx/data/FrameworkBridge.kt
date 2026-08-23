package dev.chronosx.data

import dev.chronosx.core.RulePreferenceCodec
import dev.chronosx.core.TimeRule
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class FrameworkBridge : XposedServiceHelper.OnServiceListener {
    private val serviceRef = AtomicReference<XposedService?>(null)
    private val _status = MutableStateFlow(FrameworkStatus.disconnected())
    val status: StateFlow<FrameworkStatus> = _status.asStateFlow()

    init {
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        serviceRef.set(service)
        _status.value = statusFor(service)
    }

    override fun onServiceDied(service: XposedService) {
        serviceRef.compareAndSet(service, null)
        _status.value = FrameworkStatus.disconnected()
    }

    fun refreshStatus() {
        _status.value = serviceRef.get()?.let(::statusFor) ?: FrameworkStatus.disconnected()
    }

    fun writeRule(rule: TimeRule): FrameworkActionResult {
        val service = serviceRef.get() ?: return FrameworkActionResult.Unavailable
        compatibilityFailure(service)?.let { return it }
        if (service.getFrameworkProperties() and XposedService.PROP_CAP_REMOTE == 0L) {
            return FrameworkActionResult.Failed("This framework does not expose remote preferences.")
        }

        return runCatching {
            val prefs = service.getRemotePreferences(RulePreferenceCodec.GROUP)
            val editor = prefs.edit() ?: error("Remote preference editor is unavailable.")
            RulePreferenceCodec.encode(rule).forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    else -> error("Unsupported remote preference value for $key")
                }
            }
            if (!editor.commit()) error("Remote preference commit failed.")
            FrameworkActionResult.Success
        }.getOrElse { FrameworkActionResult.Failed(it.message ?: "Unable to write remote preferences.") }
    }

    suspend fun requestScope(packageName: String): FrameworkActionResult {
        val service = serviceRef.get() ?: return FrameworkActionResult.Unavailable
        compatibilityFailure(service)?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                service.requestScope(
                    listOf(packageName),
                    object : XposedService.OnScopeEventListener {
                        override fun onScopeRequestApproved(approved: List<String>) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    if (packageName in approved) {
                                        FrameworkActionResult.Success
                                    } else {
                                        FrameworkActionResult.Failed("The framework declined $packageName.")
                                    },
                                )
                            }
                        }

                        override fun onScopeRequestFailed(message: String) {
                            if (continuation.isActive) {
                                continuation.resume(FrameworkActionResult.Failed(message))
                            }
                        }
                    },
                )
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(FrameworkActionResult.Failed(it.message ?: "Scope request failed."))
                }
            }
        }
    }

    fun removeScope(packageName: String): FrameworkActionResult {
        val service = serviceRef.get() ?: return FrameworkActionResult.Unavailable
        return runCatching {
            service.removeScope(listOf(packageName))
            FrameworkActionResult.Success
        }.getOrElse { FrameworkActionResult.Failed(it.message ?: "Unable to remove scope.") }
    }

    fun runningTargets(): List<RunningTarget> {
        val service = serviceRef.get() ?: return emptyList()
        return runCatching {
            service.getRunningTargets().map {
                RunningTarget(
                    processName = it.processName,
                    pid = it.pid,
                    uid = it.uid,
                    state = it.state.name,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun statusFor(service: XposedService): FrameworkStatus = runCatching {
        val properties = service.frameworkProperties
        val apiVersion = service.apiVersion
        FrameworkStatus(
            connected = true,
            frameworkName = service.frameworkName,
            frameworkVersion = service.frameworkVersion,
            apiVersion = apiVersion,
            remotePreferencesAvailable = properties and XposedService.PROP_CAP_REMOTE != 0L,
            scope = service.scope.sorted(),
            error = if (apiVersion < XposedService.API_102) {
                "ChronosX requires libxposed service API ${XposedService.API_102} or newer."
            } else {
                null
            },
        )
    }.getOrElse {
        FrameworkStatus(
            connected = true,
            frameworkName = null,
            frameworkVersion = null,
            apiVersion = null,
            remotePreferencesAvailable = false,
            scope = emptyList(),
            error = it.message ?: "Framework service is unavailable.",
        )
    }

    private fun compatibilityFailure(service: XposedService): FrameworkActionResult.Failed? = runCatching {
        if (service.apiVersion < XposedService.API_102) {
            FrameworkActionResult.Failed(
                "ChronosX requires libxposed service API ${XposedService.API_102} or newer.",
            )
        } else {
            null
        }
    }.getOrElse {
        FrameworkActionResult.Failed(it.message ?: "Unable to determine framework API version.")
    }
}

data class FrameworkStatus(
    val connected: Boolean,
    val frameworkName: String?,
    val frameworkVersion: String?,
    val apiVersion: Int?,
    val remotePreferencesAvailable: Boolean,
    val scope: List<String>,
    val error: String?,
) {
    companion object {
        fun disconnected() = FrameworkStatus(
            connected = false,
            frameworkName = null,
            frameworkVersion = null,
            apiVersion = null,
            remotePreferencesAvailable = false,
            scope = emptyList(),
            error = null,
        )
    }
}

data class RunningTarget(
    val processName: String,
    val pid: Int,
    val uid: Int,
    val state: String,
)

sealed interface FrameworkActionResult {
    data object Success : FrameworkActionResult
    data object Unavailable : FrameworkActionResult
    data class Failed(val message: String) : FrameworkActionResult
}
