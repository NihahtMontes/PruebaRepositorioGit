@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.security.CredentialKind
import com.nihaht.lockapp.ui.components.ChosenCredential
import com.nihaht.lockapp.ui.components.CredentialEntry
import com.nihaht.lockapp.ui.components.CredentialSetup
import com.nihaht.lockapp.ui.components.PinEntryController
import com.nihaht.lockapp.ui.components.PinEntryState
import com.nihaht.lockapp.ui.main.ChangeMasterStep

@Composable
fun ChangeMasterScreen(
    step: ChangeMasterStep,
    verifyKind: CredentialKind,
    verifyEntry: PinEntryState,
    verifyController: PinEntryController,
    busy: Boolean,
    haptics: Boolean,
    onNewChosen: (ChosenCredential) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar acceso maestro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                ChangeMasterStep.VERIFY -> CredentialEntry(
                    kind = verifyKind,
                    title = if (verifyKind == CredentialKind.PATTERN) "Patrón maestro actual" else "PIN maestro actual",
                    subtitle = "Confirma que eres tú",
                    entry = verifyEntry,
                    controller = verifyController,
                    haptics = haptics,
                )
                ChangeMasterStep.NEW_PIN -> CredentialSetup(
                    title = "Nuevo acceso maestro",
                    busy = busy,
                    haptics = haptics,
                    onChosen = onNewChosen,
                )
                ChangeMasterStep.DONE -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Acceso maestro actualizado", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack) { Text("Volver") }
                }
            }
        }
    }
}
