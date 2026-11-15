package com.nihaht.lockapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Cuadrícula 3×3 para dibujar un patrón. Es **invisible**: muestra los 9 puntos
 * fijos para saber dónde poner el dedo, pero NO dibuja el trazo ni resalta los
 * puntos tocados, así nadie puede ver qué patrón haces. La única pista es una
 * vibración corta al tocar cada punto (si [haptics] está activo).
 *
 * [onComplete] recibe la secuencia de puntos (0..8, por filas) al levantar el dedo.
 */
@Composable
fun PatternGrid(
    onComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptics: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val onCompleteState = rememberUpdatedState(onComplete)
    val dotColor = LocalContentColor.current

    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp)
            .pointerInput(enabled, haptics) {
                if (!enabled) return@pointerInput
                val hitRadius = size.width / 6f * 0.85f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sequence = mutableListOf<Int>()
                    addNode(nodeAt(down.position, size.width, size.height, hitRadius), sequence, haptic, haptics)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        addNode(nodeAt(change.position, size.width, size.height, hitRadius), sequence, haptic, haptics)
                        change.consume()
                    }
                    if (sequence.isNotEmpty()) onCompleteState.value(sequence.toList())
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            drawStaticDots(dotColor)
        }
    }
}

/** Dibuja los 9 puntos de referencia, todos iguales (no revelan nada). */
private fun DrawScope.drawStaticDots(color: androidx.compose.ui.graphics.Color) {
    val cell = size.width / 3f
    val radius = cell * 0.06f
    for (row in 0..2) {
        for (col in 0..2) {
            val center = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
            drawCircle(color = color.copy(alpha = 0.35f), radius = radius, center = center)
        }
    }
}

/** Índice del punto más cercano a [pos] si está dentro de [hitRadius], o -1. */
private fun nodeAt(pos: Offset, width: Int, height: Int, hitRadius: Float): Int {
    val cellW = width / 3f
    val cellH = height / 3f
    for (row in 0..2) {
        for (col in 0..2) {
            val cx = (col + 0.5f) * cellW
            val cy = (row + 0.5f) * cellH
            if (hypot(pos.x - cx, pos.y - cy) <= hitRadius) return row * 3 + col
        }
    }
    return -1
}

/**
 * Añade [node] a la secuencia si es válido y nuevo. Como en Android, si entre el
 * último punto y el nuevo hay un punto intermedio en línea recta sin usar, lo
 * añade antes. Vibra en cada punto nuevo.
 */
private fun addNode(
    node: Int,
    sequence: MutableList<Int>,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    haptics: Boolean,
) {
    if (node < 0 || node in sequence) return
    val previous = sequence.lastOrNull()
    if (previous != null) {
        val middle = middleNode(previous, node)
        if (middle != null && middle !in sequence) {
            sequence.add(middle)
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    sequence.add(node)
    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

/** Punto intermedio en línea recta entre [a] y [b], o null si no hay uno. */
private fun middleNode(a: Int, b: Int): Int? {
    val ra = a / 3
    val ca = a % 3
    val rb = b / 3
    val cb = b % 3
    if ((ra + rb) % 2 != 0 || (ca + cb) % 2 != 0) return null
    val mr = (ra + rb) / 2
    val mc = (ca + cb) / 2
    val mid = mr * 3 + mc
    return if (mid != a && mid != b) mid else null
}
