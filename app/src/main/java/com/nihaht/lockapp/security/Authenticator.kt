package com.nihaht.lockapp.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.nihaht.lockapp.data.LockedAppsRepository
import com.nihaht.lockapp.data.LockoutRepository
import com.nihaht.lockapp.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Punto único para comprobar PIN. Cada credencial (el PIN maestro y el de
 * cada app) tiene su propio contador de intentos fallidos.
 */
class Authenticator(
    context: Context,
    private val settings: SettingsRepository,
    private val lockedApps: LockedAppsRepository,
    private val lockout: LockoutRepository,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    suspend fun verifyMaster(pin: String): VerifyResult {
        val credential = settings.settings.first().masterCredential
            ?: return VerifyResult.Wrong(attemptsLeft = 0, lockedForMs = 0)
        return verify(MASTER_ID, credential, pin)
    }

    suspend fun verifyApp(packageName: String, pin: String): VerifyResult {
        // Si la app ya no está bloqueada no hay nada que comprobar.
        val app = lockedApps.lockedApps.first()[packageName] ?: return VerifyResult.Success
        return verify(packageName, app.credential, pin)
    }

    /** Milisegundos de espera que quedan para [id], o 0. */
    suspend fun remainingLockout(id: String): Long = mutex.withLock {
        val now = SystemClock.elapsedRealtime()
        val state = LockoutPolicy.normalize(lockout.get(id), now, bootCount())
        LockoutPolicy.remainingMs(state, now)
    }

    suspend fun resetAttempts(id: String) {
        mutex.withLock { lockout.clear(id) }
    }

    suspend fun createCredential(pin: String): PinCredential =
        withContext(Dispatchers.Default) { PinHasher.create(pin) }

    private suspend fun verify(id: String, credential: PinCredential, pin: String): VerifyResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val (result, newState) = PinVerifier.verify(
                    pin = pin,
                    credential = credential,
                    state = lockout.get(id),
                    now = SystemClock.elapsedRealtime(),
                    bootCount = bootCount(),
                )
                lockout.set(id, newState)
                result
            }
        }

    private fun bootCount(): Int =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, 0)

    companion object {
        const val MASTER_ID = "__master__"
    }
}
