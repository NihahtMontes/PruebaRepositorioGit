package com.nihaht.lockapp.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.ui.components.PinEntryPanel
import com.nihaht.lockapp.ui.components.PinEntryState
import com.nihaht.lockapp.ui.components.PinSetupFlow

/** Primer uso: crear el PIN maestro que protege la propia LockApp. */
@Composable
fun MasterSetupScreen(busy: Boolean, onPinChosen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Bienvenido a LockApp", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Crea tu PIN maestro. Lo usarás para abrir LockApp y cambiar los ajustes. " +
                "Después podrás poner un PIN distinto a cada app que bloquees.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PinSetupFlow(
            title = "Nuevo PIN maestro",
            busy = busy,
            onPinChosen = onPinChosen,
        )
    }
}

/** Entrada a LockApp: pide el PIN maestro. */
@Composable
fun MasterUnlockScreen(
    entry: PinEntryState,
    biometricAvailable: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onBiometric: () -> Unit,
) {
    PinEntryPanel(
        title = "LockApp",
        subtitle = "Introduce tu PIN maestro",
        pin = entry.pin,
        message = entry.displayMessage,
        isError = entry.isError,
        enabled = entry.canType,
        onDigit = onDigit,
        onDelete = onDelete,
        onSubmit = onSubmit,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        header = {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
        },
        footer = {
            Spacer(Modifier.height(16.dp))
            if (biometricAvailable) {
                TextButton(onClick = onBiometric) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Usar huella")
                }
            }
        },
    )
}
