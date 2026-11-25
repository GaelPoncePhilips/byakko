package com.byakko.robot.data

import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

sealed interface ConnState {
    data object Disconnected : ConnState
    data object JoiningWifi : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data class Failed(val reason: String) : ConnState
}

/**
 * Cliente HTTP del robot. Cada orden es un GET (como el simulador AutoUnificado).
 * - Las órdenes salen en orden, de a una, por una cola.
 * - Cada 400 ms pide /estado: trae la telemetría y sirve de latido (si el robot
 *   deja de recibirlo 2 s, se frena solo).
 * - El latido se saltea justo después de una orden, porque el simulador sólo
 *   guarda el ÚLTIMO pedido y lo pisaría.
 */
class RobotClient(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _telemetry = MutableSharedFlow<Telemetry>(extraBufferCapacity = 8)
    val telemetry: SharedFlow<Telemetry> = _telemetry

    private val _replies = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val replies: SharedFlow<String> = _replies

    private var jobs = listOf<Job>()
    @Volatile private var outbox: Channel<String>? = null
    @Volatile private var lastCommandAt = 0L

    fun setState(s: ConnState) { _state.value = s }

    fun connect(network: Network?, host: String) {
        disconnect()
        val base = "http://" + host.trim().removePrefix("http://").trimEnd('/')
        val box = Channel<String>(capacity = 32)
        outbox = box
        _state.value = ConnState.Connecting

        fun get(path: String): String {
            val url = URL(base + path)
            val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.useCaches = false
            // Se lee completo y se cierra el stream (sin disconnect) para que Android
            // reuse la misma conexión TCP en el próximo pedido: es mucho más rápido.
            return conn.inputStream.bufferedReader().use { it.readText() }.trim()
        }

        val sender = scope.launch(Dispatchers.IO) {
            for (path in box) {
                lastCommandAt = System.currentTimeMillis()
                runCatching { get(path) }
                    .onSuccess { body -> body.lines().filter { it.isNotBlank() && it != "OK" }.forEach { _replies.tryEmit(it) } }
                    .onFailure { _replies.tryEmit("ERR sin respuesta: $path") }
            }
        }
        val poller = scope.launch(Dispatchers.IO) {
            var failures = 0
            while (isActive) {
                if (System.currentTimeMillis() - lastCommandAt > 250) {
                    runCatching { get(RobotProtocol.STATUS) }
                        .onSuccess { body ->
                            failures = 0
                            _state.value = ConnState.Connected
                            RobotProtocol.parseTelemetry(body)?.let { _telemetry.tryEmit(it) }
                        }
                        .onFailure { e ->
                            failures++
                            if (failures >= 4) _state.value = ConnState.Failed(e.message ?: "Sin respuesta del robot")
                        }
                }
                delay(250)   // 4 veces por segundo: el recorrido se ve suave
            }
        }
        jobs = listOf(sender, poller)
    }

    fun send(path: String): Boolean = outbox?.trySend(path)?.isSuccess == true

    fun disconnect() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        outbox?.close()
        outbox = null
        _state.value = ConnState.Disconnected
    }
}
