package app.synctheme.mobile.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.synctheme.core.Theme
import app.synctheme.mobile.data.CalendarData
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.AppBackdrop
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.ColorPickerDialog
import app.synctheme.mobile.ui.ColorSwatch
import app.synctheme.mobile.ui.LabeledSlider
import app.synctheme.mobile.ui.Section
import app.synctheme.mobile.ui.SwitchRow
import app.synctheme.mobile.ui.WeatherPlaceEditor
import app.synctheme.core.FontWeight as ClockFont

/**
 * Настройка одного виджета: открывается при добавлении на экран, по «Настройки» у виджета
 * (Android 12+) и из SyncTheme. Превью — те же RemoteViews, что увидит лаунчер.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val kind = WidgetUpdater.kindOf(this, id)
            ?: intent.getStringExtra(EXTRA_KIND)?.let { runCatching { WidgetKind.valueOf(it) }.getOrNull() }
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) {
            finish()
            return
        }
        val theme = ThemeRepository.get(this).applied.value
        val initial = WidgetUpdater.config(this, id, kind)
        setContent {
            AppTheme(theme.accentColor) {
                WidgetConfigScreen(kind, initial, theme, onCancel = ::finish) { config -> save(id, kind, config) }
            }
        }
    }

    private fun save(id: Int, kind: WidgetKind, config: WidgetConfig) {
        WidgetConfigStore.set(this, id, config)
        WidgetUpdater.updateOne(this, AppWidgetManager.getInstance(this), id, kind)
        WidgetScheduler.ensure(this)
        if (kind == WidgetKind.WEATHER) WidgetScheduler.refreshWeatherNow(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }

    companion object {
        const val EXTRA_KIND = "kind"
    }
}

/** Размер превью в dp — примерно как виджет на рабочем столе. */
fun previewSize(kind: WidgetKind, style: String): Pair<Dp, Dp> = when (kind) {
    WidgetKind.CLOCK -> when {
        style.startsWith("analog_") -> 170.dp to 170.dp
        style == "stacked" -> 180.dp to 200.dp
        else -> 330.dp to 160.dp
    }
    WidgetKind.DATE -> if (style == "compact") 200.dp to 90.dp else 170.dp to 170.dp
    WidgetKind.CALENDAR -> when (style) {
        "agenda" -> 320.dp to 210.dp
        "month_agenda" -> 320.dp to 340.dp
        else -> 300.dp to 270.dp
    }
    WidgetKind.WEATHER -> when (style) {
        "compact" -> 320.dp to 120.dp
        "details" -> 320.dp to 190.dp
        "hourly" -> 330.dp to 160.dp
        "daily" -> 330.dp to 190.dp
        else -> 340.dp to 340.dp
    }
    WidgetKind.BATTERY -> if (style == "bar") 300.dp to 110.dp else 170.dp to 170.dp
}

/** Живое превью виджета на фоне обоев темы. */
@Composable
fun WidgetPreview(kind: WidgetKind, config: WidgetConfig, theme: Theme, weather: WeatherData?, modifier: Modifier = Modifier) {
    val (w, h) = previewSize(kind, config.style)
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            val views = WidgetRenderer.build(frame.context, kind, config, theme, weather)
            frame.removeAllViews()
            frame.addView(
                views.apply(frame.context, frame),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        },
        modifier = modifier.size(w, h),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigScreen(
    kind: WidgetKind,
    initial: WidgetConfig,
    theme: Theme,
    onCancel: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(initial) }
    var weather by remember { mutableStateOf(WeatherRepository(context).cached) }
    var colorEdit by remember { mutableStateOf<String?>(null) }
    var calendarAllowed by remember { mutableStateOf(CalendarData.hasPermission(context)) }
    val wallpaper = remember { ThemeRenderer.background(context, theme, 720, 640).asImageBitmap() }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarAllowed = it
        // Перерисовать превью с событиями.
        config = config.copy()
    }
    val analog = kind == WidgetKind.CLOCK && config.style.startsWith("analog_")
    val digitalClock = kind == WidgetKind.CLOCK && !analog

    AppBackdrop {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Закрыть") }
                Text("Виджет «${kind.title}»", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.fillMaxWidth().height(previewSize(kind, config.style).second + 40.dp)
                    .padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                WidgetPreview(kind, config, theme, weather)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Section("Стиль") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            kind.styles.forEach { s ->
                                FilterChip(config.style == s.id, { config = config.copy(style = s.id) }, label = { Text(s.title) })
                            }
                        }
                    }
                }
                val alignable = (digitalClock && config.style != "side") ||
                    (kind == WidgetKind.DATE && config.style in listOf("big_day", "line"))
                if (alignable || digitalClock) {
                    item {
                        Section("Текст") {
                            if (alignable) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    WidgetAlign.entries.forEach { a ->
                                        FilterChip(config.align == a, { config = config.copy(align = a) }, label = { Text(a.title) })
                                    }
                                }
                            }
                            if (digitalClock) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(config.fontWeight == null, { config = config.copy(fontWeight = null) }, label = { Text("Как в теме") })
                                    listOf(ClockFont.THIN to "Тонкий", ClockFont.LIGHT to "Лёгкий", ClockFont.REGULAR to "Обычный", ClockFont.BOLD to "Жирный")
                                        .forEach { (w, label) ->
                                            FilterChip(config.fontWeight == w, { config = config.copy(fontWeight = w) }, label = { Text(label) })
                                        }
                                }
                            }
                            if (digitalClock && config.style in listOf("classic", "seconds", "stacked")) {
                                SwitchRow("Дата под часами", config.showDate) { config = config.copy(showDate = it) }
                            }
                        }
                    }
                }
                if (analog) {
                    item {
                        Section("Стрелки") {
                            SwitchRow("Секундная стрелка", config.showSeconds) { config = config.copy(showSeconds = it) }
                        }
                    }
                }
                item {
                    Section("Подложка") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WidgetShape.entries.forEach { s ->
                                FilterChip(config.shape == s, { config = config.copy(shape = s) }, label = { Text(s.title) })
                            }
                        }
                        LabeledSlider("Непрозрачность", config.opacity, 0f..1f, "${(config.opacity * 100).toInt()}%") {
                            config = config.copy(opacity = it)
                        }
                    }
                }
                item {
                    Section("Цвета") {
                        val ownColors = config.textColor != null || config.accentColor != null
                        SwitchRow("Цвета темы", !ownColors) { useTheme ->
                            config = if (useTheme) config.copy(textColor = null, accentColor = null)
                            else config.copy(textColor = theme.textColor, accentColor = theme.accentColor)
                        }
                        if (ownColors) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ColorSwatch(config.textColor ?: theme.textColor, size = 44.dp) { colorEdit = "text" }
                                    Text("Текст", style = MaterialTheme.typography.labelMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ColorSwatch(config.accentColor ?: theme.accentColor, size = 44.dp) { colorEdit = "accent" }
                                    Text("Акцент", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (kind == WidgetKind.WEATHER) {
                            SwitchRow("Цветные иконки погоды", config.colorIcons) { config = config.copy(colorIcons = it) }
                        }
                    }
                }
                if (kind == WidgetKind.WEATHER) {
                    item { Section("Погода") { WeatherPlaceEditor { weather = it } } }
                }
                if (kind == WidgetKind.CALENDAR && config.style != "month" && !calendarAllowed) {
                    item {
                        Section("События") {
                            Text("Чтобы показывать события, SyncTheme нужен доступ к календарю.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) {
                                Text("Разрешить доступ к календарю")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            Button(
                onClick = { onSave(config) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text("Готово", style = MaterialTheme.typography.titleMedium) }
        }
    }

    colorEdit?.let { which ->
        val initialColor = if (which == "text") config.textColor ?: theme.textColor else config.accentColor ?: theme.accentColor
        ColorPickerDialog(if (which == "text") "Цвет текста" else "Акцентный цвет", initialColor, onDismiss = { colorEdit = null }) { c ->
            config = if (which == "text") config.copy(textColor = c) else config.copy(accentColor = c)
            colorEdit = null
        }
    }
}
