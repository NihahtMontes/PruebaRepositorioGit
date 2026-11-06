package com.nihaht.lockapp.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.nihaht.lockapp.data.DetectionMode
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
 * Servicio en primer plano. Mantiene el proceso con prioridad alta y, en el
 * modo "Acceso al uso", es quien detecta la app en primer plano (consultando
 * periódicamente) y manda bloquear. También vigila que la protección siga
 * activa y avisa si deja de funcionar.
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: ContentObserver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var checkJob: Job? = null
    private var pollJob: Job? = null
    private var lastProblem: String? = null
    private val detector by lazy { UsageForegroundDetector(this) }

    /** Desde cuándo el modo elegido está sin poder proteger (0 = correcto). */
    private var notReadySince = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android exige mostrar la notificación pocos segundos después de
        // startForegroundService(); si no, cierra la app entera. Por eso es lo
        // primero que se hace, antes de cualquier otro trabajo.
        promoteToForeground()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        try {
            appContainer.status.keepAliveRunning = true
            watchAccessibilitySetting()
            registerScreenReceiver()
            startUsagePolling()
            startPeriodicCheck()
            check()
        } catch (e: Exception) {
            ErrorLog.record("Vigilancia del servicio en segundo plano", e)
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        try {
            Notifications.ensureChannels(this)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, Notifications.SERVICE_ID, Notifications.service(this, "LockApp activo"), type)
        } catch (e: Exception) {
            ErrorLog.record("No se pudo mostrar la notificación del servicio", e)
        }
    }

    /** Modo "Acceso al uso": mira cada ~0,2 s qué app está delante y manda bloquear. */
    private fun startUsagePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                try {
                    if (usagePollActive()) {
                        detector.poll()?.let { appContainer.lockEngine.onForeground(it) }
                    }
                } catch (e: Exception) {
                    ErrorLog.record("Detección por acceso al uso", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun usagePollActive(): Boolean {
        val settings = appContainer.settingsState.value ?: return false
        return settings.detectionMode == DetectionMode.USAGE_ACCESS &&
            PermissionChecker.hasUsageAccess(this)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    when (intent.action) {
                        // Solo actúa en modo "Acceso al uso"; en accesibilidad lo hace su propio servicio.
                        Intent.ACTION_SCREEN_OFF ->
                            if (currentMode() == DetectionMode.USAGE_ACCESS) {
                                detector.reset()
                                appContainer.lockEngine.onScreenOff()
                            }
                        Intent.ACTION_USER_PRESENT -> {
                            detector.reset()
                            appContainer.excludedPackages.refresh()
                        }
                    }
                } catch (e: Exception) {
                    ErrorLog.record("Pantalla apagada o encendida (servicio)", e)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        screenReceiver = receiver
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

    private fun currentMode(): DetectionMode =
        appContainer.settingsState.value?.detectionMode ?: DetectionMode.USAGE_ACCESS

    private fun currentProblem(hasLockedApps: Boolean, paused: Boolean): String? {
        if (!hasLockedApps || paused) {
            notReadySince = 0L
            return null
        }
        val mode = currentMode()
        val ready = PermissionChecker.isProtectionReady(this, mode)
        val now = SystemClock.elapsedRealtime()
        if (!ready) {
            if (notReadySince == 0L) notReadySince = now
        } else {
            notReadySince = 0L
        }
        if (ready) {
            // En accesibilidad, además avisa si el servicio está activado pero no conecta.
            if (mode == DetectionMode.ACCESSIBILITY && !appContainer.status.accessibilityConnected) {
                return "LockApp está activado pero no responde. Toca para abrir el diagnóstico."
            }
            return null
        }
        return when (mode) {
            DetectionMode.USAGE_ACCESS ->
                "A LockApp le falta un permiso (acceso al uso o mostrar sobre otras apps) y tus apps no se están bloqueando. Toca para revisarlo."
            DetectionMode.ACCESSIBILITY ->
                "El servicio de accesibilidad está desactivado y tus apps no se están bloqueando. Toca para revisarlo."
        }
    }

    private fun statusText(): String {
        val container = appContainer
        val count = container.lockedAppsState.value?.size ?: 0
        return when {
            container.settingsState.value?.paused == true -> "Bloqueos en pausa"
            !PermissionChecker.isProtectionReady(this, currentMode()) -> "Protección desactivada: toca para revisarlo"
            count == 0 -> "No hay apps bloqueadas"
            count == 1 -> "Protegiendo 1 app"
            else -> "Protegiendo $count apps"
        }
    }

    override fun onDestroy() {
        running = false
        appContainer.status.keepAliveRunning = false
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                ErrorLog.record("Quitar receptor de pantalla (servicio)", e)
            }
        }
        screenReceiver = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val POLL_INTERVAL_MS = 220L

        /** true mientras el servicio está creado: evita pedir arranques repetidos. */
        @Volatile
        private var running = false

        fun start(context: Context) {
            if (running) return
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
            running = false
            start(context)
        }
    }
}
