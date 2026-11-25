package com.byakko.robot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.byakko.robot.data.ConnState
import com.byakko.robot.data.RobotMode
import com.byakko.robot.ui.theme.Palette

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
        content()
    }
}

@Composable
fun StatusPill(state: ConnState) {
    val (label, color) = when (state) {
        ConnState.Connected -> "Conectado" to Palette.Ok
        ConnState.Connecting -> "Conectando…" to Palette.Warn
        ConnState.JoiningWifi -> "Uniendo a Wi-Fi…" to Palette.Warn
        ConnState.Disconnected -> "Desconectado" to Palette.Muted
        is ConnState.Failed -> "Error" to Palette.Danger
    }
    val c by animateColorAsState(color, label = "pill")
    Row(
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
fun ModeSelector(selected: RobotMode, enabled: Boolean, onSelect: (RobotMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(RobotMode.LINE, RobotMode.AVOID, RobotMode.MANUAL).forEach { m ->
            val active = m == selected
            val bg by animateColorAsState(if (active) Palette.Accent else Palette.SurfaceHi, label = "mode")
            val fg = if (active) Palette.Bg else Palette.Ink
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(enabled = enabled) { onSelect(m) }
                    .padding(vertical = 16.dp, horizontal = 12.dp),
            ) {
                Text(m.label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) fg else Palette.Muted)
                Text(m.hint, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.6f))
            }
        }
    }
}

/** Barra horizontal 0..1 con etiqueta y valor. */
@Composable
fun Meter(label: String, value: String, fraction: Float, color: Color = Palette.Accent) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Text(value, style = MaterialTheme.typography.labelSmall, color = Palette.Ink, fontFamily = FontFamily.Monospace)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.SurfaceHi)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) Palette.Ink else Palette.Muted,
        modifier = Modifier
            .clip(CircleShape)
            .background(Palette.SurfaceHi)
            .border(1.dp, Palette.Line, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
