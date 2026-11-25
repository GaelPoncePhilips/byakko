package com.byakko.robot.data

import kotlin.math.roundToInt

/** Modos del firmware. [path] es la ruta HTTP que lo activa. */
enum class RobotMode(val wire: String, val path: String, val label: String, val hint: String) {
    IDLE("IDLE", "/reposo", "Reposo", "Motores detenidos"),
    LINE("LINE", "/siguelinea", "Línea", "2 sensores IR"),
    AVOID("AVOID", "/automatico", "Obstáculos", "3 ultrasonidos"),
    MANUAL("MANUAL", "/manual", "Manual", "Joystick + G-code");

    companion object {
        fun fromWire(s: String) = entries.firstOrNull { it.wire == s } ?: IDLE
    }
}

data class Telemetry(
    val mode: RobotMode,
    val frontMm: Int, val leftMm: Int, val rightMm: Int,
    val irLeft: Int, val irRight: Int,          // 0..1000 (0 blanco, 1000 negro)
    val vLeft: Int, val vRight: Int,            // mm/s medidos por encoder
    val pwmLeft: Int, val pwmRight: Int,
    val queue: Int,
    val speedPct: Int,                          // escala de velocidad actual
    val event: String,                          // DONE_G1, ABORT, TIMEOUT_G5...
)

/** Rutas HTTP del robot (ver docs/PROTOCOLO.md). */
object RobotProtocol {
    const val DEFAULT_SSID = "Byakko-Robot"
    const val DEFAULT_PASS = "byakko2026"
    const val DEFAULT_HOST = "192.168.4.1"

    const val STATUS = "/estado"
    const val STOP = "/stop"

    /** Escala de velocidad para todos los modos, 10..100 %. */
    fun speed(pct: Int) = "/vel?v=${pct.coerceIn(10, 100)}"

    /** Joystick: x, y en -1..1 -> -127..127 (igual que el simulador). */
    fun joystick(x: Float, y: Float) =
        "/?X=${(x.coerceIn(-1f, 1f) * 127).roundToInt()}&Y=${(y.coerceIn(-1f, 1f) * 127).roundToInt()}"

    fun straight(mm: Int, f: Int) = "G1D${mm}F$f"
    fun arcRight(r: Int, deg: Int, f: Int) = "G2R${r}A${deg}F$f"
    fun arcLeft(r: Int, deg: Int, f: Int) = "G3R${r}A${deg}F$f"
    fun rotate(deg: Int, f: Int) = "G5A${deg}F$f"
    const val ABORT_MOTION = "M0"

    /** Une uno o varios comandos G-code en una sola ruta: /g/G1D300/G5A90 */
    fun gcode(commands: List<String>) =
        "/g/" + commands.joinToString("/") { it.replace(" ", "").uppercase() }

    /** Acepta texto libre "G1 D300 F200; G5 A90" y lo parte en comandos. */
    fun splitUserGcode(text: String) =
        text.split(';', '\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    fun parseTelemetry(body: String): Telemetry? {
        val line = body.lineSequence().firstOrNull { it.startsWith("TEL ") } ?: return null
        val kv = line.removePrefix("TEL ").split(' ').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()
        fun int(k: String) = kv[k]?.toIntOrNull() ?: 0
        return Telemetry(
            mode = RobotMode.fromWire(kv["M"] ?: "IDLE"),
            frontMm = int("F"), leftMm = int("L"), rightMm = int("R"),
            irLeft = int("IL"), irRight = int("IR"),
            vLeft = int("VL"), vRight = int("VR"),
            pwmLeft = int("PL"), pwmRight = int("PR"),
            queue = int("Q"),
            speedPct = int("S"),
            event = kv["E"] ?: "-",
        )
    }
}
