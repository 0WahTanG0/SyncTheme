package app.synctheme.mobile.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PointF
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.synctheme.mobile.render.SubjectMask
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.launch

/** Чем рисует палец: обводка добавляет или убирает объект целиком, кисть и ластик правят край. */
internal enum class SubjectTool(val title: String) { ADD("Обвести"), REMOVE("Убрать"), BRUSH("Кисть"), ERASE("Ластик") }

/**
 * Объект для эффекта глубины — вручную. Достаточно примерно обвести объект пальцем: телефон сам найдёт
 * его границы внутри обводки ([SubjectMask.fromLasso]). «Убрать» обводкой снимает лишнее, кисть и ластик
 * поправляют край. Маска сохраняется рядом с фото и сразу действует на обоях, экране блокировки и циферблате.
 */
@Composable
fun SubjectEditorScreen(photo: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var mask by remember { mutableStateOf<Bitmap?>(null) }
    var tool by remember { mutableStateOf(SubjectTool.ADD) }
    var brush by remember { mutableStateOf(0.05f) }
    var busy by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf("Обведите пальцем объект, за которым будут часы, — границы телефон найдёт сам.") }
    // Маска меняется на месте: счётчик заставляет перерисовать холст.
    var frame by remember { mutableIntStateOf(0) }
    val undo = remember { ArrayDeque<Bitmap>() }
    val lasso = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(photo) {
        val small = runCatching { SubjectMask.preparedPhoto(context, photo) }.getOrNull()
        if (small == null) {
            hint = "Не удалось открыть фото"
            return@LaunchedEffect
        }
        val blank = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        SubjectMask.load(context, photo)?.let { saved ->
            android.graphics.Canvas(blank).drawBitmap(saved, null, RectF(0f, 0f, small.width.toFloat(), small.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        image = small
        mask = blank
    }

    fun snapshot(m: Bitmap) {
        undo.addLast(m.copy(Bitmap.Config.ARGB_8888, true))
        while (undo.size > 12) undo.removeFirst()
    }

    /** Обводка готова: ищем объект внутри неё и добавляем (или убираем). */
    fun applyLasso(m: Bitmap, src: Bitmap, points: List<Offset>, remove: Boolean) {
        if (points.size < 3) return
        busy = "Ищу границы объекта…"
        scope.launch {
            val found = SubjectMask.fromLasso(context, src, points.map { PointF(it.x, it.y) })
            snapshot(m)
            android.graphics.Canvas(m).drawBitmap(found, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (remove) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            })
            found.recycle()
            busy = null
            hint = if (remove) "Убрано. Обведите ещё, если нужно, или нажмите «Готово»."
            else "Объект выделен. Не то — «Отменить»; край правят «Кисть» и «Ластик»."
            frame++
        }
    }

    AppBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().wrapContentWidth().widthIn(max = 720.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Отмена") }
                Text("Объект для глубины", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(enabled = mask != null && busy == null, onClick = {
                    val m = mask ?: return@Button
                    busy = "Сохраняю…"
                    scope.launch {
                        SubjectMask.save(context, photo, m)
                        onClose()
                    }
                }) { Text("Готово") }
            }
            Text(busy ?: hint, style = MaterialTheme.typography.bodySmall, color = look.muted)
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
                val lineWidth = with(LocalDensity.current) { 3.dp.toPx() }
                fun toMask(o: Offset) = Offset((o.x - left) / scale, (o.y - top) / scale)
                Canvas(
                    Modifier.fillMaxSize().pointerInput(tool, brush, m, busy) {
                        if (busy != null) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val outline = tool == SubjectTool.ADD || tool == SubjectTool.REMOVE
                            var last = toMask(down.position)
                            if (outline) {
                                lasso.clear()
                                lasso += last
                            } else {
                                snapshot(m)
                                stroke(m, last, last, brush * m.width, tool == SubjectTool.ERASE)
                                frame++
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val now = toMask(change.position)
                                change.consume()
                                if (outline) {
                                    if (hypot(now.x - last.x, now.y - last.y) > 3f) {
                                        lasso += now
                                        last = now
                                    }
                                } else {
                                    stroke(m, last, now, brush * m.width, tool == SubjectTool.ERASE)
                                    last = now
                                    frame++
                                }
                            }
                            if (outline) {
                                val points = lasso.toList()
                                lasso.clear()
                                applyLasso(m, src, points, remove = tool == SubjectTool.REMOVE)
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
                    if (lasso.size > 1) {
                        val path = Path().apply {
                            lasso.forEachIndexed { i, p ->
                                val x = left + p.x * scale
                                val y = top + p.y * scale
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                        drawPath(path, Color.White, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                if (busy != null) CircularProgressIndicator()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SubjectTool.entries.forEach { t -> FilterChip(tool == t, { tool = t }, label = { Text(t.title) }) }
            }
            if (tool == SubjectTool.BRUSH || tool == SubjectTool.ERASE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Размер", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                    Slider(brush, { brush = it }, valueRange = 0.015f..0.15f, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = undo.isNotEmpty() && busy == null, onClick = {
                    mask = undo.removeLastOrNull() ?: return@TextButton
                    frame++
                }) { Text("Отменить") }
                TextButton(enabled = busy == null, onClick = {
                    val m = mask ?: return@TextButton
                    snapshot(m)
                    m.eraseColor(0)
                    frame++
                }) { Text("Очистить") }
                TextButton(enabled = busy == null, onClick = {
                    val m = mask ?: return@TextButton
                    val src = image ?: return@TextButton
                    busy = "Ищу объект на всём фото…"
                    scope.launch {
                        val found = runCatching { SubjectMask.segmenter(context, src) }.getOrNull()
                        if (found != null) {
                            snapshot(m)
                            m.eraseColor(0)
                            android.graphics.Canvas(m).drawBitmap(found, null, RectF(0f, 0f, m.width.toFloat(), m.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                            found.recycle()
                            hint = "Готово. Не тот объект — обведите нужный."
                        } else {
                            hint = "Сам не нашёл — обведите объект пальцем."
                        }
                        busy = null
                        frame++
                    }
                }) { Text("Найти сам") }
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
