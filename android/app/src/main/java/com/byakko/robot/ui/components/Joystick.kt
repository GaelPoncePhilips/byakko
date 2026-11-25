package com.byakko.robot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.byakko.robot.ui.theme.Palette

/**
 * Joystick analógico. Entrega (x, y) en -1..1: x + derecha, y + adelante.
 * Captura el toque al instante para que el scroll de la pantalla no lo robe.
 */
@Composable
fun Joystick(modifier: Modifier = Modifier, size: Dp = 220.dp, enabled: Boolean = true, onMove: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val move by rememberUpdatedState(onMove)
    val isEnabled by rememberUpdatedState(enabled)

    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                val r = this.size.width / 2f
                val maxR = r * 0.62f
                fun update(p: Offset) {
                    var v = p - Offset(r, r)
                    val d = v.getDistance()
                    if (d > maxR) v *= maxR / d
                    knob = v
                    move(v.x / maxR, -v.y / maxR)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!isEnabled) return@awaitEachGesture
                    down.consume()
                    update(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        update(ch.position)
                    }
                    knob = Offset.Zero
                    move(0f, 0f)
                }
            },
    ) {
        val r = this.size.minDimension / 2f
        drawCircle(Palette.SurfaceHi, radius = r)
        drawCircle(Palette.Line, radius = r * 0.62f, style = Stroke(1.5.dp.toPx()))
        drawLine(Palette.Line, center.copy(x = center.x - r * 0.8f), center.copy(x = center.x + r * 0.8f), 1.dp.toPx())
        drawLine(Palette.Line, center.copy(y = center.y - r * 0.8f), center.copy(y = center.y + r * 0.8f), 1.dp.toPx())
        val accent = if (enabled) Palette.Accent else Palette.Muted
        drawCircle(accent.copy(alpha = 0.18f), radius = r * 0.30f, center = center + knob)
        drawCircle(accent, radius = r * 0.18f, center = center + knob)
    }
}
