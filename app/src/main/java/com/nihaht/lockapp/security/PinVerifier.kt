package com.nihaht.lockapp.security

sealed interface VerifyResult {
    data object Success : VerifyResult

    /** PIN incorrecto. Si [lockedForMs] > 0, empieza una espera. */
    data class Wrong(val attemptsLeft: Int, val lockedForMs: Long) : VerifyResult

    /** Hay una espera activa: el intento ni siquiera se comprueba. */
    data class LockedOut(val remainingMs: Long) : VerifyResult
}

/** Une el hash del PIN con la política de intentos. Sin dependencias de Android. */
object PinVerifier {

    fun verify(
        pin: String,
        credential: PinCredential,
        state: LockoutState,
        now: Long,
        bootCount: Int,
    ): Pair<VerifyResult, LockoutState> {
        val current = LockoutPolicy.normalize(state, now, bootCount)
        val remaining = LockoutPolicy.remainingMs(current, now)
        if (remaining > 0) return VerifyResult.LockedOut(remaining) to current

        if (PinHasher.verify(pin, credential)) {
            return VerifyResult.Success to LockoutPolicy.registerSuccess(bootCount)
        }
        val next = LockoutPolicy.registerFailure(current, now, bootCount)
        val result = VerifyResult.Wrong(
            attemptsLeft = LockoutPolicy.attemptsLeftBeforeLockout(next),
            lockedForMs = LockoutPolicy.remainingMs(next, now),
        )
        return result to next
    }
}
