@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.applist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nihaht.lockapp.data.LockedApp
import com.nihaht.lockapp.ui.components.AppIcon
import com.nihaht.lockapp.ui.components.PinSetupFlow

private enum class EditorMode { VIEW, SET_PIN }

/**
 * Configura el bloqueo de una app concreta: su propio PIN, si admite huella,
 * cambiar el PIN o quitar el bloqueo.
 */
@Composable
fun AppLockEditorScreen(
    packageName: String,
    viewModel: AppListViewModel,
    biometricHardware: Boolean,
    onBack: () -> Unit,
) {
    val lockedApps by viewModel.lockedApps.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val label = remember(packageName) { viewModel.label(packageName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        val map = lockedApps
        if (map == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EditorContent(
                packageName = packageName,
                label = label,
                entry = map[packageName],
                busy = busy,
                biometricHardware = biometricHardware,
                viewModel = viewModel,
                onBack = onBack,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun EditorContent(
    packageName: String,
    label: String,
    entry: LockedApp?,
    busy: Boolean,
    biometricHardware: Boolean,
    viewModel: AppListViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember(packageName) {
        mutableStateOf(if (entry == null) EditorMode.SET_PIN else EditorMode.VIEW)
    }
    // Si la app está sin bloquear solo tiene sentido el modo de crear PIN.
    val effectiveMode = if (entry == null) EditorMode.SET_PIN else mode
    var allowBiometric by remember(packageName) { mutableStateOf(entry?.allowBiometric ?: false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(packageName, size = 64.dp)
        Spacer(Modifier.height(8.dp))

        when (effectiveMode) {
            EditorMode.SET_PIN -> {
                if (entry == null) {
                    Text(
                        "Elige un PIN solo para $label. Será distinto del PIN maestro y del de otras apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (biometricHardware) {
                        BiometricRow(allowBiometric) { allowBiometric = it }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PinSetupFlow(
                    title = if (entry == null) "PIN para $label" else "Nuevo PIN para $label",
                    busy = busy,
                    keySize = 64.dp,
                    onPinChosen = { pin ->
                        viewModel.lockApp(packageName, pin, allowBiometric) {
                            if (entry == null) onBack() else mode = EditorMode.VIEW
                        }
                    },
                    footer = {
                        if (entry != null) {
                            TextButton(onClick = { mode = EditorMode.VIEW }) { Text("Cancelar") }
                        }
                    },
                )
            }

            EditorMode.VIEW -> {
                Text(
                    "$label está bloqueada con su propio PIN.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text("Cambiar el PIN de esta app") },
                    leadingContent = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
                    modifier = Modifier.clickable { mode = EditorMode.SET_PIN },
                )
                if (biometricHardware) {
                    BiometricRow(entry?.allowBiometric ?: false) { allow ->
                        allowBiometric = allow
                        viewModel.setAllowBiometric(packageName, allow)
                    }
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("Quitar bloqueo")
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("¿Quitar el bloqueo?") },
            text = { Text("$label se abrirá sin pedir PIN y se borrará su PIN.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.removeLock(packageName)
                    onBack()
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BiometricRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text("Permitir huella") },
        supportingContent = {
            Text("Cualquier huella registrada en el teléfono podrá abrir esta app.")
        },
        leadingContent = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}
