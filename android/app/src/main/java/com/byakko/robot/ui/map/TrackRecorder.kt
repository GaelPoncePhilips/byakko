package com.byakko.robot.ui.map

import com.byakko.robot.data.RobotMode
import com.byakko.robot.data.Telemetry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Un punto del recorrido: dónde estaba el auto, para dónde miraba y en qué modo iba. */
data class TrackPoint(val x: Float, val y: Float, val headingDeg: Float, val mode: RobotMode)

/** Un eco de ultrasonido convertido a coordenadas del mundo (mm). [sensor]: 0 frente, 1 izq, 2 der. */
data class WallPoint(val x: Float, val y: Float, val sensor: Int)

data class TrackState(
    val path: List<TrackPoint> = emptyList(),
    val walls: List<WallPoint> = emptyList(),
)

/**
 * Arma el mapa con la telemetría: el recorrido sale de la odometría del robot (X, Y, H)
 * y las paredes de proyectar las distancias de los 3 ultrasonidos desde esa posición.
 */
object TrackRecorder {
    private const val MAX_PATH = 4000
    private const val MAX_WALLS = 3000
    private const val SENSOR_RANGE_MM = 1500       // más lejos que esto no se dibuja (2000 = nada)
    private const val SENSOR_OFFSET_MM = 60f       // los ultrasonidos están a ~6 cm del centro

    fun add(state: TrackState, t: Telemetry): TrackState {
        val p = TrackPoint(t.xMm, t.yMm, t.headingDeg, t.mode)
        val last = state.path.lastOrNull()
        val moved = last == null || hypot(p.x - last.x, p.y - last.y) > 8f || abs(p.headingDeg - last.headingDeg) > 3f
        val path = if (moved) (state.path + p).takeLast(MAX_PATH) else state.path

        // Paredes: sólo en modo obstáculos (y en manual, que también sirve para explorar)
        var walls = state.walls
        if (t.mode == RobotMode.AVOID || t.mode == RobotMode.MANUAL) {
            val a = Math.toRadians(t.headingDeg.toDouble())
            listOf(Triple(t.frontMm, 0.0, 0), Triple(t.leftMm, Math.PI / 2, 1), Triple(t.rightMm, -Math.PI / 2, 2))
                .forEach { (mm, rel, id) ->
                    if (mm in 1 until SENSOR_RANGE_MM) {
                        val d = mm + SENSOR_OFFSET_MM
                        val wx = t.xMm + (d * cos(a + rel)).toFloat()
                        val wy = t.yMm + (d * sin(a + rel)).toFloat()
                        val prev = walls.lastOrNull { it.sensor == id }
                        if (prev == null || hypot(wx - prev.x, wy - prev.y) > 6f) walls = walls + WallPoint(wx, wy, id)
                    }
                }
            walls = walls.takeLast(MAX_WALLS)
        }
        return TrackState(path, walls)
    }
}
