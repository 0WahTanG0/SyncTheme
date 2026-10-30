package app.synctheme.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme
import app.synctheme.core.WallClock

/** Цвета плиток «Плиток»: пастель с тёмными надписями — читается и на светлом, и на тёмном фоне. */
object BentoColors {
    val Ink = Color(0xFF1F1A2E)
    val Yellow = Color(0xFFFFD66B)
    val Coral = Color(0xFFFF9A8B)
    val Periwinkle = Color(0xFFB8C7FF)
    val Mint = Color(0xFF9EE6C9)
    val Lilac = Color(0xFFD9C8FF)
}

/** Плитка: фон, основной текст и подпись. */
class TileColors(val bg: Color, val ink: Color, val sub: Color, val border: Color = Color.Transparent)

/** Цвет плитки в «Плитках»; в остальных оформлениях плитки — обычные карточки. */
fun Look.tile(pastel: Color, sub: Color): TileColors =
    if (style == AppStyle.BENTO) TileColors(pastel, BentoColors.Ink, sub) else TileColors(card, text, muted, cardBorder)

/** Тёмная плитка (устройства): контраст к пастели. */
fun Look.inkTile(): TileColors = when {
    style != AppStyle.BENTO -> TileColors(highlight, onHighlight, muted, cardBorder)
    dark -> TileColors(Color(0xFF2C2540), Color.White, Color(0xFFC9C2DC))
    else -> TileColors(BentoColors.Ink, Color.White, Color(0xFFC9C2DC))
}

/** Раздел «Обоев», который открывается с плитки. */
enum class WallDetail(val title: String) {
    BACKGROUND("Фон"),
    COLORS("Цвета"),
    MODE("Светлая и тёмная"),
    CLOCK("Часы на обоях"),
    LOCK("Экран блокировки"),
    SAVE("Сохранить тему"),
}

@Composable
fun BentoTile(
    colors: TileColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.radius)
    Column(
        modifier.clip(shape).background(colors.bg).border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.ink) { content() }
    }
}

@Composable
private fun TileTitle(text: String, colors: TileColors, sub: String?) {
    val look = LocalLook.current
    Column {
        Text(text, color = colors.ink, fontFamily = look.headings, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        sub?.let { Text(it, color = colors.sub, fontFamily = look.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Плитка раздела: значок сверху, название и итог снизу. */
@Composable
private fun SectionTile(
    title: String, sub: String?, colors: TileColors, onClick: () -> Unit, modifier: Modifier,
    top: @Composable () -> Unit,
) {
    BentoTile(colors, onClick, modifier) {
        top()
        Spacer(Modifier.weight(1f))
        TileTitle(title, colors, sub)
    }
}

@Composable
private fun TileIcon(icon: ImageVector, colors: TileColors) =
    Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))

/** Кружки цветов внахлёст. */
@Composable
private fun Swatches(colors: List<Int>, ring: Color) {
    Row {
        colors.forEachIndexed { i, c ->
            Box(
                Modifier.offset(x = (-8 * i).dp).size(24.dp).clip(CircleShape).background(Color(c)).border(2.dp, ring, CircleShape),
            )
        }
    }
}

/** Пилюля-кнопка: тёмная в «Плитках», цвета акцента в остальных оформлениях. */
@Composable
fun PillButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val (bg, fg) = if (look.style == AppStyle.BENTO) look.text to look.background else look.primary to look.onPrimary
    Row(
        modifier.height(44.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)) }
        Text(text, color = fg, fontFamily = look.body, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

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
        Text(look.title(title), color = look.text, fontFamily = look.headings, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Главный экран «Обоев»: плитки разного размера, каждая открывает свой раздел.
 * withPreview — плитка с превью телефона (на широком экране превью и так рядом).
 */
fun LazyListScope.wallpaperHub(
    theme: Theme,
    a: ScreenActions,
    phone: ImageBitmap?,
    withPreview: Boolean,
    onDetail: (WallDetail) -> Unit,
    devices: (@Composable (Modifier) -> Unit)?,
) {
    item(key = "hub_head") {
        val look = LocalLook.current
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Тема сейчас", color = look.muted, fontFamily = look.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(theme.name, color = look.text, fontFamily = look.headings, fontWeight = FontWeight.Black, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            PillButton("Из фото", NavIcons.Camera, a.themeFromPhoto)
        }
    }
    item(key = "hub_grid") {
        val look = LocalLook.current
        val gap = 10.dp
        val bg = look.tile(BentoColors.Yellow, Color(0xFF5A4510))
        val colors = look.tile(BentoColors.Coral, Color(0xFF5C2219))
        val mode = look.tile(BentoColors.Periwinkle, Color(0xFF27336B))
        val bgTile: @Composable (Modifier) -> Unit = { m ->
            SectionTile("Фон", backgroundShort(theme), bg, { onDetail(WallDetail.BACKGROUND) }, m) { TileIcon(NavIcons.Wallpaper, bg) }
        }
        val colorsTile: @Composable (Modifier) -> Unit = { m ->
            SectionTile("Цвета", "Цифры и акцент", colors, { onDetail(WallDetail.COLORS) }, m) {
                Swatches(listOf(theme.textColor, theme.accentColor) + theme.background.colors.take(1), colors.ink)
            }
        }
        val modeTile: @Composable (Modifier) -> Unit = { m ->
            SectionTile("Светлая/тёмная", theme.mode.title, mode, { onDetail(WallDetail.MODE) }, m) { TileIcon(NavIcons.Moon, mode) }
        }
        if (withPreview) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val col = (maxWidth - gap) / 2
                // Превью — в пропорциях экрана телефона, но не выше, чем нужно трём плиткам справа.
                val h = minOf(col * (1170f / 540f), col * 1.9f, 380.dp)
                Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    PreviewTile(theme, a, phone, Modifier.weight(1f).fillMaxHeight(), onDetail)
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        bgTile(Modifier.weight(1f).fillMaxWidth())
                        colorsTile(Modifier.weight(1f).fillMaxWidth())
                        modeTile(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                bgTile(Modifier.weight(1f).fillMaxHeight())
                colorsTile(Modifier.weight(1f).fillMaxHeight())
                modeTile(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    item(key = "hub_row2") {
        val look = LocalLook.current
        val clock = look.tile(BentoColors.Mint, Color(0xFF17493A))
        val lock = look.tile(BentoColors.Lilac, Color(0xFF3F2E6E))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WallClockTile(theme, a, clock, Modifier.weight(1.3f).fillMaxHeight()) { onDetail(WallDetail.CLOCK) }
            SectionTile(
                "Блокировка", if (theme.lockBackground == null) "Как рабочий стол" else "Свои обои", lock,
                { onDetail(WallDetail.LOCK) }, Modifier.weight(1f).fillMaxHeight(),
            ) { TileIcon(NavIcons.Lock, lock) }
        }
    }
    if (devices != null) {
        item(key = "hub_devices") { devices(Modifier.fillMaxWidth().padding(top = 10.dp)) }
    }
    item(key = "hub_themes") {
        val saved by a.vm.saved.collectAsStateWithLifecycle()
        Spacer(Modifier.height(8.dp))
        ThemeStrip(saved, theme.id, onPhoto = a.themeFromPhoto, onSelect = a.vm::select, onDelete = a.vm::delete, onSave = { onDetail(WallDetail.SAVE) })
    }
}

/** Плитка-превью: обои с виджетами; по нажатию — подвинуть часы и фото (если есть что двигать). */
@Composable
private fun PreviewTile(theme: Theme, a: ScreenActions, phone: ImageBitmap?, modifier: Modifier, onDetail: (WallDetail) -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.radius)
    val arrange = arrangeLabel(theme, ArrangeTarget.HOME)
    Box(
        modifier.clip(shape).background(Color.Black).border(1.dp, look.cardBorder, shape)
            .clickable(role = Role.Button) { if (arrange != null) a.arrange(ArrangeTarget.HOME) else onDetail(WallDetail.BACKGROUND) }
            .semantics { contentDescription = "Превью телефона" },
    ) {
        phone?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Text(
            arrange ?: "Рабочий стол",
            color = BentoColors.Ink, fontFamily = look.body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** «Часы на обоях»: переключатель прямо на плитке, остальное — внутри. */
@Composable
private fun WallClockTile(theme: Theme, a: ScreenActions, colors: TileColors, modifier: Modifier, onOpen: () -> Unit) {
    val look = LocalLook.current
    val wall = theme.wallClock
    val photo = theme.background.type == BackgroundType.PHOTO
    BentoTile(colors, onOpen, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(NavIcons.Clock, colors)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = wall != null,
                onCheckedChange = { on -> a.vm.setWallClock(if (on) WallClock(depth = photo) else null) },
                modifier = Modifier.semantics { contentDescription = "Часы на обоях" },
                colors = if (look.style == AppStyle.BENTO) SwitchDefaults.colors(
                    checkedTrackColor = BentoColors.Ink, checkedThumbColor = colors.bg,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.6f), uncheckedBorderColor = BentoColors.Ink, uncheckedThumbColor = BentoColors.Ink,
                ) else SwitchDefaults.colors(),
            )
        }
        Spacer(Modifier.weight(1f))
        TileTitle("Часы на обоях", colors, wallClockSummary(theme).removePrefix("Вкл · ").replaceFirstChar { it.uppercase() })
    }
}

/** Плитка устройств: часы, планшет и телевизор — превью и состояние связи. */
@Composable
fun DevicesTile(
    watch: ImageBitmap?, tablet: ImageBitmap?, tv: ImageBitmap?,
    title: String, status: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier,
) {
    val look = LocalLook.current
    val colors = look.inkTile()
    BentoTile(colors, onClick, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            watch?.let {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF3E3754)).padding(4.dp).clip(CircleShape).background(Color.Black)) {
                    Image(it, contentDescription = "Превью часов", modifier = Modifier.fillMaxSize())
                }
            }
            tablet?.let { DeviceScreen(it, 56.dp, 1.6f, "Превью планшета") }
            tv?.let { DeviceScreen(it, 50.dp, 16f / 9f, "Превью телевизора") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = colors.ink, fontFamily = look.headings, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
                status()
            }
        }
    }
}

@Composable
private fun DeviceScreen(image: ImageBitmap, h: Dp, aspect: Float, description: String) {
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.height(h).width(h * aspect).clip(shape).background(Color.Black).border(2.dp, Color(0xFF3E3754), shape)) {
        Image(image, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Нижнее меню «Плиток»: плавающая капсула, выбранный раздел — тёмная пилюля. */
@Composable
fun BentoNav(tab: Tab, tabs: List<Tab>, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().background(look.bar).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(look.card).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { t ->
                val on = t == tab
                Row(
                    Modifier.weight(if (on) 1.25f else 1f).fillMaxHeight().clip(CircleShape)
                        .background(if (on) look.text else Color.Transparent)
                        .clickable(role = Role.Tab) { onTab(t) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (on) {
                        Icon(t.icon, null, tint = look.background, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        t.title, color = if (on) look.background else look.muted, fontFamily = look.body,
                        fontWeight = if (on) FontWeight.Black else FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

private fun backgroundShort(t: Theme): String {
    val type = when (t.background.type) {
        BackgroundType.AURORA -> "Аврора"
        BackgroundType.LINEAR -> "Градиент"
        BackgroundType.RADIAL -> "Сияние"
        BackgroundType.SOLID -> "Цвет"
        BackgroundType.PHOTO -> "Фото"
    }
    return if (t.dim > 0f) "$type · ${(t.dim * 100).toInt()}%" else type
}
