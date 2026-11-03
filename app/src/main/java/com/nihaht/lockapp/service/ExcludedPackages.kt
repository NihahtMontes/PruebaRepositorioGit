package com.nihaht.lockapp.service

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import com.nihaht.lockapp.data.queryActivitiesCompat

/**
 * Paquetes que nunca se bloquean, para no dejar el teléfono inutilizable:
 * la propia app, la interfaz del sistema, llamadas, teclados y launchers.
 */
class ExcludedPackages(context: Context) {

    private val appContext = context.applicationContext

    /** Ventanas que aparecen encima de la app en uso sin reemplazarla: se ignoran por completo. */
    private val transient = setOf(
        appContext.packageName,
        "android",
        "com.android.systemui",
        // Llamadas entrantes y en curso
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.google.android.dialer",
        // Diálogos de permisos y de huella del sistema
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.biometrics.app.setting",
        // Paneles laterales y servicios de Samsung que se superponen
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.app.aodservice",
        "com.samsung.android.emergencymode.service",
    )

    /** Ajustes del sistema; solo se dejan pasar mientras el asistente de permisos los abre. */
    val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.accessibility",
    )

    @Volatile
    private var launchers: Set<String> = emptySet()

    @Volatile
    private var keyboards: Set<String> = emptySet()

    /** Vuelve a leer launchers y teclados instalados. */
    fun refresh() {
        val pm = appContext.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launchers = pm.queryActivitiesCompat(home).map { it.activityInfo.packageName }.toSet()
        val imm = appContext.getSystemService(InputMethodManager::class.java)
        keyboards = imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
    }

    fun isTransient(packageName: String): Boolean =
        packageName in transient || packageName in keyboards

    fun isLauncher(packageName: String): Boolean = packageName in launchers

    /** Solo estas apps aparecen en la lista para poder bloquearlas. */
    fun canBeLocked(packageName: String): Boolean =
        !isTransient(packageName) && !isLauncher(packageName)
}
