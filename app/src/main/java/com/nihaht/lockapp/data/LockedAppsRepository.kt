package com.nihaht.lockapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nihaht.lockapp.security.PinCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.lockedAppsDataStore: DataStore<Preferences> by preferencesDataStore(name = "locked_apps")

/** Una app bloqueada con su propio PIN. */
data class LockedApp(
    val packageName: String,
    val credential: PinCredential,
    val allowBiometric: Boolean,
)

/**
 * Guarda una entrada por app: "pin:<paquete>" con la credencial (hash + sal)
 * y "bio:<paquete>" indicando si se permite la huella para esa app.
 */
class LockedAppsRepository(context: Context) {

    private val store = context.applicationContext.lockedAppsDataStore

    val lockedApps: Flow<Map<String, LockedApp>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            prefs.asMap().mapNotNull { (key, value) ->
                val name = key.name
                if (!name.startsWith(PIN_PREFIX)) return@mapNotNull null
                val packageName = name.removePrefix(PIN_PREFIX)
                val credential = PinCredential.decode(value as? String) ?: return@mapNotNull null
                packageName to LockedApp(
                    packageName = packageName,
                    credential = credential,
                    allowBiometric = prefs[bioKey(packageName)] ?: false,
                )
            }.toMap()
        }

    /** Bloquea la app (o cambia su PIN si ya estaba bloqueada). */
    suspend fun lock(packageName: String, credential: PinCredential, allowBiometric: Boolean) {
        store.edit {
            it[pinKey(packageName)] = credential.encode()
            it[bioKey(packageName)] = allowBiometric
        }
    }

    suspend fun setAllowBiometric(packageName: String, allow: Boolean) {
        store.edit { it[bioKey(packageName)] = allow }
    }

    suspend fun remove(packageName: String) {
        store.edit {
            it.remove(pinKey(packageName))
            it.remove(bioKey(packageName))
        }
    }

    private companion object {
        const val PIN_PREFIX = "pin:"
        const val BIO_PREFIX = "bio:"

        fun pinKey(packageName: String) = stringPreferencesKey(PIN_PREFIX + packageName)
        fun bioKey(packageName: String) = booleanPreferencesKey(BIO_PREFIX + packageName)
    }
}
