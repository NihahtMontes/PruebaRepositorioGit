@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.launcher.AppIconOption

@Composable
fun IconDisguiseScreen(
    current: AppIconOption,
    onChoose: (AppIconOption) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Icono y nombre") },
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
            Text(
                "Cambia cómo aparece LockApp en el cajón de aplicaciones. La app se abre igual; " +
                    "solo cambia el icono y el nombre, para que no se note qué es.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            AppIconOption.entries.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    leadingContent = { RadioButton(selected = current == option, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = current == option,
                        role = Role.RadioButton,
                        onClick = { onChoose(option) },
                    ),
                )
            }
            Text(
                "Tras cambiarlo, el icono puede tardar unos segundos en actualizarse en la pantalla de inicio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
