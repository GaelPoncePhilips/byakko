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
    val xMm: Float = 0f, val yMm: Float = 0f,   // posición por odometría (x adelante, y izquierda)
    val headingDeg: Float = 0f,                 // rumbo, + = giró a la izquierda
)

/** Animación del auto: un emoji y la lista de movimientos G-code que la hacen. */
data class Emote(val emoji: String, val name: String, val moves: List<String>)

/** Rutas HTTP del robot (ver docs/PROTOCOLO.md). */
object RobotProtocol {
    const val DEFAULT_SSID = "Byakko-Robot"
    const val DEFAULT_PASS = "byakko2026"
    const val DEFAULT_HOST = "192.168.4.1"

    const val STATUS = "/estado"
    const val STOP = "/stop"
    const val ORIGIN = "/origen"

    /** Animaciones del menú de emoticonos (se mandan como G-code, el firmware ya los entiende). */
    val EMOTES = listOf(
        Emote("😊", "Feliz", listOf("G5A25F400", "G5A-50F400", "G5A50F400", "G5A-25F400")),
        Emote("😵", "Mareado", listOf("G5A720F350")),
        Emote("🙅", "No", listOf("G5A15F450", "G5A-30F450", "G5A30F450", "G5A-30F450", "G5A15F450")),
        Emote("🙆", "Sí", listOf("G1D30F350", "G1D-30F350", "G1D30F350", "G1D-30F350")),
        Emote("😱", "Susto", listOf("G1D-150F400", "G4P400", "G5A20F300", "G5A-40F300", "G5A20F300")),
        Emote("💃", "Baile", listOf("G3R80A90F300", "G2R80A90F300", "G5A360F400", "G3R80A90F300", "G2R80A90F300")),
        Emote("👋", "Saludo", listOf("G5A45F300", "G4P200", "G5A-90F300", "G4P200", "G5A45F300")),
        Emote("😴", "Dormir", listOf("G1D40F80", "G4P800", "G1D-20F60", "G4P1200")),
        Emote("🐯", "Ataque", listOf("G1D-60F200", "G4P300", "G1D200F400")),
    )

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
        // El simulador a veces manda decimales ("186.77") donde el robot manda enteros
        fun num(k: String) = kv[k]?.toFloatOrNull() ?: 0f
        fun int(k: String) = num(k).toInt()
        return Telemetry(
            mode = RobotMode.fromWire(kv["M"] ?: "IDLE"),
            frontMm = int("F"), leftMm = int("L"), rightMm = int("R"),
            irLeft = int("IL"), irRight = int("IR"),
            vLeft = int("VL"), vRight = int("VR"),
            pwmLeft = int("PL"), pwmRight = int("PR"),
            queue = int("Q"),
            speedPct = int("S"),
            event = kv["E"] ?: "-",
            xMm = num("X"), yMm = num("Y"),
            headingDeg = num("H") / 10f,
        )
    }
}
