package app.synctheme.mobile.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import app.synctheme.core.Background
import app.synctheme.core.DigitFont
import app.synctheme.core.TimeShift
import app.synctheme.core.WatchFaceXml
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Часть фото (в долях его ширины и высоты), которая попадает в кадр. */
internal object Crop {
    /** Середина фото с пропорциями кадра — как обычно обрезаются обои. */
    fun center(imgW: Int, imgH: Int, frameW: Float, frameH: Float): RectF {
        val scale = max(frameW / imgW, frameH / imgH)
        val w = frameW / scale / imgW
        val h = frameH / scale / imgH
        return RectF((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
    }

    /** Кадр фона [bg]: его центр и увеличение (как пользователь подвинул фото), в пределах фото. */
    fun of(imgW: Int, imgH: Int, frameW: Float, frameH: Float, bg: Background): RectF {
        val base = center(imgW, imgH, frameW, frameH)
        val zoom = bg.zoom.coerceIn(1f, 5f)
        val w = base.width() / zoom
        val h = base.height() / zoom
        val cx = bg.focusX.coerceIn(w / 2, 1 - w / 2)
        val cy = bg.focusY.coerceIn(h / 2, 1 - h / 2)
        return RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    fun pixels(crop: RectF, imgW: Int, imgH: Int) = Rect(
        (crop.left * imgW).roundToInt(), (crop.top * imgH).roundToInt(),
        (crop.right * imgW).roundToInt(), (crop.bottom * imgH).roundToInt(),
    )
}

/**
 * Маска объекта, нарисованная в кадр (обои, циферблат) на грубой сетке. Сумма по прямоугольнику
 * считается за четыре обращения: так можно быстро перебрать сотни положений часов.
 */
internal class MaskGrid(val cols: Int, val rows: Int, private val alpha: FloatArray) {
    private val sat = FloatArray((cols + 1) * (rows + 1)).also { s ->
        for (y in 0 until rows) {
            var line = 0f
            for (x in 0 until cols) {
                line += alpha[y * cols + x]
                s[(y + 1) * (cols + 1) + x + 1] = s[y * (cols + 1) + x + 1] + line
            }
        }
    }

    /** Доля кадра, занятая объектом. */
    val mass = sat.last() / (cols * rows)

    /** Объект в клетке (x, y); за пределами кадра — 0. */
    fun at(x: Int, y: Int): Float = if (x in 0 until cols && y in 0 until rows) alpha[y * cols + x] else 0f

    /** Доля прямоугольника (в долях кадра), которую закрывает объект. */
    fun coverage(r: RectF): Float {
        val x0 = (r.left * cols).roundToInt().coerceIn(0, cols)
        val x1 = (r.right * cols).roundToInt().coerceIn(0, cols)
        val y0 = (r.top * rows).roundToInt().coerceIn(0, rows)
        val y1 = (r.bottom * rows).roundToInt().coerceIn(0, rows)
        if (x1 <= x0 || y1 <= y0) return 0f
        val w = cols + 1
        val sum = sat[y1 * w + x1] - sat[y0 * w + x1] - sat[y1 * w + x0] + sat[y0 * w + x0]
        return sum / ((x1 - x0) * (y1 - y0))
    }

    /** Центр тяжести объекта (доли кадра). */
    fun centroid(): Pair<Float, Float> {
        var sx = 0f
        var sy = 0f
        var m = 0f
        for (y in 0 until rows) for (x in 0 until cols) {
            val a = alpha[y * cols + x]
            sx += a * (x + 0.5f)
            sy += a * (y + 0.5f)
            m += a
        }
        return if (m == 0f) 0.5f to 0.5f else sx / m / cols to sy / m / rows
    }

    /** Рамка объекта (доли кадра) по клеткам, где он заметен. */
    fun bounds(): RectF {
        var l = cols
        var t = rows
        var r = -1
        var b = -1
        for (y in 0 until rows) for (x in 0 until cols) {
            if (alpha[y * cols + x] < 0.3f) continue
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
        }
        return if (r < 0) RectF(0.5f, 0.5f, 0.5f, 0.5f) else RectF(l.toFloat() / cols, t.toFloat() / rows, (r + 1f) / cols, (b + 1f) / rows)
    }

    companion object {
        /** Маска [mask] с обрезкой [crop] в кадре cols × rows. */
        fun of(mask: Bitmap, crop: RectF, cols: Int, rows: Int): MaskGrid {
            val small = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(
                mask, Crop.pixels(crop, mask.width, mask.height), RectF(0f, 0f, cols.toFloat(), rows.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val px = IntArray(cols * rows)
            small.getPixels(px, 0, cols, 0, 0, cols, rows)
            small.recycle()
            return MaskGrid(cols, rows, FloatArray(px.size) { (px[it] ushr 24) / 255f })
        }
    }
}

/** С какой стороны от объекта стоят часы. */
enum class ClockSide { TOP, BOTTOM, LEFT, RIGHT }

/**
 * Где на обоях стоят часы: строки цифр (центр «чернил» цифр и кегль, px) и дата.
 * side — сторона от объекта, если часы расставлены по нему (эффект глубины).
 */
class WallClockSpot(
    val lines: List<Line>, val date: DateLabel?, val side: ClockSide? = null, val overlap: Float = 0f,
    /** Размер экрана, для которого посчитано место (подписи в своих местах — в его долях); 0 — без них. */
    val width: Float = 0f, val height: Float = 0f,
) {
    enum class Part { TIME, HOURS, MINUTES }

    class Line(val part: Part, val cx: Float, val cy: Float, val size: Float, val accent: Boolean = false)

    class DateLabel(val cx: Float, val cy: Float, val size: Float, val pattern: String, val align: Paint.Align)
}

/**
 * Часы на обоях при эффекте глубины: с противоположной от объекта стороны (объект справа — часы
 * слева, снизу — сверху и т. д.), и придвинуты к нему так, что объект закрывает лишь край цифр —
 * около [TARGET] их площади.
 */
internal object WallClockPlacer {
    /** Какую долю цифр может закрыть объект. */
    const val TARGET = 0.12f

    /** И какую долю одной цифры: иначе голова может закрыть целую цифру при малой общей доле. */
    private const val DIGIT_MAX = 0.3f

    /** Свободные поля экрана: строка состояния, док с навигацией, края. */
    private const val SAFE_TOP = 0.07f
    private const val SAFE_BOTTOM = 0.16f
    private const val SAFE_SIDE = 0.06f

    private class Candidate(val spot: WallClockSpot, val overlap: Float, val minOverlap: Float)

    /**
     * @param dateWidth ширина подписи даты кеглем size, px (её измеряет вызывающий своим шрифтом).
     */
    fun place(
        grid: MaskGrid, font: DigitFont, w: Float, h: Float, showDate: Boolean,
        dateWidth: (pattern: String, size: Float) -> Float,
    ): WallClockSpot? {
        if (grid.mass < 0.01f) return null
        val (cx, cy) = grid.centroid()
        val bounds = grid.bounds()
        // Сторона напротив объекта; по центру — сверху, как привычно.
        val order = listOf(
            ClockSide.TOP to cy - 0.5f + 0.03f, ClockSide.BOTTOM to 0.5f - cy,
            ClockSide.LEFT to cx - 0.5f, ClockSide.RIGHT to 0.5f - cx,
        ).sortedByDescending { it.second }.map { it.first }
        val tried = order.mapNotNull { side ->
            val c = when (side) {
                ClockSide.TOP, ClockSide.BOTTOM -> row(grid, font, w, h, side, showDate)
                ClockSide.LEFT, ClockSide.RIGHT -> column(grid, font, w, h, side, bounds, showDate, dateWidth)
            }
            // Сторона подходит, если там часы можно поставить почти не закрытыми.
            if (c != null && c.minOverlap <= 0.3f) return c.spot
            c
        }
        return tried.minByOrNull { it.minOverlap }?.spot
    }

    /** Часы одной строкой «чч:мм» сверху или снизу от объекта; дата — с дальней от него стороны. */
    private fun row(grid: MaskGrid, font: DigitFont, w: Float, h: Float, side: ClockSide, showDate: Boolean): Candidate? {
        val top = side == ClockSide.TOP
        val dateSize = w * 0.045f
        val dateH = if (showDate) dateSize * 1.4f else 0f
        val gap = h * 0.012f
        var best: Candidate? = null
        for (scale in listOf(1f, 0.88f, 0.76f)) {
            val size = WatchFaceXml.fit(font, "00:00", (w * 0.86f).toInt(), (h * 0.12f).toInt()) * scale
            val bw = font.width("00:00") * size
            val bh = font.digitHeight * size
            // Центр цифр движется от края экрана к объекту, пока объект не закроет TARGET цифр.
            val far = if (top) h * SAFE_TOP + dateH + gap + bh / 2 else h * (1 - SAFE_BOTTOM) - dateH - gap - bh / 2
            // Дальше середины экрана часы не уходят: объекту в другой половине они всё равно не помешают.
            val near = if (top) h * 0.62f else h * 0.38f
            if (if (top) far > near else far < near) continue
            val (y, overlap, minOverlap) = slide(far, near, h / 240f) { y -> covered(grid, font, "00:00", w / 2, y, size, w, h) }
            val dateY = if (top) y - bh / 2 - gap - dateH / 2 else y + bh / 2 + gap + dateH / 2
            val date = if (showDate) WallClockSpot.DateLabel(w / 2, dateY, dateSize, "EEEE, d MMMM", Paint.Align.CENTER) else null
            val c = Candidate(WallClockSpot(listOf(WallClockSpot.Line(WallClockSpot.Part.TIME, w / 2, y, size)), date, side, overlap), overlap, minOverlap)
            if (best == null || c.minOverlap < best.minOverlap) best = c
            if (minOverlap <= TARGET) break
        }
        return best
    }

    /** Часы над минутами сбоку от объекта, на высоте его верхней части; дата — над ними. */
    private fun column(
        grid: MaskGrid, font: DigitFont, w: Float, h: Float, side: ClockSide, bounds: RectF, showDate: Boolean,
        dateWidth: (String, Float) -> Float,
    ): Candidate? {
        val left = side == ClockSide.LEFT
        val dateSize = w * 0.042f
        val pattern = "EEE, d MMM"
        val dateW = if (showDate) dateWidth(pattern, dateSize) else 0f
        val dateH = if (showDate) dateSize * 1.4f else 0f
        val gap = h * 0.01f
        var best: Candidate? = null
        for (scale in listOf(1f, 0.88f, 0.76f)) {
            val size = WatchFaceXml.fit(font, "00", (w * 0.42f).toInt(), (h * 0.12f).toInt()) * scale
            val bw = font.width("00") * size
            val digitH = font.digitHeight * size
            val lineGap = digitH * 0.16f
            val blockH = 2 * digitH + lineGap
            val total = dateH + (if (showDate) gap else 0f) + blockH
            // По высоте — у верхней части объекта (голова, плечи), но в пределах экрана.
            val wanted = (bounds.top + bounds.height() * 0.3f) * h
            val centerY = wanted.coerceIn(h * SAFE_TOP + total / 2, h * (1 - SAFE_BOTTOM) - total / 2)
            val blockY = centerY + total / 2 - blockH / 2
            val far = if (left) w * SAFE_SIDE + max(bw, dateW) / 2 else w * (1 - SAFE_SIDE) - max(bw, dateW) / 2
            val near = if (left) w * 0.6f else w * 0.4f
            if (if (left) far > near else far < near) continue
            val (x, overlap, minOverlap) = slide(far, near, w / 180f) { x ->
                val digits = max(
                    covered(grid, font, "00", x, blockY - blockH / 2 + digitH / 2, size, w, h),
                    covered(grid, font, "00", x, blockY + blockH / 2 - digitH / 2, size, w, h),
                )
                // Дату объект закрывать не должен: она тоже считается, с запасом.
                val dateBox = if (showDate) box(x, blockY - blockH / 2 - gap - dateH / 2, dateW, dateH, w, h) else null
                max(digits, (dateBox?.let(grid::coverage) ?: 0f) * 2)
            }
            val lines = listOf(
                WallClockSpot.Line(WallClockSpot.Part.HOURS, x, blockY - blockH / 2 + digitH / 2, size),
                WallClockSpot.Line(WallClockSpot.Part.MINUTES, x, blockY + blockH / 2 - digitH / 2, size, accent = true),
            )
            val date = if (showDate) WallClockSpot.DateLabel(x, blockY - blockH / 2 - gap - dateH / 2, dateSize, pattern, Paint.Align.CENTER) else null
            val c = Candidate(WallClockSpot(lines, date, side, overlap), overlap, minOverlap)
            if (best == null || c.minOverlap < best.minOverlap) best = c
            if (minOverlap <= TARGET) break
        }
        return best
    }

    /**
     * Ведёт часы от [far] (дальний от объекта край) к [near], пока объект не закроет [TARGET] цифр.
     * Возвращает положение, закрытую долю и наименьшую закрытую долю (на дальнем краю).
     */
    private fun slide(far: Float, near: Float, step: Float, coverage: (Float) -> Float): Triple<Float, Float, Float> {
        val dir = if (near > far) 1f else -1f
        val start = coverage(far)
        if (start >= TARGET) return Triple(far, start, start)
        var pos = far
        var prev = start
        while ((near - pos) * dir > 0) {
            val next = pos + dir * step
            val c = coverage(next)
            if (c >= TARGET) {
                // Ближе к цели — этот шаг или предыдущий.
                return if (abs(c - TARGET) < abs(prev - TARGET)) Triple(next, c, start) else Triple(pos, prev, start)
            }
            pos = next
            prev = c
        }
        return Triple(pos, prev, start)
    }

    /**
     * Насколько закрыта строка цифр: общая доля или, если объект закрывает одну цифру сильнее
     * [DIGIT_MAX], — столько, чтобы часы дальше не двигались.
     */
    private fun covered(grid: MaskGrid, font: DigitFont, text: String, cx: Float, cy: Float, size: Float, w: Float, h: Float): Float {
        val bw = font.width(text) * size
        val bh = font.digitHeight * size
        var x = cx - bw / 2
        var worst = 0f
        for (ch in text) {
            val cw = (if (ch == ':') font.colon else font.digit) * size
            if (ch != ':') worst = max(worst, grid.coverage(box(x + cw / 2, cy, cw, bh, w, h)))
            x += cw
        }
        return max(grid.coverage(box(cx, cy, bw, bh, w, h)), worst * TARGET / DIGIT_MAX)
    }

    private fun box(cx: Float, cy: Float, bw: Float, bh: Float, w: Float, h: Float) =
        RectF((cx - bw / 2) / w, (cy - bh / 2) / h, (cx + bw / 2) / w, (cy + bh / 2) / h)
}

/**
 * Время на циферблате при эффекте глубины: блок времени сдвигается (и при нужде уменьшается)
 * с противоположной от объекта стороны так, что объект лишь слегка заходит на цифры, — как часы на
 * обоях телефона. Фото не двигается. Блок не выходит за круг экрана и не наезжает на мини-виджеты.
 */
internal object WatchTimePlacer {
    const val GRID = 90
    private const val SIZE = 450f

    /**
     * @param mask маска объекта в кадре циферблата (GRID×GRID), [ink] — блок времени на своём месте
     * (альфа, GRID×GRID), [pivot] — центр масштабирования блока (точки 450×450), [obstacles] — мини-виджеты.
     */
    fun place(mask: MaskGrid, ink: FloatArray, pivot: Pair<Float, Float>, obstacles: List<RectF>): TimeShift? {
        if (mask.mass < 0.005f) return null
        val cells = ink.indices.filter { ink[it] > 0.05f }
        if (cells.isEmpty()) return null
        val inkSum = cells.sumOf { ink[it].toDouble() }.toFloat()
        val cell = SIZE / GRID
        // Центр блока и объекта; объект должен оказаться напротив — со своей стороны от времени.
        var bx = 0f
        var by = 0f
        for (i in cells) {
            bx += ink[i] * (i % GRID + 0.5f) * cell
            by += ink[i] * (i / GRID + 0.5f) * cell
        }
        bx /= inkSum
        by /= inkSum
        val (sx, sy) = mask.centroid().let { (x, y) -> x * SIZE to y * SIZE }
        val dirX = sx - SIZE / 2
        val dirY = sy - SIZE / 2
        val offCenter = hypot(dirX, dirY) > SIZE * 0.06f
        val horizontal = abs(dirX) > abs(dirY)
        val (px, py) = pivot
        // Клетки блока заранее: положение, вес и участок (по 15 клеток), чтобы объект не закрыл одну цифру целиком.
        val n = cells.size
        val ox = FloatArray(n) { (cells[it] % GRID + 0.5f) * cell }
        val oy = FloatArray(n) { (cells[it] / GRID + 0.5f) * cell }
        val wgt = FloatArray(n) { ink[cells[it]] }
        val part = IntArray(n) { (cells[it] / GRID / 15) * 6 + (cells[it] % GRID) / 15 }
        val partInk = FloatArray(36).also { a -> for (k in 0 until n) a[part[k]] += wgt[k] }
        val partOver = FloatArray(36)
        val r2 = (SIZE / 2 - 12) * (SIZE / 2 - 12)
        val limit = inkSum * 0.01f
        var best: TimeShift? = null
        var bestScore = Float.MAX_VALUE
        for (scale in listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)) {
            for (dy in -168..168 step 12) for (dx in -168..168 step 12) {
                var over = 0f
                var outside = 0f
                var blocked = 0f
                partOver.fill(0f)
                var k = 0
                while (k < n) {
                    val x = px + (ox[k] - px) * scale + dx
                    val y = py + (oy[k] - py) * scale + dy
                    val a = wgt[k]
                    val ex = x - SIZE / 2
                    val ey = y - SIZE / 2
                    if (ex * ex + ey * ey > r2) {
                        outside += a
                        if (outside > limit) break
                    }
                    for (o in obstacles) if (o.contains(x, y)) blocked += a
                    val m = mask.at((x / cell).toInt(), (y / cell).toInt())
                    over += a * m
                    partOver[part[k]] += a * m
                    k++
                }
                if (outside > limit) continue
                var worst = 0f
                for (p in 0 until 36) if (partInk[p] > inkSum * 0.03f) worst = max(worst, partOver[p] / partInk[p])
                val overlap = over / inkSum
                // Где окажется блок относительно объекта.
                val vx = px + (bx - px) * scale + dx - sx
                val vy = py + (by - py) * scale + dy - sy
                val wrongSide = when {
                    !offCenter -> if (vy > 0) 1f else 0f
                    horizontal -> if (vx * dirX >= 0 || abs(vx) < abs(vy) * 0.6f) 1f else 0f
                    else -> if (vy * dirY >= 0 || abs(vy) < abs(vx) * 0.6f) 1f else 0f
                }
                val score = abs(overlap - WallClockPlacer.TARGET) * 5 + max(0f, worst - 0.35f) * 4 +
                    blocked / inkSum * 6 + (1 - scale) * 1.2f + hypot(dx.toFloat(), dy.toFloat()) / SIZE * 0.6f + wrongSide * 3
                if (score < bestScore) {
                    bestScore = score
                    best = TimeShift(dx.toFloat(), dy.toFloat(), scale)
                }
            }
        }
        return best?.takeUnless { it.identity }
    }
}
