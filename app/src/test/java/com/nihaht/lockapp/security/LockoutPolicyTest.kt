package com.nihaht.lockapp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    private val boot = 7

    @Test
    fun `los primeros cuatro fallos no bloquean`() {
        var state = LockoutState(bootCount = boot)
        repeat(4) { state = LockoutPolicy.registerFailure(state, now = 1_000, bootCount = boot) }
        assertEquals(4, state.failedAttempts)
        assertEquals(0L, LockoutPolicy.remainingMs(state, 1_000))
        assertEquals(1, LockoutPolicy.attemptsLeftBeforeLockout(state))
    }

    @Test
    fun `la espera crece de forma progresiva`() {
        assertEquals(30_000L, LockoutPolicy.durationFor(5))
        assertEquals(60_000L, LockoutPolicy.durationFor(6))
        assertEquals(300_000L, LockoutPolicy.durationFor(7))
        assertEquals(900_000L, LockoutPolicy.durationFor(8))
        assertEquals(1_800_000L, LockoutPolicy.durationFor(9))
        assertEquals(1_800_000L, LockoutPolicy.durationFor(50))
    }

    @Test
    fun `el quinto fallo bloquea 30 segundos`() {
        var state = LockoutState(bootCount = boot)
        repeat(5) { state = LockoutPolicy.registerFailure(state, now = 10_000, bootCount = boot) }
        assertEquals(30_000L, LockoutPolicy.remainingMs(state, 10_000))
        assertEquals(10_000L, LockoutPolicy.remainingMs(state, 30_000))
        assertEquals(0L, LockoutPolicy.remainingMs(state, 40_000))
    }

    @Test
    fun `un acierto reinicia el contador`() {
        val state = LockoutPolicy.registerSuccess(boot)
        assertEquals(0, state.failedAttempts)
        assertEquals(0L, state.lockedUntil)
    }

    @Test
    fun `tras reiniciar el telefono la espera se reaplica completa`() {
        val before = LockoutState(failedAttempts = 6, lockedUntil = 5_000_000, bootCount = 1)
        val after = LockoutPolicy.normalize(before, now = 2_000, bootCount = 2)
        assertEquals(2_000L + 60_000L, after.lockedUntil)
        assertEquals(2, after.bootCount)
        assertEquals(6, after.failedAttempts)
    }

    @Test
    fun `normalizar en el mismo arranque no cambia nada`() {
        val state = LockoutState(failedAttempts = 5, lockedUntil = 50_000, bootCount = boot)
        assertEquals(state, LockoutPolicy.normalize(state, now = 10_000, bootCount = boot))
    }

    @Test
    fun `sin espera pendiente el reinicio solo actualiza el arranque`() {
        val state = LockoutState(failedAttempts = 2, lockedUntil = 0, bootCount = 1)
        val after = LockoutPolicy.normalize(state, now = 10, bootCount = 2)
        assertEquals(0L, after.lockedUntil)
        assertTrue(after.bootCount == 2 && after.failedAttempts == 2)
    }
}
