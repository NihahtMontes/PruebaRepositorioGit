package com.nihaht.lockapp.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Convierte cualquier fallo en un mensaje para el usuario en lugar de cerrar la app.
 * Las pantallas abiertas muestran [messages] como aviso.
 */
class ErrorReporter {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun report(userText: String, error: Throwable) {
        ErrorLog.record(userText, error)
        val detail = error.message?.takeIf { it.isNotBlank() }?.take(120)
        val technical = if (detail != null) "${error.javaClass.simpleName}: $detail" else error.javaClass.simpleName
        _messages.tryEmit("$userText. ($technical)")
    }

    fun info(text: String) {
        _messages.tryEmit(text)
    }
}

/**
 * Como `viewModelScope.launch`, pero si algo falla se informa al usuario
 * con [userText] y la app sigue funcionando.
 */
fun ViewModel.launchSafe(
    reporter: ErrorReporter,
    userText: String,
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch(CoroutineExceptionHandler { _, e -> reporter.report(userText, e) }, block = block)
