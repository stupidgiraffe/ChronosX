package dev.chronosx.module

import android.util.Log
import io.github.libxposed.api.XposedModule

/** Routes structured module diagnostics to the active libxposed framework log. */
internal class ModuleLogger(
    private val module: XposedModule,
    private val processName: String,
) {
    fun debug(message: String) = write(Log.DEBUG, message)

    fun info(message: String) = write(Log.INFO, message)

    fun warn(message: String, error: Throwable? = null) = write(Log.WARN, message, error)

    fun error(message: String, error: Throwable? = null) = write(Log.ERROR, message, error)

    private fun write(priority: Int, message: String, error: Throwable? = null) {
        runCatching {
            if (error == null) {
                module.log(priority, TAG, "[$processName] $message")
            } else {
                module.log(priority, TAG, "[$processName] $message", error)
            }
        }
    }

    private companion object {
        const val TAG = "ChronosX"
    }
}
