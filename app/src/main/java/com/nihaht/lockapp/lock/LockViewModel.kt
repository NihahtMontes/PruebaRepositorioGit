package com.nihaht.lockapp.lock

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.LockBackground
import com.nihaht.lockapp.diagnostics.launchSafe
import com.nihaht.lockapp.ui.components.BiometricHelper
import com.nihaht.lockapp.ui.components.PinEntryController
import com.nihaht.lockapp.security.Authenticator
import com.nihaht.lockapp.security.CredentialKind
import com.nihaht.lockapp.security.VerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class LockUiState(
    val packageName: String? = null,
    val appLabel: String = "",
    val appIcon: ImageBitmap? = null,
    val background: LockBackground = LockBackground.Gradient(0),
    val backgroundImage: ImageBitmap? = null,
    val biometricAvailable: Boolean = false,
    val kind: CredentialKind = CredentialKind.PIN,
    val haptics: Boolean = true,
    /** true si es el bloqueo de los Ajustes del sistema: se verifica el PIN maestro. */
    val useMaster: Boolean = false,
    val ready: Boolean = false,
    /** true cuando el usuario ya puede volver a la app: la Activity se cierra. */
    val unlocked: Boolean = false,
)

class LockViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    val entry = PinEntryController(
        scope = viewModelScope,
        verify = { secret ->
            val s = _state.value
            when {
                s.packageName == null -> VerifyResult.Wrong(0, 0)
                s.useMaster -> container.authenticator.verifyMaster(secret)
                else -> container.authenticator.verifyApp(s.packageName, secret)
            }
        },
        onSuccess = { markUnlocked() },
        secretLabel = { if (_state.value.kind == CredentialKind.PATTERN) "Patrón" else "PIN" },
    )

    private var loadJob: Job? = null

    /** Prepara la pantalla para [packageName]. Se llama también desde onNewIntent. */
    fun load(packageName: String, useMaster: Boolean = false) {
        val current = _state.value
        if (current.packageName == packageName && current.useMaster == useMaster && !current.unlocked) return

        loadJob?.cancel()
        entry.reset()
        // Primero se rellena lo que se puede obtener al instante para evitar parpadeos.
        val settingsNow = container.settingsState.value
        _state.value = LockUiState(
            packageName = packageName,
            useMaster = useMaster,
            background = settingsNow?.background ?: LockBackground.Gradient(0),
        )
        loadJob = launchSafe(container.errors, "No se pudieron cargar los datos de la app bloqueada") {
            val settings = container.settingsRepository.settings.first()
            if (useMaster) {
                // Bloqueo de los Ajustes del sistema: se pide el PIN/patrón maestro.
                val background = settings.background
                val image = (background as? LockBackground.Image)?.let { container.backgroundImage(it.version) }
                val biometric = settings.biometricEnabled && BiometricHelper.isAvailable(getApplication())
                val remaining = container.authenticator.remainingLockout(Authenticator.MASTER_ID)
                _state.update {
                    it.copy(
                        appLabel = "Ajustes del sistema",
                        background = background,
                        backgroundImage = image,
                        biometricAvailable = biometric,
                        kind = settings.masterCredential?.kind ?: CredentialKind.PIN,
                        haptics = settings.patternHaptics,
                        ready = true,
                    )
                }
                if (remaining > 0) entry.startLockout(remaining)
                return@launchSafe
            }
            val app = container.lockedAppsRepository.lockedApps.first()[packageName]
            if (app == null) {
                // Ya no está bloqueada (se quitó el bloqueo mientras tanto).
                _state.update { it.copy(unlocked = true) }
                return@launchSafe
            }
            val label = withContext(Dispatchers.IO) { container.installedApps.label(packageName) }
            val icon = container.installedApps.icon(packageName, ICON_SIZE_PX)
            val background = settings.background
            val image = (background as? LockBackground.Image)?.let { container.backgroundImage(it.version) }
            val biometric = settings.biometricEnabled && app.allowBiometric &&
                BiometricHelper.isAvailable(getApplication())
            val remaining = container.authenticator.remainingLockout(packageName)

            _state.update {
                it.copy(
                    appLabel = label,
                    appIcon = icon,
                    background = background,
                    backgroundImage = image,
                    biometricAvailable = biometric,
                    kind = app.credential.kind,
                    haptics = settings.patternHaptics,
                    ready = true,
                )
            }
            if (remaining > 0) entry.startLockout(remaining)
        }
    }

    fun onBiometricSuccess() {
        val s = _state.value
        val packageName = s.packageName ?: return
        val id = if (s.useMaster) Authenticator.MASTER_ID else packageName
        launchSafe(container.errors, "No se pudo completar el desbloqueo con huella") { container.authenticator.resetAttempts(id) }
        markUnlocked()
    }

    private fun markUnlocked() {
        val packageName = _state.value.packageName ?: return
        container.sessions.markUnlocked(packageName)
        _state.update { it.copy(unlocked = true) }
    }

    private companion object {
        const val ICON_SIZE_PX = 192
    }
}
