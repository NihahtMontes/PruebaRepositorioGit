package com.nihaht.lockapp.ui.main

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.AppSettings
import com.nihaht.lockapp.data.GracePeriod
import com.nihaht.lockapp.data.LockBackground
import com.nihaht.lockapp.security.Authenticator
import com.nihaht.lockapp.ui.components.BiometricHelper
import com.nihaht.lockapp.ui.components.PinEntryController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Estado de la puerta de entrada a LockApp (protegida por el PIN maestro). */
enum class Gate { LOADING, SETUP, LOCKED, UNLOCKED }

enum class ChangeMasterStep { VERIFY, NEW_PIN, DONE }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    val settings: StateFlow<AppSettings?> = container.settingsState

    private val unlocked = MutableStateFlow(false)

    val gate: StateFlow<Gate> = combine(settings, unlocked) { s, isUnlocked ->
        when {
            s == null -> Gate.LOADING
            !s.hasMasterPin -> Gate.SETUP
            isUnlocked -> Gate.UNLOCKED
            else -> Gate.LOCKED
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.LOADING)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Teclado de la pantalla de entrada. */
    val masterEntry = PinEntryController(
        scope = viewModelScope,
        verify = { container.authenticator.verifyMaster(it) },
        onSuccess = { unlocked.value = true },
    )

    /** Teclado para confirmar el PIN maestro actual antes de cambiarlo. */
    val changeMasterEntry = PinEntryController(
        scope = viewModelScope,
        verify = { container.authenticator.verifyMaster(it) },
        onSuccess = { _changeMasterStep.value = ChangeMasterStep.NEW_PIN },
    )

    private val _changeMasterStep = MutableStateFlow(ChangeMasterStep.VERIFY)
    val changeMasterStep: StateFlow<ChangeMasterStep> = _changeMasterStep.asStateFlow()

    private var backgroundedAt = 0L

    fun canUseBiometricForMaster(): Boolean =
        settings.value?.biometricEnabled == true && BiometricHelper.isAvailable(getApplication())

    fun isBiometricHardwareAvailable(): Boolean = BiometricHelper.isAvailable(getApplication())

    fun createMasterPin(pin: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val credential = container.authenticator.createCredential(pin)
                // Se marca como abierta antes de guardar para no mostrar un instante
                // la pantalla de "introduce tu PIN maestro" (ni lanzar la huella).
                unlocked.value = true
                container.settingsRepository.setMasterCredential(credential)
                container.authenticator.resetAttempts(Authenticator.MASTER_ID)
            } catch (e: IOException) {
                unlocked.value = false
                _message.value = "No se pudo guardar el PIN maestro. Inténtalo de nuevo"
            } finally {
                _busy.value = false
            }
        }
    }

    fun onMasterBiometricSuccess() {
        viewModelScope.launch { container.authenticator.resetAttempts(Authenticator.MASTER_ID) }
        masterEntry.reset()
        unlocked.value = true
    }

    /** Si LockApp estuvo más de un minuto en segundo plano se vuelve a pedir el PIN maestro. */
    fun onAppForeground() {
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (backgroundedAt > 0 && away > RELOCK_AFTER_MS) lockNow()
        backgroundedAt = 0L
        viewModelScope.launch {
            masterEntry.startLockout(container.authenticator.remainingLockout(Authenticator.MASTER_ID))
        }
    }

    fun onAppBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun lockNow() {
        unlocked.value = false
        masterEntry.reset()
    }

    // --- Cambio del PIN maestro ---

    fun startChangeMaster() {
        changeMasterEntry.reset()
        _changeMasterStep.value = ChangeMasterStep.VERIFY
    }

    fun saveNewMasterPin(pin: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val credential = container.authenticator.createCredential(pin)
                container.settingsRepository.setMasterCredential(credential)
                container.authenticator.resetAttempts(Authenticator.MASTER_ID)
                _changeMasterStep.value = ChangeMasterStep.DONE
            } catch (e: IOException) {
                _message.value = "No se pudo guardar el PIN maestro. Inténtalo de nuevo"
            } finally {
                _busy.value = false
            }
        }
    }

    // --- Ajustes ---

    fun setBiometricEnabled(enabled: Boolean) = update { container.settingsRepository.setBiometricEnabled(enabled) }

    fun setGracePeriod(period: GracePeriod) = update {
        container.settingsRepository.setGracePeriod(period)
        container.sessions.lockAll()
    }

    fun setRelockOnScreenOff(enabled: Boolean) = update { container.settingsRepository.setRelockOnScreenOff(enabled) }

    fun setPaused(paused: Boolean) = update {
        container.settingsRepository.setPaused(paused)
        if (!paused) container.sessions.lockAll()
    }

    fun setBackground(background: LockBackground) = update { container.settingsRepository.setBackground(background) }

    fun setImageBackground(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            if (container.backgroundStore.saveFromUri(uri)) {
                container.settingsRepository.setBackground(LockBackground.Image(System.currentTimeMillis()))
            } else {
                _message.value = "No se pudo cargar la imagen"
            }
            _busy.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Se llama al abrir Ajustes del sistema desde el asistente de permisos. */
    fun allowSystemSettingsTemporarily() = container.allowSettingsTemporarily()

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                _message.value = "No se pudo guardar el ajuste"
            }
        }
    }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}
