package com.nihaht.lockapp.android

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Espera a que [condition] se cumpla procesando el hilo principal (máx. [timeoutMs]). */
fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(20)
    }
    throw AssertionError("La condición no se cumplió en $timeoutMs ms")
}
