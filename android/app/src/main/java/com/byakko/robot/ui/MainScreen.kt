package com.byakko.robot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import com.byakko.robot.data.Emote
import com.byakko.robot.ui.components.Car3D
import com.byakko.robot.ui.components.CurvedSlider
import com.byakko.robot.ui.map.MapView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: RobotViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val connected = ui.conn == ConnState.Connected
    val t = ui.telemetry
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showEmotes by rememberSaveable { mutableStateOf(false) }
    var joystickLeft by rememberSaveable { mutableStateOf(true) }
    var bubble by remember { mutableStateOf<Emote?>(null) }

    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- encabezado ----
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BYAKKO", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink, modifier = Modifier.weight(1f))
            StatusPill(ui.conn)
            RoundIcon("⋯", onClick = { showMore = true })
        }

        // ---- los 3 modos ----
        ModeSelector(ui.mode, enabled = connected, onSelect = vm::selectMode)

        // ---- mapa + modelo 3D ----
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Surface)
                .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)),
        ) {
            MapView(track, t?.xMm ?: 0f, t?.yMm ?: 0f, t?.headingDeg ?: 0f, ui.mode, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(140.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Bg.copy(alpha = 0.6f))) {
                Car3D(t?.headingDeg ?: 0f, t?.vLeft ?: 0, t?.vRight ?: 0, Modifier.fillMaxSize())
                Text("%.0f°".format(t?.headingDeg ?: 0f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
                EmoteBubble(bubble, onDone = { bubble = null }, modifier = Modifier.align(Alignment.TopCenter))
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIcon("🗑", onClick = vm::clearTrack, enabled = connected)
            }
            RoundIcon("😊", onClick = { showEmotes = true }, enabled = connected, big = true,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            if (!connected) {
                Box(Modifier.align(Alignment.Center).padding(18.dp)) { ConnectionSection(ui, vm) }
            }
        }

        // ---- controles: joystick + deslizador curvo ----
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            val manual = connected && ui.mode == RobotMode.MANUAL
            Joystick(
                onMove = vm::joystick, enabled = manual, size = 190.dp,
                modifier = Modifier.align(if (joystickLeft) Alignment.BottomStart else Alignment.BottomEnd),
            )
            CurvedSlider(
                value = ui.speedPct, mirrored = joystickLeft, enabled = connected, onChangeFinished = vm::setSpeed,
                modifier = Modifier.align(if (joystickLeft) Alignment.BottomEnd else Alignment.BottomStart).fillMaxWidth(0.48f).fillMaxHeight(),
            )
            RoundIcon("⇄", onClick = { joystickLeft = !joystickLeft }, modifier = Modifier.align(Alignment.TopCenter))
        }

        Button(
            onClick = vm::stop, enabled = connected, shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.Bg),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).height(50.dp),
        ) { Text("DETENER", style = MaterialTheme.typography.titleSmall) }
    }

    if (showEmotes) {
        ModalBottomSheet(onDismissRequest = { showEmotes = false }, containerColor = Palette.Surface) {
            EmoteGrid(onPick = { e ->
                vm.playEmote(e); bubble = e; showEmotes = false
            })
        }
    }
    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, containerColor = Palette.Bg) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ConnectionSection(ui, vm)
                if (connected && ui.mode == RobotMode.MANUAL) GcodeSection(ui, vm)
                t?.takeIf { connected }?.let { TelemetrySection(it) }
                if (ui.log.isNotEmpty()) {
                    Section("Registro") {
                        ui.log.take(12).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                color = if (it.startsWith("ERR")) Palette.Danger else Palette.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, big: Boolean = false) {
    Box(
        modifier.size(if (big) 54.dp else 40.dp).clip(CircleShape).background(Palette.SurfaceHi)
            .border(1.dp, Palette.Line, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = if (big) 26.sp else 18.sp, color = if (enabled) Palette.Ink else Palette.Muted,
            modifier = Modifier.alpha(if (enabled) 1f else 0.4f))
    }
}

@Composable
private fun EmoteGrid(onPick: (Emote) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ANIMACIONES", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
        RP.EMOTES.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { e ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Palette.SurfaceHi)
                            .clickable { onPick(e) }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(e.emoji, fontSize = 34.sp)
                        Text(e.name, style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
                    }
                }
            }
        }
        Text("El auto pasa a modo manual para hacer la animación.", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/** Globito con el emoji que sube y se desvanece arriba del modelo 3D. */
@Composable
private fun EmoteBubble(emote: Emote?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (emote == null) return
    val anim = remember(emote) { Animatable(0f) }
    LaunchedEffect(emote) {
        anim.animateTo(1f, tween(2200))
        onDone()
    }
    val p = anim.value
    Text(
        emote.emoji, fontSize = 34.sp,
        modifier = modifier.offset(y = (8 - 28 * p).dp).scale(0.6f + 0.6f * minOf(1f, p * 4)).alpha(1f - ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)),
    )
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
