package app.synctheme.mobile.ui

import androidx.compose.foundation.background
import app.synctheme.mobile.widget.WidgetSize
import app.synctheme.mobile.widget.WidgetPreview
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.data.WeatherRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
import app.synctheme.core.DigitFont
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.mobile.watch.TimeCheck
import app.synctheme.mobile.watch.TimeCheckResult
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.watch.WatchReport
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.core.FontWeight as ClockFont

// ---------------------------------------------------------------- Обои

fun LazyListScope.wallpaperTab(theme: Theme, a: ScreenActions, lock: Boolean, onLock: (Boolean) -> Unit, editColor: (ColorTarget) -> Unit) {
    item {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            listOf(false to "Рабочий стол", true to "Экран блокировки").forEachIndexed { i, (isLock, label) ->
                SegmentedButton(
                    selected = lock == isLock, onClick = { onLock(isLock) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2), label = { Text(label, maxLines = 1) },
                )
            }
        }
    }
    if (!lock) {
        item {
            val saved by a.vm.saved.collectAsStateWithLifecycle()
            ThemeStrip(saved, theme.id, onPhoto = { a.pickPhoto(BgTarget.HOME) }, onSelect = a.vm::select, onDelete = a.vm::delete)
        }
        item {
            ExpandableSection("Фон", summary = backgroundSummary(theme.background, theme.dim)) {
                BackgroundEditorContent(
                    bg = theme.background, dim = theme.dim,
                    onBg = { b -> a.vm.update { it.copy(background = b) } },
                    onDim = { d -> a.vm.update { it.copy(dim = d) } },
                    onPickPhoto = { a.pickPhoto(BgTarget.HOME) },
                    onEditColor = { editColor(ColorTarget.Gradient(BgTarget.HOME, it)) },
                    onArrange = { a.arrange(ArrangeTarget.HOME) },
                )
            }
        }
        item {
            ExpandableSection("Цвета", summary = "Цифры и акцент — для виджетов и часов", trailing = { SwatchPair(theme.textColor, theme.accentColor) }) {
                ColorsContent(
                    theme.textColor, theme.accentColor, theme.background.colors,
                    onEditText = { editColor(ColorTarget.Text) },
                    onEditAccent = { editColor(ColorTarget.Accent) },
                    onAccent = { c -> a.vm.update { it.copy(accentColor = c) } },
                )
            }
        }
        item {
            ExpandableSection("Часы на обоях", summary = wallClockSummary(theme)) { WallClockContent(theme, a.vm) { a.arrange(ArrangeTarget.HOME) } }
        }

        item {
            ExpandableSection("Сохранить как свою тему", summary = theme.name) {
                NameRow(theme, onRename = { name -> a.vm.update { it.copy(name = name) } }, onSave = a.vm::saveCurrent)
            }
        }
    } else {
        item {
            Section("Экран блокировки") {
                SwitchRow("Свои обои для экрана блокировки", theme.lockBackground != null) { a.vm.setLockSeparate(it) }
                Text(
                    if (theme.lockBackground == null) "Сейчас на экране блокировки те же обои, что и на рабочем столе."
                    else "Обои ниже ставятся только на экран блокировки.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (theme.lockBackground != null) {
            item {
                ExpandableSection("Фон блокировки", summary = backgroundSummary(theme.lockScreenBackground(), theme.lockScreenDim()), initiallyExpanded = true) {
                    BackgroundEditorContent(
                        bg = theme.lockScreenBackground(), dim = theme.lockScreenDim(),
                        onBg = { b -> a.vm.update { it.copy(lockBackground = b) } },
                        onDim = { d -> a.vm.update { it.copy(lockDim = d) } },
                        onPickPhoto = { a.pickPhoto(BgTarget.LOCK) },
                        onEditColor = { editColor(ColorTarget.Gradient(BgTarget.LOCK, it)) },
                    )
                }
            }
        }
        item {
            ExpandableSection(
                "Эффект глубины", summary = if (theme.lockDepth) "Часы за объектом фото" else "Выключен",
                initiallyExpanded = theme.lockDepth,
            ) { LockDepthContent(theme, a.vm) }
        }
        item {
            Text(
                "Часы и дату на экране блокировки рисует сам телефон: их стиль меняется в настройках экрана блокировки " +
                    "(на Samsung — зажать экран блокировки → «Изменить»).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

private fun backgroundSummary(bg: Background, dim: Float): String {
    val type = when (bg.type) {
        BackgroundType.AURORA -> "Аврора"
        BackgroundType.LINEAR -> "Градиент"
        BackgroundType.RADIAL -> "Сияние"
        BackgroundType.SOLID -> "Цвет"
        BackgroundType.PHOTO -> "Фото"
    }
    val colors = if (bg.type == BackgroundType.PHOTO || bg.type == BackgroundType.SOLID) "" else " · цветов: ${bg.colors.size}"
    return "$type$colors · затемнение ${(dim * 100).toInt()}%"
}

@Composable
private fun SwatchPair(text: Int, accent: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        ColorSwatch(text, size = 26.dp) {}
        ColorSwatch(accent, size = 26.dp) {}
    }
}

/** Лента тем: «+ Фото» из галереи, свои темы и готовые. */
@Composable
private fun ThemeStrip(
    saved: List<Theme>, selectedId: String,
    onPhoto: () -> Unit, onSelect: (Theme) -> Unit, onDelete: (Theme) -> Unit,
) {
    val look = LocalLook.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(look.title("Темы"), style = look.sectionTitle, color = look.text)
        Text(
            "Нажмите тему или «Фото» — обои, виджеты и циферблат подстроятся под цвета.",
            style = MaterialTheme.typography.bodySmall, color = look.muted,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "photo") {
                Column(Modifier.width(66.dp).clickable(onClick = onPhoto), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.padding(bottom = 7.dp).size(54.dp, 117.dp).clip(RoundedCornerShape(14.dp)).background(look.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, contentDescription = null, tint = look.onPrimary, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text("Фото", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            items(saved, key = { "s" + it.id }) { t ->
                Box {
                    ThemeThumb(t, t.id == selectedId, onClick = { onSelect(t) })
                    IconButton(
                        onClick = { onDelete(t) },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(Color(0xCC000000)),
                    ) { Icon(Icons.Default.Close, contentDescription = "Удалить", modifier = Modifier.size(14.dp), tint = Color.White) }
                }
            }
            items(Presets.all, key = { it.id }) { t -> ThemeThumb(t, t.id == selectedId, onClick = { onSelect(t) }) }
        }
    }
}

@Composable
private fun NameRow(theme: Theme, onRename: (String) -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = theme.name, onValueChange = onRename, singleLine = true, label = { Text("Название") }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onSave) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Сохранить")
        }
    }
}

// ---------------------------------------------------------------- Виджеты

fun LazyListScope.widgetsTab(theme: Theme, a: ScreenActions) {
    item {
        val look = LocalLook.current
        val context = LocalContext.current
        val weather = remember { WeatherRepository(context).cached }
        val wallpaper = remember(theme.background, theme.dim) { ThemeRenderer.background(context, theme, 360, 360).asImageBitmap() }
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(look.title("Добавить виджет"), style = look.sectionTitle, color = look.text)
            Text(
                "Растяните виджет на рабочем столе — он покажет больше. Стиль, шрифт и цвета — в его настройке.",
                style = MaterialTheme.typography.bodySmall, color = look.muted,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(WidgetKind.entries, key = { it.name }) { kind ->
                    val shape = RoundedCornerShape(look.radius)
                    Column(
                        Modifier.width(186.dp).clip(shape).background(look.card).border(1.dp, look.cardBorder, shape).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(170.dp).clip(RoundedCornerShape(look.radius / 2)), contentAlignment = Alignment.Center) {
                            Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                            WidgetPreview(kind, WidgetConfig(style = kind.defaultStyle, opacity = theme.widgetOpacity), theme, weather, WidgetSize.M)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(kind.title, style = MaterialTheme.typography.titleSmall)
                        Text("стилей: ${kind.styles.size}", style = MaterialTheme.typography.bodySmall, color = look.muted)
                        Spacer(Modifier.height(6.dp))
                        FilledTonalButton(onClick = { a.vm.pinWidget(kind) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить") }
                    }
                }
            }
        }
    }
    item {
        val placed by a.vm.placed.collectAsStateWithLifecycle()
        ExpandableSection("На экране", summary = if (placed.isEmpty()) "Виджетов SyncTheme пока нет" else "Виджетов: ${placed.size}", initiallyExpanded = placed.isNotEmpty()) {
            placed.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(w.kind.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${w.kind.style(w.config.style).title} · подложка ${(w.config.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { a.configureWidget(w.id) }) { Text("Настроить") }
                }
            }
            TextButton(onClick = a.vm::refreshPlaced) { Text("Обновить список") }
        }
    }
    item {
        var opacity by remember(theme.widgetOpacity) { mutableFloatStateOf(theme.widgetOpacity) }
        ExpandableSection("Прозрачность всех виджетов", summary = "Подложка ${(theme.widgetOpacity * 100).toInt()}%") {
            LabeledSlider("Подложка", opacity, 0f..1f, "${(opacity * 100).toInt()}%") { opacity = it }
            Button(onClick = { a.vm.setOpacityForAll(opacity) }) { Text("Применить ко всем виджетам") }
        }
    }
    item {
        val context = LocalContext.current
        val place = remember { WeatherRepository(context).place?.name }
        ExpandableSection("Город для погоды", summary = place ?: "Не выбран") { WeatherPlaceEditor() }
    }
}

// ---------------------------------------------------------------- Часы

fun LazyListScope.watchTab(theme: Theme, a: ScreenActions, editColor: (ColorTarget) -> Unit) {
    val w = theme.watchSettings()
    item {
        val connection by a.vm.connection.collectAsStateWithLifecycle()
        val report by a.vm.watchReport.collectAsStateWithLifecycle()
        val bundled by a.vm.bundledWatchVersion.collectAsStateWithLifecycle()
        val time by a.vm.timeCheck.collectAsStateWithLifecycle()
        val checking by a.vm.timeChecking.collectAsStateWithLifecycle()
        LaunchedEffect(connection.hasApp) { if (connection.hasApp && time == null) a.vm.checkTime() }
        WatchCard(connection, report, bundled, time, checking, a.vm::openOnWatch, a.vm::refreshWatch, a.openInstaller, a.vm::checkTime)
    }
    item {
        val previews by a.vm.stylePreviews.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { a.vm.wantStylePreviews() }
        val look = LocalLook.current
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(look.title("Циферблат"), style = look.sectionTitle, color = look.text)
            Text(CLOCK_STYLE_NOTES.getValue(w.clockStyle), style = MaterialTheme.typography.bodySmall, color = look.muted)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CLOCK_STYLES, key = { it.first.name }) { (style, label) ->
                    val on = style == w.clockStyle
                    Column(
                        Modifier.width(104.dp).clickable { a.editWatchFace { it.copy(clockStyle = style) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(96.dp)
                                .then(if (on) Modifier.glow(look, CircleShape, 10.dp) else Modifier)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(if (on) 3.dp else 1.dp, if (on) look.primary else look.cardBorder, CircleShape),
                        ) {
                            previews[style]?.let { Image(it, contentDescription = label, modifier = Modifier.fillMaxSize()) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }
    if (w.clockStyle.hasDigits) {
        item {
            Section("Шрифт цифр") {
                DigitFontPicker(w.digitFont, w.clockStyle.defaultDigitFont ?: DigitFont.THIN) { font -> a.editWatchFace { it.copy(digitFont = font) } }
            }
        }
    }
    item {
        val parts = listOfNotNull(
            "секунды".takeIf { w.showSeconds }, "дата".takeIf { w.showDate }, "мини-виджеты".takeIf { w.complications },
            "глубина".takeIf { w.depth },
        )
        ExpandableSection("На циферблате", summary = parts.joinToString(" · ").ifEmpty { "только время" }) {
            if (w.clockStyle == ClockStyle.ANALOG) {
                ChipRow {
                    listOf(DialStyle.TICKS to "Деления", DialStyle.NUMBERS to "Цифры", DialStyle.MINIMAL to "Минимал").forEach { (d, label) ->
                        FilterChip(w.dialStyle == d, { a.editWatchFace { it.copy(dialStyle = d) } }, label = { Text(label) })
                    }
                }
            }
            if (w.clockStyle.classic && (w.digitFont == null || w.clockStyle == ClockStyle.ANALOG)) {
                ChipRow {
                    listOf(ClockFont.THIN to "Тонкий", ClockFont.LIGHT to "Лёгкий", ClockFont.REGULAR to "Обычный", ClockFont.BOLD to "Жирный")
                        .forEach { (f, label) ->
                            FilterChip(w.fontWeight == f, { a.editWatchFace { it.copy(fontWeight = f) } }, label = { Text(label) })
                        }
                }
            }
            SwitchRow("Секунды", w.showSeconds) { v -> a.editWatchFace { it.copy(showSeconds = v) } }
            SwitchRow("Дата", w.showDate) { v -> a.editWatchFace { it.copy(showDate = v) } }
            SwitchRow("Мини-виджеты (батарея, шаги)", w.complications) { v -> a.editWatchFace { it.copy(complications = v) } }
            val photo = theme.watchBackground().type == BackgroundType.PHOTO
            SwitchRow("Эффект глубины", w.depth && photo, enabled = photo) { v -> a.vm.setWatchDepth(v) { t -> a.editWatchFace(t) } }
            Text(
                when {
                    !photo -> "Эффект глубины работает с фото на фоне часов."
                    w.clockStyle.movableTime -> "Главный объект фото — человек, питомец, гора — стоит перед временем. Время само встаёт " +
                        "с другой стороны от объекта, так что объект лишь слегка заходит на цифры, а их видно сквозь него."
                    else -> "Главный объект фото стоит перед стрелками. У этого стиля время в центре — если объект мешает, подвиньте фото."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            arrangeLabel(theme, ArrangeTarget.WATCH)?.let { label ->
                OutlinedButton(onClick = { a.arrange(ArrangeTarget.WATCH) }) { Text(label) }
            }
            val ambient by a.vm.ambientPreview.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color.Black)) {
                    ambient?.let { Image(it, contentDescription = "Always-On", modifier = Modifier.fillMaxSize()) }
                }
                Text(
                    "Always-On: у каждого стиля свой контрастный вид — время и дата светлыми цветами на чёрном, без фона и секунд.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    item {
        ExpandableSection(
            "Фон и цвета часов",
            summary = if (theme.watch == null) "Как у телефона" else "Свои настройки для часов",
            trailing = { SwatchPair(w.textColor, w.accentColor) },
        ) {
            SwitchRow("Свои фон и цвета для часов", theme.watch != null) { a.vm.setWatchSeparate(it) }
            val own = theme.watch
            if (own != null) {
                SwitchRow("Фон как на рабочем столе", own.background == null) { same ->
                    a.vm.updateWatch { it.copy(background = if (same) null else theme.background) }
                }
                val ownBackground = own.background
                if (ownBackground != null) {
                    BackgroundEditorContent(
                        bg = ownBackground, dim = own.dim,
                        onBg = { b -> a.vm.updateWatch { it.copy(background = b) } },
                        onDim = { d -> a.vm.updateWatch { it.copy(dim = d) } },
                        onPickPhoto = { a.pickPhoto(BgTarget.WATCH) },
                        onArrange = { a.arrange(ArrangeTarget.WATCH) },
                        onEditColor = { editColor(ColorTarget.Gradient(BgTarget.WATCH, it)) },
                    )
                } else {
                    LabeledSlider("Затемнение", own.dim, 0f..0.8f, "${(own.dim * 100).toInt()}%") { d -> a.vm.updateWatch { it.copy(dim = d) } }
                }
                ColorsContent(
                    own.textColor, own.accentColor, theme.watchBackground().colors,
                    onEditText = { editColor(ColorTarget.WatchText) },
                    onEditAccent = { editColor(ColorTarget.WatchAccent) },
                    onAccent = { c -> a.vm.updateWatch { it.copy(accentColor = c) } },
                )
            } else {
                Text(
                    "Циферблат берёт фон и цвета из темы телефона. Включите переключатель, чтобы настроить часы отдельно.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Ещё

fun LazyListScope.moreTab(theme: Theme, a: ScreenActions, updates: @Composable () -> Unit) {
    item { AppearanceSection(theme.accentColor) }
    item { updates() }
    item {
        val connection by a.vm.connection.collectAsStateWithLifecycle()
        DevicesSection(a.vm, connection.appNodeName ?: connection.connectedNames.firstOrNull(), a.openInstaller)
    }
    item {
        val context = LocalContext.current
        var licenses by remember { mutableStateOf<String?>(null) }
        Section("О приложении") {
            Text("SyncTheme — обои, виджеты и циферблат часов в одном стиле.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                licenses = listOf("licenses/fonts.txt", "licenses/fonts-digits.txt").joinToString("\n\n") { path ->
                    runCatching { context.assets.open(path).bufferedReader().readText() }.getOrDefault("")
                }
            }) { Text("Лицензии шрифтов") }
        }
        licenses?.let { text ->
            AlertDialog(
                onDismissRequest = { licenses = null },
                confirmButton = { TextButton(onClick = { licenses = null }) { Text("Закрыть") } },
                title = { Text("Лицензии шрифтов") },
                text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            )
        }
    }
}

// ---------------------------------------------------------------- общие блоки

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Редактор фона: тип, цвета градиента, направление, фото и затемнение. */
@Composable
private fun BackgroundEditorContent(
    bg: Background,
    dim: Float,
    onBg: (Background) -> Unit,
    onDim: (Float) -> Unit,
    onPickPhoto: () -> Unit,
    onEditColor: (Int) -> Unit,
    onArrange: (() -> Unit)? = null,
) {
    run {
        ChipRow {
            listOf(
                BackgroundType.AURORA to "Аврора", BackgroundType.LINEAR to "Градиент",
                BackgroundType.RADIAL to "Сияние", BackgroundType.SOLID to "Цвет", BackgroundType.PHOTO to "Фото",
            ).forEach { (type, label) ->
                FilterChip(
                    selected = bg.type == type,
                    onClick = { if (type == BackgroundType.PHOTO && bg.photoFile == null) onPickPhoto() else onBg(bg.copy(type = type)) },
                    label = { Text(label) },
                )
            }
        }
        if (bg.type == BackgroundType.PHOTO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickPhoto) { Text(if (bg.photoFile == null) "Выбрать фото" else "Другое фото") }
                if (bg.photoFile != null && onArrange != null) OutlinedButton(onClick = onArrange) { Text("Подвинуть фото") }
            }
        } else {
            val count = if (bg.type == BackgroundType.SOLID) 1 else bg.colors.size
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                bg.colors.take(count).forEachIndexed { i, c -> ColorSwatch(c) { onEditColor(i) } }
                if (bg.type != BackgroundType.SOLID && bg.colors.size < 4) {
                    IconButton(onClick = { onEditColor(bg.colors.size) }) { Icon(Icons.Default.Add, "Добавить цвет") }
                }
                if (bg.type != BackgroundType.SOLID && bg.colors.size > 2) {
                    IconButton(onClick = { onBg(bg.copy(colors = bg.colors.dropLast(1))) }) { Icon(Icons.Default.Close, "Убрать цвет") }
                }
            }
            if (bg.type == BackgroundType.LINEAR) {
                LabeledSlider("Направление", bg.angle, 0f..360f, "${bg.angle.toInt()}°") { onBg(bg.copy(angle = it)) }
            }
        }
        LabeledSlider("Затемнение", dim, 0f..0.8f, "${(dim * 100).toInt()}%", onDim)
    }
}

@Composable
private fun ColorsContent(
    text: Int, accent: Int, bgColors: List<Int>,
    onEditText: () -> Unit, onEditAccent: () -> Unit, onAccent: (Int) -> Unit,
) {
    run {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ColorSwatch(text, size = 48.dp, onClick = onEditText)
                Text("Цифры", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ColorSwatch(accent, size = 48.dp, onClick = onEditAccent)
                Text("Акцент", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("Быстрый акцент", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val suggestions = (bgColors + Presets.all.map { it.accentColor }).distinct().take(12)
        ChipRow { suggestions.forEach { c -> ColorSwatch(c, size = 32.dp, selected = c == accent) { onAccent(c) } } }
    }
}

/** Стили циферблата: сначала «живые», потом классические. */
private val CLOCK_STYLES = listOf(
    ClockStyle.DRUM to "Барабан", ClockStyle.COLUMNS to "Колонки", ClockStyle.PLANETS to "Планеты",
    ClockStyle.MATRIX to "Матрица", ClockStyle.LIQUID to "Заливка", ClockStyle.DIAGONAL to "Диагональ",
    ClockStyle.BOLD to "Крупно", ClockStyle.ORBIT to "Орбита", ClockStyle.NEON to "Неон", ClockStyle.BLOB to "Облако",
    ClockStyle.GLASS to "Стекло", ClockStyle.MINIMAL to "Минимал",
    ClockStyle.DIGITAL to "Классика", ClockStyle.STACKED to "Вертикально", ClockStyle.ANALOG to "Стрелки",
)

private val CLOCK_STYLE_NOTES = mapOf(
    ClockStyle.DRUM to "Кольца с цифрами часов и минут вращаются, текущее время проходит через окошко справа.",
    ClockStyle.COLUMNS to "Час и минута столбиками — с прошлым и следующим значением; секунды растут шкалой между ними.",
    ClockStyle.PLANETS to "Часы и минуты — шары на своих орбитах, секунды — спутник с хвостом.",
    ClockStyle.MATRIX to "Точечные цифры и 60 огоньков секунд: прошедшие загораются.",
    ClockStyle.LIQUID to "За минуту циферблат наполняется «жидкостью» с волной и пузырьками.",
    ClockStyle.DIAGONAL to "Часы слева сверху, минуты справа снизу, секунда бежит по диагонали.",
    ClockStyle.BOLD to "Огромные узкие цифры: часы над минутами, секунды бегут по краю.",
    ClockStyle.ORBIT to "Три кольца заполняются вместе со временем: часы, минуты и секунды с бегущими точками.",
    ClockStyle.NEON to "Светящееся кольцо, секунды — «комета» с хвостом, время с цветным ореолом.",
    ClockStyle.BLOB to "Толстые стрелки на «печенье» цвета акцента.",
    ClockStyle.GLASS to "Матовый стеклянный диск, по его краю бегут секунды.",
    ClockStyle.MINIMAL to "Тонкие цифры и одна точка цвета акцента, которая обходит круг за час.",
    ClockStyle.DIGITAL to "Цифровые часы в одну строку.",
    ClockStyle.STACKED to "Часы над минутами.",
    ClockStyle.ANALOG to "Стрелки с делениями, цифрами или в минимальном виде.",
)

/**
 * Карточка часов: связь, установка и обновление приложения, точность времени — коротко,
 * подробности по кнопке.
 */
@Composable
private fun WatchCard(
    connection: WatchConnection,
    report: WatchReport,
    bundledVersion: Long,
    time: TimeCheckResult?,
    checking: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onCheckTime: () -> Unit,
) {
    var details by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Section("Часы") {
        val info = when {
            !connection.checked -> "Проверяю подключение…"
            connection.error != null -> "Не удаётся связаться с часами: на телефоне не работают сервисы Wear OS. " +
                "Установите или обновите приложение часов (Galaxy Wearable, Pixel Watch или Wear OS) и сервисы Google Play."
            !connection.hasWatch -> "Часы не подключены. Проверьте Bluetooth и приложение часов (Galaxy Wearable, Pixel Watch или Wear OS)."
            !connection.hasApp -> "На часах ${connection.connectedNames.joinToString()} ещё нет SyncTheme."
            else -> "${connection.appNodeName}: циферблат обновится по кнопке «Применить»."
        }
        Text(info, style = MaterialTheme.typography.bodyMedium)
        val status = if (report.state == "SENT") "Отправлено «${report.theme}», ждём ответ часов…" else report.text
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        val updateAvailable = connection.hasApp && report.appVersion in 1 until bundledVersion
        if (!connection.hasApp || updateAvailable) {
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Text(if (updateAvailable) "Обновить SyncTheme на часах" else "Установить SyncTheme на часы")
            }
        }
        if (connection.hasApp) {
            val (line, warn) = timeLine(time, checking)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = if (warn) MaterialTheme.colorScheme.error else LocalLook.current.text, modifier = Modifier.weight(1f))
                if (warn || time?.watchSilent == true) TextButton(onClick = { details = !details }) { Text(if (details) "Скрыть" else "Как исправить") }
            }
            if (details && time != null) TimeAdvice(time, muted)
        }
        // Кнопки переносятся целиком, а не по словам, если в строку не помещаются.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            FilledTonalButton(onClick = onOpen, enabled = connection.hasApp) { Text("Открыть на часах", maxLines = 1) }
            OutlinedButton(onClick = onCheckTime, enabled = connection.hasApp && !checking) { Text("Проверить время", maxLines = 1) }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Обновить") }
        }
    }
}

/** Одна строка о точности времени и признак, что есть расхождение. */
private fun timeLine(result: TimeCheckResult?, checking: Boolean): Pair<String, Boolean> {
    if (checking) return "Сверяю время часов…" to false
    if (result == null) return "Время ещё не проверялось" to false
    if (result.watchSilent) return "Время: часы не ответили (нужна SyncTheme 1.3 на часах)" to false
    val watch = result.watchMs ?: return "Время: часы не на связи" to false
    val phone = result.phoneMs
    return when {
        kotlin.math.abs(watch) >= TimeCheck.NOTICEABLE_MS ->
            (if (watch > 0) "Часы спешат на ${TimeCheck.seconds(watch)}" else "Часы отстают на ${TimeCheck.seconds(watch)}") to true
        phone != null && kotlin.math.abs(phone) >= TimeCheck.NOTICEABLE_MS ->
            (if (phone > 0) "Телефон и часы спешат на ${TimeCheck.seconds(phone)}" else "Телефон и часы отстают на ${TimeCheck.seconds(phone)}") to true
        else -> "Время точное ✓" to false
    }
}

@Composable
private fun TimeAdvice(result: TimeCheckResult, muted: Color) {
    val watch = result.watchMs
    val phone = result.phoneMs
    if (result.watchSilent) {
        Text("Проверка времени появилась в SyncTheme для часов 1.3 — обновите приложение на часах.", style = MaterialTheme.typography.bodySmall, color = muted)
    }
    if (watch != null && kotlin.math.abs(watch) >= TimeCheck.NOTICEABLE_MS) {
        Text(
            "Циферблат показывает время часов, а его ставит система. На часах: «Настройки» → «Общие» (или «Система») → " +
                "«Дата и время» → включите «Автоматически» (в SyncTheme на часах есть кнопка «Настройки времени»). " +
                "Затем переподключите часы: выключите и включите Bluetooth на телефоне.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
    if (phone != null && kotlin.math.abs(phone) >= TimeCheck.NOTICEABLE_MS) {
        Text(
            "Часы берут время с телефона. На телефоне: «Настройки» → «Общее управление» (или «Система») → «Дата и время» → " +
                "включите «Автоматические дата и время».",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
}
