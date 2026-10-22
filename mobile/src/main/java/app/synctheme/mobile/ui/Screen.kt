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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import app.synctheme.mobile.data.DeviceSet
import app.synctheme.mobile.link.LinkPush
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.widget.WidgetConfigActivity

/** Разделы нижнего меню. */
enum class Tab(val title: String) { WALLPAPER("Обои"), WIDGETS("Виджеты"), WATCH("Часы"), MORE("Настройки") }

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
    onOpenTvInstaller: () -> Unit = {},
    expandSections: Boolean = false,
) = androidx.compose.runtime.CompositionLocalProvider(LocalExpandAll provides expandSections) {
    SyncThemeContent(vm, initialTab, onOpenInstaller, initialLock, onOpenTvInstaller)
}

@Composable
private fun SyncThemeContent(vm: MainViewModel, initialTab: Tab, onOpenInstaller: () -> Unit, initialLock: Boolean, onOpenTvInstaller: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val applied by vm.applied.collectAsStateWithLifecycle()
    val phonePreview by vm.phonePreview.collectAsStateWithLifecycle()
    val lockPreview by vm.lockPreview.collectAsStateWithLifecycle()
    val watchPreview by vm.watchPreview.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val tabletPreview by vm.tabletPreview.collectAsStateWithLifecycle()
    val tvPreview by vm.tvPreview.collectAsStateWithLifecycle()
    val linkPush by vm.linkPush.collectAsStateWithLifecycle()
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
            openTvInstaller = onOpenTvInstaller,
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

    // Превью устройств: что сейчас видно на экранах (на вкладке «Часы» — только часы).
    val previews: @Composable (Dp) -> Unit = { height ->
        when (tab) {
            Tab.WATCH -> PreviewRow(null, watchPreview, null, null, height)
            Tab.WALLPAPER -> PreviewRow(
                if (lock) lockPreview else phonePreview, watchPreview.takeIf { devices.watch },
                tabletPreview.takeIf { !lock }, tvPreview.takeIf { !lock }, height, vm.selfTablet,
            )
            else -> PreviewRow(phonePreview, watchPreview.takeIf { devices.watch }, tabletPreview, tvPreview, height, vm.selfTablet)
        }
    }
    val settings: LazyListScope.() -> Unit = {
        when (tab) {
            Tab.WALLPAPER -> wallpaperTab(theme, actions, lock, { lock = it }) { colorTarget = it }
            Tab.WIDGETS -> widgetsTab(theme, actions)
            Tab.WATCH -> watchTab(theme, actions) { colorTarget = it }
            Tab.MORE -> moreTab(theme, actions) { UpdateSection(vm) }
        }
    }
    val applyStrip: @Composable () -> Unit = { ApplyStrip(targets, busy, theme != applied, devices, vm::setTargets, vm::setPeerEnabled, vm::apply) }
    val headerRow: @Composable () -> Unit = { Header(connection, devices, linkPush, onOpenInstaller) { tab = Tab.MORE } }

    AppTheme(theme.accentColor) {
        AppBackdrop {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Альбомная ориентация (и широкий планшет): меню слева, превью и «Применить» — рядом с настройками.
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                CompositionLocalProvider(LocalShowWatch provides devices.watch) {
                    if (wide) {
                        WideLayout(tab, tabs, { tab = it }, headerRow, previews, if (tab != Tab.MORE) applyStrip else null, settings)
                    } else {
                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbar) },
                            bottomBar = {
                                Column {
                                    if (tab != Tab.MORE) applyStrip()
                                    BottomNav(tab, tabs) { tab = it }
                                }
                            },
                        ) { padding ->
                            // На планшете в портрете — колонка по центру, а не растянутые на всю ширину карточки.
                            Column(
                                Modifier.fillMaxSize().nestedScroll(header)
                                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                                    .wrapContentWidth().widthIn(max = 720.dp),
                            ) {
                                // Шапка и превью закреплены; превью сжимается при прокрутке, но изменения видны сразу.
                                Column(Modifier.padding(horizontal = 16.dp)) {
                                    headerRow()
                                    if (tab != Tab.MORE) previews(with(density) { header.height.toDp() })
                                }
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                                    content = settings,
                                )
                            }
                        }
                    }
                }
                if (wide) SnackbarHost(snackbar, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(8.dp).widthIn(max = 520.dp))
                // Запросы на подключение видны на любом экране: этот телефон подключает устройство
                // или сам принимает тему с другого телефона.
                LinkingDialog(vm)
                LinkRequestDialog()
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
    /** Установка SyncTheme на телевизор с телефона. */
    val openTvInstaller: () -> Unit = {},
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

/** Шапка: название и состояние подключённых устройств (часы и устройства по Wi‑Fi — если есть). */
@Composable
private fun Header(
    connection: WatchConnection,
    devices: DeviceSet,
    push: Map<String, LinkPush>,
    onOpenInstaller: () -> Unit,
    onDevices: () -> Unit,
) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        AppMark(look, 36.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { AppTitle(look) }
        val peers = devices.peers.filter { it.enabled }
        val (watchDot, watchText) = when {
            !connection.checked -> Color.Gray to "Ищу часы…"
            connection.hasApp -> StatusOk to "Часы на связи"
            connection.hasWatch -> StatusWait to "Установить на часы"
            else -> StatusBad to "Часы не найдены"
        }
        // Точка — худшее состояние среди устройств: ошибка, затем ожидание, затем «всё на месте».
        val states = peers.map { push[it.id] }
        val peersDot = when {
            states.any { it != null && !it.ok && !it.pending } -> StatusBad
            states.any { it?.pending == true } -> StatusWait
            states.all { it?.ok == true } -> StatusOk
            else -> Color.Gray
        }
        when {
            // Часы и другие устройства вместе — одна плашка «Устройства · N», иначе не помещается название.
            devices.watch && peers.isNotEmpty() -> {
                val dot = listOf(StatusBad, StatusWait, StatusOk).firstOrNull { it == watchDot || it == peersDot } ?: Color.Gray
                StatusChip(dot, "Устройства · ${peers.size + 1}") {
                    if (connection.hasWatch && !connection.hasApp) onOpenInstaller() else onDevices()
                }
            }
            devices.watch -> StatusChip(watchDot, watchText) {
                if (connection.hasWatch && !connection.hasApp) onOpenInstaller() else onDevices()
            }
            // Одно устройство — его тип; несколько — сколько их.
            peers.isNotEmpty() -> StatusChip(peersDot, if (peers.size == 1) peers.single().kind.title else "Устройства · ${peers.size}", onDevices)
        }
    }
}

/**
 * Превью подключённых устройств рядом: телефон, планшет и телевизор (если подключены) и часы.
 * Без телефона (вкладка «Часы») часы крупнее. Если всё не помещается в строку, остальные уменьшаются.
 */
@Composable
private fun PreviewRow(phone: ImageBitmap?, watch: ImageBitmap?, tablet: ImageBitmap?, tv: ImageBitmap?, height: Dp, selfTablet: Boolean = false) {
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
    val watchOnly = phone == null
    val screenBorder = when (look.style) {
        AppStyle.GLASS -> if (look.dark) Color.White.copy(alpha = 0.22f) else Color.White
        AppStyle.EXPRESSIVE -> Color.Transparent
        AppStyle.NEON -> look.accent
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).height(height)
            .glow(look, outer, 10.dp, 0.5f)
            .clip(outer).background(look.card).border(1.dp, frameBorder, outer),
    ) {
        if (look.style == AppStyle.EXPRESSIVE) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-50).dp).size(190.dp).clip(FlowerShape).background(look.highlight))
        }
        val inner = height - 20.dp
        // Своё устройство: телефон в портрете или (приложение на планшете) планшет в альбомной ориентации.
        val selfAspect = if (selfTablet) 1.6f else 540f / 1170f
        val selfH = if (selfTablet) inner * 0.8f else inner
        val phoneW = if (watchOnly) 0.dp else selfH * selfAspect
        val many = listOfNotNull(tablet, tv, watch).size > 1
        var tabletH = if (tablet != null) inner * (if (many) 0.5f else 0.66f) else 0.dp
        var tvH = if (tv != null) inner * (if (many) 0.48f else 0.62f) else 0.dp
        var watchD = when {
            watch == null -> 0.dp
            watchOnly -> inner
            else -> minOf(inner * (if (many) 0.5f else 0.68f), 140.dp)
        }
        val count = listOf(!watchOnly, tablet != null, tv != null, watch != null).count { it }
        val room = maxWidth - 12.dp * (count + 1)
        val others = tabletH * 1.6f + tvH * (16f / 9f) + watchD
        if (phoneW + others > room && others > 0.dp) {
            val k = ((room - phoneW) / others).coerceIn(0.25f, 1f)
            tabletH *= k
            tvH *= k
            watchD *= k
        }
        @Composable
        fun Screen(image: ImageBitmap?, h: Dp, aspect: Float, radius: Dp, description: String) {
            val shape = RoundedCornerShape(radius)
            Box(
                Modifier.height(h).aspectRatio(aspect)
                    .glow(look, shape, 12.dp, 0.7f)
                    .clip(shape)
                    .background(Color.Black)
                    .border(if (look.style == AppStyle.NEON) 1.5.dp else 3.dp, screenBorder, shape),
            ) {
                image?.let { Image(it, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!watchOnly) {
                Screen(phone, selfH, selfAspect, if (look.style == AppStyle.EXPRESSIVE) 24.dp else 20.dp, if (selfTablet) "Превью планшета" else "Превью телефона")
            }
            if (tablet != null) Screen(tablet, tabletH, 1.6f, if (look.style == AppStyle.EXPRESSIVE) 18.dp else 14.dp, "Превью планшета")
            if (tv != null) Screen(tv, tvH, 16f / 9f, 6.dp, "Превью телевизора")
            if (watch == null && !watchOnly) return@Row
            // Корпус часов: рамка + круглый экран, картинка всегда растягивается на весь экран.
            val bezel = when (look.style) {
                AppStyle.GLASS -> if (look.dark) Color.White.copy(alpha = 0.16f) else Color.White
                AppStyle.EXPRESSIVE -> look.text
                AppStyle.NEON -> Color(0xFF0C0C12)
            }
            Box(
                Modifier
                    .size(watchD)
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

/**
 * Широкий экран (телефон боком, планшет): слева — меню, в середине — шапка, превью и «Применить»
 * (превью во всю высоту), справа — настройки вкладки.
 */
@Composable
private fun WideLayout(
    tab: Tab,
    tabs: List<Tab>,
    onTab: (Tab) -> Unit,
    header: @Composable () -> Unit,
    previews: @Composable (Dp) -> Unit,
    applyStrip: (@Composable () -> Unit)?,
    settings: LazyListScope.() -> Unit,
) {
    val look = LocalLook.current
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        SideNav(tab, tabs, onTab)
        Column(
            Modifier.weight(0.45f).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            header()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                previews(maxHeight.coerceAtMost(520.dp) - 12.dp)
            }
            applyStrip?.let {
                val shape = RoundedCornerShape(look.radius)
                Box(Modifier.padding(bottom = 8.dp).clip(shape)) { it() }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(0.55f).fillMaxHeight(),
            contentPadding = PaddingValues(
                start = 0.dp, end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
            content = settings,
        )
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
