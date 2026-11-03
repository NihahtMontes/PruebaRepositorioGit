package com.nihaht.lockapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import com.nihaht.lockapp.data.LockBackground

/** Colores y degradados predefinidos para la pantalla de bloqueo. */
object LockBackgrounds {
    val solidColors: List<Int> = listOf(
        0xFF101418, 0xFF1A237E, 0xFF004D40, 0xFF4A148C,
        0xFF880E4F, 0xFF263238, 0xFF3E2723, 0xFFECEFF1,
    ).map { it.toInt() }

    val gradients: List<List<Color>> = listOf(
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
        listOf(Color(0xFF41295A), Color(0xFF2F0743)),
        listOf(Color(0xFF1D2B64), Color(0xFF6A3093)),
        listOf(Color(0xFF134E5E), Color(0xFF0B3D2E)),
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
        listOf(Color(0xFFCB356B), Color(0xFF7A1B3A)),
    )

    fun gradient(preset: Int): List<Color> = gradients[preset.coerceIn(0, gradients.lastIndex)]
}

/**
 * Pinta el fondo elegido y elige un color de texto legible encima.
 * Si el fondo es imagen y aún no se ha cargado, muestra el primer degradado.
 */
@Composable
fun LockBackgroundBox(
    background: LockBackground,
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentColor: Color
    val base: Modifier
    when {
        background is LockBackground.Solid -> {
            val color = Color(background.argb)
            contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White
            base = Modifier.background(color)
        }
        background is LockBackground.Gradient -> {
            contentColor = Color.White
            base = Modifier.background(Brush.verticalGradient(LockBackgrounds.gradient(background.preset)))
        }
        image == null -> {
            contentColor = Color.White
            base = Modifier.background(Brush.verticalGradient(LockBackgrounds.gradient(0)))
        }
        else -> {
            contentColor = Color.White
            base = Modifier.background(Color.Black)
        }
    }
    Box(modifier.then(base)) {
        if (background is LockBackground.Image && image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Capa oscura para que el texto se lea sobre cualquier foto.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
