package app.synctheme.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme
import app.synctheme.core.ThemeMode
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSet
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.render.ThemeRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Подробный раздел темы: часы на обоях (из «Часов на экране»). */
enum class WallDetail(val title: String) {
    CLOCK("Часы на обоях"),
}

/** Что нужно главному экрану от остального приложения. */
class HomeState(
    val phone: ImageBitmap?,
    val watch: ImageBitmap?,
    val dirty: Boolean,
    val busy: String?,
    val targets: ApplyTargets,
    val devices: DeviceSet,
    val onDetail: (WallDetail) -> Unit,
    val openThemes: () -> Unit,
    /** Шестерёнка в шапке: «Настройки». */
    val openSettings: () -> Unit,
)

private fun wallTitle(wall: WallClock?) = when {
    wall == null -> "Без часов"
    wall.depth -> "За предметом на фото"
    else -> "Поверх фото"
}

/**
 * Главный экран — тема из фото: сверху «Мои темы», название, «Тёмная» и шестерёнка настроек; превью;
 * поля «Фото» и «Часы на экране»; большая кнопка «Поставить на телефон». При первом запуске вместо
 * полей — «Выбрать фото».
 */
fun LazyListScope.home(theme: Theme, a: ScreenActions, s: HomeState, firstRun: Boolean) {
    item(key = "h_top") { TopBar(theme, s) }
    item(key = "h_preview") { Preview(theme, a, s) }
    if (firstRun) {
        item(key = "h_first") {
            val look = LocalLook.current
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Оформление из вашего фото", color = look.text, fontFamily = look.headings, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(
                    "Выберите снимок — обои, виджеты и циферблат часов соберутся в его цветах сами.",
                    color = look.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
                SoftPill("Выбрать фото", a.themeFromPhoto)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Нет подходящего? Пример: ", color = look.text.copy(alpha = 0.85f), fontSize = 14.sp)
                    Text("закат", color = look.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { a.vm.themeFromSample(sea = false) }.padding(8.dp))
                    Text("·", color = look.muted, fontSize = 14.sp)
                    Text("море", color = look.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { a.vm.themeFromSample(sea = true) }.padding(8.dp))
                }
            }
        }
        return
    }
    item(key = "h_photo") {
        SoftField("Фото", if (theme.background.type == BackgroundType.PHOTO) "Заменить фото" else "Выбрать фото", NavIcons.Camera, a.themeFromPhoto)
    }
    item(key = "h_clock") { ClockField(theme, a, s) }
    item(key = "h_apply") { ApplyButton(a, s) }
}

/** Шестерёнка «Настройки» — круглая выпуклая кнопка, как «Назад» в шапке разделов. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        Modifier.size(44.dp).raised(look, 22.dp, 4.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Настройки" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Settings, null, tint = look.text, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun TopBar(theme: Theme, s: HomeState) {
    val look = LocalLook.current
    val context = LocalContext.current
    val appearance by AppearanceStore.get(context).collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SoftChip("Мои темы", s.openThemes)
            Text(
                theme.name, color = look.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clickable(role = Role.Button, onClick = s.openThemes),
            )
            SoftToggle("Тёмная", look.dark, { on -> AppearanceStore.set(context, appearance.copy(mode = if (on) AppMode.DARK else AppMode.LIGHT)) })
            Spacer(Modifier.width(10.dp))
            SettingsButton(s.openSettings)
        }
    }
}

/** Превью телефона в выпуклой рамке; рядом — круглый циферблат часов, как логотип на образце. */
@Composable
private fun Preview(theme: Theme, a: ScreenActions, s: HomeState) {
    val look = LocalLook.current
    val arrange = arrangeLabel(theme, ArrangeTarget.HOME)
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.raised(look, 30.dp, 8.dp).padding(10.dp)) {
                Box(
                    Modifier.width(150.dp).aspectRatio(540f / 1170f).clip(RoundedCornerShape(22.dp)).background(Color.Black)
                        .then(if (arrange != null) Modifier.clickable(role = Role.Button) { a.arrange(ArrangeTarget.HOME) } else Modifier)
                        .semantics { contentDescription = "Превью телефона" },
                ) { s.phone?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
            }
            if (s.devices.watch) {
                Box(Modifier.size(104.dp).raised(look, 52.dp, 8.dp).padding(9.dp).clip(CircleShape).background(Color.Black)) {
                    s.watch?.let { Image(it, "Превью часов", modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        if (arrange != null) {
            Text(
                arrange, color = look.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp).clickable(role = Role.Button) { a.arrange(ArrangeTarget.HOME) }.padding(6.dp),
            )
        }
    }
}

@Composable
private fun ClockField(theme: Theme, a: ScreenActions, s: HomeState) {
    val expandAll = LocalExpandAll.current
    var open by rememberSaveable { mutableStateOf(false) }
    val minis by a.vm.choicePreviews.collectAsStateWithLifecycle()
    val shown = open || expandAll
    val wall = theme.wallClock
    val photo = theme.background.type == BackgroundType.PHOTO
    SoftField(
        "Часы на экране", wallTitle(wall), if (shown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, { open = !open },
        content = if (!shown) null else {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Choice("Без часов", minis["none"], wall == null) { a.vm.setWallClock(null) }
                    Choice("Поверх фото", minis["on"], wall != null && !wall.depth) { a.vm.setWallClock((wall ?: WallClock()).copy(depth = false)) }
                    Choice("За предметом", minis["depth"], wall?.depth == true, enabled = photo) { a.vm.setWallClock((wall ?: WallClock()).copy(depth = true)) }
                }
                if (wall != null) LinkText("Где часы, дата, погода, приложение по нажатию") { s.onDetail(WallDetail.CLOCK) }
            }
        },
    )
}

/** Вариант картинкой: выбранный — вдавлен, остальные — выпуклые. */
@Composable
private fun RowScope.Choice(label: String, image: ImageBitmap?, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier.weight(1f).then(if (selected) Modifier.inset(look, 18.dp, 4.dp) else Modifier.raised(look, 18.dp, 4.dp))
            .clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.height(100.dp).aspectRatio(540f / 1170f).clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
            image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(
            label, color = if (selected) look.text else look.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp,
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «Поставить на телефон» — как «Войти»: светлая надпись, пока есть что ставить. Ниже — куда ставить. */
@Composable
private fun ApplyButton(a: ScreenActions, s: HomeState) {
    val look = LocalLook.current
    var targetsOpen by remember { mutableStateOf(false) }
    val where = buildList {
        if (s.targets.homeWallpaper) add("обои")
        if (s.targets.lockWallpaper) add("блокировка")
        if (s.targets.widgets) add("виджеты")
        if (s.devices.watch && s.targets.watch) add("часы")
        s.devices.peers.filter { it.enabled }.forEach { add(it.name) }
    }
    Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            s.busy != null -> {
                SoftPill("Ставлю на телефон…", {}, strong = false, enabled = false)
                Text(s.busy, color = look.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp), color = look.text, trackColor = look.control)
            }
            !s.dirty -> SoftPill("Стоит на телефоне ✓", {}, strong = false)
            else -> SoftPill(if (where.isEmpty()) "Выберите, куда ставить" else "Поставить на телефон", { a.vm.applyAndKeep() }, enabled = where.isNotEmpty())
        }
        if (s.busy == null) {
            SoftHint("Куда: ${where.joinToString().ifEmpty { "никуда" }}.", "Изменить", { targetsOpen = true }, Modifier.padding(top = 14.dp))
        }
    }
    if (targetsOpen) TargetsDialog(s.targets, s.devices, a.vm::setTargets, a.vm::setPeerEnabled) { targetsOpen = false }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val look = LocalLook.current
    Text(text, color = look.accentText, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp))
}

@Composable
private fun TargetsDialog(targets: ApplyTargets, devices: DeviceSet, onTargets: (ApplyTargets) -> Unit, onPeer: (String, Boolean) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
        title = { Text("Куда ставить оформление") },
        text = {
            Column {
                SwitchRow("Обои рабочего стола", targets.homeWallpaper) { onTargets(targets.copy(homeWallpaper = it)) }
                SwitchRow("Экран блокировки", targets.lockWallpaper) { onTargets(targets.copy(lockWallpaper = it)) }
                SwitchRow("Виджеты", targets.widgets) { onTargets(targets.copy(widgets = it)) }
                if (devices.watch) SwitchRow("Циферблат часов", targets.watch) { onTargets(targets.copy(watch = it)) }
                devices.peers.forEach { p -> SwitchRow(p.name, p.enabled) { onPeer(p.id, it) } }
            }
        },
    )
}

// ---------------------------------------------------------------- Мои темы

/** «Мои темы»: все оформления, сделанные из фото; нажатие возвращает тему на главный экран. */
fun LazyListScope.themesTab(theme: Theme, a: ScreenActions, onOpen: () -> Unit) {
    item(key = "t_hint") {
        val look = LocalLook.current
        Text(
            "Каждое фото, из которого вы делали оформление, сохраняется здесь. Нажмите, чтобы вернуть.",
            color = look.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
    }
    item(key = "t_grid") {
        val saved by a.vm.saved.collectAsStateWithLifecycle()
        val look = LocalLook.current
        if (saved.isEmpty()) {
            SoftCard {
                Text("Пока пусто", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Выберите фото на главном экране — тема появится здесь сама.", color = look.muted, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                saved.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { t ->
                            ThemeCard(t, t.id == theme.id, Modifier.weight(1f), onOpen = { a.vm.select(t); onOpen() }, onDelete = { a.vm.delete(t) })
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    item(key = "t_new") {
        Spacer(Modifier.height(22.dp))
        SoftPill("Новое из фото", a.themeFromPhoto)
    }
}

/** Карточка темы: превью телефона, название, «Открыть» и «Удалить». Открытая сейчас — вдавлена. */
@Composable
private fun ThemeCard(t: Theme, selected: Boolean, modifier: Modifier, onOpen: () -> Unit, onDelete: () -> Unit) {
    val look = LocalLook.current
    val image = rememberThemeShot(t)
    Column(
        modifier.then(if (selected) Modifier.inset(look, 22.dp) else Modifier.raised(look, 22.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                .clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = "Открыть «${t.name}»" },
        ) { image?.let { Image(it, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) } }
        Text(t.name, color = look.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Открыть", color = look.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).padding(4.dp))
            Text("Удалить", color = look.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onDelete).padding(4.dp))
        }
    }
}

@Composable
private fun rememberThemeShot(t: Theme): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, t) {
        value = withContext(Dispatchers.Default) {
            runCatching { ThemeRenderer.phonePreview(context, DayNight.of(context, t), 270, 585).asImageBitmap() }.getOrNull()
        }
    }
    return image
}

// ---------------------------------------------------------------- общее

/** Шапка открытого раздела: круглая «назад» и название. */
@Composable
fun DetailHeader(title: String, onBack: () -> Unit) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).raised(look, 22.dp, 4.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Назад" },
            contentAlignment = Alignment.Center,
        ) { Icon(NavIcons.Back, null, tint = look.text, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(title, color = look.text, fontFamily = look.headings, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Карточка содержимого раздела — без заголовка, он уже в шапке. */
@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) = SoftCard(content = content)

/** Нижнее меню: выпуклая капсула, выбранный раздел — вдавлен; в «Мяте» — серая дорожка и мятная вкладка. */
@Composable
fun SoftNav(tab: Tab, tabs: List<Tab>, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().background(look.bar).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)) {
        if (look.isClay) return@Box ClaySegmented(tabs.map { it.title }, tabs.indexOf(tab), { onTab(tabs[it]) }, height = 58.dp)
        Row(Modifier.fillMaxWidth().height(60.dp).raised(look, 30.dp, 6.dp).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { t ->
                val on = t == tab
                Box(
                    Modifier.weight(1f).height(48.dp).then(if (on) Modifier.inset(look, 24.dp, 4.dp) else Modifier).clip(CircleShape)
                        .clickable(role = Role.Tab) { onTab(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.title, color = if (on) look.text else look.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Плашка «Вышла новая версия»: обновить в одно нажатие или отложить до следующего запуска. */
@Composable
fun UpdateBanner(vm: MainViewModel, modifier: Modifier = Modifier) {
    val state by vm.update.collectAsStateWithLifecycle()
    val info = state.available ?: return
    var hidden by rememberSaveable { mutableStateOf<Long?>(null) }
    if (hidden == info.versionCode && state.progress == null) return
    val context = LocalContext.current
    val look = LocalLook.current
    SoftCard(modifier.padding(top = 10.dp)) {
        Text("Вышла новая версия ${info.versionName}", color = look.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val note = state.status ?: info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("-")?.trim()
        note?.let { Text(it, color = look.muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        state.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = look.text, trackColor = look.control) }
        if (state.progress == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.needsPermission) {
                    SoftPill("Разрешить установку", { context.startActivity(vm.installPermissionIntent()) }, Modifier.weight(1f), height = 52.dp)
                } else {
                    SoftPill("Обновить", vm::downloadAndInstall, Modifier.weight(1f), height = 52.dp)
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { hidden = info.versionCode }) { Text("Позже", color = look.muted, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
