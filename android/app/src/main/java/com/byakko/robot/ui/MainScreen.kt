package com.byakko.robot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.byakko.robot.data.ConnState
import com.byakko.robot.data.RobotMode
import com.byakko.robot.data.RobotProtocol as RP
import com.byakko.robot.data.Telemetry
import com.byakko.robot.ui.components.Chip
import com.byakko.robot.ui.components.Joystick
import com.byakko.robot.ui.components.Meter
import com.byakko.robot.ui.components.ModeSelector
import com.byakko.robot.ui.components.Section
import com.byakko.robot.ui.components.StatusPill
import com.byakko.robot.ui.theme.Palette
import kotlin.math.abs

@Composable
fun MainScreen(vm: RobotViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val connected = ui.conn == ConnState.Connected

    Box(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("BYAKKO", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                StatusPill(ui.conn)
            }

            ConnectionSection(ui, vm)

            Section("Modo") {
                ModeSelector(ui.mode, enabled = connected, onSelect = vm::selectMode)
                SpeedControl(ui, vm, connected)
            }

            AnimatedVisibility(visible = ui.mode == RobotMode.MANUAL && connected) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ManualSection(ui, vm)
                    GcodeSection(ui, vm)
                }
            }

            ui.telemetry?.takeIf { connected }?.let { TelemetrySection(it) }

            if (ui.log.isNotEmpty()) {
                Section("Registro") {
                    ui.log.take(8).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = if (it.startsWith("ERR")) Palette.Danger else Palette.Muted)
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }

        // STOP siempre visible
        Button(
            onClick = vm::stop,
            enabled = connected,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.Bg),
            modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp).fillMaxWidth().height(58.dp),
        ) { Text("DETENER", style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun ConnectionSection(ui: UiState, vm: RobotViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val busy = ui.conn == ConnState.Connecting || ui.conn == ConnState.JoiningWifi || ui.searching
    Section("Conexión") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ui.ssid, style = MaterialTheme.typography.bodyLarge)
                Text("http://${ui.host}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, fontFamily = FontFamily.Monospace)
            }
            Text(if (expanded) "Ocultar" else "Editar", color = Palette.Accent, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(8.dp))
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Red (SSID)", ui.ssid, vm::setSsid)
                Field("Contraseña", ui.pass, vm::setPass, password = true)
                Field("IP del robot (o de la PC con el simulador)", ui.host, vm::setHost)
            }
        }
        (ui.conn as? ConnState.Failed)?.let { Text(it.reason, color = Palette.Danger, style = MaterialTheme.typography.labelSmall) }

        if (ui.conn == ConnState.Connected) {
            OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
        } else {
            // Botón principal: red propia del robot (modo AP)
            Button(onClick = vm::connectViaWifi, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (busy) "Conectando…" else "Conectar al robot")
            }
            var otherNetwork by rememberSaveable { mutableStateOf(false) }
            Text(
                if (otherNetwork) "Ocultar otras formas" else "¿El robot está en otra red Wi-Fi?",
                color = Palette.Muted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { otherNetwork = !otherNetwork }.padding(vertical = 6.dp),
            )
            AnimatedVisibility(otherNetwork) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::searchOnMyNetwork, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(if (ui.searching) "Buscando…" else "Buscar robot")
                    }
                    OutlinedButton(onClick = vm::connectDirect, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Usar ${ui.host}") }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Palette.Line),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ManualSection(ui: UiState, vm: RobotViewModel) {
    Section("Control manual") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Joystick(onMove = vm::joystick)
        }
    }
}

/** Velocidad para todos los modos (línea, obstáculos y manual). */
@Composable
private fun SpeedControl(ui: UiState, vm: RobotViewModel, enabled: Boolean) {
    var speed by remember(ui.speedPct) { mutableFloatStateOf(ui.speedPct.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Velocidad", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            val robot = ui.telemetry?.speedPct?.takeIf { it > 0 && it != speed.toInt() }?.let { "  (robot: $it %)" } ?: ""
            Text("${speed.toInt()} %$robot", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = speed, onValueChange = { speed = it }, valueRange = 10f..100f, enabled = enabled,
            onValueChangeFinished = { vm.setSpeed(speed.toInt()) },
            colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.SurfaceHi),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GcodeSection(ui: UiState, vm: RobotViewModel) {
    var cmd by rememberSaveable { mutableStateOf("") }
    val f = 120 + ui.speedPct * 2  // F en mm/s según el deslizador (140..320)
    val presets = listOf(
        "↑ 5 cm" to RP.straight(50, f),
        "↓ 5 cm" to RP.straight(-50, f),
        "⟲ 90°" to RP.rotate(90, f),
        "⟳ 90°" to RP.rotate(-90, f),
        "⟲ 180°" to RP.rotate(180, f),
        "Curva ↰" to RP.arcLeft(150, 90, f),
        "Curva ↱" to RP.arcRight(150, 90, f),
        "Cuadrado 30" to List(4) { "${RP.straight(300, f)};${RP.rotate(90, f)}" }.joinToString(";"),
        "Zigzag" to "${RP.arcLeft(200, 60, f)};${RP.arcRight(200, 120, f)};${RP.arcLeft(200, 60, f)}",
    )
    Section("Movimientos (encoders)") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (label, c) -> Chip(label, onClick = { vm.sendGcode(c) }) }
            Chip("Cancelar", onClick = { vm.sendGcode(RP.ABORT_MOTION) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cmd, onValueChange = { cmd = it }, singleLine = true,
                placeholder = { Text("G1 D300 F200; G5 A90", fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendGcode(cmd); cmd = "" }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Palette.Line),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.sendGcode(cmd); cmd = "" }, enabled = cmd.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Text("Enviar") }
        }
        ui.telemetry?.let { if (it.queue > 0) Text("En cola: ${it.queue}", style = MaterialTheme.typography.labelSmall, color = Palette.Warn) }
    }
}

@Composable
private fun TelemetrySection(t: Telemetry) {
    fun dist(mm: Int) = if (mm >= 2000) "—" else "%.1f cm".format(mm / 10f)
    fun near(mm: Int) = 1f - (mm / 500f)  // barra llena = muy cerca (0..50 cm)
    fun col(mm: Int) = when { mm < 80 -> Palette.Danger; mm < 200 -> Palette.Warn; else -> Palette.Accent }
    Section("Telemetría") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Meter("Izquierda", dist(t.leftMm), near(t.leftMm), col(t.leftMm))
                Meter("Frente", dist(t.frontMm), near(t.frontMm), col(t.frontMm))
                Meter("Derecha", dist(t.rightMm), near(t.rightMm), col(t.rightMm))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Meter("IR izq", "${t.irLeft / 10} %", t.irLeft / 1000f, Palette.Ink)
                Meter("IR der", "${t.irRight / 10} %", t.irRight / 1000f, Palette.Ink)
                Meter("Ruedas mm/s", "${t.vLeft} | ${t.vRight}", (abs(t.vLeft) + abs(t.vRight)) / 800f)
            }
        }
    }
}
