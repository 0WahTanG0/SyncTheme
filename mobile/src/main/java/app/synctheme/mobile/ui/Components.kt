package app.synctheme.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ColorSwatch(color: Int, size: Dp = 40.dp, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** Кружок-миниатюра темы для ленты пресетов. */
@Composable
fun ThemeThumb(theme: Theme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = theme.background.colors.ifEmpty { listOf(0xFF000000.toInt()) }.map { Color(it) }
    val brush = when {
        colors.size == 1 -> Brush.linearGradient(listOf(colors[0], colors[0]))
        theme.background.type == BackgroundType.RADIAL -> Brush.radialGradient(colors)
        else -> Brush.linearGradient(colors)
    }
    Column(
        modifier = modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(brush)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when (theme.clockStyle) {
                    app.synctheme.core.ClockStyle.ANALOG -> "◷"
                    app.synctheme.core.ClockStyle.STACKED -> "10\n08"
                    app.synctheme.core.ClockStyle.DIGITAL -> "10:08"
                },
                color = Color(theme.textColor),
                style = MaterialTheme.typography.labelMedium,
                lineHeight = MaterialTheme.typography.labelMedium.fontSize,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(theme.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private val PALETTE = listOf(
    0xFFFFFFFF, 0xFFF5F5F5, 0xFF9E9E9E, 0xFF212121, 0xFF000000, 0xFFFF3B30, 0xFFFF6D00, 0xFFFFB86C,
    0xFFFFD166, 0xFFC5E1A5, 0xFF64FFDA, 0xFF1DE9B6, 0xFF00E5FF, 0xFF0091EA, 0xFF3D5AFE, 0xFF6A3DE8,
    0xFFB388FF, 0xFFFF2A6D, 0xFFFF8FAB, 0xFFD6336C, 0xFF3A1C71, 0xFF0B3D2E, 0xFF0B1026, 0xFF5D4037,
).map { it.toInt() }

/** Простой выбор цвета: палитра + ползунки HSV. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember(initial) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(initial) { mutableFloatStateOf(hsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

    fun setFrom(c: Int) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(c, out)
        hue = out[0]; sat = out[1]; value = out[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(color))
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PALETTE.forEach { c -> ColorSwatch(c, size = 30.dp, selected = c == color) { setFrom(c) } }
                }
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })
                    )
                )
                LabeledSlider("Оттенок", hue, 0f..360f, "${hue.toInt()}°") { hue = it }
                LabeledSlider("Насыщенность", sat, 0f..1f, "${(sat * 100).toInt()}%") { sat = it }
                LabeledSlider("Яркость", value, 0f..1f, "${(value * 100).toInt()}%") { value = it }
            }
        },
    )
}
