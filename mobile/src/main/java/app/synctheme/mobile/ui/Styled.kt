package app.synctheme.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** «Печенье», «цветок» и «клевер» — круг с волнистым краем. depth — глубина волн в долях радиуса. */
class WavyShape(private val lobes: Int, private val depth: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(wavyPath(size, lobes, depth, rotation))
}

fun wavyPath(size: Size, lobes: Int, depth: Float, rotation: Float = 0f, center: Offset = size.center): Path {
    val r = min(size.width, size.height) / 2f
    val amp = r * depth
    val path = Path()
    val steps = 144
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val rr = r - amp + amp * cos(lobes * (t + rotation))
        val x = center.x + (rr * sin(t)).toFloat()
        val y = center.y - (rr * cos(t)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private val Size.center get() = Offset(width / 2f, height / 2f)

val CookieShape = WavyShape(9, 0.07f)
val FlowerShape = WavyShape(6, 0.12f)
val CloverShape = WavyShape(4, 0.15f)

/** Свечение «Неона»: цветная тень вокруг элемента. В остальных стилях ничего не делает. */
fun Modifier.glow(look: Look, shape: Shape, elevation: Dp = 14.dp, strength: Float = 1f): Modifier =
    if (!look.glows) this else this.shadow(
        elevation = elevation, shape = shape, clip = false,
        ambientColor = look.accent.copy(alpha = strength * if (look.dark) 1f else 0.6f),
        spotColor = look.accent.copy(alpha = strength * if (look.dark) 1f else 0.6f),
    )

/** Фон экрана с декором выбранного стиля. */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val look = LocalLook.current
    CompositionLocalProvider(LocalContentColor provides look.text) {
        Box(
            modifier.fillMaxSize().background(look.background).drawBehind { drawDecor(look) },
            content = content,
        )
    }
}

fun DrawScope.drawDecor(look: Look) {
    val w = size.width
    when (look.style) {
        AppStyle.GLASS -> {
            val r1 = w * 0.62f
            drawCircle(Brush.radialGradient(listOf(look.decor, Color.Transparent), Offset(w * 0.12f, w * 0.1f), r1), r1, Offset(w * 0.12f, w * 0.1f))
            val r2 = w * 0.55f
            val c2 = Offset(w * 1.02f, size.height * 0.42f)
            drawCircle(Brush.radialGradient(listOf(look.decor.copy(alpha = look.decor.alpha * 0.6f), Color.Transparent), c2, r2), r2, c2)
        }
        AppStyle.EXPRESSIVE -> {
            val s = w * 0.7f
            val path = wavyPath(Size(s, s), 6, 0.17f, center = Offset(w * 0.98f, size.height * 0.93f))
            drawPath(path, look.decor.copy(alpha = 0.55f))
        }
        AppStyle.NEON -> {
            val step = 26.dp.toPx()
            var x = 0f
            while (x < w) { drawLine(look.decor, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(look.decor, Offset(0f, y), Offset(w, y), 1f); y += step }
            val c = Offset(w / 2f, 190.dp.toPx())
            val r = w * 0.6f
            drawCircle(Brush.radialGradient(listOf(look.accent.copy(alpha = if (look.dark) 0.26f else 0.14f), Color.Transparent), c, r), r, c)
        }
    }
}

/** Значок приложения в шапке: свой для каждого стиля. */
@Composable
fun AppMark(look: Look, size: Dp = 36.dp) {
    Canvas(Modifier.size(size)) { drawAppMark(look) }
}

fun DrawScope.drawAppMark(look: Look) {
    val s = size.minDimension
    val u = s / 48f
    when (look.style) {
        AppStyle.GLASS -> {
            drawRoundRect(
                Brush.linearGradient(listOf(mix(look.accent, Color.White, 0.45f), look.accent), Offset.Zero, Offset(s, s)),
                Offset(2 * u, 2 * u), Size(44 * u, 44 * u), CornerRadius(13 * u),
            )
            drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(2 * u, 2 * u), Size(44 * u, 22 * u), CornerRadius(13 * u))
            drawRoundRect(Color.White, Offset(11 * u, 10 * u), Size(15 * u, 27 * u), CornerRadius(4 * u), style = Stroke(2.4f * u))
            drawCircle(Color.White, 9 * u, Offset(31 * u, 27 * u))
            val hand = Path().apply { moveTo(31 * u, 22.5f * u); lineTo(31 * u, 27 * u); lineTo(34 * u, 29 * u) }
            drawPath(hand, look.accent, style = Stroke(2.2f * u, cap = StrokeCap.Round))
        }
        AppStyle.EXPRESSIVE -> {
            drawPath(wavyPath(size, 9, 0.09f), look.primary)
            val c = Offset(s / 2, s / 2)
            drawLine(look.onPrimary, c, Offset(s / 2, s * 0.26f), 3.4f * u, StrokeCap.Round)
            drawLine(look.onPrimary, c, Offset(s * 0.66f, s * 0.6f), 3.4f * u, StrokeCap.Round)
            drawCircle(look.onPrimary, 2.6f * u, c)
        }
        AppStyle.NEON -> drawNeonRing(look.accent, s, u, tile = true)
    }
}

/** Кольцо «Неона» с разрывом — оно же иконка приложения. */
fun DrawScope.drawNeonRing(accent: Color, s: Float, u: Float, tile: Boolean) {
    if (tile) drawRoundRect(Color(0xFF0C0C12), Offset(u, u), Size(46 * u, 46 * u), CornerRadius(12 * u))
    val c = Offset(s / 2, s / 2)
    val r = 13 * u
    val tl = Offset(c.x - r, c.y - r)
    val box = Size(2 * r, 2 * r)
    listOf(10f to 0.10f, 6.5f to 0.22f).forEach { (w, a) ->
        drawArc(accent.copy(alpha = a), -60f, 273f, false, tl, box, style = Stroke(w * u, cap = StrokeCap.Round))
    }
    drawArc(accent, -60f, 273f, false, tl, box, style = Stroke(3.2f * u, cap = StrokeCap.Round))
    drawCircle(accent.copy(alpha = 0.25f), 6.5f * u, c)
    drawCircle(mix(accent, Color.White, 0.5f), 4 * u, c)
}

@Composable
fun AppTitle(look: Look) {
    when (look.style) {
        AppStyle.NEON -> Text(
            buildAnnotatedString {
                append("SYNC")
                withStyle(SpanStyle(color = look.accentText, shadow = if (look.dark) Shadow(look.accent, blurRadius = 24f) else null)) { append("THEME") }
            },
            fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.5.sp, color = look.text,
        )
        AppStyle.EXPRESSIVE -> Text("SyncTheme", fontFamily = look.headings, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.4).sp, color = look.text)
        AppStyle.GLASS -> Text("SyncTheme", fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, color = look.text)
    }
}

/** Плашка статуса часов в шапке. */
@Composable
fun StatusChip(dot: Color, text: String, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = if (look.style == AppStyle.NEON) RoundedCornerShape(8.dp) else CircleShape
    val container = when (look.style) {
        AppStyle.GLASS -> look.control
        AppStyle.EXPRESSIVE -> look.highlight
        AppStyle.NEON -> Color.Transparent
    }
    val border = when (look.style) {
        AppStyle.GLASS -> look.cardBorder
        AppStyle.EXPRESSIVE -> Color.Transparent
        AppStyle.NEON -> look.accent.copy(alpha = 0.6f)
    }
    val content = when (look.style) {
        AppStyle.EXPRESSIVE -> look.onHighlight
        AppStyle.NEON -> look.accentText
        AppStyle.GLASS -> look.text
    }
    Row(
        Modifier.clip(shape).background(container).border(1.dp, border, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).glow(look, CircleShape, 6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(
            if (look.style == AppStyle.NEON) text.uppercase() else text,
            color = content, fontFamily = look.body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
        )
    }
}

/** Вкладки в стиле оформления; ширина делится пропорционально длине подписей. */
@Composable
fun StyledTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val font = look.body
    when (look.style) {
        AppStyle.GLASS -> Row(
            modifier.fillMaxWidth().clip(CircleShape).background(look.control).border(1.dp, look.cardBorder, CircleShape).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                Text(
                    t, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    color = if (on) look.onPrimary else look.muted, fontFamily = font,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.weight(t.length + 3f).clip(CircleShape)
                        .background(if (on) look.primary else Color.Transparent)
                        .clickable { onSelect(i) }.padding(vertical = 9.dp),
                )
            }
        }

        AppStyle.EXPRESSIVE -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                Text(
                    t, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    color = if (on) look.onPrimary else look.onHighlight, fontFamily = font,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.weight(t.length + 3f).clip(CircleShape)
                        .background(if (on) look.primary else look.control)
                        .clickable { onSelect(i) }.padding(vertical = 10.dp),
                )
            }
        }

        AppStyle.NEON -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                val shape = RoundedCornerShape(10.dp)
                Text(
                    t, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    color = if (on) (if (look.dark) Color.White else look.text) else look.muted, fontFamily = font,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp,
                    modifier = Modifier.weight(t.length + 3f)
                        .then(if (on) Modifier.glow(look, shape, 10.dp, 0.8f) else Modifier)
                        .clip(shape)
                        .background(if (on) mix(look.control, look.accent, 0.3f) else look.control)
                        .border(1.dp, if (on) look.accent else look.cardBorder, shape)
                        .clickable { onSelect(i) }.padding(vertical = 9.dp),
                )
            }
        }
    }
}

/** Главная кнопка («Применить»). quiet — спокойный вид, когда применять нечего. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    quiet: Boolean = false,
    height: Dp? = null,
) {
    val look = LocalLook.current
    val shape = when (look.style) {
        AppStyle.GLASS -> CircleShape
        AppStyle.EXPRESSIVE -> RoundedCornerShape(22.dp)
        AppStyle.NEON -> RoundedCornerShape(14.dp)
    }
    val h = height ?: if (look.style == AppStyle.EXPRESSIVE) 58.dp else 52.dp
    val loud = enabled && !quiet
    Box(
        modifier.fillMaxWidth().height(h)
            .then(if (loud) Modifier.glow(look, shape, 18.dp) else Modifier)
            .clip(shape)
            .background(
                when {
                    !enabled -> look.muted.copy(alpha = 0.3f)
                    quiet -> look.control
                    else -> look.primary
                },
            )
            .border(1.dp, if (quiet && enabled) look.primary.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (look.style == AppStyle.NEON) text.uppercase() else text,
            color = if (quiet && enabled) look.text else look.onPrimary,
            fontFamily = look.headings,
            fontWeight = if (look.style == AppStyle.GLASS) FontWeight.ExtraBold else FontWeight.Black,
            fontSize = if (look.style == AppStyle.NEON) 15.sp else 16.sp,
            letterSpacing = if (look.style == AppStyle.NEON) 2.sp else 0.sp,
        )
    }
}

/** Выбор оформления приложения на вкладке «Настройки». */
@Composable
fun AppearanceSection(accentColor: Int) {
    val context = LocalContext.current
    val appearance by AppearanceStore.get(context).collectAsState()
    val look = LocalLook.current
    Section("Оформление приложения") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppStyle.entries.forEach { style ->
                val preview = lookFor(style, look.dark, accentColor)
                val on = style == appearance.style
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.weight(1f).clip(shape)
                        .border(if (on) 2.dp else 1.dp, if (on) look.primary else look.muted.copy(alpha = 0.25f), shape)
                        .clickable { AppearanceStore.set(context, appearance.copy(style = style)) },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(112.dp)) { drawMiniPreview(preview) }
                    Text(
                        style.title, color = look.text, fontFamily = preview.headings, fontWeight = FontWeight.Bold,
                        fontSize = if (style == AppStyle.NEON) 11.sp else 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(appearance.style.note, style = MaterialTheme.typography.bodySmall, color = look.muted)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = appearance.mode == mode,
                    onClick = { AppearanceStore.set(context, appearance.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(i, AppMode.entries.size),
                    label = { Text(mode.title, maxLines = 1, fontSize = 13.sp) },
                )
            }
        }
        Text(
            "Цвет акцента, как и раньше, подстраивается под обои.",
            style = MaterialTheme.typography.bodySmall, color = look.muted,
        )
    }
}

/** Миниатюра оформления: фон, превью-карточка, вкладка и кнопка. */
private fun DrawScope.drawMiniPreview(look: Look) {
    drawRect(look.background)
    drawDecor(look)
    val w = size.width
    val pad = 10.dp.toPx()
    val r = (look.radius.toPx() / 2.4f)
    val card = Size(w - 2 * pad, size.height * 0.42f)
    val cardTl = Offset(pad, pad + 16.dp.toPx())
    // Заголовок: значок + полоска названия.
    val markSize = 12.dp.toPx()
    val markScope = Size(markSize, markSize)
    translate(pad, pad - 2.dp.toPx()) { drawMiniMark(look, markScope) }
    drawRoundRect(look.text.copy(alpha = 0.85f), Offset(pad + markSize + 5.dp.toPx(), pad + 2.dp.toPx()), Size(w * 0.36f, 5.dp.toPx()), CornerRadius(3.dp.toPx()))
    drawRoundRect(look.card, cardTl, card, CornerRadius(r))
    if (look.cardBorder.alpha > 0f) drawRoundRect(look.cardBorder, cardTl, card, CornerRadius(r), style = Stroke(1.dp.toPx()))
    if (look.style == AppStyle.EXPRESSIVE) {
        val s = card.height * 1.1f
        drawPath(wavyPath(Size(s, s), 6, 0.17f, center = Offset(cardTl.x + card.width * 0.85f, cardTl.y + card.height * 0.2f)), look.highlight)
    }
    // Телефон и часы внутри карточки.
    val phone = Size(card.height * 0.42f, card.height * 0.78f)
    val phoneTl = Offset(cardTl.x + card.width * 0.18f, cardTl.y + (card.height - phone.height) / 2)
    drawRoundRect(Brush.linearGradient(listOf(mix(look.accent, Color.Black, 0.35f), mix(look.accent, Color.Black, 0.8f)), phoneTl, phoneTl + Offset(phone.width, phone.height)), phoneTl, phone, CornerRadius(phone.width * 0.22f))
    val watchC = Offset(cardTl.x + card.width * 0.68f, cardTl.y + card.height / 2)
    val watchR = card.height * 0.3f
    drawCircle(mix(look.accent, Color.Black, 0.7f), watchR, watchC)
    drawCircle(if (look.glows) look.accent else look.text.copy(alpha = 0.35f), watchR, watchC, style = Stroke(1.5.dp.toPx()))
    // Вкладки.
    val tabY = cardTl.y + card.height + 7.dp.toPx()
    val tabH = 9.dp.toPx()
    val tabW = (w - 2 * pad - 3 * 3.dp.toPx()) / 4
    repeat(4) { i ->
        val tl = Offset(pad + i * (tabW + 3.dp.toPx()), tabY)
        val on = i == 0
        val corner = if (look.style == AppStyle.NEON) CornerRadius(2.dp.toPx()) else CornerRadius(tabH / 2)
        drawRoundRect(if (on) look.primary else look.control, tl, Size(tabW, tabH), corner)
        if (look.style == AppStyle.NEON) drawRoundRect(if (on) look.accent else look.cardBorder, tl, Size(tabW, tabH), corner, style = Stroke(1f))
    }
    // Кнопка «Применить».
    val btnH = 12.dp.toPx()
    val btnTl = Offset(pad, size.height - pad - btnH)
    val corner = when (look.style) {
        AppStyle.GLASS -> CornerRadius(btnH / 2)
        AppStyle.EXPRESSIVE -> CornerRadius(btnH * 0.4f)
        AppStyle.NEON -> CornerRadius(3.dp.toPx())
    }
    if (look.glows) drawRoundRect(look.accent.copy(alpha = 0.35f), btnTl - Offset(3f, 3f), Size(w - 2 * pad + 6f, btnH + 6f), corner)
    drawRoundRect(look.primary, btnTl, Size(w - 2 * pad, btnH), corner)
}

private fun DrawScope.drawMiniMark(look: Look, s: Size) {
    val u = s.minDimension / 48f
    when (look.style) {
        AppStyle.GLASS -> drawRoundRect(look.accent, Offset.Zero, s, CornerRadius(13 * u))
        AppStyle.EXPRESSIVE -> drawPath(wavyPath(s, 9, 0.09f), look.primary)
        AppStyle.NEON -> {
            drawRoundRect(Color(0xFF0C0C12), Offset.Zero, s, CornerRadius(12 * u))
            drawArc(look.accent, -60f, 273f, false, Offset(11 * u, 11 * u), Size(26 * u, 26 * u), style = Stroke(5 * u, cap = StrokeCap.Round))
        }
    }
}
