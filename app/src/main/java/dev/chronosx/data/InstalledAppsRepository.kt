package dev.chronosx.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.chronosx.core.PackageTargetPolicy

class InstalledAppsRepository(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    @Suppress("DEPRECATION")
    fun listUserApplications(): List<InstalledApplication> {
        val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            packageManager.getInstalledApplications(0)
        }

        return applications
            .asSequence()
            .filterNot { it.flags and SYSTEM_APPLICATION_FLAGS != 0 }
            .filter { PackageTargetPolicy.isTargetable(it.packageName) }
            .map {
                InstalledApplication(
                    packageName = it.packageName,
                    label = packageManager.getApplicationLabel(it).toString().ifBlank { it.packageName },
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private companion object {
        val SYSTEM_APPLICATION_FLAGS =
            ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    }
}
