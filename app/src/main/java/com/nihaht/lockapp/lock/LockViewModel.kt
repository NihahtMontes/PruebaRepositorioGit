package com.nihaht.lockapp.lock

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.LockBackground
import com.nihaht.lockapp.ui.components.BiometricHelper
import com.nihaht.lockapp.ui.components.PinEntryController
import com.nihaht.lockapp.security.VerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LockUiState(
    val packageName: String? = null,
    val appLabel: String = "",
    val appIcon: ImageBitmap? = null,
    val background: LockBackground = LockBackground.Gradient(0),
    val backgroundImage: ImageBitmap? = null,
    val biometricAvailable: Boolean = false,
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
        verify = { pin ->
            val packageName = _state.value.packageName
            if (packageName == null) VerifyResult.Wrong(0, 0)
            else container.authenticator.verifyApp(packageName, pin)
        },
        onSuccess = { markUnlocked() },
    )

    private var loadJob: Job? = null

    /** Prepara la pantalla para [packageName]. Se llama también desde onNewIntent. */
    fun load(packageName: String) {
        val current = _state.value
        if (current.packageName == packageName && !current.unlocked) return

        loadJob?.cancel()
        entry.reset()
        // Primero se rellena lo que se puede obtener al instante para evitar parpadeos.
        val settingsNow = container.settingsState.value
        _state.value = LockUiState(
            packageName = packageName,
            background = settingsNow?.background ?: LockBackground.Gradient(0),
        )
        loadJob = viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val app = container.lockedAppsRepository.lockedApps.first()[packageName]
            if (app == null) {
                // Ya no está bloqueada (se quitó el bloqueo mientras tanto).
                _state.update { it.copy(unlocked = true) }
                return@launch
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
                    ready = true,
                )
            }
            if (remaining > 0) entry.startLockout(remaining)
        }
    }

    fun onBiometricSuccess() {
        val packageName = _state.value.packageName ?: return
        viewModelScope.launch { container.authenticator.resetAttempts(packageName) }
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
