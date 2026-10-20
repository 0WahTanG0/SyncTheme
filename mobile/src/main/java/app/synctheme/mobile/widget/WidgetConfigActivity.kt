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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.synctheme.core.DigitFont
import app.synctheme.core.Theme
import app.synctheme.mobile.data.CalendarData
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.AccentButton
import app.synctheme.mobile.ui.AppBackdrop
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.ChipRow
import app.synctheme.mobile.ui.ColorPickerDialog
import app.synctheme.mobile.ui.ColorSwatch
import app.synctheme.mobile.ui.DigitFontPicker
import app.synctheme.mobile.ui.LabeledSlider
import app.synctheme.mobile.ui.LocalLook
import app.synctheme.mobile.ui.Section
import app.synctheme.mobile.ui.SwitchRow
import app.synctheme.mobile.ui.WeatherPlaceEditor

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
        val theme = ThemeRepository.get(this).effective.value
        val stored = WidgetUpdater.config(this, id, kind)
        val initial = if (kind == WidgetKind.APPS && stored.apps.isEmpty()) stored.copy(apps = AppCatalog.defaults(this)) else stored
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

/** Живое превью виджета нужного размера. */
@Composable
fun WidgetPreview(
    kind: WidgetKind,
    config: WidgetConfig,
    theme: Theme,
    weather: WeatherData?,
    size: WidgetSize,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            val views = WidgetRenderer.buildSize(frame.context, kind, config, theme, size, weather)
            frame.removeAllViews()
            frame.addView(
                views.apply(frame.context, frame),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        },
        modifier = modifier.size(size.previewW.dp, size.previewH.dp),
    )
}

/** Размер, с которого удобно начинать смотреть превью. */
fun defaultPreviewSize(kind: WidgetKind) = if (kind == WidgetKind.CLOCK) WidgetSize.WIDE else WidgetSize.M

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigScreen(
    kind: WidgetKind,
    initial: WidgetConfig,
    theme: Theme,
    onCancel: () -> Unit,
    initialSize: WidgetSize = defaultPreviewSize(kind),
    onSave: (WidgetConfig) -> Unit,
) {
    val context = LocalContext.current
    val look = LocalLook.current
    var config by remember { mutableStateOf(initial) }
    var size by remember { mutableStateOf(initialSize) }
    var weather by remember { mutableStateOf(WeatherRepository(context).cached) }
    var colorEdit by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) } // "open" или "apps"
    var calendarAllowed by remember { mutableStateOf(CalendarData.hasPermission(context)) }
    val wallpaper = remember { ThemeRenderer.background(context, theme, 720, 720).asImageBitmap() }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarAllowed = it
        config = config.copy() // перерисовать превью с событиями
    }
    val style = kind.style(config.style).id
    val analog = style.startsWith("analog_")
    val alignable = kind in listOf(WidgetKind.CLOCK, WidgetKind.CALENDAR) && style in listOf("bold", "glass", "minimal")
    val digital = kind == WidgetKind.CLOCK && !analog

    AppBackdrop {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Закрыть") }
                Text("Виджет «${kind.title}»", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.fillMaxWidth().height(350.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(look.radius)),
                contentAlignment = Alignment.Center,
            ) {
                Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                WidgetPreview(kind, config, theme, weather, size)
            }
            ChipRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                WidgetSize.entries.forEach { s ->
                    FilterChip(size == s, { size = s }, label = { Text(s.title) })
                }
            }
            Text(
                "Так виджет выглядит при разных размерах: растяните его на рабочем столе — информации станет больше.",
                style = MaterialTheme.typography.bodySmall, color = look.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Section("Стиль") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            kind.styles.forEach { s ->
                                FilterChip(style == s.id, { config = config.copy(style = s.id) }, label = { Text(s.title) })
                            }
                        }
                        if (alignable) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WidgetAlign.entries.forEach { a ->
                                    FilterChip(config.align == a, { config = config.copy(align = a) }, label = { Text(a.title) })
                                }
                            }
                        }
                        if (analog) {
                            SwitchRow("Секундная стрелка", config.showSeconds) { config = config.copy(showSeconds = it) }
                        }
                    }
                }
                if (digital) {
                    item {
                        Section("Шрифт цифр") {
                            DigitFontPicker(
                                selected = config.font?.let { f -> DigitFont.entries.firstOrNull { it.name == f } },
                                styleDefault = defaultDigitFont(style),
                            ) { font -> config = config.copy(font = font?.name) }
                        }
                    }
                }
                if (kind == WidgetKind.APPS) {
                    item { AppsSection(config, onChange = { config = it }, onAdd = { picker = "apps" }) }
                }
                item {
                    Section("Подложка") {
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
                if (kind != WidgetKind.APPS) {
                    item {
                        Section("По нажатию") {
                            val target = config.openApp?.let { AppCatalog.label(context, it) ?: it } ?: defaultTargetTitle(context, kind)
                            Text("Открывается: $target", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { picker = "open" }) { Text("Другое приложение") }
                                if (config.openApp != null) {
                                    TextButton(onClick = { config = config.copy(openApp = null) }) { Text("Стандартное") }
                                }
                            }
                        }
                    }
                }
                if (kind == WidgetKind.WEATHER) {
                    item { Section("Город") { WeatherPlaceEditor { weather = it } } }
                }
                if (kind == WidgetKind.CALENDAR && !calendarAllowed) {
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
            AccentButton("Готово", { onSave(config) }, Modifier.padding(16.dp))
        }
    }

    colorEdit?.let { which ->
        val initialColor = if (which == "text") config.textColor ?: theme.textColor else config.accentColor ?: theme.accentColor
        ColorPickerDialog(if (which == "text") "Цвет текста" else "Акцентный цвет", initialColor, onDismiss = { colorEdit = null }) { c ->
            config = if (which == "text") config.copy(textColor = c) else config.copy(accentColor = c)
            colorEdit = null
        }
    }
    when (picker) {
        "open" -> AppPickerDialog(
            title = "Что открывать", multi = false, selected = listOfNotNull(config.openApp),
            onDismiss = { picker = null },
        ) { picked ->
            config = config.copy(openApp = picked.firstOrNull())
            picker = null
        }
        "apps" -> AppPickerDialog(
            title = "Приложения в папке", multi = true, selected = config.apps,
            onDismiss = { picker = null },
        ) { picked ->
            config = config.copy(apps = picked.take(8))
            picker = null
        }
    }
}

/** Что откроется по нажатию, если пользователь ничего не выбрал. */
private fun defaultTargetTitle(context: android.content.Context, kind: WidgetKind): String = when (kind) {
    WidgetKind.CLOCK -> "будильники"
    WidgetKind.CALENDAR -> "календарь"
    WidgetKind.BATTERY -> "настройки батареи"
    WidgetKind.WEATHER -> when {
        AppCatalog.weatherApp(context)?.component?.packageName == AppCatalog.SAMSUNG_WEATHER -> "Погода Samsung"
        AppCatalog.weatherApp(context) != null -> "приложение погоды"
        else -> "SyncTheme (приложение погоды не найдено)"
    }
    WidgetKind.APPS -> ""
}

@Composable
private fun AppsSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    Section("Приложения") {
        Text(
            "До 8 приложений. На маленьком виджете видно первые 4, на широком и большом — все с подписями.",
            style = MaterialTheme.typography.bodySmall, color = LocalLook.current.muted,
        )
        config.apps.forEachIndexed { i, component ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AppIcon(component, 32)
                Spacer(Modifier.width(10.dp))
                Text(AppCatalog.label(context, component) ?: component, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { onChange(config.copy(apps = config.apps.toMutableList().also { it.removeAt(i) })) }) {
                    Icon(Icons.Default.Close, "Убрать")
                }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = true) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Выбрать приложения")
        }
        OutlinedTextField(
            value = config.title, onValueChange = { onChange(config.copy(title = it.take(24))) }, singleLine = true,
            label = { Text("Подпись папки (необязательно)") }, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppIcon(component: String, sizeDp: Int) {
    val context = LocalContext.current
    val bitmap = remember(component) { AppCatalog.icon(context, component, sizeDp * 3, circle = true)?.asImageBitmap() }
    if (bitmap != null) Image(bitmap, null, modifier = Modifier.size(sizeDp.dp)) else Spacer(Modifier.size(sizeDp.dp))
}

/** Выбор приложений из установленных: одного или нескольких (до 8). */
@Composable
private fun AppPickerDialog(
    title: String,
    multi: Boolean,
    selected: List<String>,
    onDismiss: () -> Unit,
    onPick: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val all = remember { AppCatalog.launchable(context) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { if (multi) TextButton(onClick = { onPick(chosen) }) { Text("Готово (${chosen.size}/8)") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Поиск") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val shown = all.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.component }) { app ->
                        val on = app.component in chosen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (!multi) onPick(listOf(app.component))
                                else chosen = if (on) chosen - app.component else if (chosen.size < 8) chosen + app.component else chosen
                            }.padding(vertical = 6.dp),
                        ) {
                            AppIcon(app.component, 36)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, modifier = Modifier.weight(1f), maxLines = 1)
                            if (multi) Checkbox(checked = on, onCheckedChange = null)
                        }
                    }
                }
            }
        },
    )
}
