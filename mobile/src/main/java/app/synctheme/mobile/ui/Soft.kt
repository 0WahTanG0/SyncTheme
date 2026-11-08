package app.synctheme.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Мягкий» вид: всё одного серого цвета, а выпуклость и вдавленность — светом сверху-слева и тенью
 * снизу-справа. В остальных оформлениях те же элементы — обычные карточки.
 */
class SoftColors(val base: Color, val hi: Color, val lo: Color, val field: Color)

object SoftPalette {
    val Dark = SoftColors(base = Color(0xFF3C3C3E), hi = Color(0xFF4D4D50), lo = Color(0xFF232325), field = Color(0xFF363638))
    val Light = SoftColors(base = Color(0xFFE3E4E8), hi = Color(0xFFFFFFFF), lo = Color(0xFFB4B6BE), field = Color(0xFFDADBE0))
}

val Look.soft: SoftColors get() = if (dark) SoftPalette.Dark else SoftPalette.Light

private val Look.isSoft get() = style == AppStyle.SOFT

/** Выпуклая поверхность. depth — насколько «приподнята». В «Мяте» — объёмная плитка. */
fun Modifier.raised(look: Look, radius: Dp, depth: Dp = 6.dp): Modifier = when {
    look.isClay -> this.claySlab(look, radius, level = if (depth >= 7.dp) ClayLevel.L3 else if (depth >= 5.dp) ClayLevel.L2 else ClayLevel.L1)
    look.isSoft -> this.drawBehind { drawRaised(look.soft, radius.toPx(), depth.toPx()) }
    else -> this.clip(RoundedCornerShape(radius)).background(look.card).border(1.dp, look.cardBorder, RoundedCornerShape(radius))
}

/** Вдавленная поверхность — поля ввода и выбранные варианты. В «Мяте» выбранное — с мятной кромкой. */
fun Modifier.inset(look: Look, radius: Dp, depth: Dp = 5.dp): Modifier = when {
    look.isClay -> this.clayHollow(look, radius, focused = true)
    look.isSoft -> this.drawBehind { drawInset(look.soft, radius.toPx(), depth.toPx()) }
    else -> this.clip(RoundedCornerShape(radius)).background(look.control)
}

/** Подложка группы настроек: «Мягкий» — выпуклая, «Мята» — плитка со стопкой слоёв, «Стекло» — карточка. */
fun Modifier.card(look: Look, radius: Dp = look.radius, highlight: Boolean = false): Modifier = when {
    look.isClay -> this.clayStack(look, radius)
    look.isSoft -> this.raised(look, radius)
    else -> this.clip(RoundedCornerShape(radius)).background(if (highlight) look.highlight else look.card).border(1.dp, look.cardBorder, RoundedCornerShape(radius))
}

/** Отступы снаружи подложки: у «Мягкого» место под тень, у «Мяты» — под стопку слоёв. */
fun Modifier.cardSpacing(look: Look): Modifier = when {
    look.isClay -> this.padding(top = 8.dp, bottom = 8.dp + clayStackExtra)
    look.isSoft -> this.padding(vertical = 10.dp)
    else -> this.padding(vertical = 6.dp)
}

private fun DrawScope.drawRaised(c: SoftColors, r: Float, d: Float) {
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = c.base.toArgb() }
        p.setShadowLayer(d * 1.8f, d, d, c.lo.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
        p.setShadowLayer(d * 1.8f, -d, -d, c.hi.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
    }
}

private fun DrawScope.drawInset(c: SoftColors, r: Float, d: Float) {
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = c.field.toArgb() }
        nc.drawRoundRect(0f, 0f, size.width, size.height, r, r, fill)
        val shape = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW) }
        // Рамка вокруг поля: сама она обрезается, а её тень падает внутрь — край кажется вдавленным.
        val frame = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(-d * 6, -d * 6, size.width + d * 6, size.height + d * 6, android.graphics.Path.Direction.CW)
            addPath(shape)
        }
        nc.save()
        nc.clipPath(shape)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = c.base.toArgb() }
        p.setShadowLayer(d * 1.6f, d, d, c.lo.toArgb())
        nc.drawPath(frame, p)
        p.setShadowLayer(d * 1.6f, -d, -d, c.hi.toArgb())
        nc.drawPath(frame, p)
        nc.restore()
    }
}

/** Большая выпуклая кнопка-капсула — как «Войти» на образце. strong — есть что делать: светлый текст. */
@Composable
fun SoftPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = true, enabled: Boolean = true, height: Dp = 62.dp) {
    val look = LocalLook.current
    if (look.isClay) return ClayButton(text, onClick, modifier, if (strong) ClayKind.PRIMARY else ClayKind.SECONDARY, enabled, height = height - 4.dp)
    Box(
        modifier.fillMaxWidth().height(height).raised(look, height / 2, 7.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (strong && enabled) look.text else look.muted, fontFamily = look.headings,
            fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1,
        )
    }
}

/** Маленькая выпуклая кнопка — как «UI Series». */
@Composable
fun SoftChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (look.isClay) return ClayChip(text, onClick, modifier, icon = NavIcons.Save)
    Box(
        modifier.height(40.dp).raised(look, 20.dp, 4.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = look.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

/** Переключатель-капсула с подписью и шариком — как «Dark Mode». */
@Composable
fun SoftToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (look.isClay) return ClayToggle(label, checked, onChange, modifier)
    Row(
        modifier.height(40.dp).raised(look, 20.dp, 4.dp).clip(CircleShape)
            .clickable(role = Role.Switch) { onChange(!checked) }.padding(start = 14.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = look.text, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(30.dp).then(if (checked) Modifier.raised(look, 15.dp, 3.dp) else Modifier.inset(look, 15.dp, 3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Box(Modifier.size(22.dp).clip(CircleShape).background(look.text.copy(alpha = 0.9f)))
        }
    }
}

/**
 * Поле с подписью сверху — как «Логин» и «Пароль» на образце: внутри текущее значение, справа значок.
 * Нажатие раскрывает [content] под полем (если он есть) или выполняет [onClick].
 */
@Composable
fun SoftField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val look = LocalLook.current
    val clay = look.isClay
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label, color = look.text, fontSize = 13.sp, fontWeight = if (clay) FontWeight.SemiBold else null,
            modifier = Modifier.padding(start = 18.dp, bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().then(if (clay) Modifier.padding(bottom = 3.dp) else Modifier).height(if (clay) 64.dp else 60.dp)
                .then(if (clay) Modifier.clayInput(look, 32.dp, focused = content != null) else Modifier.inset(look, 30.dp))
                .clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
                .padding(start = if (clay) 28.dp else 24.dp, end = if (clay) 24.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value, color = look.text, fontWeight = if (clay) FontWeight.SemiBold else FontWeight.Bold, fontSize = if (clay) 16.sp else 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(icon, contentDescription = null, tint = look.text, modifier = Modifier.size(24.dp))
        }
        content?.let {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalContentColor provides look.text) { it() }
            }
        }
    }
}

/** Круглая выпуклая кнопка с кольцом — как отпечаток пальца на образце. */
@Composable
fun SoftRound(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 104.dp) {
    val look = LocalLook.current
    // В «Мяте» кольцо — мятное «Focus ring» из образца.
    val ring = if (look.isClay) look.clay.focus else look.muted.copy(alpha = 0.8f)
    Box(
        modifier.size(size).raised(look, size / 2, 8.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                val w = (if (look.isClay) 4.dp else 5.dp).toPx()
                drawCircle(ring, radius = this.size.minDimension / 2 - 10.dp.toPx(), center = Offset(this.size.width / 2, this.size.height / 2), style = Stroke(w))
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = look.text, modifier = Modifier.size(size * 0.38f)) }
}

/** Выпуклая карточка-подложка для групп настроек. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Column(
        modifier.fillMaxWidth().then(if (look.isClay) Modifier.cardSpacing(look) else Modifier.padding(vertical = 8.dp))
            .then(if (look.isClay) Modifier.card(look, ClayRadius.card) else Modifier.raised(look, 28.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides look.text) { content() }
    }
}

/** Мелкий текст с выделенным действием — как «Забыли данные? Помощь со входом». */
@Composable
fun SoftHint(text: String, action: String?, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = look.text.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center)
        action?.let {
            Text(
                " $it", color = look.text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(vertical = 8.dp),
            )
        }
    }
}

/** «Тёмная» в «Мяте»: светлая плитка-капсула, включено — мятный шарик, выключено — вдавленная лунка. */
@Composable
private fun ClayToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val look = LocalLook.current
    val c = look.clay
    Row(
        modifier.padding(bottom = ClayLevel.L2.depth).height(40.dp).claySlab(look, 20.dp).clip(CircleShape)
            .clickable(role = Role.Switch) { onChange(!checked) }.padding(start = 14.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(30.dp).then(
                if (checked) Modifier.padding(bottom = 2.dp).claySlab(look, 15.dp, c.mint, ClayLevel.L1) else Modifier.clayHollow(look, 15.dp),
            ),
        )
    }
}
