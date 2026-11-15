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
import com.nihaht.lockapp.security.CredentialKind
import com.nihaht.lockapp.ui.components.ChosenCredential
import com.nihaht.lockapp.ui.components.CredentialEntry
import com.nihaht.lockapp.ui.components.CredentialSetup
import com.nihaht.lockapp.ui.components.PinEntryController
import com.nihaht.lockapp.ui.components.PinEntryState

/** Primer uso: crear la credencial maestra (PIN o patrón) que protege LockApp. */
@Composable
fun MasterSetupScreen(busy: Boolean, haptics: Boolean, onChosen: (ChosenCredential) -> Unit) {
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
            "Crea tu acceso maestro. Puedes usar un PIN o un patrón. Lo usarás para abrir " +
                "LockApp y cambiar los ajustes. Luego podrás poner un acceso distinto a cada app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CredentialSetup(
            title = "Nuevo acceso maestro",
            busy = busy,
            haptics = haptics,
            onChosen = onChosen,
        )
    }
}

/** Entrada a LockApp: pide el PIN o el patrón maestro. */
@Composable
fun MasterUnlockScreen(
    kind: CredentialKind,
    entry: PinEntryState,
    controller: PinEntryController,
    biometricAvailable: Boolean,
    haptics: Boolean,
    onBiometric: () -> Unit,
) {
    CredentialEntry(
        kind = kind,
        title = "LockApp",
        subtitle = if (kind == CredentialKind.PATTERN) "Dibuja tu patrón maestro" else "Introduce tu PIN maestro",
        entry = entry,
        controller = controller,
        haptics = haptics,
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
