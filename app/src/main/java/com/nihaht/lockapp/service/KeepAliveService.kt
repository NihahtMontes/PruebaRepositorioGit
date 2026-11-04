package com.nihaht.lockapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.permissions.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano ligero. Mantiene el proceso con prioridad alta y
 * vigila que la protección siga activa: si el servicio de accesibilidad se
 * desactiva o deja de responder, avisa con una notificación.
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: ContentObserver? = null
    private var checkJob: Job? = null
    private var lastProblem: String? = null

    /** Desde cuándo el servicio de accesibilidad está activado pero sin conectar (0 = conectado). */
    private var notConnectedSince = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Notifications.ensureChannels(this)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, Notifications.SERVICE_ID, Notifications.service(this, statusText()), type)
            appContainer.status.keepAliveRunning = true
            watchAccessibilitySetting()
            startPeriodicCheck()
            check()
        } catch (e: Exception) {
            ErrorLog.record("No se pudo mantener LockApp en segundo plano", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /** Reacciona al instante si alguien desactiva el servicio de accesibilidad. */
    private fun watchAccessibilitySetting() {
        if (observer != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = check()
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            obs,
        )
        observer = obs
    }

    private fun startPeriodicCheck() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                check()
            }
        }
    }

    /** Comprueba el estado, actualiza la notificación fija y avisa si hay un problema. */
    private fun check() {
        try {
            val container = appContainer
            val hasLockedApps = container.lockedAppsState.value?.isNotEmpty() ?: false
            val paused = container.settingsState.value?.paused ?: false
            val problem = currentProblem(hasLockedApps, paused)

            Notifications.updateService(this, statusText())
            if (problem != null && problem != lastProblem) {
                Notifications.showProtectionProblem(this, problem)
            } else if (problem == null) {
                Notifications.cancelProtectionProblem(this)
            }
            lastProblem = problem
        } catch (e: Exception) {
            ErrorLog.record("Comprobación periódica de la protección", e)
        }
    }

    private fun currentProblem(hasLockedApps: Boolean, paused: Boolean): String? {
        if (!hasLockedApps || paused) return null
        val enabled = PermissionChecker.isAccessibilityServiceEnabled(this)
        val now = SystemClock.elapsedRealtime()
        if (enabled && !appContainer.status.accessibilityConnected) {
            if (notConnectedSince == 0L) notConnectedSince = now
        } else {
            notConnectedSince = 0L
        }
        return when {
            !enabled ->
                "El servicio de accesibilidad está desactivado y tus apps no se están bloqueando. Toca para revisarlo."
            notConnectedSince != 0L && now - notConnectedSince > NOT_RESPONDING_MS ->
                "LockApp está activado pero no responde. Toca para abrir el diagnóstico."
            else -> null
        }
    }

    private fun statusText(): String {
        val container = appContainer
        val count = container.lockedAppsState.value?.size ?: 0
        return when {
            container.settingsState.value?.paused == true -> "Bloqueos en pausa"
            !PermissionChecker.isAccessibilityServiceEnabled(this) -> "Protección desactivada: toca para revisarlo"
            count == 0 -> "No hay apps bloqueadas"
            count == 1 -> "Protegiendo 1 app"
            else -> "Protegiendo $count apps"
        }
    }

    override fun onDestroy() {
        appContainer.status.keepAliveRunning = false
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val NOT_RESPONDING_MS = 2 * 60_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
            } catch (e: IllegalStateException) {
                // Android puede rechazar iniciarlo desde segundo plano; la vigilancia periódica lo reintenta.
                ErrorLog.record("No se pudo iniciar el servicio en segundo plano", e)
            } catch (e: SecurityException) {
                ErrorLog.record("No se pudo iniciar el servicio en segundo plano", e)
            }
        }

        fun restart(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: RuntimeException) {
                ErrorLog.record("Detener el servicio en segundo plano", e)
            }
            start(context)
        }
    }
}
