package com.nihaht.lockapp.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.nihaht.lockapp.data.DetectionMode
import com.nihaht.lockapp.service.AppLockAccessibilityService

data class PermissionStatus(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notifications: Boolean = false,
)

/** Consulta qué permisos faltan y crea los Intent que abren cada ajuste. */
object PermissionChecker {

    fun status(context: Context) = PermissionStatus(
        accessibility = isAccessibilityServiceEnabled(context),
        usageAccess = hasUsageAccess(context),
        overlay = hasOverlay(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )

    /** true si el modo elegido tiene todo lo imprescindible para bloquear. */
    fun isProtectionReady(context: Context, mode: DetectionMode): Boolean = when (mode) {
        DetectionMode.USAGE_ACCESS -> hasUsageAccess(context) && hasOverlay(context)
        DetectionMode.ACCESSIBILITY -> isAccessibilityServiceEnabled(context)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppLockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** Permiso "Acceso al uso" (PACKAGE_USAGE_STATS): solo dice qué app está delante. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Permiso "Mostrar sobre otras apps": necesario para abrir el bloqueo sin accesibilidad. */
    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    val isSamsung: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun accessibilitySettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageAccessSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlaySettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))

    /** Diálogo del sistema para quitar la optimización de batería solo a esta app. */
    fun batteryExemptionIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))

    /** Información de la app: "Permitir ajustes restringidos", batería, etc. */
    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
