@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onCopied: () -> Unit,
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    // Se vuelve a comprobar todo al volver de los ajustes del sistema.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        val current = report
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(current.allOk)

            current.checks.forEach { CheckRow(it) }

            Button(
                onClick = viewModel::restartProtection,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (working) "Reiniciando…" else "Reiniciar protección") }
            OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(if (current.accessibilityProblem) "Configurar permisos" else "Permisos y batería")
            }

            HorizontalDivider()
            Text("Errores registrados: ${current.errorCount}", style = MaterialTheme.typography.titleMedium)
            Text(
                current.lastError ?: "No se ha registrado ningún error.",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(viewModel.fullReport()))
                    onCopied()
                }) { Text("Copiar informe") }
                TextButton(onClick = viewModel::clearErrors, enabled = current.errorCount > 0) {
                    Text("Borrar errores")
                }
            }
            Text(
                "Si algo falla, pulsa «Copiar informe» y pégalo en el chat para que lo revisemos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("Detalles técnicos", style = MaterialTheme.typography.titleSmall)
            current.technical.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SummaryCard(allOk: Boolean) {
    val container = if (allOk) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (allOk) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                if (allOk) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    if (allOk) "Todo funciona correctamente" else "Hay problemas que revisar",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (allOk) "Tus apps bloqueadas están protegidas."
                    else "Mientras haya puntos en rojo, el bloqueo puede fallar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    val (icon, tint) = when {
        check.ok -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary
        check.critical -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        else -> (if (check.title.startsWith("Apps")) Icons.Filled.Info else Icons.Filled.Warning) to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = if (check.ok) "Correcto" else "Revisar", tint = tint)
        Column {
            Text(check.title, style = MaterialTheme.typography.titleSmall)
            Text(check.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
