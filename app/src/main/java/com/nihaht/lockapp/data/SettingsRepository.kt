package com.nihaht.lockapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nihaht.lockapp.security.PinCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Tiempo durante el que no se vuelve a pedir el PIN tras salir de una app desbloqueada. */
enum class GracePeriod(val millis: Long, val label: String) {
    NONE(0L, "Pedir siempre"),
    SECONDS_30(30_000L, "30 segundos"),
    MINUTE_1(60_000L, "1 minuto"),
    MINUTES_5(300_000L, "5 minutos"),
}

/** Fondo de la pantalla de bloqueo. */
sealed interface LockBackground {
    data class Solid(val argb: Int) : LockBackground
    data class Gradient(val preset: Int) : LockBackground

    /** Imagen guardada en el almacenamiento interno; [version] cambia con cada imagen nueva. */
    data class Image(val version: Long) : LockBackground
}

data class AppSettings(
    /** PIN maestro: protege la propia LockApp (lista de apps y ajustes). */
    val masterCredential: PinCredential? = null,
    val biometricEnabled: Boolean = true,
    val gracePeriod: GracePeriod = GracePeriod.NONE,
    val relockOnScreenOff: Boolean = true,
    val paused: Boolean = false,
    val background: LockBackground = LockBackground.Gradient(0),
) {
    val hasMasterPin: Boolean get() = masterCredential != null
}

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                masterCredential = PinCredential.decode(p[MASTER_PIN]),
                biometricEnabled = p[BIOMETRIC] ?: true,
                gracePeriod = GracePeriod.entries.firstOrNull { it.name == p[GRACE] } ?: GracePeriod.NONE,
                relockOnScreenOff = p[RELOCK_SCREEN_OFF] ?: true,
                paused = p[PAUSED] ?: false,
                background = when (p[BG_TYPE]) {
                    BG_SOLID -> LockBackground.Solid(p[BG_VALUE] ?: DEFAULT_SOLID)
                    BG_IMAGE -> LockBackground.Image(p[BG_VERSION] ?: 0L)
                    else -> LockBackground.Gradient(p[BG_VALUE] ?: 0)
                },
            )
        }

    suspend fun setMasterCredential(credential: PinCredential) {
        store.edit { it[MASTER_PIN] = credential.encode() }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        store.edit { it[BIOMETRIC] = enabled }
    }

    suspend fun setGracePeriod(period: GracePeriod) {
        store.edit { it[GRACE] = period.name }
    }

    suspend fun setRelockOnScreenOff(enabled: Boolean) {
        store.edit { it[RELOCK_SCREEN_OFF] = enabled }
    }

    suspend fun setPaused(paused: Boolean) {
        store.edit { it[PAUSED] = paused }
    }

    suspend fun setBackground(background: LockBackground) {
        store.edit { p ->
            when (background) {
                is LockBackground.Solid -> {
                    p[BG_TYPE] = BG_SOLID
                    p[BG_VALUE] = background.argb
                }
                is LockBackground.Gradient -> {
                    p[BG_TYPE] = BG_GRADIENT
                    p[BG_VALUE] = background.preset
                }
                is LockBackground.Image -> {
                    p[BG_TYPE] = BG_IMAGE
                    p[BG_VERSION] = background.version
                }
            }
        }
    }

    private companion object {
        val MASTER_PIN = stringPreferencesKey("master_pin")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val GRACE = stringPreferencesKey("grace_period")
        val RELOCK_SCREEN_OFF = booleanPreferencesKey("relock_screen_off")
        val PAUSED = booleanPreferencesKey("paused")
        val BG_TYPE = stringPreferencesKey("bg_type")
        val BG_VALUE = intPreferencesKey("bg_value")
        val BG_VERSION = longPreferencesKey("bg_version")

        const val BG_SOLID = "solid"
        const val BG_GRADIENT = "gradient"
        const val BG_IMAGE = "image"
        const val DEFAULT_SOLID = 0xFF101418.toInt()
    }
}
