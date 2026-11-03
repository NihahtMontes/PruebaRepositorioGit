package com.nihaht.lockapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.security.PinHasher

/** Puntos que indican cuántos dígitos se han escrito. */
@Composable
fun PinDots(
    length: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val color = if (isError) MaterialTheme.colorScheme.error else LocalContentColor.current
    Row(
        modifier = modifier.semantics { contentDescription = "$length dígitos escritos" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(PinHasher.MAX_LENGTH) { index ->
            val dot = Modifier
                .size(14.dp)
                .clip(CircleShape)
            Box(
                if (index < length) dot.background(color)
                else dot.border(1.5.dp, color.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
}

/** Teclado numérico propio: 1-9, borrar, 0 y aceptar. */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keySize: Dp = 72.dp,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    DigitKey(digit, enabled, keySize) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconKey(Icons.AutoMirrored.Filled.Backspace, "Borrar", enabled, keySize, onDelete)
            DigitKey('0', enabled, keySize) { onDigit('0') }
            IconKey(Icons.Filled.Check, "Aceptar", enabled, keySize, onSubmit)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    KeyContainer(enabled, size, onClick) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IconKey(icon: ImageVector, description: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    KeyContainer(enabled, size, onClick) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun KeyContainer(enabled: Boolean, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val color: Color = LocalContentColor.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Bloque completo: cabecera, título, puntos, mensaje y teclado. */
@Composable
fun PinEntryPanel(
    title: String,
    subtitle: String?,
    pin: String,
    message: String?,
    isError: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keySize: Dp = 72.dp,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header()
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.8f),
            )
        }
        Spacer(Modifier.height(24.dp))
        PinDots(length = pin.length, isError = isError)
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
        PinPad(
            onDigit = onDigit,
            onDelete = onDelete,
            onSubmit = onSubmit,
            enabled = enabled,
            keySize = keySize,
        )
        footer()
    }
}
