package app.synctheme.mobile.ui

import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.widget.WidgetConfigActivity

/** Разделы нижнего меню. */
enum class Tab(val title: String) { WALLPAPER("Обои"), WIDGETS("Виджеты"), WATCH("Часы"), MORE("Ещё") }

/** Показывать ли часы в превью и миниатюрах (часы подключены). */
val LocalShowWatch = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Чей фон редактируется. */
enum class BgTarget { HOME, LOCK, WATCH }

/** Что редактируется в диалоге выбора цвета. */
sealed interface ColorTarget {
    data object Text : ColorTarget
    data object Accent : ColorTarget
    data object WatchText : ColorTarget
    data object WatchAccent : ColorTarget
    data class Gradient(val target: BgTarget, val index: Int) : ColorTarget
}

@Composable
fun SyncThemeScreen(
    vm: MainViewModel,
    initialTab: Tab = Tab.WALLPAPER,
    onOpenInstaller: () -> Unit = {},
    initialLock: Boolean = false,
    expandSections: Boolean = false,
) = androidx.compose.runtime.CompositionLocalProvider(LocalExpandAll provides expandSections) {
    SyncThemeContent(vm, initialTab, onOpenInstaller, initialLock)
}

@Composable
private fun SyncThemeContent(vm: MainViewModel, initialTab: Tab, onOpenInstaller: () -> Unit, initialLock: Boolean) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val applied by vm.applied.collectAsStateWithLifecycle()
    val phonePreview by vm.phonePreview.collectAsStateWithLifecycle()
    val lockPreview by vm.lockPreview.collectAsStateWithLifecycle()
    val watchPreview by vm.watchPreview.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    // Без часов вкладка «Часы» не нужна.
    val tabs = Tab.entries.filter { it != Tab.WATCH || devices.watch }
    val context = LocalContext.current

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(Unit) { vm.tabRequests.collect { tab = it } }
    LaunchedEffect(tabs) { if (tab !in tabs) tab = Tab.WALLPAPER }
    /** На вкладке «Обои»: редактируется рабочий стол или экран блокировки. */
    var lock by rememberSaveable { mutableStateOf(initialLock) }
    var photoTarget by remember { mutableStateOf(BgTarget.HOME) }
    /** Открыт редактор расположения (часы и фото). */
    var arrange by rememberSaveable { mutableStateOf<ArrangeTarget?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.pickPhoto(it, photoTarget) }
    }

    val actions = remember(vm) {
        ScreenActions(
            vm = vm,
            pickPhoto = { target ->
                photoTarget = target
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            openInstaller = onOpenInstaller,
            arrange = { arrange = it },
            configureWidget = { id ->
                context.startActivity(
                    Intent(context, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    arrange?.let { target ->
        androidx.activity.compose.BackHandler { arrange = null }
        ArrangeScreen(vm, target) { arrange = null }
        return
    }

    val density = LocalDensity.current
    val header = remember(density) { CollapsingHeader(with(density) { 224.dp.toPx() }, with(density) { 96.dp.toPx() }) }
    LaunchedEffect(tab) { header.expand() }

    AppTheme(theme.accentColor) {
        AppBackdrop {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        if (tab != Tab.MORE) ApplyStrip(targets, busy, theme != applied, devices, vm::setTargets, vm::apply)
                        BottomNav(tab, tabs) { tab = it }
                    }
                },
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().nestedScroll(header)
                        .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
                ) {
                    // Шапка и превью закреплены; превью сжимается при прокрутке, но изменения видны сразу.
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Header(connection, devices.watch, onOpenInstaller)
                        if (tab != Tab.MORE) {
                            val height = with(density) { header.height.toDp() }
                            when (tab) {
                                Tab.WATCH -> PreviewRow(null, watchPreview, height)
                                Tab.WALLPAPER -> PreviewRow(if (lock) lockPreview else phonePreview, watchPreview, height, showWatch = devices.watch)
                                else -> PreviewRow(phonePreview, watchPreview, height, showWatch = devices.watch)
                            }
                        }
                    }
                    CompositionLocalProvider(LocalShowWatch provides devices.watch) { LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    ) {
                        when (tab) {
                            Tab.WALLPAPER -> wallpaperTab(theme, actions, lock, { lock = it }) { colorTarget = it }
                            Tab.WIDGETS -> widgetsTab(theme, actions)
                            Tab.WATCH -> watchTab(theme, actions) { colorTarget = it }
                            Tab.MORE -> moreTab(theme, actions) { UpdateSection(vm) }
                        }
                    } }
                }
            }
        }

        colorTarget?.let { target ->
            val watch = theme.watchSettings()
            val (title, initial) = when (target) {
                ColorTarget.Text -> "Цвет цифр" to theme.textColor
                ColorTarget.Accent -> "Акцентный цвет" to theme.accentColor
                ColorTarget.WatchText -> "Цифры на часах" to watch.textColor
                ColorTarget.WatchAccent -> "Акцент на часах" to watch.accentColor
                is ColorTarget.Gradient -> {
                    val bg = when (target.target) {
                        BgTarget.HOME -> theme.background
                        BgTarget.LOCK -> theme.lockScreenBackground()
                        BgTarget.WATCH -> theme.watchBackground()
                    }
                    "Цвет фона" to (bg.colors.getOrNull(target.index) ?: 0xFF000000.toInt())
                }
            }
            ColorPickerDialog(title, initial, onDismiss = { colorTarget = null }) { c ->
                when (target) {
                    ColorTarget.Text -> vm.update { it.copy(textColor = c) }
                    ColorTarget.Accent -> vm.update { it.copy(accentColor = c) }
                    ColorTarget.WatchText -> vm.updateWatch { it.copy(textColor = c) }
                    ColorTarget.WatchAccent -> vm.updateWatch { it.copy(accentColor = c) }
                    is ColorTarget.Gradient -> actions.editBackground(target.target) { bg ->
                        bg.copy(colors = bg.colors.toMutableList().also { list ->
                            if (target.index < list.size) list[target.index] = c else list += c
                        })
                    }
                }
                colorTarget = null
            }
        }
    }
}

/** Действия, которые нужны вкладкам. */
class ScreenActions(
    val vm: MainViewModel,
    val pickPhoto: (BgTarget) -> Unit,
    val openInstaller: () -> Unit,
    val configureWidget: (Int) -> Unit,
    /** Редактор расположения: подвинуть часы (время) и фото. */
    val arrange: (ArrangeTarget) -> Unit = {},
) {
    /** Меняет циферблат: в отдельных настройках часов или, если их нет, в общей теме. */
    fun editWatchFace(transform: (app.synctheme.core.WatchSettings) -> app.synctheme.core.WatchSettings) {
        val t = vm.theme.value
        if (t.watch != null) return vm.updateWatch(transform)
        val n = transform(t.followPhone())
        vm.update {
            it.copy(
                clockStyle = n.clockStyle, dialStyle = n.dialStyle, fontWeight = n.fontWeight, showSeconds = n.showSeconds,
                showDate = n.showDate, watchComplications = n.complications, digitFont = n.digitFont,
                watchDepth = n.depth, watchTimeShift = n.timeShift,
            )
        }
    }

    fun editBackground(target: BgTarget, transform: (app.synctheme.core.Background) -> app.synctheme.core.Background) {
        when (target) {
            BgTarget.HOME -> vm.update { it.copy(background = transform(it.background)) }
            BgTarget.LOCK -> vm.update { it.copy(lockBackground = transform(it.lockScreenBackground())) }
            BgTarget.WATCH -> vm.updateWatch { it.copy(background = transform(it.background ?: vm.theme.value.background)) }
        }
    }
}

@Composable
private fun Header(connection: WatchConnection, watch: Boolean, onOpenInstaller: () -> Unit) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        AppMark(look, 36.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { AppTitle(look) }
        // Статус часов — только если часы подключены в «Устройствах».
        if (!watch) return@Row
        val (dot, text) = when {
            !connection.checked -> Color.Gray to "Ищу часы…"
            connection.hasApp -> Color(0xFF39D86A) to "Часы на связи"
            connection.hasWatch -> Color(0xFFFFCC00) to "Установить на часы"
            else -> Color(0xFFFF3B30) to "Часы не найдены"
        }
        StatusChip(dot, text) { if (connection.hasWatch && !connection.hasApp) onOpenInstaller() }
    }
}

@Composable
private fun PreviewRow(phone: ImageBitmap?, watch: ImageBitmap?, height: Dp, showPhone: Boolean = true, showWatch: Boolean = true) {
    val look = LocalLook.current
    val outer = when (look.style) {
        AppStyle.GLASS -> RoundedCornerShape(28.dp)
        AppStyle.EXPRESSIVE -> RoundedCornerShape(36.dp)
        AppStyle.NEON -> RoundedCornerShape(22.dp)
    }
    val frameBorder = when (look.style) {
        AppStyle.GLASS -> look.cardBorder
        AppStyle.EXPRESSIVE -> Color.Transparent
        AppStyle.NEON -> look.accent.copy(alpha = 0.4f)
    }
    // Без телефона (вкладка «Часы») часы крупнее.
    val watchOnly = phone == null || !showPhone
    val inner = height - 20.dp
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).height(height)
            .glow(look, outer, 10.dp, 0.5f)
            .clip(outer).background(look.card).border(1.dp, frameBorder, outer),
    ) {
        if (look.style == AppStyle.EXPRESSIVE) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-50).dp).size(190.dp).clip(FlowerShape).background(look.highlight))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!watchOnly) {
                val phoneShape = RoundedCornerShape(if (look.style == AppStyle.EXPRESSIVE) 24.dp else 20.dp)
                val phoneBorder = when (look.style) {
                    AppStyle.GLASS -> if (look.dark) Color.White.copy(alpha = 0.22f) else Color.White
                    AppStyle.EXPRESSIVE -> Color.Transparent
                    AppStyle.NEON -> look.accent
                }
                Box(
                    Modifier
                        .height(inner)
                        .aspectRatio(540f / 1170f)
                        .glow(look, phoneShape, 12.dp, 0.7f)
                        .clip(phoneShape)
                        .background(Color.Black)
                        .border(if (look.style == AppStyle.NEON) 1.5.dp else 3.dp, phoneBorder, phoneShape),
                ) {
                    phone?.let {
                        Image(it, contentDescription = "Превью телефона", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            if (!showWatch) return@Row
            // Корпус часов: рамка + круглый экран, картинка всегда растягивается на весь экран.
            val bezel = when (look.style) {
                AppStyle.GLASS -> if (look.dark) Color.White.copy(alpha = 0.16f) else Color.White
                AppStyle.EXPRESSIVE -> look.text
                AppStyle.NEON -> Color(0xFF0C0C12)
            }
            val watchSize = if (watchOnly) inner else minOf(inner * 0.68f, 140.dp)
            Box(
                Modifier
                    .size(watchSize)
                    .glow(look, CircleShape, 12.dp, 0.8f)
                    .clip(CircleShape)
                    .background(bezel)
                    .border(if (look.glows) 1.5.dp else 0.dp, if (look.glows) look.accent else Color.Transparent, CircleShape)
                    .padding(if (look.style == AppStyle.EXPRESSIVE) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
            ) {
                watch?.let {
                    Image(it, contentDescription = "Превью часов", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun UpdateSection(vm: MainViewModel) {
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
