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
import com.nihaht.lockapp.lock.LockActivity

/**
 * Detecta qué app pasa a primer plano (solo el nombre del paquete, nunca el
 * contenido de la pantalla) y muestra la pantalla de bloqueo cuando toca.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var container: AppContainer
    private var screenReceiver: BroadcastReceiver? = null

    /** Última app "real" en primer plano (sin contar ventanas del sistema). */
    private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = appContainer
        container.excludedPackages.refresh()
        registerScreenReceiver()
        KeepAliveService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (!::container.isInitialized) return
        onForeground(packageName)
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

    private fun isSettingsBypassed(packageName: String): Boolean =
        packageName in container.excludedPackages.settingsPackages &&
            SystemClock.elapsedRealtime() < container.settingsBypassUntil

    /**
     * Se lanza en cada evento de la app bloqueada, sin filtrar repeticiones: si la
     * app abre otra pantalla (p. ej. tras su splash) la de bloqueo vuelve encima.
     * LockActivity es singleTask, así que repetir el lanzamiento solo la reutiliza.
     */
    private fun showLockScreen(packageName: String) {
        startActivity(LockActivity.intent(this, packageName))
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_USER_PRESENT -> container.excludedPackages.refresh()
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

    override fun onDestroy() {
        screenReceiver?.let { unregisterReceiver(it) }
        screenReceiver = null
        super.onDestroy()
    }
}
