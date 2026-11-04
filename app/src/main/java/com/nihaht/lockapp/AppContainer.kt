package com.nihaht.lockapp

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import com.nihaht.lockapp.data.AppSettings
import com.nihaht.lockapp.data.BackgroundStore
import com.nihaht.lockapp.data.InstalledAppsRepository
import com.nihaht.lockapp.data.LockedApp
import com.nihaht.lockapp.data.LockedAppsRepository
import com.nihaht.lockapp.data.LockoutRepository
import com.nihaht.lockapp.data.SettingsRepository
import com.nihaht.lockapp.diagnostics.ErrorReporter
import com.nihaht.lockapp.diagnostics.ProtectionStatus
import com.nihaht.lockapp.security.Authenticator
import com.nihaht.lockapp.security.UnlockSessionManager
import com.nihaht.lockapp.service.ExcludedPackages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** Contenedor de dependencias manual: una sola instancia por proceso. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(appContext)
    val lockedAppsRepository = LockedAppsRepository(appContext)
    val lockoutRepository = LockoutRepository(appContext)
    val installedApps = InstalledAppsRepository(appContext)
    val backgroundStore = BackgroundStore(appContext)
    val excludedPackages = ExcludedPackages(appContext)
    val authenticator = Authenticator(appContext, settingsRepository, lockedAppsRepository, lockoutRepository)
    val sessions = UnlockSessionManager { SystemClock.elapsedRealtime() }
    val status = ProtectionStatus()
    val errors = ErrorReporter()

    // Copias en memoria para que el servicio de accesibilidad decida al instante, sin esperar al disco.
    val settingsState: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)
    val lockedAppsState: StateFlow<Map<String, LockedApp>?> =
        lockedAppsRepository.lockedApps.stateIn(appScope, SharingStarted.Eagerly, null)

    /** Ajustes actuales. Solo bloquea el hilo la primera vez, si aún no se han leído. */
    fun currentSettings(): AppSettings =
        settingsState.value ?: runBlocking(Dispatchers.IO) { settingsRepository.settings.first() }

    fun currentLockedApps(): Map<String, LockedApp> =
        lockedAppsState.value ?: runBlocking(Dispatchers.IO) { lockedAppsRepository.lockedApps.first() }

    /**
     * Mientras el asistente abre Ajustes para conceder permisos, no se bloquea
     * la app de Ajustes aunque el usuario la haya bloqueado.
     */
    @Volatile
    var settingsBypassUntil: Long = 0L
        private set

    fun allowSettingsTemporarily() {
        settingsBypassUntil = SystemClock.elapsedRealtime() + SETTINGS_BYPASS_MS
    }

    @Volatile
    private var cachedBackground: Pair<Long, ImageBitmap>? = null

    /** Imagen de fondo ya decodificada, para que la pantalla de bloqueo aparezca sin retraso. */
    suspend fun backgroundImage(version: Long): ImageBitmap? {
        cachedBackground?.let { (v, image) -> if (v == version) return image }
        val image = backgroundStore.load() ?: return null
        cachedBackground = version to image
        return image
    }

    private companion object {
        const val SETTINGS_BYPASS_MS = 2 * 60_000L
    }
}
