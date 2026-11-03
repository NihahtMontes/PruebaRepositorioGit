package com.nihaht.lockapp.data

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** Variantes de las consultas a PackageManager que usan la API nueva en Android 13+. */
internal fun PackageManager.queryActivitiesCompat(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        queryActivitiesLegacy(intent)
    }

internal fun PackageManager.applicationInfoCompat(packageName: String): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        applicationInfoLegacy(packageName)
    }

@Suppress("DEPRECATION")
private fun PackageManager.queryActivitiesLegacy(intent: Intent): List<ResolveInfo> =
    queryIntentActivities(intent, 0)

@Suppress("DEPRECATION")
private fun PackageManager.applicationInfoLegacy(packageName: String): ApplicationInfo =
    getApplicationInfo(packageName, 0)
