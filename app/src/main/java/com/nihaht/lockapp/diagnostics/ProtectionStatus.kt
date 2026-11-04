package com.nihaht.lockapp.diagnostics

import android.os.SystemClock

/**
 * Estado en memoria de la protección, para la pantalla de Diagnóstico.
 * Lo actualizan el servicio de accesibilidad y el servicio en segundo plano.
 */
class ProtectionStatus {

    @Volatile var accessibilityConnected: Boolean = false
    @Volatile var accessibilityConnectedAt: Long = 0L
    @Volatile var lastEventAt: Long = 0L
    @Volatile var lastEventPackage: String? = null
    @Volatile var locksShown: Int = 0
    @Volatile var lastLockAt: Long = 0L
    @Volatile var keepAliveRunning: Boolean = false
    @Volatile var recentsDetectedAt: Long = 0L
    @Volatile var recentsShieldShown: Int = 0

    private val launcherEvents = ArrayDeque<String>()

    fun noteEvent(packageName: String) {
        lastEventAt = SystemClock.elapsedRealtime()
        lastEventPackage = packageName
    }

    fun noteLock() {
        locksShown++
        lastLockAt = SystemClock.elapsedRealtime()
    }

    /** Guarda los últimos eventos del launcher para ajustar la detección de Recientes. */
    @Synchronized
    fun noteLauncherEvent(description: String) {
        if (launcherEvents.lastOrNull() == description) return
        launcherEvents.addLast(description)
        while (launcherEvents.size > MAX_LAUNCHER_EVENTS) launcherEvents.removeFirst()
    }

    @Synchronized
    fun launcherEvents(): List<String> = launcherEvents.toList()

    private companion object {
        const val MAX_LAUNCHER_EVENTS = 10
    }
}

/** "hace 5 s", "hace 3 min"… a partir de un instante de elapsedRealtime. */
fun ago(elapsedAt: Long): String {
    if (elapsedAt <= 0L) return "nunca"
    val seconds = (SystemClock.elapsedRealtime() - elapsedAt) / 1000
    return when {
        seconds < 60 -> "hace $seconds s"
        seconds < 3600 -> "hace ${seconds / 60} min"
        else -> "hace ${seconds / 3600} h"
    }
}
