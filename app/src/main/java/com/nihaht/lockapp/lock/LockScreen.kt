package com.nihaht.lockapp.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.security.CredentialKind
import com.nihaht.lockapp.ui.components.CredentialEntry
import com.nihaht.lockapp.ui.components.LockBackgroundBox
import com.nihaht.lockapp.ui.components.PinEntryController
import com.nihaht.lockapp.ui.components.PinEntryState

@Composable
fun LockScreen(
    state: LockUiState,
    entry: PinEntryState,
    controller: PinEntryController,
    onBiometric: () -> Unit,
) {
    LockBackgroundBox(
        background = state.background,
        image = state.backgroundImage,
        modifier = Modifier.fillMaxSize(),
    ) {
        CredentialEntry(
            kind = state.kind,
            title = state.appLabel.ifEmpty { " " },
            subtitle = if (state.kind == CredentialKind.PATTERN) "Dibuja el patrón de esta app"
            else "Introduce el PIN de esta app",
            entry = entry,
            controller = controller,
            haptics = state.haptics,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            header = {
                val icon = state.appIcon
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(72.dp))
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(72.dp))
                }
                Spacer(Modifier.height(16.dp))
            },
            footer = {
                Spacer(Modifier.height(16.dp))
                if (state.biometricAvailable) {
                    TextButton(onClick = onBiometric) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                        Text("Usar huella", color = LocalContentColor.current)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            },
        )
    }
}
