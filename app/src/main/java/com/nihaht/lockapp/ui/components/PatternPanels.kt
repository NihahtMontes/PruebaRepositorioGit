package com.nihaht.lockapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.security.PinHasher

/** Bloque de patrón invisible: cabecera, título, mensaje, cuadrícula y pie. */
@Composable
fun PatternEntryPanel(
    title: String,
    subtitle: String?,
    message: String?,
    isError: Boolean,
    onPattern: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptics: Boolean = true,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header()
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.8f),
            )
        }
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
                    textAlign = TextAlign.Center,
                )
            }
        }
        PatternGrid(onComplete = onPattern, enabled = enabled, haptics = haptics)
        footer()
    }
}

/** Crear un patrón en dos pasos: dibujarlo y confirmarlo. */
@Composable
fun PatternSetupFlow(
    title: String,
    onPatternChosen: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    haptics: Boolean = true,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    var firstPattern by remember { mutableStateOf<List<Int>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val confirming = firstPattern != null

    PatternEntryPanel(
        title = if (confirming) "Confirma el patrón" else title,
        subtitle = when {
            busy -> "Guardando…"
            confirming -> "Dibújalo otra vez"
            else -> "Une al menos ${PinHasher.PATTERN_MIN_NODES} puntos. No se verá el trazo."
        },
        message = message,
        isError = isError,
        enabled = !busy,
        haptics = haptics,
        modifier = modifier,
        header = header,
        footer = footer,
        onPattern = { nodes ->
            val first = firstPattern
            when {
                !PinHasher.isValidPattern(nodes) -> {
                    message = "Une al menos ${PinHasher.PATTERN_MIN_NODES} puntos"
                    isError = true
                }
                first == null -> {
                    firstPattern = nodes
                    message = "Ahora confírmalo"
                    isError = false
                }
                first == nodes -> {
                    firstPattern = null
                    message = null
                    onPatternChosen(nodes)
                }
                else -> {
                    firstPattern = null
                    message = "Los patrones no coinciden. Empieza de nuevo"
                    isError = true
                }
            }
        },
    )
}
