package com.byakko.robot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.byakko.robot.data.ConnState
import com.byakko.robot.data.RobotClient
import com.byakko.robot.data.RobotFinder
import com.byakko.robot.data.RobotMode
import com.byakko.robot.data.RobotProtocol
import com.byakko.robot.data.Telemetry
import com.byakko.robot.data.WifiConnector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val conn: ConnState = ConnState.Disconnected,
    val ssid: String = RobotProtocol.DEFAULT_SSID,
    val pass: String = RobotProtocol.DEFAULT_PASS,
    val host: String = RobotProtocol.DEFAULT_HOST,
    val mode: RobotMode = RobotMode.IDLE,
    val telemetry: Telemetry? = null,
    val speedPct: Int = 60,
    val log: List<String> = emptyList(),
    val searching: Boolean = false,
)

class RobotViewModel(app: Application) : AndroidViewModel(app) {
    private val wifi = WifiConnector(app)
    private val client = RobotClient(viewModelScope)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    @Volatile private var joyX = 0f
    @Volatile private var joyY = 0f
    private var lastJoy = ""

    init {
        viewModelScope.launch {
            client.state.collect { s ->
                val wasConnected = _ui.value.conn == ConnState.Connected
                _ui.update { it.copy(conn = s) }
                // Al conectar, el robot toma la velocidad elegida en la app
                if (s == ConnState.Connected && !wasConnected) client.send(RobotProtocol.speed(_ui.value.speedPct))
            }
        }
        viewModelScope.launch { client.replies.collect { log(it) } }
        viewModelScope.launch {
            client.telemetry.collect { t ->
                val previous = _ui.value.telemetry?.event
                if (t.event != "-" && t.event != previous) log(t.event)
                _ui.update { it.copy(telemetry = t, mode = t.mode) }
            }
        }
        // Joystick: manda sólo cuando cambia, a lo sumo cada 100 ms.
        viewModelScope.launch {
            while (true) {
                delay(100)
                val s = _ui.value
                if (s.conn != ConnState.Connected || s.mode != RobotMode.MANUAL) continue
                val path = RobotProtocol.joystick(joyX, joyY)
                if (path != lastJoy) {
                    client.send(path)
                    lastJoy = path
                }
            }
        }
    }

    private fun log(line: String) = _ui.update { it.copy(log = (listOf(line) + it.log).take(40)) }

    fun setSsid(s: String) = _ui.update { it.copy(ssid = s) }
    fun setPass(s: String) = _ui.update { it.copy(pass = s) }
    fun setHost(s: String) = _ui.update { it.copy(host = s) }

    /** Une el teléfono a la red del robot y habla por esa red. */
    fun connectViaWifi() {
        client.disconnect()
        client.setState(ConnState.JoiningWifi)
        // En modo AP el robot siempre está en 192.168.4.1
        _ui.update { it.copy(host = RobotProtocol.DEFAULT_HOST) }
        val s = _ui.value
        wifi.request(
            s.ssid, s.pass,
            onAvailable = { net -> client.connect(net, s.host) },
            onLost = { client.disconnect() },
            onUnavailable = { client.setState(ConnState.Failed("No se encontró la red ${s.ssid}")) },
        )
    }

    /** Para cuando el teléfono ya está en la red (o para usar el simulador en la PC). */
    fun connectDirect() = client.connect(null, _ui.value.host)

    /** Busca el robot en la red Wi-Fi a la que ya está conectado el teléfono. */
    fun searchOnMyNetwork() {
        if (_ui.value.searching) return
        client.disconnect()
        _ui.update { it.copy(searching = true) }
        viewModelScope.launch {
            val found = RobotFinder.scan(getApplication())
            _ui.update { it.copy(searching = false) }
            if (found != null) {
                log("Robot encontrado en $found")
                _ui.update { it.copy(host = found) }
                client.connect(null, found)
            } else {
                client.setState(ConnState.Failed("No se encontró el robot en esta red. Revisá el monitor serie para ver su IP."))
            }
        }
    }

    fun disconnect() {
        client.send(RobotProtocol.STOP)
        viewModelScope.launch {
            delay(300)
            client.disconnect()
            wifi.release()
        }
    }

    fun selectMode(m: RobotMode) {
        _ui.update { it.copy(mode = m) }
        client.send(m.path)
    }

    fun stop() {
        joyX = 0f; joyY = 0f
        client.send(RobotProtocol.STOP)
        client.send(RobotMode.IDLE.path)
        _ui.update { it.copy(mode = RobotMode.IDLE) }
    }

    fun joystick(x: Float, y: Float) { joyX = x; joyY = y }

    fun setSpeed(pct: Int) {
        _ui.update { it.copy(speedPct = pct) }
        client.send(RobotProtocol.speed(pct))
    }

    /** G-code: texto libre "G1 D300; G5 A90" o lista ya armada. */
    fun sendGcode(text: String) {
        val cmds = RobotProtocol.splitUserGcode(text)
        if (cmds.isEmpty()) return
        client.send(RobotProtocol.gcode(cmds))
        log("> " + cmds.joinToString(" ; ") { it.uppercase() })
    }

    override fun onCleared() {
        client.send(RobotProtocol.STOP)
        client.disconnect()
        wifi.release()
    }
}
