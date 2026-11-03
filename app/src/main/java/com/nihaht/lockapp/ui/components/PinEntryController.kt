package com.nihaht.lockapp.ui.components

import android.os.SystemClock
import com.nihaht.lockapp.security.PinHasher
import com.nihaht.lockapp.security.VerifyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PinEntryState(
    val pin: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val checking: Boolean = false,
    val lockoutRemainingMs: Long = 0L,
) {
    val canType: Boolean get() = !checking && lockoutRemainingMs == 0L

    val displayMessage: String?
        get() = if (lockoutRemainingMs > 0) {
            "Demasiados intentos. Espera ${formatRemaining(lockoutRemainingMs)}"
        } else {
            message
        }
}

/** Formatea milisegundos como "m:ss". */
fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Lógica compartida de un teclado de PIN que verifica contra una credencial:
 * escribir, borrar, comprobar y mostrar la cuenta atrás del bloqueo temporal.
 */
class PinEntryController(
    private val scope: CoroutineScope,
    private val verify: suspend (String) -> VerifyResult,
    private val onSuccess: () -> Unit,
) {
    private val _state = MutableStateFlow(PinEntryState())
    val state: StateFlow<PinEntryState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun reset() {
        countdownJob?.cancel()
        _state.value = PinEntryState()
    }

    fun onDigit(digit: Char) {
        _state.update {
            if (!it.canType || it.pin.length >= PinHasher.MAX_LENGTH) it
            else it.copy(pin = it.pin + digit, message = null, isError = false)
        }
    }

    fun onDelete() {
        _state.update { if (it.canType) it.copy(pin = it.pin.dropLast(1)) else it }
    }

    fun onSubmit() {
        val current = _state.value
        if (!current.canType) return
        if (current.pin.length < PinHasher.MIN_LENGTH) {
            _state.update { it.copy(message = "El PIN tiene entre 4 y 6 dígitos", isError = true) }
            return
        }
        _state.update { it.copy(checking = true) }
        scope.launch {
            val result = try {
                verify(current.pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Error al leer o guardar en disco: no se cuenta como intento.
                _state.update {
                    it.copy(pin = "", checking = false, isError = true, message = "No se pudo comprobar el PIN. Inténtalo de nuevo")
                }
                return@launch
            }
            when (result) {
                VerifyResult.Success -> {
                    _state.value = PinEntryState()
                    onSuccess()
                }
                is VerifyResult.Wrong -> {
                    _state.update {
                        it.copy(
                            pin = "",
                            checking = false,
                            isError = true,
                            message = when (result.attemptsLeft) {
                                0 -> "PIN incorrecto"
                                1 -> "PIN incorrecto. Te queda 1 intento"
                                else -> "PIN incorrecto. Te quedan ${result.attemptsLeft} intentos"
                            },
                        )
                    }
                    if (result.lockedForMs > 0) startLockout(result.lockedForMs)
                }
                is VerifyResult.LockedOut -> {
                    _state.update { it.copy(pin = "", checking = false) }
                    startLockout(result.remainingMs)
                }
            }
        }
    }

    /** Muestra la cuenta atrás y bloquea el teclado durante [ms]. */
    fun startLockout(ms: Long) {
        if (ms <= 0) return
        countdownJob?.cancel()
        val end = SystemClock.elapsedRealtime() + ms
        countdownJob = scope.launch {
            var shownSeconds = -1L
            while (true) {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) break
                // Solo se redibuja cuando cambia el segundo que se muestra.
                val seconds = (left + 999) / 1000
                if (seconds != shownSeconds) {
                    shownSeconds = seconds
                    _state.update { it.copy(lockoutRemainingMs = left, isError = true) }
                }
                delay(TICK_MS)
            }
            _state.update { it.copy(lockoutRemainingMs = 0L, message = null, isError = false) }
        }
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
