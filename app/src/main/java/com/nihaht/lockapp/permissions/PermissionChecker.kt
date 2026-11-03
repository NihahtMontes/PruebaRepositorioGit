package com.nihaht.lockapp.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.nihaht.lockapp.service.AppLockAccessibilityService

data class PermissionStatus(
    val accessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notifications: Boolean = false,
)

/** Consulta qué permisos faltan y crea los Intent que abren cada ajuste. */
object PermissionChecker {

    fun status(context: Context) = PermissionStatus(
        accessibility = isAccessibilityServiceEnabled(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppLockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    val isSamsung: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun accessibilitySettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Diálogo del sistema para quitar la optimización de batería solo a esta app. */
    fun batteryExemptionIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Información de la app: "Permitir ajustes restringidos", batería, etc. */
    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
