package com.nihaht.lockapp.security

/**
 * Estado de intentos fallidos de una credencial.
 * [lockedUntil] usa el reloj monotónico (elapsedRealtime) para que cambiar
 * la hora del teléfono no acorte la espera. [bootCount] permite detectar
 * reinicios, porque ese reloj vuelve a cero al arrancar.
 */
data class LockoutState(
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
    val bootCount: Int = -1,
)

/** Bloqueo temporal progresivo tras [FREE_ATTEMPTS] fallos. */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND

    /** Espera que se aplica al alcanzar [failedAttempts] fallos seguidos. */
    fun durationFor(failedAttempts: Int): Long = when {
        failedAttempts < FREE_ATTEMPTS -> 0L
        failedAttempts == FREE_ATTEMPTS -> 30 * SECOND
        failedAttempts == FREE_ATTEMPTS + 1 -> 1 * MINUTE
        failedAttempts == FREE_ATTEMPTS + 2 -> 5 * MINUTE
        failedAttempts == FREE_ATTEMPTS + 3 -> 15 * MINUTE
        else -> 30 * MINUTE
    }

    fun remainingMs(state: LockoutState, now: Long): Long =
        (state.lockedUntil - now).coerceAtLeast(0L)

    fun attemptsLeftBeforeLockout(state: LockoutState): Int =
        (FREE_ATTEMPTS - state.failedAttempts).coerceAtLeast(0)

    fun registerFailure(state: LockoutState, now: Long, bootCount: Int): LockoutState {
        val failed = state.failedAttempts + 1
        val duration = durationFor(failed)
        return LockoutState(
            failedAttempts = failed,
            lockedUntil = if (duration > 0) now + duration else 0L,
            bootCount = bootCount,
        )
    }

    fun registerSuccess(bootCount: Int): LockoutState = LockoutState(bootCount = bootCount)

    /**
     * Ajusta un estado guardado en un arranque anterior: como elapsedRealtime
     * se reinicia, la espera pendiente se vuelve a aplicar completa.
     */
    fun normalize(state: LockoutState, now: Long, bootCount: Int): LockoutState = when {
        state.bootCount == bootCount -> state
        state.lockedUntil > 0L -> state.copy(
            lockedUntil = now + durationFor(state.failedAttempts),
            bootCount = bootCount,
        )
        else -> state.copy(bootCount = bootCount)
    }
}
