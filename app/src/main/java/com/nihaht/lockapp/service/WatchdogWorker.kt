package com.nihaht.lockapp.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.permissions.PermissionChecker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Vigilancia cada 15 minutos (sobrevive a reinicios y a que el sistema cierre
 * la app): vuelve a arrancar el servicio en segundo plano y avisa si la
 * protección está desactivada.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val context = applicationContext
            KeepAliveService.start(context)
            val container = context.appContainer
            val hasLockedApps = container.lockedAppsRepository.lockedApps.first().isNotEmpty()
            val paused = container.settingsRepository.settings.first().paused
            if (hasLockedApps && !paused && !PermissionChecker.isAccessibilityServiceEnabled(context)) {
                Notifications.ensureChannels(context)
                Notifications.showProtectionProblem(
                    context,
                    "El servicio de accesibilidad está desactivado y tus apps no se están bloqueando. Toca para revisarlo.",
                )
            }
        } catch (e: Exception) {
            ErrorLog.record("Vigilancia periódica", e)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "vigilancia_lockapp"

        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                ErrorLog.record("Programar la vigilancia periódica", e)
            }
        }
    }
}
