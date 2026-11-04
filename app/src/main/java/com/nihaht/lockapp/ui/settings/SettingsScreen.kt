@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.data.AppSettings
import com.nihaht.lockapp.data.GracePeriod

@Composable
fun SettingsScreen(
    settings: AppSettings,
    biometricHardware: Boolean,
    onBack: () -> Unit,
    onChangeMaster: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onPausedChange: (Boolean) -> Unit,
    onGraceChange: (GracePeriod) -> Unit,
    onRelockChange: (Boolean) -> Unit,
    onProtectRecentsChange: (Boolean) -> Unit,
    onOpenBackground: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLockNow: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Seguridad")
            ListItem(
                headlineContent = { Text("Cambiar PIN maestro") },
                supportingContent = { Text("El que protege la entrada a LockApp") },
                leadingContent = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onChangeMaster),
            )
            ListItem(
                headlineContent = { Text("Usar huella") },
                supportingContent = {
                    Text(
                        if (biometricHardware) "Permite la huella en LockApp y en las apps que la tengan activada"
                        else "No hay huella registrada en este teléfono",
                    )
                },
                leadingContent = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = settings.biometricEnabled && biometricHardware,
                        onCheckedChange = onBiometricChange,
                        enabled = biometricHardware,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Proteger Recientes") },
                supportingContent = {
                    Text("Pide el PIN maestro al abrir Recientes para que nadie vea el contenido de tus apps")
                },
                leadingContent = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                trailingContent = { Switch(checked = settings.protectRecents, onCheckedChange = onProtectRecentsChange) },
            )
            ListItem(
                headlineContent = { Text("Pausar todos los bloqueos") },
                supportingContent = { Text("Ninguna app pedirá PIN hasta que lo reactives") },
                leadingContent = { Icon(Icons.Filled.PauseCircleFilled, contentDescription = null) },
                trailingContent = { Switch(checked = settings.paused, onCheckedChange = onPausedChange) },
            )

            HorizontalDivider()
            SectionTitle("Periodo de gracia")
            ListItem(
                headlineContent = { Text("Tras desbloquear una app") },
                supportingContent = { Text("Tiempo durante el que puedes volver a ella sin escribir el PIN otra vez") },
                leadingContent = { Icon(Icons.Filled.Timer, contentDescription = null) },
            )
            GracePeriod.entries.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    leadingContent = {
                        RadioButton(selected = settings.gracePeriod == option, onClick = null)
                    },
                    modifier = Modifier.selectable(
                        selected = settings.gracePeriod == option,
                        role = Role.RadioButton,
                        onClick = { onGraceChange(option) },
                    ),
                )
            }
            ListItem(
                headlineContent = { Text("Bloquear al apagar la pantalla") },
                supportingContent = { Text("Olvida los desbloqueos cuando se apaga la pantalla") },
                leadingContent = { Icon(Icons.Filled.ScreenLockPortrait, contentDescription = null) },
                trailingContent = { Switch(checked = settings.relockOnScreenOff, onCheckedChange = onRelockChange) },
            )

            HorizontalDivider()
            SectionTitle("Apariencia")
            ListItem(
                headlineContent = { Text("Fondo de la pantalla de bloqueo") },
                supportingContent = { Text("Color, degradado o imagen de tu galería") },
                leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenBackground),
            )

            HorizontalDivider()
            SectionTitle("Sistema")
            ListItem(
                headlineContent = { Text("Diagnóstico") },
                supportingContent = { Text("Comprueba que todo funciona y reinicia la protección") },
                leadingContent = { Icon(Icons.Filled.HealthAndSafety, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDiagnostics),
            )
            ListItem(
                headlineContent = { Text("Permisos y batería") },
                supportingContent = { Text("Comprueba que LockApp puede funcionar siempre") },
                leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenPermissions),
            )
            ListItem(
                headlineContent = { Text("Cerrar LockApp ahora") },
                supportingContent = { Text("Vuelve a pedir el PIN maestro") },
                leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onLockNow),
            )
            Text(
                "LockApp 1.0 · Sin internet, sin anuncios y sin telemetría.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
