@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.LockBackground
import com.nihaht.lockapp.ui.components.LockBackgroundBox
import com.nihaht.lockapp.ui.components.LockBackgrounds
import com.nihaht.lockapp.ui.components.PinDots

@Composable
fun BackgroundScreen(
    current: LockBackground,
    busy: Boolean,
    onSelect: (LockBackground) -> Unit,
    onImagePicked: (android.net.Uri) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Photo Picker del sistema: no necesita permisos de almacenamiento.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        try {
            if (uri != null) onImagePicked(uri)
        } catch (e: Exception) {
            context.appContainer.errors.report("No se pudo usar esa imagen", e)
        }
    }
    val image by produceState<ImageBitmap?>(initialValue = null, current) {
        value = (current as? LockBackground.Image)?.let { context.appContainer.backgroundImage(it.version) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondo de bloqueo") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            // Vista previa
            LockBackgroundBox(
                background = current,
                image = image,
                modifier = Modifier
                    .size(width = 170.dp, height = 300.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    PinDots(length = 2)
                }
            }

            SectionLabel("Colores")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(LockBackgrounds.solidColors) { _, argb ->
                    Swatch(
                        selected = current == LockBackground.Solid(argb),
                        brush = Brush.linearGradient(listOf(Color(argb), Color(argb))),
                        onClick = { onSelect(LockBackground.Solid(argb)) },
                    )
                }
            }

            SectionLabel("Degradados")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(LockBackgrounds.gradients) { index, colors ->
                    Swatch(
                        selected = current == LockBackground.Gradient(index),
                        brush = Brush.verticalGradient(colors),
                        onClick = { onSelect(LockBackground.Gradient(index)) },
                    )
                }
            }

            SectionLabel("Imagen")
            Button(
                onClick = {
                    try {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (e: Exception) {
                        context.appContainer.errors.report("No se pudo abrir la galería", e)
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (current is LockBackground.Image) "Cambiar imagen" else "Elegir de la galería")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Swatch(selected: Boolean, brush: Brush, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(brush)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
    )
}
