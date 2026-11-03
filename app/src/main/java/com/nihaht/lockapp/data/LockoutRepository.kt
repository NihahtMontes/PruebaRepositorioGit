package com.nihaht.lockapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nihaht.lockapp.security.LockoutState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.lockoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "lockout")

/** Intentos fallidos por credencial (PIN maestro o PIN de cada app). Se guarda en disco para que reiniciar la app no los borre. */
class LockoutRepository(context: Context) {

    private val store = context.applicationContext.lockoutDataStore

    suspend fun get(id: String): LockoutState {
        val prefs = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return decode(prefs[key(id)]) ?: LockoutState()
    }

    suspend fun set(id: String, state: LockoutState) {
        store.edit {
            if (state.failedAttempts == 0 && state.lockedUntil == 0L) it.remove(key(id))
            else it[key(id)] = encode(state)
        }
    }

    suspend fun clear(id: String) {
        store.edit { it.remove(key(id)) }
    }

    private fun key(id: String) = stringPreferencesKey("lo:$id")

    private fun encode(s: LockoutState) = "${s.failedAttempts}:${s.lockedUntil}:${s.bootCount}"

    private fun decode(text: String?): LockoutState? {
        val parts = text?.split(':') ?: return null
        if (parts.size != 3) return null
        return LockoutState(
            failedAttempts = parts[0].toIntOrNull() ?: return null,
            lockedUntil = parts[1].toLongOrNull() ?: return null,
            bootCount = parts[2].toIntOrNull() ?: return null,
        )
    }
}
