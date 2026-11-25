package com.byakko.robot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.byakko.robot.ui.theme.Palette
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Deslizador de velocidad con forma de "aleta": abajo es angosto (lento) y sube
 * curvándose hasta la punta ancha (rápido). Sigue el arco natural del pulgar.
 * [mirrored] = true lo da vuelta para usarlo del lado derecho de la pantalla.
 * Entrega el valor al soltar ([onChangeFinished]) para no saturar al robot.
 */
@Composable
fun CurvedSlider(
    value: Int,
    modifier: Modifier = Modifier,
    range: IntRange = 10..100,
    mirrored: Boolean = false,
    enabled: Boolean = true,
    onChangeFinished: (Int) -> Unit,
) {
    // t = posición a lo largo de la curva (0 abajo, 1 en la punta)
    var t by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableFloatStateOf(0f) }   // 1 mientras se arrastra
    val span = (range.last - range.first).toFloat()
    if (dragging == 0f) t = ((value - range.first) / span).coerceIn(0f, 1f)
    val finished by rememberUpdatedState(onChangeFinished)
    val isEnabled by rememberUpdatedState(enabled)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier.pointerInput(mirrored) {
            val samples = curveSamples(Size(size.width.toFloat(), size.height.toFloat()), mirrored)
            fun nearest(p: Offset): Pair<Float, Float> {
                var best = 0; var bestD = Float.MAX_VALUE
                samples.forEachIndexed { i, s -> val d = (s.point - p).getDistance(); if (d < bestD) { bestD = d; best = i } }
                return best / (samples.size - 1f) to bestD
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!isEnabled) return@awaitEachGesture
                val (t0, d0) = nearest(down.position)
                if (d0 > size.width * 0.28f) return@awaitEachGesture   // toque lejos de la aleta
                down.consume()
                dragging = 1f
                t = t0
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    ch.consume()
                    t = nearest(ch.position).first
                }
                dragging = 0f
                finished((range.first + t * span).roundToInt())
            }
        },
    ) {
        val samples = curveSamples(size, mirrored)
        drawFin(samples, 1f, if (enabled) Palette.SurfaceHi else Palette.Surface)
        drawFin(samples, t, if (enabled) Palette.Accent.copy(alpha = 0.85f) else Palette.Muted)

        // Pulgar: una pastilla cruzada a la curva
        val idx = (t * (samples.size - 1)).roundToInt()
        val s = samples[idx]
        val pillW = s.width + 26f
        val pillH = 22f + 10f * dragging
        rotate(Math.toDegrees(s.angle.toDouble()).toFloat() + 90f, pivot = s.point) {
            drawRoundRect(Palette.Ink, topLeft = s.point - Offset(pillW / 2, pillH / 2), size = Size(pillW, pillH), cornerRadius = CornerRadius(pillH / 2))
            drawRoundRect(Palette.Bg, topLeft = s.point - Offset(pillW / 2 - 4, pillH / 2 - 4), size = Size(pillW - 8, pillH - 8), cornerRadius = CornerRadius(pillH / 2))
        }

        // Valor grande al lado de la punta
        val pct = (range.first + t * span).roundToInt()
        val txt = measurer.measure("$pct%", TextStyle(fontSize = 26.sp, color = Palette.Ink))
        val tx = if (mirrored) size.width * 0.06f else size.width - txt.size.width - size.width * 0.06f
        drawText(txt, topLeft = Offset(tx, size.height * 0.30f))
        val lbl = measurer.measure("VELOCIDAD", TextStyle(fontSize = 10.sp, color = Palette.Muted, letterSpacing = 1.5.sp))
        val lx = if (mirrored) size.width * 0.06f else size.width - lbl.size.width - size.width * 0.06f
        drawText(lbl, topLeft = Offset(lx, size.height * 0.30f + txt.size.height))
    }
}

private class Sample(val point: Offset, val angle: Float, val width: Float)

/** Curva central de la aleta (Bezier cúbica) con su ancho en cada punto. */
private fun curveSamples(size: Size, mirrored: Boolean, n: Int = 80): List<Sample> {
    fun pt(x: Float, y: Float) = Offset((if (mirrored) 1f - x else x) * size.width, y * size.height)
    val p0 = pt(0.20f, 0.94f)
    val p1 = pt(0.16f, 0.40f)
    val p2 = pt(0.30f, 0.10f)
    val p3 = pt(0.90f, 0.08f)
    val pts = List(n) { i ->
        val t = i / (n - 1f); val u = 1 - t
        p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
    }
    val minW = size.width * 0.10f
    val maxW = size.width * 0.30f
    return pts.mapIndexed { i, p ->
        val a = pts[(i - 1).coerceAtLeast(0)]
        val b = pts[(i + 1).coerceAtMost(n - 1)]
        val t = i / (n - 1f)
        // Se ensancha hacia arriba y se afina un poco en la punta, como el dibujo
        val w = minW + (maxW - minW) * (t * 1.6f).coerceAtMost(1f) * (1f - 0.55f * ((t - 0.75f) / 0.25f).coerceIn(0f, 1f))
        Sample(p, atan2(b.y - a.y, b.x - a.x), w)
    }
}

/** Rellena la aleta desde el inicio hasta [upTo] (0..1). */
private fun DrawScope.drawFin(samples: List<Sample>, upTo: Float, color: androidx.compose.ui.graphics.Color) {
    val last = (upTo * (samples.size - 1)).roundToInt().coerceAtLeast(1)
    val part = samples.subList(0, last + 1)
    fun side(s: Sample, sign: Float): Offset {
        val nx = -kotlin.math.sin(s.angle); val ny = kotlin.math.cos(s.angle)
        return s.point + Offset(nx, ny) * (sign * s.width / 2)
    }
    val path = Path()
    part.forEachIndexed { i, s -> val p = side(s, 1f); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    part.asReversed().forEach { s -> val p = side(s, -1f); path.lineTo(p.x, p.y) }
    path.close()
    drawPath(path, color)
    // Puntas redondeadas
    drawCircle(color, part.first().width / 2, part.first().point)
    drawCircle(color, part.last().width / 2, part.last().point)
}
