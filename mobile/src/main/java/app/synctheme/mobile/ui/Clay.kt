package app.synctheme.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * «Мята» — дизайн-система по образцу «Product UI Styleguide»: тёплый светлый фон, матовые объёмные
 * плитки с толщиной снизу и мягкой тенью, мятный главный цвет, вдавленные поля в серой оправе.
 * Здесь токены (цвета, шрифты, отступы, радиусы, уровни высоты) и компоненты из образца:
 * кнопки, поля, чипы, вкладки двух видов, плашки-сообщения и карточка со стопкой слоёв.
 */

// ---------------------------------------------------------------- токены

/** Цвет плитки: лицевая сторона и её торец (толщина, видная снизу). */
class ClayTone(val face: Color, val side: Color, val content: Color)

class ClayColors(
    /** Фон экрана. */
    val canvas: Color,
    /** Карточки, кнопки второго плана («Surface»). */
    val surface: ClayTone,
    /** Главный цвет («Primary»): кнопки, выбранная вкладка, выбранный чип. */
    val mint: ClayTone,
    /** Свечение фокуса («Focus ring»). */
    val focus: Color,
    /** Оправа полей ввода («Border»). */
    val ring: ClayTone,
    /** Дорожка вкладок. */
    val track: ClayTone,
    val disabled: ClayTone,
    val success: ClayTone,
    val warning: ClayTone,
    val error: ClayTone,
    /** Вдавленное поле. */
    val field: Color,
    /** Тень под плитками. */
    val shadow: Color,
    /** Блик на верхней кромке. */
    val rim: Color,
    /** Тень внутри вдавленного поля. */
    val hollow: Color,
    val ink: Color,
    val body: Color,
    val muted: Color,
    /** Ссылки: мятный, но читаемый на фоне. */
    val link: Color,
)

object ClayPalette {
    val Light = ClayColors(
        canvas = Color(0xFFE6E2DC),
        surface = ClayTone(Color(0xFFF3F1ED), Color(0xFFCFC9C1), Color(0xFF2E2E2E)),
        mint = ClayTone(Color(0xFFA8DCC3), Color(0xFF7FBB9F), Color(0xFF203229)),
        focus = Color(0xFF6FE0B5),
        ring = ClayTone(Color(0xFFC6C1B9), Color(0xFFA7A097), Color(0xFF2E2E2E)),
        track = ClayTone(Color(0xFFBDB8B0), Color(0xFFA19B92), Color(0xFF34332F)),
        disabled = ClayTone(Color(0xFFC9C4BC), Color(0xFFAEA89F), Color(0xFFF1EDE7)),
        success = ClayTone(Color(0xFFB0DCC7), Color(0xFF88BEA4), Color(0xFF243229)),
        warning = ClayTone(Color(0xFFF1DE95), Color(0xFFCFB86C), Color(0xFF3A3220)),
        error = ClayTone(Color(0xFFF0958F), Color(0xFFCC6F69), Color(0xFF3A2120)),
        field = Color(0xFFEDEAE5),
        shadow = Color(0x3D4A4036),
        rim = Color(0xB3FFFFFF),
        hollow = Color(0x52574B3E),
        ink = Color(0xFF2E2E2E),
        body = Color(0xFF4A4A4A),
        muted = Color(0xFF85817B),
        link = Color(0xFF2E8A66),
    )
    val Dark = ClayColors(
        canvas = Color(0xFF232427),
        surface = ClayTone(Color(0xFF303135), Color(0xFF17181A), Color(0xFFEEEDE9)),
        mint = ClayTone(Color(0xFF8ED3B3), Color(0xFF5B9C80), Color(0xFF13241C)),
        focus = Color(0xFF5FE0AE),
        ring = ClayTone(Color(0xFF414247), Color(0xFF26272A), Color(0xFFEEEDE9)),
        track = ClayTone(Color(0xFF3B3C41), Color(0xFF232427), Color(0xFFD9D7D2)),
        disabled = ClayTone(Color(0xFF45464B), Color(0xFF2C2D31), Color(0xFF7C7D82)),
        success = ClayTone(Color(0xFF7FC3A4), Color(0xFF55937A), Color(0xFF14241C)),
        warning = ClayTone(Color(0xFFDCC575), Color(0xFFA9944A), Color(0xFF2B2412)),
        error = ClayTone(Color(0xFFDE7F79), Color(0xFFA5524D), Color(0xFF2E1513)),
        field = Color(0xFF29292C),
        shadow = Color(0xA6000000),
        rim = Color(0x1FFFFFFF),
        hollow = Color(0xCC000000),
        ink = Color(0xFFEEEDE9),
        body = Color(0xFFCFCDC8),
        muted = Color(0xFF9A9893),
        link = Color(0xFF8ED3B3),
    )
}

val Look.clay: ClayColors get() = if (dark) ClayPalette.Dark else ClayPalette.Light

val Look.isClay get() = style == AppStyle.CLAY

/** Отступы из образца: 2, 6, 10, 20, 25 и 70. */
object ClaySpace {
    val xxs = 2.dp
    val xs = 6.dp
    val s = 10.dp
    val m = 20.dp
    val l = 25.dp
    val xl = 70.dp
}

/** Радиусы: плитка образца — 20 при стороне 70; кнопки и поля — капсулы. */
object ClayRadius {
    val small = 10.dp
    val tile = 20.dp
    val card = 24.dp
}

/** Уровни высоты 1–5: толщина плитки и размер тени. */
enum class ClayLevel(val depth: Dp, val blur: Dp, val drop: Dp) {
    L1(2.dp, 4.dp, 2.dp), L2(3.dp, 7.dp, 3.dp), L3(4.dp, 10.dp, 5.dp), L4(5.dp, 14.dp, 7.dp), L5(7.dp, 18.dp, 9.dp),
}

/** Шкала шрифтов: Display, Heading, Title, Medium, Caption. */
object ClayType {
    val display = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    val heading = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val title = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val medium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp)
    val caption = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
}

// ---------------------------------------------------------------- поверхности

/**
 * Объёмная плитка: мягкая тень, торец высотой level.depth под лицевой стороной и блик по верхней
 * кромке. Торец и тень рисуются ниже границ элемента — снаружи нужен отступ не меньше depth.
 */
fun Modifier.claySlab(look: Look, radius: Dp, tone: ClayTone = look.clay.surface, level: ClayLevel = ClayLevel.L2): Modifier =
    drawBehind { drawSlab(look.clay, tone, radius.toPx(), level) }

/** Вдавленное поле; focused — мятная кромка, как у поля ввода в фокусе. */
fun Modifier.clayHollow(look: Look, radius: Dp, focused: Boolean = false): Modifier =
    drawBehind { drawHollow(look.clay, radius.toPx(), 0f, focused) }

/** Поле ввода: серая объёмная оправа, внутри — вдавленное поле. */
fun Modifier.clayInput(look: Look, radius: Dp, focused: Boolean = false, ring: Dp = 7.dp): Modifier =
    drawBehind {
        val c = look.clay
        drawSlab(c, c.ring, radius.toPx(), ClayLevel.L2)
        drawHollow(c, radius.toPx(), ring.toPx(), focused)
    }

/** Карточка со стопкой слоёв под ней, как «Cards» в образце. Снизу нужен отступ ~[clayStackExtra]. */
fun Modifier.clayStack(look: Look, radius: Dp, layers: Int = 2): Modifier =
    drawBehind {
        val c = look.clay
        val r = radius.toPx()
        for (i in layers downTo 1) {
            val inset = 9.dp.toPx() * i
            val drop = 6.dp.toPx() * i
            val face = mix(c.surface.face, c.surface.side, 0.18f * i)
            drawSlab(c, ClayTone(face, c.surface.side, c.ink), r, ClayLevel.L1, SlabRect(inset, drop, size.width - 2 * inset, size.height))
        }
        drawSlab(c, c.surface, r, ClayLevel.L3)
    }

/** Сколько места занимает стопка слоёв под карточкой. */
val clayStackExtra = 16.dp

/** Мятное свечение вокруг элемента («Hover» и «Focus ring» в образце). */
fun Modifier.clayGlow(look: Look, radius: Dp, width: Dp = 2.dp): Modifier =
    drawBehind {
        val c = look.clay
        val r = radius.toPx()
        val w = width.toPx()
        drawIntoCanvas { canvas ->
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = w
                color = c.focus.toArgb()
                setShadowLayer(w * 3.5f, 0f, 0f, c.focus.copy(alpha = 0.85f).toArgb())
            }
            val o = w * 1.6f
            canvas.nativeCanvas.drawRoundRect(-o, -o, size.width + o, size.height + o, r + o, r + o, p)
        }
    }

private class SlabRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun DrawScope.drawSlab(c: ClayColors, tone: ClayTone, r: Float, level: ClayLevel, rect: SlabRect? = null) {
    val l = rect?.left ?: 0f
    val t = rect?.top ?: 0f
    val w = rect?.width ?: size.width
    val h = rect?.height ?: size.height
    val d = level.depth.toPx()
    val rr = r.coerceAtMost(minOf(w, h) / 2f)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        // Тень и торец: та же форма, сдвинутая вниз на толщину.
        val side = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tone.side.toArgb()
            setShadowLayer(level.blur.toPx(), 0f, level.drop.toPx(), c.shadow.toArgb())
        }
        nc.drawRoundRect(l, t + d, l + w, t + h + d, rr, rr, side)
        // Лицевая сторона: чуть светлее вверху.
        val face = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, t, 0f, t + h,
                intArrayOf(mix(tone.face, Color.White, 0.16f).toArgb(), tone.face.toArgb(), mix(tone.face, tone.side, 0.10f).toArgb()),
                floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP,
            )
        }
        nc.drawRoundRect(l, t, l + w, t + h, rr, rr, face)
        // Блик по верхней кромке.
        val stroke = 1.2f * density
        val rim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            shader = android.graphics.LinearGradient(
                0f, t, 0f, t + minOf(h, rr * 2f + 1f),
                intArrayOf(c.rim.toArgb(), c.rim.copy(alpha = 0f).toArgb()), null, android.graphics.Shader.TileMode.CLAMP,
            )
        }
        val s = stroke / 2
        nc.drawRoundRect(l + s, t + s, l + w - s, t + h - s, rr - s, rr - s, rim)
    }
}

private fun DrawScope.drawHollow(c: ClayColors, r: Float, inset: Float, focused: Boolean) {
    val w = size.width - 2 * inset
    val h = size.height - 2 * inset
    if (w <= 0f || h <= 0f) return
    val rr = (r - inset).coerceIn(0f, minOf(w, h) / 2f)
    val d = 3.dp.toPx()
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = c.field.toArgb() }
        nc.drawRoundRect(inset, inset, inset + w, inset + h, rr, rr, fill)
        val shape = android.graphics.Path().apply { addRoundRect(inset, inset, inset + w, inset + h, rr, rr, android.graphics.Path.Direction.CW) }
        // Рамка вокруг поля: сама обрезана, её тень падает внутрь сверху — поле кажется вдавленным.
        val frame = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(inset - d * 8, inset - d * 8, inset + w + d * 8, inset + h + d * 8, android.graphics.Path.Direction.CW)
            addPath(shape)
        }
        nc.save()
        nc.clipPath(shape)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = c.field.toArgb() }
        p.setShadowLayer(d * 2.2f, 0f, d, c.hollow.toArgb())
        nc.drawPath(frame, p)
        if (focused) {
            val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.dp.toPx()
                color = c.focus.toArgb()
                setShadowLayer(5.dp.toPx(), 0f, 0f, c.focus.toArgb())
            }
            val g = 1.5.dp.toPx()
            nc.drawRoundRect(inset + g, inset + g, inset + w - g, inset + h - g, (rr - g).coerceAtLeast(0f), (rr - g).coerceAtLeast(0f), glow)
        }
        nc.restore()
    }
}

// ---------------------------------------------------------------- компоненты

enum class ClayKind { PRIMARY, SECONDARY }

/** Кнопка-капсула: мятная главная, светлая второстепенная, серая недоступная. */
@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ClayKind = ClayKind.PRIMARY,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    icon: ImageVector? = null,
) {
    val look = LocalLook.current
    val c = look.clay
    val tone = when {
        !enabled -> c.disabled
        kind == ClayKind.PRIMARY -> c.mint
        else -> c.surface
    }
    Row(
        modifier.fillMaxWidth().padding(bottom = ClayLevel.L3.depth).height(height)
            .claySlab(look, height / 2, tone, ClayLevel.L3).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = tone.content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tone.content, style = ClayType.medium, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
    }
}

/** Чип: мятный («Filter»), выбранный — с галочкой и свечением, недоступный — серый. */
@Composable
fun ClayChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    mint: Boolean = true,
) {
    val look = LocalLook.current
    val c = look.clay
    val tone = when {
        !enabled -> c.disabled
        mint || selected -> c.mint
        else -> c.surface
    }
    Row(
        modifier.padding(bottom = ClayLevel.L2.depth).height(38.dp)
            .then(if (selected && enabled) Modifier.clayGlow(look, 19.dp) else Modifier)
            .claySlab(look, 19.dp, tone, ClayLevel.L2).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lead = if (selected) NavIcons.Check else icon
        lead?.let {
            Icon(it, null, tint = tone.content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = tone.content, style = ClayType.caption, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Вкладки-переключатель: серая дорожка, выбранная — мятная плитка. Ширина — по длине подписей. */
@Composable
fun ClaySegmented(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 50.dp) {
    val look = LocalLook.current
    val c = look.clay
    Row(
        modifier.fillMaxWidth().padding(bottom = ClayLevel.L2.depth).height(height)
            .claySlab(look, height / 2, c.track, ClayLevel.L2).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        titles.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(t.length + 4f).fillMaxWidth().padding(bottom = if (on) 3.dp else 0.dp).height(height - 13.dp)
                    .then(if (on) Modifier.claySlab(look, (height - 13.dp) / 2, c.mint, ClayLevel.L1) else Modifier)
                    .clip(CircleShape).clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t, color = if (on) c.mint.content else c.track.content, style = ClayType.caption,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1, softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Вкладки с полосой: подписи, под ними объёмная дорожка, под выбранной — мятный отрезок. */
@Composable
fun ClayUnderlineTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val c = look.clay
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                Text(
                    t, color = if (on) c.ink else c.muted, style = ClayType.medium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 10.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = ClayLevel.L1.depth).height(6.dp).claySlab(look, 3.dp, c.track, ClayLevel.L1)) {
            titles.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(6.dp)
                        .then(if (i == selected) Modifier.claySlab(look, 3.dp, c.mint, ClayLevel.L1) else Modifier),
                )
            }
        }
    }
}

enum class ClayAlertKind { SUCCESS, WARNING, ERROR }

/** Плашка-сообщение: мятная «готово», жёлтая «внимание», коралловая «ошибка». */
@Composable
fun ClayAlert(text: String, kind: ClayAlertKind, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val look = LocalLook.current
    val c = look.clay
    val (tone, icon) = when (kind) {
        ClayAlertKind.SUCCESS -> c.success to NavIcons.CheckCircle
        ClayAlertKind.WARNING -> c.warning to NavIcons.Warning
        ClayAlertKind.ERROR -> c.error to NavIcons.ErrorCircle
    }
    Row(
        modifier.fillMaxWidth().padding(bottom = ClayLevel.L2.depth).heightIn(min = 46.dp)
            .claySlab(look, 23.dp, tone, ClayLevel.L2).clip(RoundedCornerShape(23.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tone.content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = tone.content, style = ClayType.medium, fontWeight = FontWeight.Medium)
    }
}

/** Карточка со стопкой слоёв: заголовок, текст и что угодно ниже. */
@Composable
fun ClayCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Column(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp + clayStackExtra).clayStack(look, ClayRadius.card).padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides look.clay.ink) { content() }
    }
}

/**
 * Чип выбора для всего приложения: в «Мяте» — объёмная капсула (выбранная — мятная со свечением),
 * в остальных оформлениях — обычный FilterChip.
 */
@Composable
fun StyledChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val look = LocalLook.current
    if (!look.isClay) {
        return androidx.compose.material3.FilterChip(selected, onClick, label, modifier, enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon)
    }
    val c = look.clay
    val tone = when {
        !enabled -> c.disabled
        selected -> c.mint
        else -> c.surface
    }
    Row(
        modifier.padding(top = 4.dp, bottom = 4.dp + ClayLevel.L2.depth).height(36.dp)
            .then(if (selected && enabled) Modifier.clayGlow(look, 18.dp) else Modifier)
            .claySlab(look, 18.dp, tone, ClayLevel.L2).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(start = if (leadingIcon != null) 8.dp else 14.dp, end = if (trailingIcon != null) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides tone.content,
            androidx.compose.material3.LocalTextStyle provides ClayType.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        ) {
            if (leadingIcon != null) leadingIcon() else if (selected) Icon(NavIcons.Check, null, Modifier.size(15.dp))
            label()
            trailingIcon?.invoke()
        }
    }
}

/** Кнопки Material по всему приложению: в «Мяте» — объёмные капсулы, в остальных — как были. */
@Composable
fun StyledButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    if (!look.isClay) return androidx.compose.material3.Button(onClick, modifier, enabled, content = content)
    ClayContentButton(onClick, modifier, enabled, look.clay.mint, content)
}

@Composable
fun StyledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    if (!look.isClay) return androidx.compose.material3.FilledTonalButton(onClick, modifier, enabled, content = content)
    ClayContentButton(onClick, modifier, enabled, look.clay.surface, content)
}

@Composable
fun StyledOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    if (!look.isClay) return androidx.compose.material3.OutlinedButton(onClick, modifier, enabled, content = content)
    ClayContentButton(onClick, modifier, enabled, look.clay.surface, content)
}

@Composable
private fun ClayContentButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, tone: ClayTone, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val t = if (enabled) tone else look.clay.disabled
    Row(
        modifier.padding(top = 2.dp, bottom = 2.dp + ClayLevel.L2.depth).heightIn(min = 44.dp)
            .claySlab(look, 22.dp, t, ClayLevel.L2).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides t.content,
            androidx.compose.material3.LocalTextStyle provides ClayType.medium.copy(fontWeight = FontWeight.SemiBold),
        ) { content() }
    }
}
