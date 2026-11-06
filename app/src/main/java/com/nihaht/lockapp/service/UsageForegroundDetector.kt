package com.nihaht.lockapp.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Detecta la app en primer plano con el permiso "Acceso al uso", sin
 * accesibilidad. Solo obtiene el NOMBRE del paquete; no puede leer la pantalla
 * ni lo que el usuario escribe, por eso las apps de banca no lo rechazan.
 */
class UsageForegroundDetector(context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private var lastPackage: String? = null
    private var lastEventTime = 0L

    /**
     * Devuelve el paquete que ha pasado a primer plano desde la última consulta,
     * o null si no ha cambiado. Hay que llamarlo con frecuencia (cada ~0,2 s).
     */
    fun poll(): String? {
        val usm = usageStats ?: return null
        val now = System.currentTimeMillis()
        val begin = if (lastEventTime == 0L) now - INITIAL_WINDOW_MS else lastEventTime
        val events = usm.queryEvents(begin, now)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND (== ACTIVITY_RESUMED en API 29+).
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = event.packageName
            }
            if (event.timeStamp >= lastEventTime) lastEventTime = event.timeStamp + 1
        }
        if (latest != null && latest != lastPackage) {
            lastPackage = latest
            return latest
        }
        return null
    }

    /** Olvida el estado (p. ej. al encender la pantalla) para volver a detectar. */
    fun reset() {
        lastPackage = null
        lastEventTime = 0L
    }

    private companion object {
        const val INITIAL_WINDOW_MS = 10_000L
    }
}
