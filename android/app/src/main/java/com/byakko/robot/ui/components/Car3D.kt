package com.byakko.robot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.byakko.robot.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modelo 3D del auto dibujado a mano (cajas con perspectiva y sombreado, sin librerías).
 * Gira con el rumbo del robot ([headingDeg], + = izquierda), se inclina al doblar
 * según la diferencia de velocidad de las ruedas y cabecea al acelerar.
 */
@Composable
fun Car3D(headingDeg: Float, vLeft: Int, vRight: Int, modifier: Modifier = Modifier) {
    // Suavizado: el rumbo llega 4 veces por segundo, el dibujo va a 60 fps
    val heading by rememberUpdatedState(headingDeg)
    val vl by rememberUpdatedState(vLeft)
    val vr by rememberUpdatedState(vRight)
    var yaw by remember { mutableFloatStateOf(headingDeg) }
    var roll by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var wheelSpin by remember { mutableFloatStateOf(0f) }
    var bob by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var prev = 0L
        var lastSpeed = 0f
        var speedSmooth = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (prev == 0L) 0.016f else ((now - prev) / 1e9f).coerceAtMost(0.1f)
                prev = now
                val k = (dt * 8f).coerceAtMost(1f)
                var diff = (heading - yaw) % 360f
                if (diff > 180f) diff -= 360f
                if (diff < -180f) diff += 360f
                yaw += diff * k
                // Se inclina hacia afuera de la curva y cabecea al acelerar o frenar
                val speed = (vl + vr) / 2f
                speedSmooth += (speed - speedSmooth) * k
                val accel = (speedSmooth - lastSpeed) / dt
                lastSpeed = speedSmooth
                roll += (((vr - vl) / 400f).coerceIn(-1f, 1f) * 10f - roll) * k
                pitch += ((accel / 600f).coerceIn(-1f, 1f) * 6f - pitch) * k
                wheelSpin = (wheelSpin + speedSmooth * dt * 2f) % 360f
                bob = (bob + dt / 2.4f) % 1f
            }
        }
    }

    Canvas(modifier) {
        val scale = size.minDimension * 0.30f
        val center = Offset(size.width / 2, size.height * 0.56f)
        val camPitch = Math.toRadians(32.0).toFloat()
        val yawR = Math.toRadians(yaw.toDouble()).toFloat()
        val rollR = Math.toRadians(roll.toDouble()).toFloat()
        val pitchR = Math.toRadians(pitch.toDouble()).toFloat()
        val lift = sin(bob * 2 * Math.PI).toFloat() * 0.015f

        // Coordenadas del auto: x adelante, y izquierda, z arriba
        fun toWorld(v: V3): V3 {
            // cabeceo (eje y) y rolido (eje x) del chasis
            var x = v.x; var y = v.y; var z = v.z + lift
            val z1 = z * cos(pitchR) + x * sin(pitchR); val x1 = x * cos(pitchR) - z * sin(pitchR); x = x1; z = z1
            val z2 = z * cos(rollR) - y * sin(rollR); val y2 = y * cos(rollR) + z * sin(rollR); y = y2; z = z2
            // rumbo
            return V3(x * cos(yawR) - y * sin(yawR), x * sin(yawR) + y * cos(yawR), z)
        }
        // Mundo -> pantalla: adelante (yaw 0) es "hacia el fondo", izquierda es izquierda
        fun project(w: V3): Pair<Offset, Float> {
            val right = -w.y
            val depth = w.x
            val up = w.z * cos(camPitch) + depth * sin(camPitch)
            val camDepth = depth * cos(camPitch) - w.z * sin(camPitch)
            val f = 4f / (4f + camDepth)
            return Offset(center.x + right * scale * f, center.y - up * scale * f) to camDepth
        }

        // Sombra en el piso
        val shadow = Path()
        listOf(V3(1.0f, 0.62f, 0f), V3(1.0f, -0.62f, 0f), V3(-1.0f, -0.62f, 0f), V3(-1.0f, 0.62f, 0f)).forEachIndexed { i, v ->
            val p = project(V3(v.x * cos(yawR) - v.y * sin(yawR), v.x * sin(yawR) + v.y * cos(yawR), 0f)).first
            if (i == 0) shadow.moveTo(p.x, p.y) else shadow.lineTo(p.x, p.y)
        }
        shadow.close()
        drawPath(shadow, Color.Black.copy(alpha = 0.35f))

        // Piezas del auto
        val parts = buildList {
            addAll(box(0f, 0f, 0.30f, 1.70f, 1.05f, 0.26f, Palette.Accent))            // chasis
            addAll(box(-0.15f, 0f, 0.55f, 0.85f, 0.80f, 0.24f, Color(0xFF2B3A4A)))       // protoboard / cabina
            addAll(box(0.80f, 0f, 0.48f, 0.14f, 0.62f, 0.18f, Color(0xFF1F6FEB)))        // ultrasonido frontal
            addAll(box(0.80f, 0.22f, 0.48f, 0.16f, 0.14f, 0.10f, Palette.Ink))           // "ojos"
            addAll(box(0.80f, -0.22f, 0.48f, 0.16f, 0.14f, 0.10f, Palette.Ink))
            addAll(box(0.15f, 0.55f, 0.48f, 0.12f, 0.10f, 0.14f, Color(0xFF1F6FEB)))     // ultrasonido izq
            addAll(box(0.15f, -0.55f, 0.48f, 0.12f, 0.10f, 0.14f, Color(0xFF1F6FEB)))    // ultrasonido der
            // Ruedas: caja oscura con una franja que gira para que se note el movimiento
            for (side in listOf(1f, -1f)) {
                addAll(box(0.45f, side * 0.62f, 0.22f, 0.46f, 0.18f, 0.44f, Color(0xFF15181D)))
                addAll(box(-0.55f, side * 0.62f, 0.22f, 0.46f, 0.18f, 0.44f, Color(0xFF15181D)))
            }
        }
        val light = V3(-0.4f, 0.5f, 0.75f).norm()
        val faces = parts.map { face ->
            val wv = face.verts.map { toWorld(it) }
            val pr = wv.map { project(it) }
            val n = toWorld(face.normal + V3(0f, 0f, lift)) - toWorld(V3(0f, 0f, lift))
            Triple(face, pr, n)
        }.filter { (_, _, n) ->
            // Sólo las caras que miran a la cámara (la cámara mira hacia adelante y abajo)
            (n dot V3(cos(camPitch), 0f, -sin(camPitch))) < 0f
        }.sortedByDescending { (_, pr, _) -> pr.sumOf { it.second.toDouble() } }

        faces.forEach { (face, pr, n) ->
            val lambert = (n.norm() dot light).coerceIn(0f, 1f)
            val col = lerp(face.color.copy(alpha = 1f) * 0.35f, face.color, 0.35f + 0.65f * lambert)
            val path = Path()
            pr.forEachIndexed { i, (p, _) -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            path.close()
            drawPath(path, col)
            drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(1f))
        }

        // Franjas de las ruedas (dan sensación de giro)
        val stripe = Math.toRadians(wheelSpin.toDouble()).toFloat()
        for (side in listOf(1f, -1f)) for (wx in listOf(0.45f, -0.55f)) {
            val o = V3(wx + 0.22f * cos(stripe), side * (0.62f + 0.095f), 0.22f + 0.22f * sin(stripe))
            val i = V3(wx - 0.22f * cos(stripe), side * (0.62f + 0.095f), 0.22f - 0.22f * sin(stripe))
            drawLine(Palette.Muted, project(toWorld(o)).first, project(toWorld(i)).first, 2f)
        }
    }
}

private operator fun Color.times(k: Float) = Color(red * k, green * k, blue * k, alpha)

internal data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun norm(): V3 { val l = kotlin.math.sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f); return V3(x / l, y / l, z / l) }
}

private class Face(val verts: List<V3>, val normal: V3, val color: Color)

/** Caja centrada en (cx, cy, cz) con tamaño (sx, sy, sz). Vértices en sentido antihorario visto desde afuera. */
private fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, color: Color): List<Face> {
    val x0 = cx - sx / 2; val x1 = cx + sx / 2
    val y0 = cy - sy / 2; val y1 = cy + sy / 2
    val z0 = cz - sz / 2; val z1 = cz + sz / 2
    return listOf(
        Face(listOf(V3(x0, y0, z1), V3(x1, y0, z1), V3(x1, y1, z1), V3(x0, y1, z1)), V3(0f, 0f, 1f), color),  // arriba
        Face(listOf(V3(x0, y1, z0), V3(x1, y1, z0), V3(x1, y0, z0), V3(x0, y0, z0)), V3(0f, 0f, -1f), color), // abajo
        Face(listOf(V3(x1, y0, z0), V3(x1, y1, z0), V3(x1, y1, z1), V3(x1, y0, z1)), V3(1f, 0f, 0f), color),  // frente
        Face(listOf(V3(x0, y1, z0), V3(x0, y0, z0), V3(x0, y0, z1), V3(x0, y1, z1)), V3(-1f, 0f, 0f), color), // atrás
        Face(listOf(V3(x0, y1, z0), V3(x0, y1, z1), V3(x1, y1, z1), V3(x1, y1, z0)), V3(0f, 1f, 0f), color),  // izquierda
        Face(listOf(V3(x0, y0, z0), V3(x1, y0, z0), V3(x1, y0, z1), V3(x0, y0, z1)), V3(0f, -1f, 0f), color), // derecha
    )
}
