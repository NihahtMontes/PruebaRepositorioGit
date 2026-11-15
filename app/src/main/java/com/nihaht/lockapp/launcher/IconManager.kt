package com.nihaht.lockapp.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.nihaht.lockapp.diagnostics.ErrorLog

/** Icono y nombre con los que LockApp aparece en el cajón de aplicaciones. */
enum class AppIconOption(val aliasSuffix: String, val label: String) {
    DEFAULT("Default", "LockApp (normal)"),
    CALCULATOR("Calculator", "Calculadora"),
    CLOCK("Clock", "Reloj"),
    TOOLS("Settings", "Herramientas"),
}

/**
 * Cambia el icono disfrazando LockApp: hay un activity-alias por opción y se
 * activa solo uno a la vez. La app sigue abriéndose igual; solo cambia cómo se ve.
 */
object IconManager {

    private const val BASE = "com.nihaht.lockapp.launcher"

    /** Aplica la opción elegida: activa su alias y desactiva los demás. */
    fun apply(context: Context, option: AppIconOption) {
        val pm = context.packageManager
        AppIconOption.entries.forEach { opt ->
            val state = if (opt == option) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(component(context, opt), state, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {
                ErrorLog.record("Cambiar el icono (${opt.name})", e)
            }
        }
    }

    private fun component(context: Context, option: AppIconOption) =
        ComponentName(context.packageName, "$BASE.${option.aliasSuffix}")
}
