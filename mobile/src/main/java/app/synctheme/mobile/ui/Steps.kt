package app.synctheme.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Цвета «Трёх шагов»: тёплая бумага, чернила и оранжевый — у кнопок и номеров шагов. */
object StepColors {
    val Paper = Color(0xFFF1ECE4)
    val Ink = Color(0xFF1E1B26)
    val Orange = Color(0xFFFF7A45)
    val Green = Color(0xFF2E8B57)
}

/** Подробный раздел «Оформления», который открывается из дополнительных настроек. */
enum class WallDetail(val title: String) {
    BACKGROUND("Фон без фото"),
    COLORS("Цвета"),
    MODE("Светлая и тёмная"),
    CLOCK("Часы на обоях"),
    LOCK("Экран блокировки"),
    SAVE("Название темы"),
}

/** Тёмная карточка открытого шага и её детали — в «Трёх шагах» и (по-своему) в остальных оформлениях. */
private class StepLook(val hero: Color, val onHero: Color, val heroMuted: Color, val heroSoft: Color, val accent: Color, val onAccent: Color, val soft: Color, val ok: Color)

private val Look.steps: StepLook
    get() = if (style == AppStyle.STEPS) StepLook(
        hero = highlight, onHero = onHighlight, heroMuted = onHighlight.copy(alpha = 0.72f), heroSoft = mix(highlight, Color.White, 0.08f),
        accent = StepColors.Orange, onAccent = StepColors.Ink, soft = card, ok = if (dark) Color(0xFF5CC98A) else StepColors.Green,
    ) else StepLook(
        hero = card, onHero = text, heroMuted = muted, heroSoft = control, accent = primary, onAccent = onPrimary,
        soft = card, ok = if (dark) Color(0xFF5CC98A) else StepColors.Green,
    )

/** Что нужно плиткам шагов от экрана. */
class StepsState(
    val phone: ImageBitmap?,
    val watch: ImageBitmap?,
    val dirty: Boolean,
    val busy: String?,
    val targets: ApplyTargets,
    val devices: DeviceSet,
    val onDetail: (WallDetail) -> Unit,
    val editColor: (ColorTarget) -> Unit,
    val header: @Composable () -> Unit,
)

/**
 * «Оформление» в три шага: 1 — выбрать фото, 2 — посмотреть и поправить картинками, 3 — поставить.
 * При первом запуске открыт только первый шаг, остальные — серые, с пояснением, что будет дальше.
 */
fun LazyListScope.stepsHub(theme: Theme, a: ScreenActions, s: StepsState, firstRun: Boolean) {
    val photo = theme.background.type == BackgroundType.PHOTO
    if (firstRun) {
        item(key = "s_title") {
            val look = LocalLook.current
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text("Оформление телефона из вашего фото", color = look.text, fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text("Всего три шага. Займёт минуту.", color = look.muted, fontSize = 14.sp)
            }
        }
    } else {
        item(key = "s_head") { s.header() }
    }
    item(key = "s_1") {
        if (photo && !firstRun) StepDone(s.phone, a) else StepPickPhoto(a)
    }
    if (firstRun) {
        item(key = "s_2w") { StepWaiting(2, "Посмотрите, что получилось", "Обои, виджеты и часы — сразу в цветах фото") }
        item(key = "s_3w") { StepWaiting(3, "Поставьте на телефон", "Одна кнопка — и всё на месте") }
        return
    }
    item(key = "s_2") { StepResult(theme, a, s) }
    item(key = "s_3") { StepApply(theme, a, s) }
}

@Composable
private fun StepNumber(n: String, color: Color, size: Int = 34) {
    val look = LocalLook.current
    Text(n, color = color, fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = size.sp)
}

/** Открытый шаг: тёмная карточка с номером и заголовком. */
@Composable
private fun OpenStep(n: Int, title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    val st = look.steps
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(st.hero).border(1.dp, look.cardBorder, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            StepNumber("$n", st.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 2.dp)) {
                Text(title, color = st.onHero, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                subtitle?.let { Text(it, color = st.heroMuted, fontSize = 13.sp) }
            }
        }
        CompositionLocalProvider(LocalContentColor provides st.onHero) { content() }
    }
}

@Composable
private fun StepWaiting(n: Int, title: String, subtitle: String) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(22.dp)).background(look.steps.soft).padding(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        StepNumber("$n", look.muted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.padding(bottom = 2.dp)) {
            Text(title, color = look.muted, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = look.muted, fontSize = 13.sp)
        }
    }
}

/** Шаг 1: выбрать фото — большая рамка с примерами, кнопка галереи и готовые картинки. */
@Composable
private fun StepPickPhoto(a: ScreenActions) {
    val look = LocalLook.current
    val st = look.steps
    OpenStep(1, "Выберите фото", "Любое: море, питомец, вы сами") {
        Column(
            Modifier.fillMaxWidth().height(210.dp).drawBehind {
                drawRoundRect(
                    st.heroMuted.copy(alpha = 0.5f), cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fan(listOf(Color(0xFF1E2A4A), Color(0xFFF29E6D)), -8f)
                Fan(listOf(Color(0xFF9ED8F0), Color(0xFF0F4C6B)), 0f)
                Fan(listOf(Color(0xFFF7C8D6), Color(0xFFC24D7A)), 8f)
            }
            Spacer(Modifier.height(14.dp))
            BigButton("Открыть галерею", a.themeFromPhoto)
        }
        FlowRow(verticalArrangement = Arrangement.Center) {
            Text("Нет подходящего снимка? Попробуйте на примере: ", color = st.heroMuted, fontSize = 13.sp)
            Text("закат", color = st.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button) { a.vm.themeFromSample(sea = false) })
            Text(" или ", color = st.heroMuted, fontSize = 13.sp)
            Text("море", color = st.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button) { a.vm.themeFromSample(sea = true) })
        }
    }
}

@Composable
private fun Fan(colors: List<Color>, angle: Float) {
    Box(
        Modifier.size(54.dp, 76.dp).rotate(angle).clip(RoundedCornerShape(10.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(colors)),
    )
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val st = LocalLook.current.steps
    Box(
        modifier.height(52.dp).clip(CircleShape).background(st.accent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = st.onAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1) }
}

/** Шаг 1 пройден: маленькая строка с фото и «Заменить». */
@Composable
private fun StepDone(phone: ImageBitmap?, a: ScreenActions) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(look.steps.soft).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepNumber("1", look.steps.ok, 26)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
            phone?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Text("Фото выбрано", color = look.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = a.themeFromPhoto) { Text("Заменить", color = look.accentText, fontWeight = FontWeight.Bold) }
    }
}

/** Шаг 2: результат и варианты картинками; ниже — дополнительные настройки обычными словами. */
@Composable
private fun StepResult(theme: Theme, a: ScreenActions, s: StepsState) {
    val look = LocalLook.current
    val st = look.steps
    val expandAll = LocalExpandAll.current
    var more by rememberSaveable { mutableStateOf(false) }
    val photo = theme.background.type == BackgroundType.PHOTO
    val minis by a.vm.choicePreviews.collectAsStateWithLifecycle()
    Column {
        OpenStep(2, "Вот что получилось", null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val arrange = arrangeLabel(theme, ArrangeTarget.HOME)
                Box(
                    Modifier.width(122.dp).aspectRatio(540f / 1170f).clip(RoundedCornerShape(18.dp)).background(Color.Black)
                        .then(if (arrange != null) Modifier.clickable(role = Role.Button) { a.arrange(ArrangeTarget.HOME) } else Modifier)
                        .semantics { contentDescription = "Превью телефона" },
                ) { s.phone?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.devices.watch) {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(st.heroSoft).padding(4.dp).clip(CircleShape).background(Color.Black)) {
                            s.watch?.let { Image(it, "Превью часов", modifier = Modifier.fillMaxSize()) }
                        }
                    }
                    Text(
                        (if (s.devices.watch) "Слева — телефон, сверху — циферблат часов." else "Слева — телефон.") +
                            if (photo) " Цвета цифр и виджетов взяты с фото." else "",
                        color = st.heroMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    if (arrange != null) {
                        Text(arrange, color = st.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button) { a.arrange(ArrangeTarget.HOME) }.padding(vertical = 6.dp))
                    }
                }
            }
            Text("Яркость — нажмите на картинку:", color = st.heroMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.ORIGINAL to "Как на фото", ThemeMode.LIGHT to "Светлее", ThemeMode.DARK to "Темнее", ThemeMode.AUTO to "Днём светло, ночью темно",
                ).forEach { (m, label) ->
                    Choice(label, minis[m.name], theme.mode == m) { a.vm.update { it.copy(mode = m) } }
                }
            }
            Text("Часы на экране:", color = st.heroMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val wall = theme.wallClock
                Choice("Без часов", minis["none"], wall == null) { a.vm.setWallClock(null) }
                Choice("Часы на фото", minis["on"], wall != null && !wall.depth) { a.vm.setWallClock((wall ?: WallClock()).copy(depth = false)) }
                Choice("Часы за предметом", minis["depth"], wall?.depth == true, enabled = photo) { a.vm.setWallClock((wall ?: WallClock()).copy(depth = true)) }
            }
            Text(
                if (more || expandAll) "Скрыть дополнительные настройки ▲" else "Дополнительные настройки ▼",
                color = st.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clickable(role = Role.Button) { more = !more }.padding(vertical = 6.dp),
            )
        }
        if (more || expandAll) MoreSettings(theme, a, s)
    }
}

@Composable
private fun RowScope.Choice(label: String, image: ImageBitmap?, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val st = LocalLook.current.steps
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.weight(1f).clip(shape).background(if (selected) st.accent else st.heroSoft)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 9.dp)
            .then(if (enabled) Modifier else Modifier.alphaHalf()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.height(104.dp).aspectRatio(540f / 1170f).clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
            image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(
            label, color = if (selected) st.onAccent else st.onHero, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp,
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.alphaHalf() = this.alpha(0.45f)

/** Дополнительные настройки: каждая подписана тем, что она делает. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSettings(theme: Theme, a: ScreenActions, s: StepsState) {
    val look = LocalLook.current
    val photo = theme.background.type == BackgroundType.PHOTO
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(look.steps.soft).border(1.dp, look.cardBorder, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides look.text) {
            Setting("Цвет виджетов и подписей", if (photo) "Взят с вашего фото. Нажмите другой кружок, чтобы поменять." else "Нажмите кружок, чтобы поменять.") {
                val colors = (theme.background.colors + theme.accentColor + app.synctheme.core.Presets.all.map { it.accentColor }).distinct().take(6)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    colors.forEach { c -> ColorSwatch(c, size = 38.dp, selected = c == theme.accentColor) { a.vm.update { it.copy(accentColor = c) } } }
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, look.muted, CircleShape).clickable { s.editColor(ColorTarget.Accent) }
                            .semantics { contentDescription = "Другой цвет" },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = look.muted, fontSize = 20.sp) }
                }
            }
            Setting("Цвет цифр", null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val white = 0xFFFFFFFF.toInt()
                    val dark = 0xFF1A1A1A.toInt()
                    FilterChip(theme.textColor == white, { a.vm.update { it.copy(textColor = white) } }, label = { Text("Белые") })
                    FilterChip(theme.textColor == dark, { a.vm.update { it.copy(textColor = dark) } }, label = { Text("Тёмные") })
                    FilterChip(theme.textColor != white && theme.textColor != dark, { s.editColor(ColorTarget.Text) }, label = { Text("Другой…") })
                }
            }
            Setting("Затемнить фото", "Цифры и виджеты читаются лучше.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("нет", color = look.muted, fontSize = 12.sp)
                    Slider(theme.dim.coerceIn(0f, 0.8f), { d -> a.vm.update { it.copy(dim = d) } }, valueRange = 0f..0.8f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("сильно", color = look.muted, fontSize = 12.sp)
                }
            }
            if (photo) {
                LinkText("Подвинуть или приблизить фото") { a.arrange(ArrangeTarget.HOME) }
            }
            theme.wallClock?.let { wall ->
                Setting("Где часы", null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(WallClockLayout.TOP to "Вверху", WallClockLayout.STACKED to "Столбиком", WallClockLayout.CENTER to "По центру").forEach { (l, label) ->
                            FilterChip(wall.layout == l && wall.place == null, { a.vm.setWallClock(wall.copy(layout = l, place = null)) }, label = { Text(label) })
                        }
                    }
                }
                Setting("Что показать рядом с часами", null) {
                    SwitchRow("Дату", wall.showDate) { on -> a.vm.setWallClock(wall.copy(showDate = on)) }
                    SwitchRow("Погоду", wall.weather) { on -> a.vm.setWallClock(wall.copy(weather = on)) }
                    SwitchRow("Заряд батареи", wall.battery) { on -> a.vm.setWallClock(wall.copy(battery = on)) }
                }
                LinkText("Все настройки часов: шрифт, приложение по нажатию…") { s.onDetail(WallDetail.CLOCK) }
            }
            Setting("Экран блокировки", null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(theme.lockBackground == null, { a.vm.setLockSeparate(false) }, label = { Text("То же фото") })
                    FilterChip(theme.lockBackground != null, { a.pickPhoto(BgTarget.LOCK) }, label = { Text(if (theme.lockBackground != null) "Другое фото ✓" else "Другое фото") })
                }
                LinkText("Часы и другие настройки блокировки") { s.onDetail(WallDetail.LOCK) }
            }
            Setting("Без фото", "Фон из цветов — градиент или один цвет.") {
                LinkText(if (photo) "Сделать фон из цветов" else "Настроить цвета фона") { s.onDetail(WallDetail.BACKGROUND) }
            }
            Setting("Название темы", theme.name) {
                LinkText("Переименовать или сохранить копию") { s.onDetail(WallDetail.SAVE) }
            }
        }
    }
}

@Composable
private fun Setting(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, color = look.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            subtitle?.let { Text(it, color = look.muted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        content()
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val look = LocalLook.current
    Text(text, color = look.accentText, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp))
}

/** Шаг 3: одна большая кнопка; после установки — зелёное «Готово». */
@Composable
private fun StepApply(theme: Theme, a: ScreenActions, s: StepsState) {
    val look = LocalLook.current
    val st = look.steps
    var targetsOpen by remember { mutableStateOf(false) }
    val where = buildList {
        if (s.targets.homeWallpaper) add("обои")
        if (s.targets.lockWallpaper) add("экран блокировки")
        if (s.targets.widgets) add("виджеты")
        if (s.devices.watch && s.targets.watch) add("часы")
        s.devices.peers.filter { it.enabled }.forEach { add(it.name) }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val done = !s.dirty && s.busy == null
        val bg = if (done) st.ok else st.accent
        val fg = if (done) Color.White else st.onAccent
        Row(
            Modifier.fillMaxWidth().clip(shape).background(bg)
                .clickable(enabled = s.busy == null && (done || where.isNotEmpty()), role = Role.Button) { if (done) a.themeFromPhoto() else a.vm.applyAndKeep() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepNumber(if (done) "✓" else "3", fg, 30)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                when {
                    s.busy != null -> {
                        Text("Ставлю на телефон…", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(s.busy, color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = fg, trackColor = fg.copy(alpha = 0.25f))
                    }
                    done -> {
                        Text("Готово — стоит на телефоне", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Хотите ещё? Нажмите, чтобы выбрать другое фото", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    else -> {
                        Text(if (where.isEmpty()) "Выберите, куда ставить" else "Поставить на телефон", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(if (where.isEmpty()) "ничего не выбрано" else where.joinToString(), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                }
            }
            if (s.busy == null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(fg), contentAlignment = Alignment.Center) {
                    Text(if (done) "+" else "→", color = bg, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                }
            }
        }
        if (s.busy == null) {
            TextButton(onClick = { targetsOpen = true }) { Text("Куда ставить — изменить", color = look.accentText, fontWeight = FontWeight.Bold) }
        }
    }
    if (targetsOpen) TargetsDialog(s.targets, s.devices, a.vm::setTargets, a.vm::setPeerEnabled) { targetsOpen = false }
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

/** «Мои темы»: все оформления, сделанные из фото; нажатие возвращает тему в «Оформление». */
fun LazyListScope.themesTab(theme: Theme, a: ScreenActions, onOpen: () -> Unit) {
    item(key = "t_title") {
        val look = LocalLook.current
        Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
            Text("Мои темы", color = look.text, fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text("Каждое фото, из которого вы делали оформление, сохраняется здесь. Нажмите, чтобы вернуть.", color = look.muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
    item(key = "t_grid") {
        val saved by a.vm.saved.collectAsStateWithLifecycle()
        val look = LocalLook.current
        if (saved.isEmpty()) {
            Panel {
                Text("Пока пусто", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Выберите фото на вкладке «Оформление» — тема появится здесь сама.", color = look.muted, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                saved.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Spacer(Modifier.height(10.dp))
        BigButton("Новое оформление из фото", a.themeFromPhoto, Modifier.fillMaxWidth())
    }
}

/** Карточка темы в «Моих темах»: превью телефона, название, «Открыть» и «Удалить». */
@Composable
private fun ThemeCard(t: Theme, selected: Boolean, modifier: Modifier, onOpen: () -> Unit, onDelete: () -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(20.dp)
    val image = rememberThemeShot(t)
    Column(
        modifier.clip(shape).background(look.steps.soft).border(if (selected) 3.dp else 1.dp, if (selected) look.steps.accent else look.cardBorder, shape).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                .clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = "Открыть «${t.name}»" },
        ) { image?.let { Image(it, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) } }
        Text(t.name, color = look.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Открыть", color = look.accentText, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).padding(4.dp))
            Text("Удалить", color = look.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onDelete).padding(4.dp))
        }
    }
}

// ---------------------------------------------------------------- общее

/** Шапка открытого раздела: «назад» и название. */
@Composable
fun DetailHeader(title: String, onBack: () -> Unit) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(look.card).border(1.dp, look.cardBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Назад" },
            contentAlignment = Alignment.Center,
        ) { Icon(NavIcons.Back, null, tint = look.text, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(look.title(title), color = look.text, fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Карточка содержимого раздела — без заголовка, он уже в шапке. */
@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.radius)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape).background(look.card).border(1.dp, look.cardBorder, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides look.text) { content() }
    }
}

/** Нижнее меню «Трёх шагов»: подписи словами, выбранная — на светлой плашке. */
@Composable
fun StepsNav(tab: Tab, tabs: List<Tab>, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    Column(Modifier.fillMaxWidth().background(look.bar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(look.muted.copy(alpha = 0.2f)))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEach { t ->
                val on = t == tab
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) look.steps.soft else Color.Transparent)
                        .clickable(role = Role.Tab) { onTab(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.title, color = if (on) look.text else look.muted, fontWeight = FontWeight.Bold,
                        fontSize = if (tabs.size > 4) 11.sp else 13.sp, maxLines = 1, softWrap = false,
                    )
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
    val st = look.steps
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape).background(st.hero).border(1.dp, look.cardBorder, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Вышла новая версия ${info.versionName}", color = st.onHero, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        val note = state.status ?: info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("-")?.trim()
        note?.let { Text(it, color = st.heroMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        state.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = st.accent, trackColor = st.heroSoft) }
        if (state.progress == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.needsPermission) {
                    BigButton("Разрешить установку", { context.startActivity(vm.installPermissionIntent()) })
                } else {
                    BigButton("Обновить", vm::downloadAndInstall)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { hidden = info.versionCode }) { Text("Позже", color = st.heroMuted, fontWeight = FontWeight.Bold) }
            }
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
