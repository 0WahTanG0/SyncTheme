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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        AppStyle.CLAY, AppStyle.SOFT -> Unit
        AppStyle.GLASS -> {
            val r1 = w * 0.62f
            drawCircle(Brush.radialGradient(listOf(look.decor, Color.Transparent), Offset(w * 0.12f, w * 0.1f), r1), r1, Offset(w * 0.12f, w * 0.1f))
            val r2 = w * 0.55f
            val c2 = Offset(w * 1.02f, size.height * 0.42f)
            drawCircle(Brush.radialGradient(listOf(look.decor.copy(alpha = look.decor.alpha * 0.6f), Color.Transparent), c2, r2), r2, c2)
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
        AppStyle.CLAY -> drawClayMark(u, look)
        AppStyle.SOFT -> drawSoftMark(u, look.dark)
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
    }
}

/** Значок «Мяты»: мятная плитка с торцом (как «Primary» в образце), на ней кольцо со стрелками. */
private fun DrawScope.drawClayMark(u: Float, look: Look) {
    val c = look.clay
    drawRoundRect(c.shadow, Offset(4 * u, 8 * u), Size(40 * u, 38 * u), CornerRadius(13 * u))
    drawRoundRect(c.mint.side, Offset(4 * u, 6 * u), Size(40 * u, 38 * u), CornerRadius(13 * u))
    drawRoundRect(
        Brush.verticalGradient(listOf(mix(c.mint.face, Color.White, 0.25f), c.mint.face), 3 * u, 40 * u),
        Offset(4 * u, 3 * u), Size(40 * u, 38 * u), CornerRadius(13 * u),
    )
    val center = Offset(24 * u, 22 * u)
    drawCircle(c.mint.content, 11 * u, center, style = Stroke(3 * u))
    drawLine(c.mint.content, center, center + Offset(0f, -6 * u), 3 * u, StrokeCap.Round)
    drawLine(c.mint.content, center, center + Offset(4.5f * u, 3 * u), 3 * u, StrokeCap.Round)
}

/** Значок «Мягкого» вида: выпуклый серый круг с кольцом и точкой — как кнопка-отпечаток на образце. */
private fun DrawScope.drawSoftMark(u: Float, dark: Boolean) {
    val c = if (dark) SoftPalette.Dark else SoftPalette.Light
    val center = Offset(24 * u, 24 * u)
    drawCircle(c.lo, 21 * u, center + Offset(1.5f * u, 1.5f * u))
    drawCircle(c.hi, 21 * u, center - Offset(1.5f * u, 1.5f * u))
    drawCircle(c.base, 21 * u, center)
    val ink = if (dark) Color(0xFFF0F0F2) else Color(0xFF2B2C30)
    drawCircle(ink.copy(alpha = 0.75f), 13 * u, center, style = Stroke(3 * u))
    drawCircle(ink, 4 * u, center)
}

@Composable
fun AppTitle(look: Look) {
    when (look.style) {
        AppStyle.CLAY -> Text("SyncTheme", style = ClayType.heading, color = look.text)
        AppStyle.SOFT -> Text("SyncTheme", fontFamily = look.headings, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = look.text)
        AppStyle.GLASS -> Text("SyncTheme", fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, color = look.text)
    }
}

/** Вкладки в стиле оформления; ширина делится пропорционально длине подписей. */
@Composable
fun StyledTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val font = look.body
    when (look.style) {
        AppStyle.CLAY -> ClaySegmented(titles, selected, onSelect, modifier, height = 46.dp)

        AppStyle.SOFT -> Row(
            modifier.fillMaxWidth().clip(CircleShape).background(look.card).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                Text(
                    t, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    color = if (on) look.background else look.muted, fontFamily = font,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.weight(t.length + 3f).clip(CircleShape)
                        .background(if (on) look.text else Color.Transparent)
                        .clickable { onSelect(i) }.padding(vertical = 10.dp),
                )
            }
        }

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
    if (look.style != AppStyle.GLASS) return SoftPill(text, onClick, modifier, strong = enabled && !quiet, enabled = enabled, height = height ?: 56.dp)
    val shape = CircleShape
    val loud = enabled && !quiet
    Box(
        modifier.fillMaxWidth().height(height ?: 52.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> look.muted.copy(alpha = 0.3f)
                    !loud -> look.control
                    else -> look.primary
                },
            )
            .border(1.dp, if (quiet && enabled) look.primary.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (quiet && enabled) look.text else look.onPrimary,
            fontFamily = look.headings, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
        )
    }
}

/** Выбор оформления приложения на вкладке «Настройки». */
@Composable
fun AppearanceSection(accentColor: Int) {
    val context = LocalContext.current
    val appearance by AppearanceStore.get(context).collectAsState()
    val look = LocalLook.current
    Section("Вид приложения") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppStyle.entries.forEach { style ->
                val preview = lookFor(style, look.dark, accentColor)
                val on = style == appearance.style
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.weight(1f)
                        .then(
                            when {
                                look.isClay && on -> Modifier.clayGlow(look, 20.dp)
                                look.isClay -> Modifier
                                else -> Modifier.border(if (on) 2.dp else 1.dp, if (on) look.primary else look.muted.copy(alpha = 0.25f), shape)
                            },
                        )
                        .clip(shape)
                        .clickable { AppearanceStore.set(context, appearance.copy(style = style)) },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(112.dp)) { drawMiniPreview(preview) }
                    Text(
                        style.title, color = look.text, fontFamily = preview.body, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, maxLines = 1, softWrap = false,
                        modifier = Modifier.fillMaxWidth().background(if (look.isClay) look.clay.surface.face else Color.Transparent).padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(appearance.style.note, style = MaterialTheme.typography.bodySmall, color = look.muted)
        StyledTabs(AppMode.entries.map { it.title }, AppMode.entries.indexOf(appearance.mode), { AppearanceStore.set(context, appearance.copy(mode = AppMode.entries[it])) })
        Text(
            "Цвет акцента подстраивается под обои.",
            style = MaterialTheme.typography.bodySmall, color = look.muted,
        )
    }
}

/** Миниатюра оформления: фон, превью-карточка, вкладки и кнопка. */
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
    val phone = Size(card.height * 0.42f, card.height * 0.8f)
    val phoneTl = Offset(cardTl.x + (card.width - phone.width) / 2, cardTl.y + (card.height - phone.height) / 2)
    val photo = Brush.linearGradient(listOf(Color(0xFF1E2A4A), Color(0xFFF29E6D)), phoneTl, phoneTl + Offset(0f, phone.height))
    when (look.style) {
        AppStyle.CLAY -> {
            // Мята: светлая плитка с торцом и стопкой слоя под ней.
            val c = look.clay
            val d = 2.dp.toPx()
            drawRoundRect(mix(c.surface.face, c.surface.side, 0.3f), cardTl + Offset(5.dp.toPx(), 4.dp.toPx() + d), Size(card.width - 10.dp.toPx(), card.height), CornerRadius(r))
            drawRoundRect(c.surface.side, cardTl + Offset(0f, d), card, CornerRadius(r))
            drawRoundRect(c.surface.face, cardTl, card, CornerRadius(r))
            drawRoundRect(photo, phoneTl, phone, CornerRadius(phone.width * 0.2f))
        }
        AppStyle.SOFT -> {
            // Мягкий: выпуклая рамка с телефоном.
            val c = look.soft
            val d = 3.dp.toPx()
            drawRoundRect(c.lo, cardTl + Offset(d, d), card, CornerRadius(r))
            drawRoundRect(c.hi, cardTl - Offset(d, d), card, CornerRadius(r))
            drawRoundRect(c.base, cardTl, card, CornerRadius(r))
            drawRoundRect(photo, phoneTl, phone, CornerRadius(phone.width * 0.2f))
        }
        AppStyle.GLASS -> {
            drawRoundRect(look.card, cardTl, card, CornerRadius(r))
            drawRoundRect(look.cardBorder, cardTl, card, CornerRadius(r), style = Stroke(1.dp.toPx()))
            drawMiniDevices(look, cardTl, card)
        }
    }
    // Вкладки: у «Мяты» — серая дорожка с мятной вкладкой.
    val tabY = cardTl.y + card.height + 8.dp.toPx()
    val tabH = 9.dp.toPx()
    if (look.isClay) {
        val c = look.clay
        drawRoundRect(c.track.side, Offset(pad, tabY + 1.5f.dp.toPx()), Size(w - 2 * pad, tabH + 4.dp.toPx()), CornerRadius(tabH))
        drawRoundRect(c.track.face, Offset(pad, tabY), Size(w - 2 * pad, tabH + 4.dp.toPx()), CornerRadius(tabH))
        drawRoundRect(c.mint.face, Offset(pad + 2.dp.toPx(), tabY + 2.dp.toPx()), Size((w - 2 * pad) * 0.38f, tabH), CornerRadius(tabH / 2))
    } else {
        val tabW = (w - 2 * pad - 3 * 3.dp.toPx()) / 4
        repeat(4) { i ->
            val tl = Offset(pad + i * (tabW + 3.dp.toPx()), tabY)
            drawRoundRect(if (i == 0) look.primary else look.control, tl, Size(tabW, tabH), CornerRadius(tabH / 2))
        }
    }
    // Кнопка «Применить».
    val btnH = 12.dp.toPx()
    val btnTl = Offset(pad, size.height - pad - btnH)
    if (look.isClay) drawRoundRect(look.clay.mint.side, btnTl + Offset(0f, 2.dp.toPx()), Size(w - 2 * pad, btnH), CornerRadius(btnH / 2))
    drawRoundRect(look.primary, btnTl, Size(w - 2 * pad, btnH), CornerRadius(btnH / 2))
}

private fun DrawScope.drawMiniDevices(look: Look, cardTl: Offset, card: Size) {
    val phone = Size(card.height * 0.42f, card.height * 0.78f)
    val phoneTl = Offset(cardTl.x + card.width * 0.18f, cardTl.y + (card.height - phone.height) / 2)
    drawRoundRect(Brush.linearGradient(listOf(mix(look.accent, Color.Black, 0.35f), mix(look.accent, Color.Black, 0.8f)), phoneTl, phoneTl + Offset(phone.width, phone.height)), phoneTl, phone, CornerRadius(phone.width * 0.22f))
    val watchC = Offset(cardTl.x + card.width * 0.68f, cardTl.y + card.height / 2)
    val watchR = card.height * 0.3f
    drawCircle(mix(look.accent, Color.Black, 0.7f), watchR, watchC)
    drawCircle(look.text.copy(alpha = 0.35f), watchR, watchC, style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawMiniMark(look: Look, s: Size) {
    val u = s.minDimension / 48f
    when (look.style) {
        AppStyle.CLAY -> drawClayMark(u, look)
        AppStyle.SOFT -> drawSoftMark(u, look.dark)
        AppStyle.GLASS -> drawRoundRect(look.accent, Offset.Zero, s, CornerRadius(13 * u))
    }
}
