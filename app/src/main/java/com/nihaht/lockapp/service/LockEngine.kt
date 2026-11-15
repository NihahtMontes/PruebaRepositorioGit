package com.nihaht.lockapp.service

import android.content.Context
import android.os.SystemClock
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.lock.LockActivity

/**
 * Lógica central del bloqueo, común a los dos modos de detección (accesibilidad
 * y "Acceso al uso"). Decide si una app en primer plano debe bloquearse y abre
 * la pantalla de bloqueo. No lanza excepciones hacia fuera.
 */
class LockEngine(private val context: Context) {

    private val container get() = context.appContainer

    /** Última app "real" en primer plano (sin contar ventanas del sistema). */
    @Volatile
    var foregroundPackage: String? = null
        private set

    /** Notifica que [packageName] pasó a primer plano y bloquea si procede. */
    fun onForeground(packageName: String) {
        try {
            val c = container
            val excluded = c.excludedPackages
            if (excluded.isTransient(packageName)) return
            foregroundPackage = packageName

            val settings = c.currentSettings()
            val inSettings = packageName in excluded.settingsPackages
            // Proteger los Ajustes del sistema con el PIN maestro (protege la desinstalación).
            val settingsLocked = settings.lockSystemSettings && inSettings && !isSettingsBypassed(packageName)
            val appLocked = !inSettings && c.currentLockedApps().containsKey(packageName)
            val isLocked = !settings.paused &&
                !excluded.isLauncher(packageName) &&
                (settingsLocked || appLocked)

            val mustLock = c.sessions.onForeground(
                packageName = packageName,
                isLocked = isLocked,
                graceMs = settings.gracePeriod.millis,
            )
            if (mustLock) showLockScreen(packageName, useMaster = settingsLocked)
        } catch (e: Exception) {
            ErrorLog.record("Decidir el bloqueo de la app", e)
        }
    }

    /** Pantalla apagada: olvida los desbloqueos y re-bloquea la app abierta. */
    fun onScreenOff() {
        try {
            val c = container
            val settings = c.currentSettings()
            if (!settings.relockOnScreenOff) return
            c.sessions.lockAll()
            val current = foregroundPackage ?: return
            if (!settings.paused && c.currentLockedApps().containsKey(current)) {
                showLockScreen(current)
            }
        } catch (e: Exception) {
            ErrorLog.record("Re-bloquear al apagar la pantalla", e)
        }
    }

    fun showLockScreen(packageName: String, useMaster: Boolean = false) {
        try {
            context.startActivity(LockActivity.intent(context, packageName, useMaster))
            container.status.noteLock()
        } catch (e: Exception) {
            ErrorLog.record("Mostrar la pantalla de bloqueo", e)
        }
    }

    private fun isSettingsBypassed(packageName: String): Boolean =
        packageName in container.excludedPackages.settingsPackages &&
            SystemClock.elapsedRealtime() < container.settingsBypassUntil
}
