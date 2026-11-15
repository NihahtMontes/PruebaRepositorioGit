package com.nihaht.lockapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.security.CredentialKind

/** Lo que el usuario eligió al crear una credencial. */
sealed interface ChosenCredential {
    data class Pin(val pin: String) : ChosenCredential
    data class Pattern(val nodes: List<Int>) : ChosenCredential
}

/** Elige el método (PIN o patrón) con dos chips. */
@Composable
private fun MethodToggle(method: CredentialKind, onChange: (CredentialKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = method == CredentialKind.PIN,
            onClick = { onChange(CredentialKind.PIN) },
            label = { Text("PIN") },
        )
        FilterChip(
            selected = method == CredentialKind.PATTERN,
            onClick = { onChange(CredentialKind.PATTERN) },
            label = { Text("Patrón") },
        )
    }
}

/**
 * Crear una credencial: elige PIN o patrón y la introduce dos veces.
 * Devuelve lo elegido en [onChosen].
 */
@Composable
fun CredentialSetup(
    title: String,
    busy: Boolean,
    haptics: Boolean,
    onChosen: (ChosenCredential) -> Unit,
    modifier: Modifier = Modifier,
    allowMethodChoice: Boolean = true,
    footer: @Composable () -> Unit = {},
) {
    var method by remember { mutableStateOf(CredentialKind.PIN) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (allowMethodChoice) {
            MethodToggle(method) { method = it }
            Spacer(Modifier.height(16.dp))
        }
        when (method) {
            CredentialKind.PIN -> PinSetupFlow(
                title = title,
                busy = busy,
                onPinChosen = { onChosen(ChosenCredential.Pin(it)) },
                footer = footer,
            )
            CredentialKind.PATTERN -> PatternSetupFlow(
                title = title,
                busy = busy,
                haptics = haptics,
                onPatternChosen = { onChosen(ChosenCredential.Pattern(it)) },
                footer = footer,
            )
        }
    }
}

/**
 * Pide una credencial para desbloquear, mostrando el teclado de PIN o la
 * cuadrícula de patrón según [kind], conectada a [controller].
 */
@Composable
fun CredentialEntry(
    kind: CredentialKind,
    title: String,
    subtitle: String?,
    entry: PinEntryState,
    controller: PinEntryController,
    haptics: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    when (kind) {
        CredentialKind.PIN -> PinEntryPanel(
            title = title,
            subtitle = subtitle,
            pin = entry.pin,
            message = entry.displayMessage,
            isError = entry.isError,
            enabled = entry.canType,
            onDigit = controller::onDigit,
            onDelete = controller::onDelete,
            onSubmit = controller::onSubmit,
            modifier = modifier,
            header = header,
            footer = footer,
        )
        CredentialKind.PATTERN -> PatternEntryPanel(
            title = title,
            subtitle = subtitle,
            message = entry.displayMessage,
            isError = entry.isError,
            enabled = entry.canType,
            haptics = haptics,
            onPattern = controller::submitPattern,
            modifier = modifier,
            header = header,
            footer = footer,
        )
    }
}
