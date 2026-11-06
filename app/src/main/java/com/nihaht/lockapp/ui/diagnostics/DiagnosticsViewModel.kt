package com.nihaht.lockapp.ui.diagnostics

import android.app.Application
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.work.WorkManager
import com.nihaht.lockapp.BuildConfig
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.data.DetectionMode
import com.nihaht.lockapp.diagnostics.ago
import com.nihaht.lockapp.diagnostics.launchSafe
import com.nihaht.lockapp.permissions.PermissionChecker
import com.nihaht.lockapp.service.KeepAliveService
import com.nihaht.lockapp.service.WatchdogWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Una comprobación. [critical]: si falla, la protección no funciona. */
data class Check(
    val title: String,
    val ok: Boolean,
    val detail: String,
    val critical: Boolean = true,
)

data class DiagnosticsReport(
    val checks: List<Check>,
    val allOk: Boolean,
    val accessibilityProblem: Boolean,
    val errorCount: Int,
    val lastError: String?,
    val technical: List<String>,
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    private val _report = MutableStateFlow<DiagnosticsReport?>(null)
    val report: StateFlow<DiagnosticsReport?> = _report.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    init {
        ErrorLog.markCrashSeen()
        refresh()
    }

    fun refresh() {
        launchSafe(container.errors, "No se pudo completar el diagnóstico") {
            _report.value = withContext(Dispatchers.IO) { build() }
        }
    }

    /** Vuelve a arrancar todo lo que LockApp puede arrancar por sí misma. */
    fun restartProtection() {
        if (_working.value) return
        launchSafe(container.errors, "No se pudo reiniciar la protección") {
            _working.value = true
            try {
                val context = getApplication<Application>()
                KeepAliveService.restart(context)
                WatchdogWorker.schedule(context)
                withContext(Dispatchers.IO) { container.excludedPackages.refresh() }
                container.sessions.lockAll()
                delay(RESTART_SETTLE_MS)
                _report.value = withContext(Dispatchers.IO) { build() }
                container.errors.info(
                    if (_report.value?.allOk == true) "Protección reiniciada: todo funciona"
                    else "Protección reiniciada. Revisa los puntos en rojo",
                )
            } finally {
                _working.value = false
            }
        }
    }

    fun clearErrors() {
        ErrorLog.clear()
        refresh()
    }

    /** Texto completo para copiar y enviar si algo falla. */
    fun fullReport(): String {
        val report = _report.value ?: return "Diagnóstico no disponible"
        return buildString {
            appendLine("INFORME DE LOCKAPP")
            appendLine(if (report.allOk) "Estado: todo funciona" else "Estado: hay problemas")
            report.checks.forEach { appendLine("${if (it.ok) "[OK]" else "[X]"} ${it.title}: ${it.detail}") }
            appendLine()
            report.technical.forEach { appendLine(it) }
            appendLine()
            appendLine("--- Errores (${report.errorCount}) ---")
            append(ErrorLog.read().takeLast(MAX_REPORT_LOG_CHARS).ifEmpty { "Ninguno" })
        }
    }

    private suspend fun build(): DiagnosticsReport {
        val context = getApplication<Application>()
        val status = container.status
        val settings = container.settingsRepository.settings.first()
        val locked = container.lockedAppsRepository.lockedApps.first()
        val mode = settings.detectionMode
        val accessibilityEnabled = PermissionChecker.isAccessibilityServiceEnabled(context)
        val connected = accessibilityEnabled && status.accessibilityConnected
        val usageAccess = PermissionChecker.hasUsageAccess(context)
        val overlay = PermissionChecker.hasOverlay(context)
        val ready = PermissionChecker.isProtectionReady(context, mode)
        val battery = PermissionChecker.isIgnoringBatteryOptimizations(context)
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val watchdog = try {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WatchdogWorker.WORK_NAME).get()
                .any { !it.state.isFinished }
        } catch (e: Exception) {
            ErrorLog.record("Consultar la vigilancia periódica", e)
            false
        }

        val checks = buildList {
            if (mode == DetectionMode.USAGE_ACCESS) {
                add(
                    Check(
                        "Acceso al uso",
                        usageAccess,
                        if (usageAccess) "Activado · LockApp ve qué app abres (sin leer la pantalla)"
                        else "Desactivado: actívalo en Ajustes > Acceso al uso > LockApp",
                    ),
                )
                add(
                    Check(
                        "Mostrar sobre otras apps",
                        overlay,
                        if (overlay) "Permitido · puede mostrar el PIN encima de la app"
                        else "Desactivado: sin este permiso no aparece la pantalla de bloqueo",
                    ),
                )
                add(
                    Check(
                        "Detección de apps",
                        ready && status.keepAliveRunning,
                        when {
                            ready && status.keepAliveRunning -> "Funcionando · última app ${status.lastEventPackage ?: "ninguna"}"
                            !ready -> "Necesita los permisos de arriba"
                            else -> "El servicio en segundo plano está parado: pulsa «Reiniciar protección»"
                        },
                    ),
                )
            } else {
                add(
                    Check(
                        "Servicio de accesibilidad",
                        accessibilityEnabled,
                        if (accessibilityEnabled) "Activado en los ajustes de Android"
                        else "Desactivado: actívalo en Ajustes > Accesibilidad > Aplicaciones instaladas > LockApp",
                    ),
                )
                add(
                    Check(
                        "Detección de apps",
                        connected,
                        when {
                            connected -> "Funcionando · último evento ${ago(status.lastEventAt)}"
                            accessibilityEnabled -> "Activado pero sin responder: desactívalo y vuelve a activarlo en Accesibilidad"
                            else -> "Necesita el servicio de accesibilidad"
                        },
                    ),
                )
            }
            add(
                Check(
                    "Servicio en segundo plano",
                    status.keepAliveRunning,
                    if (status.keepAliveRunning) "En marcha" else "Detenido: pulsa «Reiniciar protección»",
                ),
            )
            add(
                Check(
                    "Batería sin restricciones",
                    battery,
                    if (battery) "Android no cerrará LockApp para ahorrar batería"
                    else "Android puede cerrar LockApp: quita la optimización de batería",
                ),
            )
            add(
                Check(
                    "Bloqueos activos",
                    !settings.paused,
                    if (settings.paused) "Están en pausa: reactívalos en Ajustes" else "Sí",
                ),
            )
            add(
                Check(
                    "Apps bloqueadas",
                    locked.isNotEmpty(),
                    if (locked.isEmpty()) "Todavía no has bloqueado ninguna app"
                    else "${locked.size} ${if (locked.size == 1) "app bloqueada" else "apps bloqueadas"}",
                    critical = false,
                ),
            )
            add(
                Check(
                    "Avisos (notificaciones)",
                    notifications,
                    if (notifications) "Te avisará si la protección deja de funcionar"
                    else "Permite las notificaciones para recibir avisos si algo falla",
                    critical = false,
                ),
            )
            add(
                Check(
                    "Vigilancia cada 15 minutos",
                    watchdog,
                    if (watchdog) "Programada: vuelve a arrancar LockApp si el sistema lo cierra"
                    else "No programada: pulsa «Reiniciar protección»",
                    critical = false,
                ),
            )
            if (mode == DetectionMode.ACCESSIBILITY) {
                add(
                    Check(
                        "Protección de Recientes",
                        settings.protectRecents && status.recentsDetectedAt > 0,
                        when {
                            !settings.protectRecents -> "Desactivada en Ajustes"
                            status.recentsDetectedAt > 0 ->
                                "Funciona · Recientes detectado ${ago(status.recentsDetectedAt)}, protegido ${status.recentsShieldShown} veces"
                            else -> "Aún sin comprobar: abre Recientes una vez y vuelve aquí"
                        },
                        critical = false,
                    ),
                )
            }
        }

        val technical = buildList {
            add("Teléfono: ${Build.MANUFACTURER} ${Build.MODEL}")
            add("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · LockApp ${BuildConfig.VERSION_NAME}")
            add("Método: " + if (mode == DetectionMode.USAGE_ACCESS) "Acceso al uso" else "Accesibilidad")
            add("Bloqueos mostrados desde el arranque: ${status.locksShown} (último ${ago(status.lastLockAt)})")
            add("Última app detectada: ${status.lastEventPackage ?: "ninguna"}")
            status.launcherEvents().forEach { add("Launcher: $it") }
        }

        return DiagnosticsReport(
            checks = checks,
            allOk = checks.filter { it.critical }.all { it.ok },
            accessibilityProblem = !ready,
            errorCount = ErrorLog.count(),
            lastError = ErrorLog.lastEntry(),
            technical = technical,
        )
    }

    private companion object {
        const val RESTART_SETTLE_MS = 1_500L
        const val MAX_REPORT_LOG_CHARS = 6_000
    }
}
