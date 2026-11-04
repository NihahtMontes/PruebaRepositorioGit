@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.applist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nihaht.lockapp.ui.components.AppIcon

@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    paused: Boolean,
    accessibilityEnabled: Boolean,
    serviceResponding: Boolean,
    crashToReview: Boolean,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val onlyLocked by viewModel.onlyLocked.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf<AppRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LockApp") },
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = "Diagnóstico")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (!accessibilityEnabled) {
                Banner(
                    icon = Icons.Filled.Warning,
                    text = "El servicio de accesibilidad está desactivado: las apps no se bloquearán. Toca para activarlo.",
                    onClick = onOpenPermissions,
                )
            }
            if (accessibilityEnabled && !serviceResponding) {
                Banner(
                    icon = Icons.Filled.Warning,
                    text = "LockApp está activado pero no responde. Toca para abrir el diagnóstico.",
                    onClick = onOpenDiagnostics,
                )
            }
            if (crashToReview) {
                Banner(
                    icon = Icons.Filled.Warning,
                    text = "LockApp se cerró por un error. Toca para ver el diagnóstico.",
                    onClick = onOpenDiagnostics,
                )
            }
            if (paused) {
                Banner(
                    icon = Icons.Filled.PauseCircleFilled,
                    text = "Los bloqueos están en pausa. Reactívalos en Ajustes.",
                    onClick = onOpenSettings,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Buscar app") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = !onlyLocked,
                    onClick = { viewModel.setOnlyLocked(false) },
                    label = { Text("Todas") },
                )
                FilterChip(
                    selected = onlyLocked,
                    onClick = { viewModel.setOnlyLocked(true) },
                    label = { Text("Bloqueadas") },
                )
            }

            val list = rows
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (onlyLocked) "Todavía no has bloqueado ninguna app." else "No se encontraron apps.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list, key = { it.app.packageName }) { row ->
                        ListItem(
                            headlineContent = { Text(row.app.label) },
                            supportingContent = {
                                Text(if (row.locked) "Bloqueada con su propio PIN" else "Sin bloquear")
                            },
                            leadingContent = { AppIcon(row.app.packageName) },
                            trailingContent = {
                                Switch(
                                    checked = row.locked,
                                    onCheckedChange = { checked ->
                                        if (checked) onOpenApp(row.app.packageName) else confirmRemove = row
                                    },
                                )
                            },
                            modifier = Modifier.clickable { onOpenApp(row.app.packageName) },
                        )
                    }
                }
            }
        }
    }

    confirmRemove?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("¿Quitar el bloqueo?") },
            text = { Text("${row.app.label} se abrirá sin pedir PIN y se borrará su PIN.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeLock(row.app.packageName)
                    confirmRemove = null
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
