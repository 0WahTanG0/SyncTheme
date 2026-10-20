package app.synctheme.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.watch.WatchReport
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.core.FontWeight as ClockFont

// ---------------------------------------------------------------- Тема

fun LazyListScope.themeTab(theme: Theme, a: ScreenActions, editColor: (ColorTarget) -> Unit) {
    item {
        val saved by a.vm.saved.collectAsStateWithLifecycle()
        PresetRow(Presets.all, saved, theme.id, a.vm::select, a.vm::delete)
    }
    item {
        Section("Обои из галереи") {
            Text(
                "Выберите фото — цифры, акцент, виджеты и циферблат часов подстроятся под его цвета.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { a.pickPhoto(BgTarget.HOME) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать фото") }
        }
    }
    item { NameSection(theme, onRename = { name -> a.vm.update { it.copy(name = name) } }, onSave = a.vm::saveCurrent) }
    item {
        BackgroundEditor(
            title = "Фон рабочего стола", bg = theme.background, dim = theme.dim,
            onBg = { b -> a.vm.update { it.copy(background = b) } },
            onDim = { d -> a.vm.update { it.copy(dim = d) } },
            onPickPhoto = { a.pickPhoto(BgTarget.HOME) },
            onEditColor = { editColor(ColorTarget.Gradient(BgTarget.HOME, it)) },
        )
    }
    item {
        ColorsSection(
            "Цвета", theme.textColor, theme.accentColor, theme.background.colors,
            onEditText = { editColor(ColorTarget.Text) },
            onEditAccent = { editColor(ColorTarget.Accent) },
            onAccent = { c -> a.vm.update { it.copy(accentColor = c) } },
        )
    }
    item {
        ClockStyleSection(
            title = "Стиль часов",
            note = if (theme.watch == null) "Используется на циферблате часов и как стиль по умолчанию для виджетов." else null,
            style = theme.clockStyle, dial = theme.dialStyle, font = theme.fontWeight,
            seconds = theme.showSeconds, date = theme.showDate, complications = theme.watchComplications,
            onChange = { s, d, f, sec, dt, c ->
                a.vm.update { it.copy(clockStyle = s, dialStyle = d, fontWeight = f, showSeconds = sec, showDate = dt, watchComplications = c) }
            },
        )
    }
}

// ---------------------------------------------------------------- Виджеты

fun LazyListScope.widgetsTab(theme: Theme, a: ScreenActions) {
    item {
        Section("Добавить виджет") {
            Text(
                "После добавления откроется настройка: стиль, расположение, шрифт, подложка и цвета. " +
                    "Изменить позже: зажмите виджет → «Настройки» или кнопка ниже.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WidgetKind.entries.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            kind.description + " · стилей: ${kind.styles.size}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { a.vm.pinWidget(kind) }) { Text("Добавить") }
                }
            }
        }
    }
    item {
        val placed by a.vm.placed.collectAsStateWithLifecycle()
        Section("На экране") {
            if (placed.isEmpty()) {
                Text("Виджетов SyncTheme пока нет.", style = MaterialTheme.typography.bodyMedium)
            }
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
        Section("Прозрачность всех виджетов") {
            LabeledSlider("Подложка", opacity, 0f..1f, "${(opacity * 100).toInt()}%") { opacity = it }
            Button(onClick = { a.vm.setOpacityForAll(opacity) }) { Text("Применить ко всем виджетам") }
        }
    }
    item { Section("Погода") { WeatherPlaceEditor() } }
    item {
        Section("Экран блокировки") {
            Text(
                "Виджеты SyncTheme можно поставить и на экран блокировки, если это позволяет One UI: " +
                    "«Настройки» → «Экран блокировки» → «Виджеты».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- Блокировка

fun LazyListScope.lockTab(theme: Theme, a: ScreenActions, editColor: (ColorTarget) -> Unit) {
    item {
        Section("Экран блокировки") {
            SwitchRow("Отдельные обои для экрана блокировки", theme.lockBackground != null) { a.vm.setLockSeparate(it) }
            Text(
                if (theme.lockBackground == null) "Сейчас на экране блокировки те же обои, что и на рабочем столе."
                else "Обои ниже ставятся только на экран блокировки.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (theme.lockBackground != null) {
        item {
            BackgroundEditor(
                title = "Фон экрана блокировки", bg = theme.lockScreenBackground(), dim = theme.lockScreenDim(),
                onBg = { b -> a.vm.update { it.copy(lockBackground = b) } },
                onDim = { d -> a.vm.update { it.copy(lockDim = d) } },
                onPickPhoto = { a.pickPhoto(BgTarget.LOCK) },
                onEditColor = { editColor(ColorTarget.Gradient(BgTarget.LOCK, it)) },
            )
        }
    }
    item {
        Section("Часы на экране блокировки") {
            Text(
                "Часы и дату на экране блокировки рисует One UI. Их стиль и цвет меняются в " +
                    "«Настройки» → «Экран блокировки» → «Изменить». SyncTheme меняет обои под ними.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- Часы

fun LazyListScope.watchTab(theme: Theme, a: ScreenActions, editColor: (ColorTarget) -> Unit) {
    item {
        val connection by a.vm.connection.collectAsStateWithLifecycle()
        val report by a.vm.watchReport.collectAsStateWithLifecycle()
        val bundled by a.vm.bundledWatchVersion.collectAsStateWithLifecycle()
        WatchSection(connection, report, bundled, a.vm::openOnWatch, a.vm::refreshWatch, a.openInstaller)
    }
    item {
        Section("Циферблат") {
            SwitchRow("Настроить часы отдельно", theme.watch != null) { a.vm.setWatchSeparate(it) }
            Text(
                if (theme.watch == null) "Сейчас циферблат повторяет стиль телефона: фон, цвета и часы со вкладки «Тема»."
                else "Настройки ниже действуют только на часы.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val w = theme.watch ?: return
    item {
        Section("Фон циферблата") {
            SwitchRow("Как на рабочем столе телефона", w.background == null) { same ->
                a.vm.updateWatch { it.copy(background = if (same) null else theme.background) }
            }
        }
    }
    val ownBackground = w.background
    if (ownBackground != null) {
        item {
            BackgroundEditor(
                title = "Свой фон часов", bg = ownBackground, dim = w.dim,
                onBg = { b -> a.vm.updateWatch { it.copy(background = b) } },
                onDim = { d -> a.vm.updateWatch { it.copy(dim = d) } },
                onPickPhoto = { a.pickPhoto(BgTarget.WATCH) },
                onEditColor = { editColor(ColorTarget.Gradient(BgTarget.WATCH, it)) },
            )
        }
    } else {
        item {
            Section("Затемнение фона") {
                LabeledSlider("Затемнение", w.dim, 0f..0.8f, "${(w.dim * 100).toInt()}%") { d -> a.vm.updateWatch { it.copy(dim = d) } }
            }
        }
    }
    item {
        ColorsSection(
            "Цвета циферблата", w.textColor, w.accentColor, theme.watchBackground().colors,
            onEditText = { editColor(ColorTarget.WatchText) },
            onEditAccent = { editColor(ColorTarget.WatchAccent) },
            onAccent = { c -> a.vm.updateWatch { it.copy(accentColor = c) } },
        )
    }
    item {
        ClockStyleSection(
            title = "Часы на циферблате", note = null,
            style = w.clockStyle, dial = w.dialStyle, font = w.fontWeight,
            seconds = w.showSeconds, date = w.showDate, complications = w.complications,
            onChange = { s, d, f, sec, dt, c ->
                a.vm.updateWatch { it.copy(clockStyle = s, dialStyle = d, fontWeight = f, showSeconds = sec, showDate = dt, complications = c) }
            },
        )
    }
}

// ---------------------------------------------------------------- общие блоки

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun PresetRow(
    presets: List<Theme>, saved: List<Theme>, selectedId: String,
    onSelect: (Theme) -> Unit, onDelete: (Theme) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("Готовые темы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.id }) { t -> ThemeThumb(t, t.id == selectedId, onClick = { onSelect(t) }) }
        }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Мои темы", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(saved, key = { it.id }) { t ->
                    Box {
                        ThemeThumb(t, t.id == selectedId, onClick = { onSelect(t) })
                        IconButton(
                            onClick = { onDelete(t) },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                .clip(CircleShape).background(Color(0xCC000000)),
                        ) { Icon(Icons.Default.Close, contentDescription = "Удалить", modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameSection(theme: Theme, onRename: (String) -> Unit, onSave: () -> Unit) {
    Section("Название") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = theme.name, onValueChange = onRename, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onSave) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Сохранить")
            }
        }
    }
}

/** Редактор фона: тип, цвета градиента, направление, фото и затемнение. */
@Composable
private fun BackgroundEditor(
    title: String,
    bg: Background,
    dim: Float,
    onBg: (Background) -> Unit,
    onDim: (Float) -> Unit,
    onPickPhoto: () -> Unit,
    onEditColor: (Int) -> Unit,
) {
    Section(title) {
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
            OutlinedButton(onClick = onPickPhoto) { Text(if (bg.photoFile == null) "Выбрать фото" else "Другое фото") }
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
private fun ColorsSection(
    title: String, text: Int, accent: Int, bgColors: List<Int>,
    onEditText: () -> Unit, onEditAccent: () -> Unit, onAccent: (Int) -> Unit,
) {
    Section(title) {
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

@Composable
private fun ClockStyleSection(
    title: String,
    note: String?,
    style: ClockStyle,
    dial: DialStyle,
    font: ClockFont,
    seconds: Boolean,
    date: Boolean,
    complications: Boolean,
    onChange: (ClockStyle, DialStyle, ClockFont, Boolean, Boolean, Boolean) -> Unit,
) {
    Section(title) {
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ChipRow {
            listOf(ClockStyle.DIGITAL to "Цифровые", ClockStyle.STACKED to "Вертикальные", ClockStyle.ANALOG to "Стрелочные")
                .forEach { (s, label) ->
                    FilterChip(style == s, { onChange(s, dial, font, seconds, date, complications) }, label = { Text(label) })
                }
        }
        if (style == ClockStyle.ANALOG) {
            ChipRow {
                listOf(DialStyle.TICKS to "Деления", DialStyle.NUMBERS to "Цифры", DialStyle.MINIMAL to "Минимал")
                    .forEach { (d, label) ->
                        FilterChip(dial == d, { onChange(style, d, font, seconds, date, complications) }, label = { Text(label) })
                    }
            }
        }
        ChipRow {
            listOf(ClockFont.THIN to "Тонкий", ClockFont.LIGHT to "Лёгкий", ClockFont.REGULAR to "Обычный", ClockFont.BOLD to "Жирный")
                .forEach { (f, label) ->
                    FilterChip(font == f, { onChange(style, dial, f, seconds, date, complications) }, label = { Text(label) })
                }
        }
        SwitchRow("Секунды", seconds) { onChange(style, dial, font, it, date, complications) }
        SwitchRow("Дата", date) { onChange(style, dial, font, seconds, it, complications) }
        SwitchRow("Мини-виджеты на часах (батарея, шаги)", complications) { onChange(style, dial, font, seconds, date, it) }
    }
}

@Composable
private fun WatchSection(
    connection: WatchConnection,
    report: WatchReport,
    bundledVersion: Long,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
) {
    Section("Galaxy Watch") {
        val info = when {
            !connection.checked -> "Проверяю подключение…"
            connection.error != null -> "Сервисы Wear OS недоступны: ${connection.error}"
            !connection.hasWatch -> "Часы не подключены. Проверьте Bluetooth и приложение Galaxy Wearable."
            !connection.hasApp -> "На часах ${connection.connectedNames.joinToString()} ещё нет SyncTheme — установите его отсюда."
            else -> "${connection.appNodeName}: циферблат обновится по кнопке «Применить»."
        }
        Text(info, style = MaterialTheme.typography.bodyMedium)
        val status = if (report.state == "SENT") "Отправлено «${report.theme}», ждём ответ часов…" else report.text
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        val updateAvailable = connection.hasApp && report.appVersion in 1 until bundledVersion
        if (updateAvailable) {
            Text("Доступна новая версия приложения для часов.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (!connection.hasApp || updateAvailable) {
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Text(if (updateAvailable) "Обновить SyncTheme на часах" else "Установить SyncTheme на часы")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onOpen, enabled = connection.hasApp) { Text("Открыть на часах") }
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Обновить")
            }
        }
        if (connection.hasApp) {
            TextButton(onClick = onInstall) { Text("Подключить другие часы / переустановить") }
        }
    }
}
