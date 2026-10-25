package app.synctheme.mobile.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.synctheme.mobile.render.SubjectMask
import kotlin.math.min
import kotlinx.coroutines.launch

private enum class SubjectTool(val title: String) { PICK("Касанием"), BRUSH("Кисть"), ERASE("Ластик") }

/**
 * Объект для эффекта глубины — вручную: касанием выбрать один из найденных объектов (или убрать лишний),
 * а если распознавание объекта не увидело — обвести его кистью и подправить ластиком. Маска сохраняется
 * рядом с фото и сразу действует на обоях, экране блокировки и циферблате.
 */
@Composable
fun SubjectEditorScreen(photo: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var mask by remember { mutableStateOf<Bitmap?>(null) }
    var subjects by remember { mutableStateOf<List<Bitmap>?>(null) }
    var tool by remember { mutableStateOf(SubjectTool.BRUSH) }
    var brush by remember { mutableStateOf(0.06f) }
    var hint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Маска меняется на месте: счётчик заставляет перерисовать холст.
    var frame by remember { mutableIntStateOf(0) }
    val undo = remember { ArrayDeque<Bitmap>() }

    LaunchedEffect(photo) {
        val small = runCatching { SubjectMask.preparedPhoto(context, photo) }.getOrNull()
        if (small == null) {
            hint = "Не удалось открыть фото"
            return@LaunchedEffect
        }
        image = small
        val blank = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        SubjectMask.load(context, photo)?.let { saved ->
            android.graphics.Canvas(blank).drawBitmap(saved, null, RectF(0f, 0f, small.width.toFloat(), small.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        mask = blank
        val found = runCatching { SubjectMask.subjects(context, small) }.getOrDefault(emptyList())
        subjects = found
        if (found.isNotEmpty()) {
            tool = SubjectTool.PICK
            hint = "Нашлось объектов: ${found.size}. Коснитесь объекта, за которым будут часы; коснитесь ещё раз — уберётся."
        } else {
            hint = "Объект не нашёлся сам — обведите его кистью. Ластик убирает лишнее."
        }
    }

    fun snapshot(m: Bitmap) {
        undo.addLast(m.copy(Bitmap.Config.ARGB_8888, true))
        while (undo.size > 12) undo.removeFirst()
    }

    AppBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Отмена") }
                Text("Объект для глубины", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(enabled = mask != null && !saving, onClick = {
                    val m = mask ?: return@Button
                    saving = true
                    scope.launch {
                        SubjectMask.save(context, photo, m)
                        onClose()
                    }
                }) { Text("Готово") }
            }
            Text(
                hint ?: "Ищу объекты на фото…",
                style = MaterialTheme.typography.bodySmall, color = look.muted,
            )
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val src = image
                val m = mask
                if (src == null || m == null) {
                    CircularProgressIndicator()
                    return@BoxWithConstraints
                }
                val accent = look.accent.toArgb()
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()
                val scale = min(wPx / src.width, hPx / src.height)
                val left = (wPx - src.width * scale) / 2
                val top = (hPx - src.height * scale) / 2
                fun toMask(o: Offset) = Offset((o.x - left) / scale, (o.y - top) / scale)
                Canvas(
                    Modifier.fillMaxSize().pointerInput(tool, brush, subjects, m) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = toMask(down.position)
                            snapshot(m)
                            if (tool == SubjectTool.PICK) {
                                hint = pick(m, subjects.orEmpty(), start) ?: hint
                                frame++
                                return@awaitEachGesture
                            }
                            var last = start
                            stroke(m, last, last, brush * m.width, tool == SubjectTool.ERASE)
                            frame++
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val now = toMask(change.position)
                                stroke(m, last, now, brush * m.width, tool == SubjectTool.ERASE)
                                last = now
                                if (change.positionChange() != Offset.Zero) change.consume()
                                frame++
                            }
                        }
                    },
                ) {
                    frame.let { }
                    drawIntoCanvas { c ->
                        val dst = RectF(left, top, left + src.width * scale, top + src.height * scale)
                        c.nativeCanvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                        c.nativeCanvas.drawBitmap(m, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                            colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
                            alpha = 140
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SubjectTool.entries.forEach { t ->
                    FilterChip(
                        tool == t, { tool = t }, label = { Text(t.title) },
                        enabled = t != SubjectTool.PICK || !subjects.isNullOrEmpty(),
                    )
                }
            }
            if (tool != SubjectTool.PICK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Размер", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                    Slider(brush, { brush = it }, valueRange = 0.015f..0.15f, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = undo.isNotEmpty(), onClick = {
                    val previous = undo.removeLastOrNull() ?: return@TextButton
                    mask = previous
                    frame++
                }) { Text("Отменить") }
                TextButton(onClick = {
                    val m = mask ?: return@TextButton
                    snapshot(m)
                    m.eraseColor(0)
                    frame++
                }) { Text("Очистить") }
                Box(Modifier.weight(1f))
            }
        }
    }
}

/** Мазок кистью (или ластиком) от [from] до [to] в координатах маски. */
private fun stroke(mask: Bitmap, from: Offset, to: Offset, width: Float, erase: Boolean) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = width
        color = android.graphics.Color.WHITE
        if (erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        else maskFilter = BlurMaskFilter(width * 0.12f, BlurMaskFilter.Blur.NORMAL)
    }
    android.graphics.Canvas(mask).drawLine(from.x, from.y, to.x, to.y, paint)
}

/** Касание в режиме «Касанием»: добавить объект под пальцем или убрать, если он уже выбран. Ответ — подсказка. */
private fun pick(mask: Bitmap, subjects: List<Bitmap>, at: Offset): String? {
    val x = at.x.toInt()
    val y = at.y.toInt()
    if (x !in 0 until mask.width || y !in 0 until mask.height) return null
    val subject = subjects.firstOrNull { s -> x < s.width && y < s.height && (s.getPixel(x, y) ushr 24) > 100 }
        ?: return "Здесь объекта не нашлось — выберите «Кисть» и обведите его."
    val selected = (mask.getPixel(x, y) ushr 24) > 128
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (selected) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    android.graphics.Canvas(mask).drawBitmap(subject, null, RectF(0f, 0f, mask.width.toFloat(), mask.height.toFloat()), paint)
    return if (selected) "Объект убран." else "Объект выбран — часы встанут за ним."
}
