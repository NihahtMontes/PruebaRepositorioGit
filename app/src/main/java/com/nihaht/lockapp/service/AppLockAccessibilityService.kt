package com.nihaht.lockapp.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.nihaht.lockapp.AppContainer
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.lock.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Detecta qué app pasa a primer plano (solo el nombre del paquete y el tipo de
 * ventana, nunca el contenido) y muestra la pantalla de bloqueo cuando toca.
 * Ningún fallo puede cerrar este servicio: todo se registra en el diagnóstico,
 * porque si el servicio se cierra Android puede desactivarlo.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var screenReceiver: BroadcastReceiver? = null
    private var shield: RecentsShield? = null

    /** Última app "real" en primer plano (sin contar ventanas del sistema). */
    private var foregroundPackage: String? = null

    /** Hasta cuándo se puede ver Recientes sin PIN (tras desbloquearlo). */
    private var recentsAllowedUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            container = appContainer
            container.status.accessibilityConnected = true
            container.status.accessibilityConnectedAt = SystemClock.elapsedRealtime()
            container.excludedPackages.refresh()
            registerScreenReceiver()
            shield = RecentsShield(
                service = this,
                scope = scope,
                onUnlocked = { recentsAllowedUntil = SystemClock.elapsedRealtime() + RECENTS_GRACE_MS },
                onGoHome = { recentsAllowedUntil = SystemClock.elapsedRealtime() + GO_HOME_GRACE_MS },
            )
            KeepAliveService.start(this)
            WatchdogWorker.schedule(this)
            Notifications.cancelProtectionProblem(this)
        } catch (e: Exception) {
            ErrorLog.record("Iniciar el servicio de accesibilidad", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            if (!::container.isInitialized) return
            val packageName = event.packageName?.toString() ?: return
            container.status.noteEvent(packageName)
            handleRecents(packageName, event.className?.toString().orEmpty())
            onForeground(packageName)
        } catch (e: Exception) {
            ErrorLog.record("Detectar la app abierta", e)
        }
    }

    private fun onForeground(packageName: String) {
        val excluded = container.excludedPackages
        if (excluded.isTransient(packageName)) return
        foregroundPackage = packageName

        val settings = container.currentSettings()
        val isLocked = !settings.paused &&
            !excluded.isLauncher(packageName) &&
            !isSettingsBypassed(packageName) &&
            container.currentLockedApps().containsKey(packageName)

        val mustLock = container.sessions.onForeground(
            packageName = packageName,
            isLocked = isLocked,
            graceMs = settings.gracePeriod.millis,
        )
        if (mustLock) showLockScreen(packageName)
    }

    /**
     * Recientes es una ventana del launcher (o de la interfaz del sistema) cuya
     * clase contiene "Recents". Si está protegido, se tapa con [RecentsShield].
     */
    private fun handleRecents(packageName: String, className: String) {
        val excluded = container.excludedPackages
        val fromLauncher = excluded.isLauncher(packageName) || packageName == SYSTEM_UI
        if (!fromLauncher) {
            // Se abrió otra app: Recientes ya no está en pantalla.
            if (!excluded.isTransient(packageName)) shield?.hide()
            return
        }
        container.status.noteLauncherEvent("$packageName / $className")
        val now = SystemClock.elapsedRealtime()
        if (className.contains("Recents", ignoreCase = true)) {
            container.status.recentsDetectedAt = now
            val settings = container.currentSettings()
            val protect = settings.protectRecents &&
                !settings.paused &&
                container.currentLockedApps().isNotEmpty() &&
                now >= recentsAllowedUntil
            if (protect && shield?.isShowing == false) {
                shield?.show()
                container.status.recentsShieldShown++
            }
        } else if (packageName != SYSTEM_UI && className.endsWith("Activity")) {
            // Volvió a la pantalla de inicio.
            shield?.hide()
        }
    }

    private fun isSettingsBypassed(packageName: String): Boolean =
        packageName in container.excludedPackages.settingsPackages &&
            SystemClock.elapsedRealtime() < container.settingsBypassUntil

    /**
     * Se lanza en cada evento de la app bloqueada, sin filtrar repeticiones: si la
     * app abre otra pantalla (p. ej. tras su splash) la de bloqueo vuelve encima.
     * LockActivity es singleTask, así que repetir el lanzamiento solo la reutiliza.
     */
    private fun showLockScreen(packageName: String) {
        try {
            startActivity(LockActivity.intent(this, packageName))
            container.status.noteLock()
        } catch (e: Exception) {
            ErrorLog.record("Mostrar la pantalla de bloqueo", e)
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> onScreenOff()
                        Intent.ACTION_USER_PRESENT -> container.excludedPackages.refresh()
                    }
                } catch (e: Exception) {
                    ErrorLog.record("Pantalla apagada o encendida", e)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Son difusiones protegidas que solo puede enviar el sistema, así que exportarlo es seguro
        // y garantiza que llegan también en Android 12 o anterior.
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        screenReceiver = receiver
    }

    private fun onScreenOff() {
        recentsAllowedUntil = 0L
        shield?.hide()
        val settings = container.currentSettings()
        if (!settings.relockOnScreenOff) return
        container.sessions.lockAll()
        // Al volver a encender la pantalla puede no llegar ningún evento de la app
        // que estaba abierta, así que se deja la pantalla de bloqueo puesta ya.
        val current = foregroundPackage ?: return
        if (!settings.paused && container.currentLockedApps().containsKey(current)) {
            showLockScreen(current)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        markDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        markDisconnected()
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: IllegalArgumentException) {
            ErrorLog.record("Quitar receptor de pantalla", e)
        }
        screenReceiver = null
        scope.cancel()
        super.onDestroy()
    }

    private fun markDisconnected() {
        shield?.hide()
        if (::container.isInitialized) container.status.accessibilityConnected = false
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val RECENTS_GRACE_MS = 60_000L
        const val GO_HOME_GRACE_MS = 3_000L
    }
}
