package com.byakko.robot.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.byakko.robot.data.RobotMode
import com.byakko.robot.ui.theme.Palette
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Mapa visto desde arriba. "Adelante" del punto de partida es hacia arriba.
 * - Siguelínea: el recorrido se dibuja como cinta negra (la pista que fue siguiendo)
 *   con flechitas que muestran para dónde miraba el auto.
 * - Obstáculos / manual: el recorrido en celeste y las paredes que vieron los ultrasonidos.
 * Se ajusta solo para que entre todo lo recorrido.
 */
@Composable
fun MapView(track: TrackState, robotX: Float, robotY: Float, headingDeg: Float, mode: RobotMode, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        // ---- encuadre: todo el recorrido + paredes + un margen ----
        var minX = robotX; var maxX = robotX; var minY = robotY; var maxY = robotY
        track.path.forEach { minX = min(minX, it.x); maxX = max(maxX, it.x); minY = min(minY, it.y); maxY = max(maxY, it.y) }
        track.walls.forEach { minX = min(minX, it.x); maxX = max(maxX, it.x); minY = min(minY, it.y); maxY = max(maxY, it.y) }
        val spanX = (maxX - minX).coerceAtLeast(600f) + 300f   // mm (adelante/atrás, vertical en pantalla)
        val spanY = (maxY - minY).coerceAtLeast(600f) + 300f   // mm (izquierda/derecha, horizontal)
        val target = min(size.height / spanX, size.width / spanY)
        val k = target
        val cxW = (minX + maxX) / 2; val cyW = (minY + maxY) / 2
        // mundo (x adelante, y izquierda) -> pantalla (arriba, izquierda)
        fun s(x: Float, y: Float) = Offset(size.width / 2 - (y - cyW) * k, size.height / 2 - (x - cxW) * k)

        drawGrid(k, ::s, cxW, cyW)

        // ---- recorrido ----
        val path = track.path
        if (path.size > 1) {
            // Cinta negra en los tramos de siguelínea
            segments(path) { it.mode == RobotMode.LINE }.forEach { seg ->
                drawPolyline(seg.map { s(it.x, it.y) }, Color(0xFF050608), 22f * k.coerceAtLeast(0.15f) + 6f)
            }
            // Línea fina en todo el recorrido
            drawPolyline(path.map { s(it.x, it.y) }, Palette.Accent.copy(alpha = 0.85f), 3f)
            // Flechitas de rumbo cada ~10 cm
            var acc = 0f
            for (i in 1 until path.size) {
                val a = path[i - 1]; val b = path[i]
                acc += hypot(b.x - a.x, b.y - a.y)
                if (acc >= 100f) {
                    acc = 0f
                    drawArrow(s(b.x, b.y), b.headingDeg, 7f, if (b.mode == RobotMode.LINE) Palette.Ink else Palette.Accent)
                }
            }
            // Punto de partida
            val start = s(path.first().x, path.first().y)
            drawCircle(Palette.Ok, 6f, start)
            drawCircle(Palette.Ok.copy(alpha = 0.25f), 14f, start)
        }

        // ---- paredes (ultrasonidos) ----
        val wallColor = Palette.Warn
        for (id in 0..2) {
            val pts = track.walls.filter { it.sensor == id }
            for (i in 1 until pts.size) {
                val a = pts[i - 1]; val b = pts[i]
                if (hypot(b.x - a.x, b.y - a.y) < 90f) drawLine(wallColor, s(a.x, a.y), s(b.x, b.y), 4f, cap = StrokeCap.Round)
            }
            pts.forEach { drawCircle(wallColor.copy(alpha = 0.55f), 3f, s(it.x, it.y)) }
        }

        // ---- el auto ----
        val car = s(robotX, robotY)
        drawCircle(Palette.Accent.copy(alpha = 0.18f), 26f, car)
        drawArrow(car, headingDeg, 16f, Palette.Accent)

        // Escala
        val tenCm = 100f * k
        val y0 = size.height - 18f
        drawLine(Palette.Muted, Offset(16f, y0), Offset(16f + tenCm, y0), 2f)
        drawText(measurer.measure("10 cm", TextStyle(fontSize = 10.sp, color = Palette.Muted)), topLeft = Offset(16f, y0 - 30f))

        if (path.isEmpty() && track.walls.isEmpty()) {
            val hint = when (mode) {
                RobotMode.LINE -> "El recorrido de la línea va a aparecer acá"
                RobotMode.AVOID -> "Las paredes que vean los ultrasonidos van a aparecer acá"
                else -> "Mové el auto para empezar a dibujar el mapa"
            }
            val m = measurer.measure(hint, TextStyle(fontSize = 12.sp, color = Palette.Muted))
            drawText(m, topLeft = Offset((size.width - m.size.width) / 2, size.height * 0.72f))
        }
    }
}

/** Tramos seguidos del recorrido que cumplen [pred]. */
private fun segments(path: List<TrackPoint>, pred: (TrackPoint) -> Boolean): List<List<TrackPoint>> {
    val out = mutableListOf<List<TrackPoint>>()
    var cur = mutableListOf<TrackPoint>()
    path.forEach { p -> if (pred(p)) cur.add(p) else { if (cur.size > 1) out.add(cur); cur = mutableListOf() } }
    if (cur.size > 1) out.add(cur)
    return out
}

private fun DrawScope.drawPolyline(pts: List<Offset>, color: Color, width: Float) {
    if (pts.size < 2) return
    val p = Path()
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
    drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Flecha que apunta según el rumbo (0° = arriba, + = gira a la izquierda). */
private fun DrawScope.drawArrow(at: Offset, headingDeg: Float, r: Float, color: Color) {
    rotate(-headingDeg, pivot = at) {
        val p = Path()
        p.moveTo(at.x, at.y - r)
        p.lineTo(at.x + r * 0.7f, at.y + r * 0.7f)
        p.lineTo(at.x, at.y + r * 0.3f)
        p.lineTo(at.x - r * 0.7f, at.y + r * 0.7f)
        p.close()
        drawPath(p, color)
    }
}

private fun DrawScope.drawGrid(k: Float, s: (Float, Float) -> Offset, cx: Float, cy: Float) {
    // Una línea cada 10 cm (o cada 50 cm si está muy alejado)
    val step = if (100f * k < 18f) 500f else 100f
    val halfW = size.width / 2 / k; val halfH = size.height / 2 / k
    var x = kotlin.math.floor((cx - halfH) / step) * step
    while (x <= cx + halfH) {
        drawLine(Palette.Line.copy(alpha = 0.6f), s(x, cy + halfW), s(x, cy - halfW), 1f); x += step
    }
    var y = kotlin.math.floor((cy - halfW) / step) * step
    while (y <= cy + halfW) {
        drawLine(Palette.Line.copy(alpha = 0.6f), s(cx - halfH, y), s(cx + halfH, y), 1f); y += step
    }
}
