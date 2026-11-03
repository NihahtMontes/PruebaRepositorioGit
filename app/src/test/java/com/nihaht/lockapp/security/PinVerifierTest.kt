package com.nihaht.lockapp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinVerifierTest {

    private val cred = PinHasher.create("1357", iterations = 1_000)
    private val boot = 3

    @Test
    fun `pin correcto devuelve exito y limpia fallos`() {
        val start = LockoutState(failedAttempts = 3, bootCount = boot)
        val (result, state) = PinVerifier.verify("1357", cred, start, now = 0, bootCount = boot)
        assertEquals(VerifyResult.Success, result)
        assertEquals(0, state.failedAttempts)
    }

    @Test
    fun `pin incorrecto informa intentos restantes`() {
        val (result, state) = PinVerifier.verify("0000", cred, LockoutState(bootCount = boot), now = 0, bootCount = boot)
        assertEquals(VerifyResult.Wrong(attemptsLeft = 4, lockedForMs = 0), result)
        assertEquals(1, state.failedAttempts)
    }

    @Test
    fun `durante la espera ni el pin correcto entra`() {
        var state = LockoutState(bootCount = boot)
        repeat(5) { state = PinVerifier.verify("0000", cred, state, now = 1_000, bootCount = boot).second }
        val (result, after) = PinVerifier.verify("1357", cred, state, now = 2_000, bootCount = boot)
        assertTrue(result is VerifyResult.LockedOut)
        assertEquals(29_000L, (result as VerifyResult.LockedOut).remainingMs)
        assertEquals(state, after)
    }

    @Test
    fun `al terminar la espera el pin correcto vuelve a funcionar`() {
        var state = LockoutState(bootCount = boot)
        repeat(5) { state = PinVerifier.verify("0000", cred, state, now = 1_000, bootCount = boot).second }
        val (result, _) = PinVerifier.verify("1357", cred, state, now = 31_000, bootCount = boot)
        assertEquals(VerifyResult.Success, result)
    }

    @Test
    fun `el quinto fallo devuelve la espera iniciada`() {
        var state = LockoutState(bootCount = boot)
        repeat(4) { state = PinVerifier.verify("0000", cred, state, now = 0, bootCount = boot).second }
        val (result, _) = PinVerifier.verify("0000", cred, state, now = 0, bootCount = boot)
        assertEquals(VerifyResult.Wrong(attemptsLeft = 0, lockedForMs = 30_000), result)
    }
}
