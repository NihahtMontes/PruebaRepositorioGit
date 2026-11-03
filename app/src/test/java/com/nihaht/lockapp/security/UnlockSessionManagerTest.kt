package com.nihaht.lockapp.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockSessionManagerTest {

    private var now = 0L
    private val sessions = UnlockSessionManager { now }

    private val wa = "com.whatsapp"
    private val ig = "com.instagram.android"
    private val home = "com.sec.android.app.launcher"

    @Test
    fun `una app bloqueada pide pin la primera vez`() {
        assertTrue(sessions.onForeground(wa, isLocked = true, graceMs = 30_000))
    }

    @Test
    fun `una app no bloqueada nunca pide pin`() {
        assertFalse(sessions.onForeground(home, isLocked = false, graceMs = 0))
    }

    @Test
    fun `dentro de la app desbloqueada no se vuelve a pedir`() {
        sessions.markUnlocked(wa)
        now = 10 * 60_000
        assertFalse(sessions.onForeground(wa, isLocked = true, graceMs = 0))
    }

    @Test
    fun `con gracia cero se pide al volver`() {
        sessions.markUnlocked(wa)
        sessions.onForeground(home, isLocked = false, graceMs = 0)
        now += 1
        assertTrue(sessions.onForeground(wa, isLocked = true, graceMs = 0))
    }

    @Test
    fun `volver dentro del periodo de gracia no pide pin`() {
        sessions.markUnlocked(wa)
        now = 1_000
        sessions.onForeground(home, isLocked = false, graceMs = 30_000)
        now = 30_999
        assertFalse(sessions.onForeground(wa, isLocked = true, graceMs = 30_000))
    }

    @Test
    fun `volver despues del periodo de gracia pide pin`() {
        sessions.markUnlocked(wa)
        now = 1_000
        sessions.onForeground(home, isLocked = false, graceMs = 30_000)
        now = 31_000
        assertTrue(sessions.onForeground(wa, isLocked = true, graceMs = 30_000))
    }

    @Test
    fun `la gracia cuenta desde la ultima salida tras reanudar`() {
        sessions.markUnlocked(wa)
        now = 0
        sessions.onForeground(home, isLocked = false, graceMs = 60_000)
        now = 50_000
        assertFalse(sessions.onForeground(wa, isLocked = true, graceMs = 60_000))
        now = 100_000
        sessions.onForeground(home, isLocked = false, graceMs = 60_000)
        now = 150_000
        assertFalse(sessions.onForeground(wa, isLocked = true, graceMs = 60_000))
    }

    @Test
    fun `desbloquear una app no desbloquea otra`() {
        sessions.markUnlocked(wa)
        assertTrue(sessions.onForeground(ig, isLocked = true, graceMs = 300_000))
    }

    @Test
    fun `lockAll borra todas las sesiones`() {
        sessions.markUnlocked(wa)
        sessions.onForeground(home, isLocked = false, graceMs = 300_000)
        sessions.lockAll()
        assertTrue(sessions.onForeground(wa, isLocked = true, graceMs = 300_000))
    }

    @Test
    fun `lock de una app borra solo esa sesion`() {
        sessions.markUnlocked(wa)
        sessions.lock(wa)
        assertTrue(sessions.onForeground(wa, isLocked = true, graceMs = 300_000))
    }
}
