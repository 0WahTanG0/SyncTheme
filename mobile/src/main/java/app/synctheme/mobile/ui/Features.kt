package app.synctheme.mobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Chameleon
import app.synctheme.core.ChameleonMode
import app.synctheme.core.ClockStyle
import app.synctheme.core.LiveData
import app.synctheme.core.Theme
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.core.WatchSettings
import app.synctheme.mobile.render.ExportKind
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.wallpaper.ClockWallpaper
import kotlin.math.abs

// ---------------------------------------------------------------- цвета с вещи

/** Цвета со снимка вещи и тема из одного цвета. */
object ThemeColors {
    /** Яркие и приглушённые цвета снимка — без повторов, от самых заметных. */
    fun swatches(bitmap: Bitmap): List<Int> {
        val p = Palette.from(bitmap).maximumColorCount(24).generate()
        return listOfNotNull(
            p.vibrantSwatch, p.lightVibrantSwatch, p.darkVibrantSwatch,
            p.mutedSwatch, p.lightMutedSwatch, p.darkMutedSwatch, p.dominantSwatch,
        ).map { it.rgb or 0xFF000000.toInt() }.distinct()
    }

    /** Акцент из цвета вещи: тот же оттенок, но достаточно яркий, чтобы читаться на тёмном фоне. */
    fun accent(color: Int): Int {
        val (h, s, v) = Chameleon.hsv(color)
        // Почти серые вещи (графит, белый) — светлый нейтральный акцент.
        return if (s < 0.12f) Chameleon.fromHsv(255, h, s, maxOf(v, 0.88f))
        else Chameleon.fromHsv(255, h, maxOf(s, 0.45f), maxOf(v, 0.85f))
    }

    /**
     * Тема в тон вещи: глубокий фон того же оттенка, «аврора» из цвета вещи и второго цвета снимка,
     * светлые цифры и яркий акцент. Раскладка часов и виджеты остаются как были.
     */
    fun fromColor(base: Theme, color: Int, swatches: List<Int>): Theme {
        val (h, s, v) = Chameleon.hsv(color)
        val deep = Chameleon.fromHsv(255, h, (s * 0.9f).coerceIn(0.15f, 0.85f), 0.12f)
        val mid = Chameleon.fromHsv(255, h, s.coerceAtLeast(0.2f), (v * 0.8f).coerceIn(0.35f, 0.7f))
        val partner = swatches.firstOrNull { c ->
            val (ph, ps, _) = Chameleon.hsv(c)
            ps > 0.15f && abs(((ph - h + 540f) % 360f) - 180f) < 155f
        } ?: Chameleon.fromHsv(255, (h + 35f) % 360f, s.coerceAtLeast(0.3f), 0.6f)
        val accent = accent(color)
        val hex = String.format("#%06X", color and 0xFFFFFF)
        return base.copy(
            id = "match",
            name = "В тон вещи $hex",
            background = Background(BackgroundType.AURORA, listOf(deep, mid, partner, Chameleon.fromHsv(255, h, s, 0.45f))),
            textColor = 0xFFFFFFFF.toInt(),
            accentColor = accent,
            dim = 0.08f,
            watch = base.watch?.copy(background = null, accentColor = accent, textColor = 0xFFFFFFFF.toInt()),
        )
    }
}

/**
 * «В тон вещи»: снимок ремешка, кофты или кроссовок. Цвет — нажатием на снимок или из найденных
 * на нём цветов; дальше — целая тема в этом цвете или только акцент.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchColorsDialog(photo: MainViewModel.MatchPhoto, onDismiss: () -> Unit, onApply: (Int, Boolean) -> Unit) {
    var picked by remember(photo) { mutableIntStateOf(photo.swatches.firstOrNull() ?: 0xFF888888.toInt()) }
    var mark by remember(photo) { mutableStateOf<Offset?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("В тон вещи") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Нажмите на снимке на нужный цвет или выберите из найденных.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ratio = photo.bitmap.width.toFloat() / photo.bitmap.height
                BoxWithConstraints(
                    Modifier.fillMaxWidth().heightIn(max = 300.dp).aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    Image(photo.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(photo) {
                            detectTapGestures { pos ->
                                val x = (pos.x / size.width * photo.bitmap.width).toInt().coerceIn(0, photo.bitmap.width - 1)
                                val y = (pos.y / size.height * photo.bitmap.height).toInt().coerceIn(0, photo.bitmap.height - 1)
                                picked = averageAround(photo.bitmap, x, y)
                                mark = pos
                            }
                        },
                    ) {
                        mark?.let {
                            drawCircle(Color.White, 18.dp.toPx(), it, style = Stroke(3.dp.toPx()))
                            drawCircle(Color(picked), 14.dp.toPx(), it)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    photo.swatches.forEach { c -> ColorSwatch(c, size = 36.dp, selected = c == picked) { picked = c; mark = null } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorSwatch(picked, size = 44.dp) {}
                    Column {
                        Text("Цвет вещи", style = MaterialTheme.typography.labelMedium)
                        Text(String.format("#%06X", picked and 0xFFFFFF), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
                    ColorSwatch(ThemeColors.accent(picked), size = 32.dp) {}
                    Text("акцент", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(picked, true) }) { Text("Тема в этом цвете") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                TextButton(onClick = { onApply(picked, false) }) { Text("Только акцент") }
            }
        },
    )
}

/** Средний цвет вокруг точки — одна пиксельная точка на ткани слишком «шумная». */
private fun averageAround(b: Bitmap, cx: Int, cy: Int, r: Int = 6): Int {
    var rs = 0L
    var gs = 0L
    var bs = 0L
    var n = 0
    for (y in (cy - r).coerceAtLeast(0)..(cy + r).coerceAtMost(b.height - 1)) {
        for (x in (cx - r).coerceAtLeast(0)..(cx + r).coerceAtMost(b.width - 1)) {
            val c = b.getPixel(x, y)
            rs += c shr 16 and 0xFF
            gs += c shr 8 and 0xFF
            bs += c and 0xFF
            n++
        }
    }
    return (0xFF shl 24) or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
}

// ---------------------------------------------------------------- часы на обоях

fun wallClockSummary(theme: Theme): String {
    val wall = theme.wallClock ?: return "Выключены"
    val layout = when (wall.layout) {
        WallClockLayout.TOP -> "вверху"
        WallClockLayout.STACKED -> "столбиком"
        WallClockLayout.CENTER -> "по центру"
    }
    return listOfNotNull("Вкл", layout, "глубина".takeIf { wall.depth }).joinToString(" · ")
}

/** Часы прямо на обоях рабочего стола (живые обои) и эффект глубины. */
@Composable
fun WallClockContent(theme: Theme, vm: MainViewModel) {
    val context = LocalContext.current
    val wall = theme.wallClock
    val photo = theme.background.type == BackgroundType.PHOTO
    SwitchRow("Часы на обоях", wall != null) { on -> vm.setWallClock(if (on) WallClock(depth = photo) else null) }
    if (wall == null) {
        Text(
            "Крупные часы рисуются прямо на обоях рабочего стола, а главный объект фото — человек, питомец, гора — " +
                "встаёт поверх цифр, как будто часы за ним.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    ChipRow {
        listOf(WallClockLayout.TOP to "Вверху", WallClockLayout.STACKED to "Столбиком", WallClockLayout.CENTER to "По центру").forEach { (l, label) ->
            FilterChip(wall.layout == l, { vm.setWallClock(wall.copy(layout = l)) }, label = { Text(label) })
        }
    }
    SwitchRow("Эффект глубины", wall.depth && photo, enabled = photo) { on -> vm.setWallClock(wall.copy(depth = on)) }
    if (!photo) {
        Text("Эффект глубины работает с фото — выберите фото в «Фон».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SwitchRow("Дата над часами", wall.showDate) { on -> vm.setWallClock(wall.copy(showDate = on)) }
    Text("Шрифт", style = MaterialTheme.typography.labelMedium)
    DigitFontPicker(wall.font, ThemeRenderer.wallClockFont(theme.copy(wallClock = wall.copy(font = null)))) { f -> vm.setWallClock(wall.copy(font = f)) }
    var active by remember { mutableStateOf(ClockWallpaper.isActive(context)) }
    LaunchedEffect(theme) { active = ClockWallpaper.isActive(context) }
    if (active) {
        Text("Живые обои SyncTheme включены ✓ — часы обновляются сами.", style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            "Часам на обоях нужны живые обои SyncTheme: включите их один раз — дальше тема, «Хамелеон» и время " +
                "меняются на них сами. Экран блокировки останется с обычными обоями и часами телефона.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = {
            runCatching { context.startActivity(ClockWallpaper.setIntent(context)) }
                .onFailure { android.widget.Toast.makeText(context, "Живые обои не поддерживаются этим телефоном", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text("Включить живые обои") }
    }
}

// ---------------------------------------------------------------- Хамелеон

@Composable
fun ChameleonContent(vm: MainViewModel) {
    val mode by vm.chameleon.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val previews by vm.moodPreviews.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.wantMoodPreviews() }
    Text(
        "Тема сама подстраивается: утром теплее, вечером — закатные тона, ночью — темнее; в дождь — холоднее, " +
            "в снег — светлее. Меняются обои, виджеты и циферблат — туда, что отмечено в «Применить».",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChipRow {
        ChameleonMode.entries.forEach { m -> FilterChip(mode == m, { vm.setChameleon(m) }, label = { Text(m.title) }) }
    }
    if (mode != ChameleonMode.OFF) {
        mood?.let { Text("Сейчас: ${it.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        Text(
            "SyncTheme проверяет время и погоду раз в полчаса. Погода — для города из «Виджеты» → «Город для погоды».",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (previews.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(previews, key = { it.first }) { (title, image) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        image, title, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(56.dp).height(112.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, LocalLook.current.cardBorder, RoundedCornerShape(12.dp)),
                    )
                    Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- живые данные

fun liveSummary(w: WatchSettings): String =
    listOfNotNull("шаги".takeIf { w.live.steps }, "пульс".takeIf { w.live.pulse }, "заряд".takeIf { w.live.battery })
        .joinToString(" · ").ifEmpty { "Выключены" }

@Composable
fun LiveDataContent(w: WatchSettings, edit: ((WatchSettings) -> WatchSettings) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun set(transform: (LiveData) -> LiveData) = edit { it.copy(live = transform(it.live)) }
    Text(
        "Циферблат меняется вместе с вами. Шаги, пульс и заряд берёт сам циферблат на часах — телефон не нужен.",
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
    SwitchRow(if (w.clockStyle == ClockStyle.LIQUID) "Шаги наполняют «Заливку»" else "Шаги наполняют циферблат", w.live.steps) { v -> set { it.copy(steps = v) } }
    Text("Чем ближе к цели шагов за день, тем выше «вода» цвета акцента.", style = MaterialTheme.typography.bodySmall, color = muted)
    SwitchRow(if (w.clockStyle == ClockStyle.NEON) "Неон пульсирует в такт сердцу" else "Кольцо пульса", w.live.pulse) { v -> set { it.copy(pulse = v) } }
    Text("Кольцо по краю вспыхивает на каждый удар. Часы один раз спросят разрешение на пульс.", style = MaterialTheme.typography.bodySmall, color = muted)
    SwitchRow("Заряд гасит свечение", w.live.battery) { v -> set { it.copy(battery = v) } }
    Text("Ниже 50% фон понемногу темнеет, у неона слабеет ореол.", style = MaterialTheme.typography.bodySmall, color = muted)
    Text(
        "Шаги и пульс работают на часах с Wear OS 6. На Wear OS 3–5 классический циферблат показывает заряд.",
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
}

// ---------------------------------------------------------------- другие устройства

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtherDevicesSection(vm: MainViewModel) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var kinds by rememberSaveable { mutableStateOf(setOf(ExportKind.IPHONE, ExportKind.APPLE_WATCH)) }
    Section("Другие устройства") {
        Text(
            "Телефон — любой на Android 12 и новее. Часы — на Wear OS: Galaxy Watch, Pixel Watch, OnePlus, TicWatch, " +
                "Xiaomi, Fossil и другие. С Wear OS 6 циферблат ставится сам. На часах с Wear OS 3–5, вышедших до 2024 года " +
                "(Galaxy Watch 4–6, Pixel Watch 1–2, TicWatch и др.), SyncTheme рисует циферблат сам — выберите «SyncTheme» " +
                "в списке циферблатов один раз. Часам, которые вышли сразу с Wear OS 5 (Galaxy Watch 7, Pixel Watch 3), нужна Wear OS 6 — обновите ПО.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        Text("iPhone, Apple Watch и другие часы", style = MaterialTheme.typography.titleSmall)
        Text(
            "Apple не даёт ставить свои циферблаты и не связывает Apple Watch с Android, у Huawei, Amazfit и Garmin — " +
                "свои закрытые системы. Для них SyncTheme рисует картинки в цветах темы: обои для iPhone, iPad и Mac, " +
                "фон для циферблата «Фото» на Apple Watch и для фото-циферблатов других часов. Отправьте их себе " +
                "(мессенджер, почта, облако) и поставьте как обои или фото циферблата.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportKind.entries.forEach { k ->
                FilterChip(
                    k in kinds, { kinds = if (k in kinds) kinds - k else kinds + k },
                    label = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(k.title)
                            Text(k.note, style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.export(kinds, toGallery = false) { context.startActivity(it) } }, enabled = kinds.isNotEmpty()) { Text("Поделиться") }
            OutlinedButton(onClick = { vm.export(kinds, toGallery = true) {} }, enabled = kinds.isNotEmpty()) { Text("В галерею") }
        }
        Text(
            "Apple Watch: в приложении «Watch» на iPhone → «Циферблаты» → «Фото» → выберите картинку. " +
                "iPhone: «Настройки» → «Обои» → «Добавить новые обои».",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
}

/** Плитка ленты тем: круглая кнопка со значком и подписью. */
@Composable
fun StripAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).pointerInput(Unit) { detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(look.primary), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = look.onPrimary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
