package app.synctheme.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockStyle
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.watch.WatchReport
import app.synctheme.mobile.widget.AnalogClockWidget
import app.synctheme.mobile.widget.BatteryWidget
import app.synctheme.mobile.widget.ClockWidget
import app.synctheme.mobile.widget.DateWidget
import app.synctheme.core.FontWeight as ClockFont

/** Что редактируется в диалоге выбора цвета. */
private sealed interface ColorTarget {
    data object Text : ColorTarget
    data object Accent : ColorTarget
    data class Gradient(val index: Int) : ColorTarget
}

@Composable
fun SyncThemeScreen(vm: MainViewModel, onOpenInstaller: () -> Unit = {}) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val phonePreview by vm.phonePreview.collectAsStateWithLifecycle()
    val watchPreview by vm.watchPreview.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val report by vm.watchReport.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val bundledWatchVersion by vm.bundledWatchVersion.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::pickPhoto)
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    AppTheme(theme.accentColor) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { ApplyBar(targets, busy, vm::setTargets, vm::apply) },
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            ) {
                // Шапка и превью закреплены — настройки прокручиваются под ними, изменения видны сразу.
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Header(connection, onOpenInstaller)
                    PreviewRow(phonePreview, watchPreview)
                }
                HorizontalDivider(color = Color(0xFF24242C))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                ) {
                item {
                    PresetRow(
                        presets = Presets.all, saved = saved, selectedId = theme.id,
                        onSelect = vm::select, onDelete = vm::delete,
                    )
                }
                item { NameSection(theme, onRename = { name -> vm.update { it.copy(name = name) } }, onSave = vm::saveCurrent) }
                item {
                    BackgroundSection(
                        theme = theme,
                        onUpdate = vm::update,
                        onPickPhoto = pickPhoto,
                        onEditColor = { colorTarget = ColorTarget.Gradient(it) },
                    )
                }
                item {
                    ColorsSection(
                        theme = theme,
                        onEditText = { colorTarget = ColorTarget.Text },
                        onEditAccent = { colorTarget = ColorTarget.Accent },
                        onAccent = { c -> vm.update { it.copy(accentColor = c) } },
                    )
                }
                item { ClockSection(theme, vm::update) }
                item {
                    WidgetsSection(
                        theme = theme,
                        onOpacity = { o -> vm.update { it.copy(widgetOpacity = o) } },
                        onPin = vm::pinWidget,
                    )
                }
                item {
                    WatchSection(
                        connection, report, bundledWatchVersion,
                        onOpen = vm::openOnWatch, onRefresh = vm::refreshWatch, onInstall = onOpenInstaller,
                    )
                }
                item { UpdateSection(vm) }
                }
            }
        }

        colorTarget?.let { target ->
            val (title, initial) = when (target) {
                ColorTarget.Text -> "Цвет цифр" to theme.textColor
                ColorTarget.Accent -> "Акцентный цвет" to theme.accentColor
                is ColorTarget.Gradient -> "Цвет фона" to (theme.background.colors.getOrNull(target.index) ?: 0xFF000000.toInt())
            }
            ColorPickerDialog(title, initial, onDismiss = { colorTarget = null }) { c ->
                vm.update {
                    when (target) {
                        ColorTarget.Text -> it.copy(textColor = c)
                        ColorTarget.Accent -> it.copy(accentColor = c)
                        is ColorTarget.Gradient -> it.copy(
                            background = it.background.copy(
                                colors = it.background.colors.toMutableList().also { list ->
                                    if (target.index < list.size) list[target.index] = c else list += c
                                },
                            ),
                        )
                    }
                }
                colorTarget = null
            }
        }
    }
}

@Composable
private fun Header(connection: WatchConnection, onOpenInstaller: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SyncTheme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        val (dot, text) = when {
            !connection.checked -> Color.Gray to "Ищу часы…"
            connection.hasApp -> Color(0xFF4CD964) to "Часы на связи"
            connection.hasWatch -> Color(0xFFFFCC00) to "Установить на часы"
            else -> Color(0xFFFF3B30) to "Часы не найдены"
        }
        AssistChip(
            onClick = { if (connection.hasWatch && !connection.hasApp) onOpenInstaller() },
            label = { Text(text, maxLines = 1) },
            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)) },
        )
    }
}

@Composable
private fun PreviewRow(phone: ImageBitmap?, watch: ImageBitmap?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(230.dp)
                .aspectRatio(540f / 1170f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .border(3.dp, Color(0xFF2A2A33), RoundedCornerShape(22.dp)),
        ) {
            phone?.let {
                Image(
                    it, contentDescription = "Превью телефона",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Корпус часов: рамка + круглый экран, картинка всегда растягивается на весь экран.
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A33))
                .padding(7.dp)
                .clip(CircleShape)
                .background(Color.Black),
        ) {
            watch?.let {
                Image(
                    it, contentDescription = "Превью часов",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
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
            OutlinedTextField(
                value = theme.name, onValueChange = onRename, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onSave) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun BackgroundSection(
    theme: Theme,
    onUpdate: ((Theme) -> Theme) -> Unit,
    onPickPhoto: () -> Unit,
    onEditColor: (Int) -> Unit,
) {
    val bg = theme.background
    Section("Фон") {
        ChipRow {
            listOf(
                BackgroundType.AURORA to "Аврора", BackgroundType.LINEAR to "Градиент",
                BackgroundType.RADIAL to "Сияние", BackgroundType.SOLID to "Цвет", BackgroundType.PHOTO to "Фото",
            ).forEach { (type, label) ->
                FilterChip(
                    selected = bg.type == type,
                    onClick = {
                        if (type == BackgroundType.PHOTO && bg.photoFile == null) onPickPhoto()
                        else onUpdate { it.copy(background = it.background.copy(type = type)) }
                    },
                    label = { Text(label) },
                )
            }
        }
        if (bg.type == BackgroundType.PHOTO) {
            OutlinedButton(onClick = onPickPhoto) { Text(if (bg.photoFile == null) "Выбрать фото" else "Другое фото") }
            Text(
                "Цвета подобраны по фото автоматически — их можно поменять ниже.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val count = if (bg.type == BackgroundType.SOLID) 1 else bg.colors.size
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                bg.colors.take(count).forEachIndexed { i, c -> ColorSwatch(c) { onEditColor(i) } }
                if (bg.type != BackgroundType.SOLID && bg.colors.size < 4) {
                    IconButton(onClick = { onEditColor(bg.colors.size) }) { Icon(Icons.Default.Add, "Добавить цвет") }
                }
                if (bg.type != BackgroundType.SOLID && bg.colors.size > 2) {
                    IconButton(onClick = {
                        onUpdate { it.copy(background = it.background.copy(colors = it.background.colors.dropLast(1))) }
                    }) { Icon(Icons.Default.Close, "Убрать цвет") }
                }
            }
            if (bg.type == BackgroundType.LINEAR) {
                LabeledSlider("Направление", bg.angle, 0f..360f, "${bg.angle.toInt()}°") { a ->
                    onUpdate { it.copy(background = it.background.copy(angle = a)) }
                }
            }
        }
        LabeledSlider("Затемнение", theme.dim, 0f..0.8f, "${(theme.dim * 100).toInt()}%") { d ->
            onUpdate { it.copy(dim = d) }
        }
    }
}

@Composable
private fun ColorsSection(theme: Theme, onEditText: () -> Unit, onEditAccent: () -> Unit, onAccent: (Int) -> Unit) {
    Section("Цвета") {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ColorSwatch(theme.textColor, size = 48.dp, onClick = onEditText)
                Text("Цифры", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ColorSwatch(theme.accentColor, size = 48.dp, onClick = onEditAccent)
                Text("Акцент", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("Быстрый акцент", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val suggestions = (theme.background.colors + Presets.all.map { it.accentColor }).distinct().take(12)
        ChipRow { suggestions.forEach { c -> ColorSwatch(c, size = 32.dp, selected = c == theme.accentColor) { onAccent(c) } } }
    }
}

@Composable
private fun ClockSection(theme: Theme, onUpdate: ((Theme) -> Theme) -> Unit) {
    Section("Часы") {
        ChipRow {
            listOf(ClockStyle.DIGITAL to "Цифровые", ClockStyle.STACKED to "Вертикальные", ClockStyle.ANALOG to "Стрелочные")
                .forEach { (style, label) ->
                    FilterChip(theme.clockStyle == style, onClick = { onUpdate { it.copy(clockStyle = style) } }, label = { Text(label) })
                }
        }
        ChipRow {
            listOf(ClockFont.THIN to "Тонкий", ClockFont.LIGHT to "Лёгкий", ClockFont.REGULAR to "Обычный", ClockFont.BOLD to "Жирный")
                .forEach { (w, label) ->
                    FilterChip(theme.fontWeight == w, onClick = { onUpdate { it.copy(fontWeight = w) } }, label = { Text(label) })
                }
        }
        SwitchRow("Секунды", theme.showSeconds) { v -> onUpdate { it.copy(showSeconds = v) } }
        SwitchRow("Дата", theme.showDate) { v -> onUpdate { it.copy(showDate = v) } }
        SwitchRow("Мини-виджеты на часах (батарея, шаги)", theme.watchComplications) { v ->
            onUpdate { it.copy(watchComplications = v) }
        }
    }
}

@Composable
private fun WidgetsSection(theme: Theme, onOpacity: (Float) -> Unit, onPin: (Class<out app.synctheme.mobile.widget.ThemedWidget>) -> Unit) {
    Section("Виджеты на телефоне") {
        LabeledSlider("Подложка", theme.widgetOpacity, 0f..1f, "${(theme.widgetOpacity * 100).toInt()}%", onOpacity)
        Text("Добавить на рабочий стол:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPin(ClockWidget::class.java) }, modifier = Modifier.weight(1f)) { Text("Часы") }
            OutlinedButton(onClick = { onPin(AnalogClockWidget::class.java) }, modifier = Modifier.weight(1f)) { Text("Стрелки") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPin(DateWidget::class.java) }, modifier = Modifier.weight(1f)) { Text("Дата") }
            OutlinedButton(onClick = { onPin(BatteryWidget::class.java) }, modifier = Modifier.weight(1f)) { Text("Батарея") }
        }
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
    Section("Часы") {
        val info = when {
            !connection.checked -> "Проверяю подключение…"
            connection.error != null -> "Сервисы Wear OS недоступны: ${connection.error}"
            !connection.hasWatch -> "Часы не подключены. Проверьте Bluetooth и приложение Galaxy Wearable."
            !connection.hasApp -> "На часах ${connection.connectedNames.joinToString()} ещё нет SyncTheme — установите его отсюда."
            else -> "Циферблат обновится автоматически по кнопке «Применить»."
        }
        Text(info, style = MaterialTheme.typography.bodyMedium)
        val status = if (report.state == "SENT") "Отправлено «${report.theme}», ждём ответ часов…" else report.text
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        val updateAvailable = connection.hasApp && report.appVersion in 1 until bundledVersion
        if (updateAvailable) {
            Text(
                "Доступна новая версия приложения для часов.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )
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

@Composable
private fun ApplyBar(targets: ApplyTargets, busy: String?, onTargets: (ApplyTargets) -> Unit, onApply: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = Color(0xFF15151B)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            ChipRow {
                FilterChip(targets.homeWallpaper, { onTargets(targets.copy(homeWallpaper = !targets.homeWallpaper)) }, label = { Text("Рабочий стол") })
                FilterChip(targets.lockWallpaper, { onTargets(targets.copy(lockWallpaper = !targets.lockWallpaper)) }, label = { Text("Экран блокировки") })
                FilterChip(targets.widgets, { onTargets(targets.copy(widgets = !targets.widgets)) }, label = { Text("Виджеты") })
                FilterChip(targets.watch, { onTargets(targets.copy(watch = !targets.watch)) }, label = { Text("Часы") })
            }
            Spacer(Modifier.height(8.dp))
            if (busy != null) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Button(
                    onClick = onApply,
                    enabled = targets.any,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(),
                ) { Text("Применить", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun UpdateSection(vm: MainViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editRepo by remember { mutableStateOf(false) }
    var repoText by remember(state.repo) { mutableStateOf(state.repo) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::installFromFile)
    }
    Section("Обновления") {
        Text("Установлена версия ${state.currentName} (${state.currentCode})", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Новая версия ставится поверх текущей: темы, сопряжение с часами и настройки сохраняются.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.available?.let { info ->
            Text("Доступна версия ${info.versionName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodySmall)
            Button(onClick = vm::downloadAndInstall, enabled = state.progress == null, modifier = Modifier.fillMaxWidth()) {
                Text("Скачать и обновить")
            }
        }
        state.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.needsPermission) {
            FilledTonalButton(onClick = { context.startActivity(vm.installPermissionIntent()) }) {
                Text("Разрешить установку обновлений")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.checkUpdates() }, enabled = state.repo.isNotBlank() && !state.checking) {
                Text(if (state.checking) "Проверяю…" else "Проверить")
            }
            OutlinedButton(onClick = {
                filePicker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream"))
            }) { Text("Из файла APK") }
        }
        if (state.repo.isBlank() || editRepo) {
            OutlinedTextField(
                value = repoText, onValueChange = { repoText = it }, singleLine = true,
                label = { Text("Источник: GitHub owner/repo") }, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.setUpdateRepo(repoText); editRepo = false }) { Text("Сохранить источник") }
        } else {
            TextButton(onClick = { editRepo = true }) { Text("Источник: github.com/${state.repo}") }
        }
    }
}
