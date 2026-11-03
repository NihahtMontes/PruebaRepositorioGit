package com.nihaht.lockapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.security.PinHasher

/**
 * Crear un PIN en dos pasos: escribirlo y confirmarlo.
 * Se usa `remember` (no `rememberSaveable`) para no guardar el PIN en el estado del sistema.
 */
@Composable
fun PinSetupFlow(
    title: String,
    onPinChosen: (String) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    keySize: Dp = 68.dp,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val confirming = firstPin != null

    PinEntryPanel(
        title = if (confirming) "Confirma el PIN" else title,
        subtitle = if (busy) "Guardando…" else if (confirming) "Escríbelo otra vez" else "Entre 4 y 6 dígitos",
        pin = pin,
        message = message,
        isError = isError,
        enabled = !busy,
        keySize = keySize,
        modifier = modifier,
        header = header,
        footer = footer,
        onDigit = { digit ->
            if (pin.length < PinHasher.MAX_LENGTH) {
                pin += digit
                message = null
                isError = false
            }
        },
        onDelete = { pin = pin.dropLast(1) },
        onSubmit = {
            val first = firstPin
            when {
                !PinHasher.isValidFormat(pin) -> {
                    message = "El PIN debe tener entre 4 y 6 dígitos"
                    isError = true
                }
                first == null -> {
                    firstPin = pin
                    pin = ""
                    message = null
                    isError = false
                }
                first == pin -> {
                    val chosen = pin
                    pin = ""
                    firstPin = null
                    message = null
                    onPinChosen(chosen)
                }
                else -> {
                    firstPin = null
                    pin = ""
                    message = "Los PIN no coinciden. Empieza de nuevo"
                    isError = true
                }
            }
        },
    )
}
