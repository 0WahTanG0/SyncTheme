package app.synctheme.mobile.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockPlace
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
import app.synctheme.core.DigitFont
import app.synctheme.core.FontWeight
import app.synctheme.core.Theme
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.core.TimeShift
import app.synctheme.core.WatchSettings
import app.synctheme.core.WatchFaceXml
import android.text.format.DateFormat
import app.synctheme.faces.FaceFonts
import app.synctheme.faces.FaceRenderer
import app.synctheme.faces.FaceState
import app.synctheme.mobile.data.ThemeRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Рисует обои, фон циферблата и превью телефона/часов из одной темы,
 * чтобы всё выглядело одинаково.
 */
object ThemeRenderer {
    private const val WATCH = WatchFaceXml.SIZE.toFloat()

    /** Те же «пятна», что в DefaultAssets (:buildtools): x, y, радиус (доли размера). */
    private val AURORA_SPOTS = listOf(
        Triple(0.15f, 0.2f, 0.75f),
        Triple(0.9f, 0.45f, 0.7f),
        Triple(0.35f, 0.95f, 0.8f),
    )

    /** Небольшой кэш фото: фоны рабочего стола, экрана блокировки и часов могут быть разными. */
    private val photoCache = LinkedHashMap<String, Bitmap>()

    @Synchronized
    private fun photo(context: Context, bg: Background): Bitmap? {
        val name = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO } ?: return null
        photoCache[name]?.let { return it }
        val file = ThemeRepository.get(context).photoFile(name)
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        photoCache[name] = bmp
        while (photoCache.size > 3) photoCache.remove(photoCache.keys.first())
        return bmp
    }

    /** Фон в кадре width × height; фото — в том кадре, как его подвинули ([Background.focusX], zoom). */
    fun background(context: Context, bg: Background, dim: Float, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, bg, width.toFloat(), height.toFloat(), photo(context, bg))
        darken(canvas, dim)
        return bmp
    }

    /** Затемнение — одинаково для фона и для слоя глубины. */
    private fun darken(canvas: Canvas, dim: Float) {
        if (dim > 0f) canvas.drawColor(Colors.withAlpha(Color.BLACK, dim.coerceIn(0f, 0.8f)))
    }

    /**
     * Слой глубины: главный объект фото (по маске [SubjectMask]) на прозрачном фоне, обрезанный
     * и затемнённый так же, как фон. null — у фона нет фото или маски.
     */
    fun foreground(context: Context, bg: Background, dim: Float, width: Int, height: Int): Bitmap? {
        val name = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO } ?: return null
        val mask = SubjectMask.load(context, name) ?: return null
        val photo = photo(context, bg) ?: return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Обрезка в долях: у маски те же пропорции, что у фото, поэтому она ложится точно на объект.
        val c = Crop.of(photo.width, photo.height, width.toFloat(), height.toFloat(), bg)
        drawCrop(canvas, photo, c, width.toFloat(), height.toFloat())
        darken(canvas, dim)
        val cut = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        drawCrop(canvas, mask, c, width.toFloat(), height.toFloat(), cut)
        return bmp
    }

    /** Слой глубины для часов (450×450, в том же кадре фото, что и фон циферблата). */
    fun watchForeground(context: Context, theme: Theme): Bitmap? =
        foreground(context, theme.watchBackground(), theme.watchSettings().dim, WatchFaceXml.SIZE, WatchFaceXml.SIZE)

    private val watchShifts = LinkedHashMap<String, TimeShift?>()

    /**
     * Где стоит время на циферблате: передвинутое вручную, а с эффектом глубины — напротив объекта
     * фото и так, что объект лишь слегка заходит на цифры ([WatchTimePlacer]). Фото при этом не двигается.
     * null — время на своём месте в стиле.
     */
    fun watchTimeShift(context: Context, theme: Theme): TimeShift? {
        val w = theme.watchSettings()
        if (!w.clockStyle.movableTime) return null
        w.timeShift?.let { return it.takeUnless { s -> s.identity } }
        if (!w.depth) return null
        val bg = theme.watchBackground()
        val name = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO } ?: return null
        val file = SubjectMask.file(context, name)
        if (!file.exists()) return null
        val key = "$name|${file.lastModified()}|${bg.focusX}|${bg.focusY}|${bg.zoom}|" +
            w.copy(textColor = 0, accentColor = 0, background = null, dim = 0f).hashCode()
        synchronized(watchShifts) { if (key in watchShifts) return watchShifts[key] }
        val mask = SubjectMask.load(context, name) ?: return null
        val n = WatchTimePlacer.GRID
        val grid = MaskGrid.of(mask, Crop.of(mask.width, mask.height, 1f, 1f, bg), n, n)
        val shift = WatchTimePlacer.place(
            grid, blockInk(context, w), WatchFaceXml.timePivot(w.clockStyle), FaceRenderer.complicationAreas(context, w, fonts(context)),
        )
        synchronized(watchShifts) {
            watchShifts[key] = shift
            while (watchShifts.size > 16) watchShifts.remove(watchShifts.keys.first())
        }
        return shift
    }

    /**
     * Сдвиг времени, при котором блок времени целиком внутри круга экрана: если палец увёл его за край,
     * блок останавливается у края.
     */
    fun fitShift(context: Context, theme: Theme, shift: TimeShift): TimeShift {
        val w = theme.watchSettings()
        val ink = blockInk(context, w)
        val n = WatchTimePlacer.GRID
        val cell = WATCH / n
        val (px, py) = WatchFaceXml.timePivot(w.clockStyle)
        val r = WATCH / 2 - 8
        fun inside(dx: Float, dy: Float, s: Float): Boolean {
            for (i in ink.indices) {
                if (ink[i] < 0.2f) continue
                val x = px + ((i % n + 0.5f) * cell - px) * s + dx - WATCH / 2
                val y = py + ((i / n + 0.5f) * cell - py) * s + dy - WATCH / 2
                if (x * x + y * y > r * r) return false
            }
            return true
        }
        var s = shift.scale
        // Блок крупнее экрана даже по центру — уменьшаем.
        while (s > 0.4f && !inside(0f, 0f, s)) s -= 0.05f
        if (inside(shift.dx, shift.dy, s)) return shift.copy(scale = s)
        // Ближайшая к желаемой точка на пути к центру.
        var lo = 0f
        var hi = 1f
        repeat(12) {
            val mid = (lo + hi) / 2
            if (inside(shift.dx * mid, shift.dy * mid, s)) lo = mid else hi = mid
        }
        return TimeShift(shift.dx * lo, shift.dy * lo, s)
    }

    private val blockInks = LinkedHashMap<WatchSettings, FloatArray>()

    /** Где на циферблате блок времени стиля (альфа на сетке [WatchTimePlacer.GRID]) — на своём месте. */
    private fun blockInk(context: Context, w0: WatchSettings): FloatArray {
        val w = w0.copy(textColor = 0xFFFFFFFF.toInt(), accentColor = 0xFFFFFFFF.toInt(), background = null, dim = 0f, timeShift = null, depth = false)
        synchronized(blockInks) { blockInks[w]?.let { return it } }
        return renderBlockInk(context, w).also { ink ->
            synchronized(blockInks) {
                blockInks[w] = ink
                while (blockInks.size > 8) blockInks.remove(blockInks.keys.first())
            }
        }
    }

    private fun renderBlockInk(context: Context, w: WatchSettings): FloatArray {
        val n = WatchTimePlacer.GRID
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(n / WATCH, n / WATCH)
        // 20:48 — широкие цифры: блок с ними занимает больше всего места.
        FaceRenderer.drawTimeBlock(canvas, w, FaceState(System.currentTimeMillis(), 20, 48, 0f), fonts(context))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()
        return FloatArray(px.size) { (px[it] ushr 24) / 255f }
    }

    /** Обои рабочего стола. */
    fun background(context: Context, theme: Theme, width: Int, height: Int): Bitmap =
        background(context, theme.background, theme.dim, width, height)

    /** Обои экрана блокировки (свои или как на рабочем столе). */
    fun lockBackground(context: Context, theme: Theme, width: Int, height: Int): Bitmap =
        background(context, theme.lockScreenBackground(), theme.lockScreenDim(), width, height)

    /**
     * Квадратный фон для часов (фото обрезается по центру). Неподвижные фигуры стиля
     * («печенье», стеклянный диск) рисуются прямо в фон — так их не нужно описывать в WFF.
     */
    fun watchBackground(context: Context, theme: Theme): Bitmap {
        val w = theme.watchSettings()
        val bmp = background(context, theme.watchBackground(), w.dim, WatchFaceXml.SIZE, WatchFaceXml.SIZE)
        drawWatchDecor(Canvas(bmp), w, watchTimeShift(context, theme))
        return bmp
    }

    /** shift — сдвиг блока времени: стеклянный диск «Стекла» стоит под временем и едет вместе с ним. */
    private fun drawWatchDecor(canvas: Canvas, w: WatchSettings, shift: TimeShift?) {
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (w.clockStyle) {
            ClockStyle.BLOB -> {
                p.color = w.accentColor or 0xFF000000.toInt()
                canvas.drawPath(wavy(c, c, 184f, 9, 0.07f), p)
                p.color = Colors.withAlpha(WatchFaceXml.onAccent(w.accentColor), 0.55f)
                for (h in 0 until 12) {
                    if (h == 3 && w.showDate || h == 6 && w.complications) continue
                    val a = Math.toRadians(h * 30.0)
                    canvas.drawCircle(c + 152 * sin(a).toFloat(), c - 152 * cos(a).toFloat(), if (h % 3 == 0) 8f else 5f, p)
                }
            }
            ClockStyle.GLASS -> {
                val moved = WatchFaceXml.activeShift(w, shift)
                if (moved != null) {
                    val (px, py) = WatchFaceXml.timePivot(w.clockStyle)
                    canvas.translate(moved.dx, moved.dy)
                    canvas.scale(moved.scale, moved.scale, px, py)
                }
                val r = 172f
                p.color = Colors.withAlpha(Color.WHITE, 0.14f)
                canvas.drawCircle(c, c, r, p)
                p.shader = LinearGradient(0f, c - r, 0f, c, Colors.withAlpha(Color.WHITE, 0.18f), Colors.withAlpha(Color.WHITE, 0f), Shader.TileMode.CLAMP)
                canvas.drawCircle(c, c, r, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = Colors.withAlpha(Color.WHITE, 0.35f)
                canvas.drawCircle(c, c, r, p)
            }
            // Неону нужен тёмный фон, чтобы свечение читалось.
            ClockStyle.NEON -> canvas.drawColor(Colors.withAlpha(Color.BLACK, 0.45f))
            else -> Unit
        }
    }

    /** Круг с волнистым краем («печенье»). */
    private fun wavy(cx: Float, cy: Float, r: Float, lobes: Int, depth: Float): Path {
        val path = Path()
        val amp = r * depth
        for (i in 0..240) {
            val t = 2 * Math.PI * i / 240
            val rr = r - amp + amp * cos(lobes * t)
            val x = cx + (rr * sin(t)).toFloat()
            val y = cy - (rr * cos(t)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun drawBackground(canvas: Canvas, bg: Background, w: Float, h: Float, photo: Bitmap?) {
        val colors = bg.colors.ifEmpty { listOf(Color.BLACK) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        when (bg.type) {
            BackgroundType.SOLID -> canvas.drawColor(colors.first())
            BackgroundType.PHOTO -> if (photo != null) {
                drawCrop(canvas, photo, Crop.of(photo.width, photo.height, w, h, bg), w, h)
            } else {
                drawBackground(canvas, bg.copy(type = BackgroundType.LINEAR), w, h, null)
            }
            BackgroundType.LINEAR -> {
                if (colors.size == 1) return canvas.drawColor(colors.first())
                val a = Math.toRadians(bg.angle.toDouble())
                val dx = sin(a).toFloat()
                val dy = -cos(a).toFloat()
                val half = (abs(w * dx) + abs(h * dy)) / 2f
                paint.shader = LinearGradient(
                    w / 2 - dx * half, h / 2 - dy * half, w / 2 + dx * half, h / 2 + dy * half,
                    colors.toIntArray(), null, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            BackgroundType.RADIAL -> {
                if (colors.size == 1) return canvas.drawColor(colors.first())
                paint.shader = RadialGradient(
                    w / 2, h * 0.35f, max(w, h) * 0.8f, colors.toIntArray(), null, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            BackgroundType.AURORA -> {
                canvas.drawColor(colors.first())
                val scale = sqrt(w * h)
                AURORA_SPOTS.forEachIndexed { i, (cx, cy, r) ->
                    val c = if (colors.size > 1) colors[1 + i % (colors.size - 1)] else colors[0]
                    paint.shader = RadialGradient(
                        cx * w, cy * h, r * scale,
                        intArrayOf(Colors.withAlpha(c, 200 / 255f), Colors.withAlpha(c, 0f)), null,
                        Shader.TileMode.CLAMP,
                    )
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
            }
        }
    }

    /** Часть фото crop (доли) во весь кадр w × h. */
    private fun drawCrop(
        canvas: Canvas, photo: Bitmap, crop: RectF, w: Float, h: Float,
        paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
    ) {
        canvas.drawBitmap(photo, Crop.pixels(crop, photo.width, photo.height), RectF(0f, 0f, w, h), paint)
    }

    // ---------- Превью циферблата (тот же рендер, что у классического циферблата на часах) ----------

    fun watchPreview(
        context: Context, theme: WatchSettings, watchBg: Bitmap, sizePx: Int, foreground: Bitmap? = null, shift: TimeShift? = theme.timeShift,
    ): Bitmap = FaceRenderer.render(context, theme, watchBg, foreground, FaceState.preview(DateFormat.is24HourFormat(context)), sizePx, fonts(context), shift)

    /** Превью циферблата темы целиком: фон, слой глубины (если включён) и время на своём месте. */
    fun watchPreview(context: Context, theme: Theme, sizePx: Int): Bitmap {
        val bg = watchBackground(context, theme)
        val fg = if (theme.watchSettings().depth) watchForeground(context, theme) else null
        return watchPreview(context, theme.watchSettings(), bg, sizePx, fg, watchTimeShift(context, theme)).also {
            bg.recycle()
            fg?.recycle()
        }
    }

    /** Тот же циферблат в режиме Always-On (без фона, светлыми цветами). */
    fun watchAmbientPreview(context: Context, theme: Theme, sizePx: Int): Bitmap =
        FaceRenderer.render(context, theme.watchSettings(), null, null, FaceState.preview(DateFormat.is24HourFormat(context), ambient = true), sizePx, fonts(context))

    @Volatile private var faceFonts: FaceFonts? = null

    private fun fonts(context: Context): FaceFonts =
        faceFonts ?: FaceFonts(context.applicationContext).also { faceFonts = it }

    private fun typeface(weight: FontWeight): Typeface = Typeface.create(
        Typeface.DEFAULT,
        when (weight) {
            FontWeight.THIN -> 100
            FontWeight.LIGHT -> 300
            FontWeight.REGULAR -> 400
            FontWeight.BOLD -> 700
        },
        false,
    )

    private fun textPaint(color: Int, size: Float, tf: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = tf
            textAlign = Paint.Align.CENTER
        }

    private fun Canvas.textCentered(text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, paint)
    }

    private fun dateString(pattern: String) =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date()).replace(".", "")

    // ---------- Превью телефона: обои + виджеты ----------

    /** @param widgets рисовать ли виджеты и док (в редакторе расположения — только обои и часы). */
    fun phonePreview(context: Context, theme: Theme, width: Int, height: Int, widgets: Boolean = true): Bitmap {
        val bmp = background(context, theme, width, height)
        val canvas = Canvas(bmp)
        val u = width / 100f
        val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Colors.withAlpha(Colors.widgetSurface(theme), theme.widgetOpacity)
        }
        val wall = theme.wallClock
        var clockArea: RectF? = null
        if (wall != null) {
            // Часы на обоях (живые обои) и объект с фото поверх них.
            val spot = wallClockSpot(context, theme, width, height)
            clockArea = wallClockArea(theme, spot)
            drawWallClock(context, canvas, theme, spot, previewMillis())
            if (wall.depth) foreground(context, theme.background, theme.dim, width, height)?.let {
                canvas.drawBitmap(it, 0f, 0f, null)
                it.recycle()
                drawWallClock(context, canvas, theme, spot, previewMillis(), alpha = WatchFaceXml.DEPTH_TIME_ALPHA)
            }
        } else if (widgets) {
            // Виджет часов 4x2.
            val clock = RectF(6 * u, 16 * u, 94 * u, 52 * u)
            canvas.drawRoundRect(clock, 7 * u, 7 * u, surface)
            val tf = typeface(theme.fontWeight)
            if (theme.clockStyle == ClockStyle.STACKED) {
                canvas.textCentered("10", 50 * u, 25 * u, textPaint(theme.textColor, 15 * u, tf))
                canvas.textCentered("08", 50 * u, 39 * u, textPaint(theme.accentColor, 15 * u, tf))
            } else {
                canvas.textCentered("10:08", 50 * u, 31 * u, textPaint(theme.textColor, 22 * u, tf))
            }
            canvas.textCentered(dateString("EEEE, d MMMM"), 50 * u, 47 * u, textPaint(theme.accentColor, 4.2f * u))
        }

        if (!widgets) return bmp
        // Дата 2x2 и батарея 2x2; с часами на обоях — над доком или вверху, где не закрывают часы.
        val top = when {
            wall == null -> 57 * u
            clockArea == null || !RectF.intersects(clockArea, RectF(0f, height - 64 * u, width.toFloat(), height - 23 * u)) -> height - 64 * u
            !RectF.intersects(clockArea, RectF(0f, 12 * u, width.toFloat(), 53 * u)) -> 12 * u
            else -> Float.NaN
        }
        if (!top.isNaN()) drawPreviewWidgets(canvas, theme, top, u, surface)

        // Док с иконками.
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.WHITE, 0.85f) }
        val dockY = height - 14 * u
        for (i in 0 until 4) canvas.drawCircle((20 + i * 20) * u, dockY, 6.5f * u, icon)
        return bmp
    }

    /** Превью планшета (альбомная ориентация): обои, часы с датой, два виджета и док. */
    fun tabletPreview(context: Context, theme: Theme, width: Int, height: Int): Bitmap {
        val bmp = background(context, theme, width, height)
        val canvas = Canvas(bmp)
        val u = height / 100f
        val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Colors.widgetSurface(theme), theme.widgetOpacity) }
        val clock = RectF(6 * u, 8 * u, 76 * u, 44 * u)
        canvas.drawRoundRect(clock, 6 * u, 6 * u, surface)
        canvas.textCentered("10:08", clock.centerX(), clock.centerY() - 3 * u, textPaint(theme.textColor, 20 * u, typeface(theme.fontWeight)))
        canvas.textCentered(dateString("EEEE, d MMMM"), clock.centerX(), clock.bottom - 6 * u, textPaint(theme.accentColor, 4.2f * u))
        drawPreviewWidgets(canvas, theme, 50 * u, u * 0.82f, surface, left = 6 * u)
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.WHITE, 0.85f) }
        for (i in 0 until 6) canvas.drawCircle(width / 2f + (i - 2.5f) * 17 * u, height - 10 * u, 5f * u, icon)
        return bmp
    }

    // ---------- Телевизор: заставка и её превью ----------

    /** Тема для телевизора: часы на обоях — как на телефоне, а если их там нет — по центру с датой. */
    private fun tvTheme(theme: Theme): Theme =
        theme.copy(wallClock = (theme.wallClock ?: WallClock(WallClockLayout.CENTER, depth = false)).copy(place = null))

    /**
     * Часы на экране ТВ: время крупно чуть выше середины, дата под ним. [dx], [dy] — медленный сдвиг
     * против выгорания экрана (заставка висит часами).
     */
    fun tvClockSpot(theme: Theme, width: Int, height: Int, dx: Float = 0f, dy: Float = 0f): WallClockSpot {
        val font = wallClockFont(tvTheme(theme))
        val w = width.toFloat()
        val h = height.toFloat()
        val size = WatchFaceXml.fit(font, "00:00", (w * 0.44f).toInt(), (h * 0.26f).toInt())
        val cy = h * 0.44f + dy
        return WallClockSpot(
            listOf(WallClockSpot.Line(WallClockSpot.Part.TIME, w / 2 + dx, cy, size)),
            WallClockSpot.DateLabel(w / 2 + dx, cy + size * 0.66f, h * 0.045f, "EEEE, d MMMM", Paint.Align.CENTER),
        )
    }

    /** Часы и дата поверх уже нарисованных обоев; [foreground] — объект с фото перед цифрами (эффект глубины). */
    fun drawTvClock(
        context: Context, canvas: Canvas, theme: Theme, width: Int, height: Int, millis: Long,
        dx: Float = 0f, dy: Float = 0f, foreground: Bitmap? = null,
    ) {
        val t = tvTheme(theme)
        val spot = tvClockSpot(t, width, height, dx, dy)
        drawWallClock(context, canvas, t, spot, millis)
        if (foreground != null) {
            canvas.drawBitmap(foreground, 0f, 0f, null)
            drawWallClock(context, canvas, t, spot, millis, alpha = WatchFaceXml.DEPTH_TIME_ALPHA)
        }
    }

    /** Обои телевизора и, с эффектом глубины, объект с фото отдельным слоем. */
    fun tvLayers(context: Context, theme: Theme, width: Int, height: Int): Pair<Bitmap, Bitmap?> {
        val bg = background(context, theme, width, height)
        val fg = if (theme.wallClock?.depth == true) foreground(context, theme.background, theme.dim, width, height) else null
        return bg to fg
    }

    /** Кадр заставки телевизора целиком (превью). */
    fun tvFrame(context: Context, theme: Theme, width: Int, height: Int, millis: Long = previewMillis()): Bitmap {
        val (bg, fg) = tvLayers(context, theme, width, height)
        drawTvClock(context, Canvas(bg), theme, width, height, millis, foreground = fg)
        fg?.recycle()
        return bg
    }

    private fun drawPreviewWidgets(canvas: Canvas, theme: Theme, top: Float, u: Float, surface: Paint, left: Float = 6 * u) {
        val date = RectF(left, top, left + 41 * u, top + 41 * u)
        canvas.drawRoundRect(date, 7 * u, 7 * u, surface)
        canvas.textCentered(dateString("EEEE").uppercase(), date.centerX(), date.top + 8 * u, textPaint(theme.accentColor, 3.6f * u))
        canvas.textCentered(dateString("d"), date.centerX(), date.centerY() + 2 * u, textPaint(theme.textColor, 19 * u, typeface(FontWeight.LIGHT)))
        canvas.textCentered(dateString("LLLL"), date.centerX(), date.bottom - 7 * u, textPaint(theme.textColor, 3.6f * u))

        val battery = RectF(left + 47 * u, top, left + 88 * u, top + 41 * u)
        canvas.drawRoundRect(battery, 7 * u, 7 * u, surface)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * u; strokeCap = Paint.Cap.ROUND }
        val r = 15 * u
        val arc = RectF(battery.centerX() - r, battery.centerY() - r, battery.centerX() + r, battery.centerY() + r)
        ring.color = Colors.withAlpha(theme.textColor, 0.25f)
        canvas.drawArc(arc, 0f, 360f, false, ring)
        ring.color = theme.accentColor
        canvas.drawArc(arc, -90f, 270f, false, ring)
        canvas.textCentered("75%", battery.centerX(), battery.centerY(), textPaint(theme.textColor, 7 * u, typeface(FontWeight.LIGHT)))
    }

    /** 10:08 сегодня — время превью, как у циферблатов. */
    private fun previewMillis(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 10)
        set(java.util.Calendar.MINUTE, 8)
    }.timeInMillis

    /** Шрифт часов на обоях: свой, как у циферблата или «Узкий». */
    fun wallClockFont(theme: Theme): DigitFont =
        theme.wallClock?.font ?: theme.digitFont ?: theme.clockStyle.defaultDigitFont ?: DigitFont.NARROW

    /**
     * Где стоят часы на обоях. С эффектом глубины — напротив объекта фото (объект справа — часы слева,
     * снизу — сверху …), придвинутые к нему так, что объект закрывает лишь край цифр ([WallClockPlacer]).
     * Без глубины или без объекта — выбранная раскладка.
     */
    fun wallClockSpot(context: Context, theme: Theme, width: Int, height: Int): WallClockSpot {
        val wall = theme.wallClock ?: return WallClockSpot(emptyList(), null)
        val font = wallClockFont(theme)
        val w = width.toFloat()
        val h = height.toFloat()
        wall.place?.let { return placedSpot(it, font, w, h) }
        val bg = theme.background
        val photo = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO }
        val mask = if (wall.depth && photo != null) SubjectMask.load(context, photo) else null
        if (mask != null) {
            val grid = MaskGrid.of(mask, Crop.of(mask.width, mask.height, w, h, bg), 72, (72 * h / w).roundToInt())
            val label = fonts(context).label
            WallClockPlacer.place(grid, font, w, h, wall.showDate) { pattern, size ->
                textPaint(Color.WHITE, size, label).measureText(dateString(System.currentTimeMillis(), pattern))
            }?.let { return it }
        }
        val date = { cy: Float, size: Float -> WallClockSpot.DateLabel(w / 2, cy, size, "EEEE, d MMMM", Paint.Align.CENTER) }
        return when (wall.layout) {
            app.synctheme.core.WallClockLayout.TOP -> WallClockSpot(
                listOf(WallClockSpot.Line(WallClockSpot.Part.TIME, w / 2, h * 0.215f, WatchFaceXml.fit(font, "00:00", (w * 0.86f).toInt(), (h * 0.12f).toInt()))),
                date(h * 0.115f, w * 0.045f),
            )
            app.synctheme.core.WallClockLayout.STACKED -> {
                val size = WatchFaceXml.fit(font, "00", (w * 0.78f).toInt(), (h * 0.15f).toInt())
                WallClockSpot(
                    listOf(
                        WallClockSpot.Line(WallClockSpot.Part.HOURS, w / 2, h * 0.2f, size),
                        WallClockSpot.Line(WallClockSpot.Part.MINUTES, w / 2, h * 0.37f, size, accent = true),
                    ),
                    date(h * 0.095f, w * 0.045f),
                )
            }
            app.synctheme.core.WallClockLayout.CENTER -> WallClockSpot(
                listOf(WallClockSpot.Line(WallClockSpot.Part.TIME, w / 2, h * 0.42f, WatchFaceXml.fit(font, "00:00", (w * 0.56f).toInt(), (h * 0.075f).toInt()))),
                date(h * 0.48f, w * 0.04f),
            )
        }
    }

    /** Обычный кегль часов на обоях: одной строкой во всю ширину или часы над минутами. */
    private fun baseClockSize(font: DigitFont, w: Float, h: Float, stacked: Boolean) =
        if (stacked) WatchFaceXml.fit(font, "00", (w * 0.42f).toInt(), (h * 0.12f).toInt())
        else WatchFaceXml.fit(font, "00:00", (w * 0.86f).toInt(), (h * 0.12f).toInt())

    /** Часы, передвинутые вручную: центр цифр в (x, y), дата над ними. */
    private fun placedSpot(p: ClockPlace, font: DigitFont, w: Float, h: Float): WallClockSpot {
        val cx = p.x * w
        val cy = p.y * h
        val size = baseClockSize(font, w, h, p.stacked) * p.scale.coerceIn(0.3f, 2.5f)
        val digitH = font.digitHeight * size
        val dateSize = w * 0.042f * p.scale.coerceIn(0.75f, 1.3f)
        return if (p.stacked) {
            val blockH = 2 * digitH + digitH * 0.16f
            WallClockSpot(
                listOf(
                    WallClockSpot.Line(WallClockSpot.Part.HOURS, cx, cy - blockH / 2 + digitH / 2, size),
                    WallClockSpot.Line(WallClockSpot.Part.MINUTES, cx, cy + blockH / 2 - digitH / 2, size, accent = true),
                ),
                WallClockSpot.DateLabel(cx, cy - blockH / 2 - h * 0.01f - dateSize * 0.7f, dateSize, "EEE, d MMM", Paint.Align.CENTER),
            )
        } else {
            WallClockSpot(
                listOf(WallClockSpot.Line(WallClockSpot.Part.TIME, cx, cy, size)),
                WallClockSpot.DateLabel(cx, cy - digitH / 2 - h * 0.012f - dateSize * 0.7f, dateSize, "EEEE, d MMMM", Paint.Align.CENTER),
            )
        }
    }

    /** Место часов, при котором они вместе с датой целиком на экране (с полями по краям). */
    fun clampPlace(theme: Theme, p: ClockPlace, width: Int, height: Int): ClockPlace {
        val font = wallClockFont(theme)
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = p.scale.coerceIn(0.3f, 2.5f)
        // Если и при этом размере часы шире экрана — уменьшаем.
        val base = baseClockSize(font, w, h, p.stacked)
        val maxScale = (w * 0.94f) / (font.width(if (p.stacked) "00" else "00:00") * base)
        val s = minOf(scale, maxScale)
        val size = base * s
        val halfW = font.width(if (p.stacked) "00" else "00:00") * size / 2 + w * 0.03f
        val digitH = font.digitHeight * size
        val blockH = if (p.stacked) 2 * digitH + digitH * 0.16f else digitH
        val dateH = w * 0.042f * s.coerceIn(0.75f, 1.3f) * 1.4f + h * 0.012f
        val top = blockH / 2 + dateH + h * 0.03f
        val bottom = blockH / 2 + h * 0.03f
        return p.copy(
            x = (p.x * w).coerceIn(halfW, w - halfW) / w,
            y = (p.y * h).coerceIn(top, h - bottom) / h,
            scale = s,
        )
    }

    /**
     * Те же часы, что стоят сейчас (сами по объекту или по раскладке), как место [ClockPlace] —
     * с него начинается перетаскивание.
     */
    fun clockPlaceOf(context: Context, theme: Theme, width: Int, height: Int): ClockPlace? {
        theme.wallClock?.place?.let { return it }
        val spot = wallClockSpot(context, theme, width, height)
        if (spot.lines.isEmpty()) return null
        val font = wallClockFont(theme)
        val stacked = spot.lines.size == 2
        val top = spot.lines.minOf { it.cy - font.digitHeight * it.size / 2 }
        val bottom = spot.lines.maxOf { it.cy + font.digitHeight * it.size / 2 }
        val w = width.toFloat()
        val h = height.toFloat()
        return ClockPlace(
            spot.lines.first().cx / w, (top + bottom) / 2 / h,
            spot.lines.first().size / baseClockSize(font, w, h, stacked), stacked,
        )
    }

    /** Прямоугольник, который занимают часы на обоях (для превью: куда не ставить виджеты). */
    /** Попало ли касание (x, y) в часы на обоях — с запасом [slop] под палец. */
    fun wallClockHit(theme: Theme, spot: WallClockSpot, x: Float, y: Float, slop: Float): Boolean {
        val area = wallClockArea(theme, spot) ?: return false
        return x in area.left - slop..area.right + slop && y in area.top - slop..area.bottom + slop
    }

    /** Прямоугольник, который занимают цифры и дата часов на обоях. */
    fun wallClockArea(theme: Theme, spot: WallClockSpot): RectF? {
        val font = wallClockFont(theme)
        var area: RectF? = null
        for (line in spot.lines) {
            val text = if (line.part == WallClockSpot.Part.TIME) "00:00" else "00"
            val hw = font.width(text) * line.size / 2
            val hh = font.digitHeight * line.size / 2
            val r = RectF(line.cx - hw, line.cy - hh, line.cx + hw, line.cy + hh)
            area = area?.apply { union(r) } ?: r
        }
        spot.date?.let { d -> area?.union(d.cx - d.size * 5, d.cy - d.size, d.cx + d.size * 5, d.cy + d.size) }
        return area
    }

    /**
     * Часы на обоях телефона (рисуют живые обои SyncTheme) в месте [spot]: время шрифтом цифр темы,
     * дата цветом акцента, лёгкая тень для читаемости на фото.
     */
    fun drawWallClock(context: Context, canvas: Canvas, theme: Theme, spot: WallClockSpot, millis: Long, alpha: Int = 255) {
        val wall = theme.wallClock ?: return
        val font = wallClockFont(theme)
        val tf = fonts(context).of(font)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val hours = if (DateFormat.is24HourFormat(context)) cal.get(java.util.Calendar.HOUR_OF_DAY)
            else (cal.get(java.util.Calendar.HOUR) + 11) % 12 + 1
        val hh = "%02d".format(hours)
        val mm = "%02d".format(cal.get(java.util.Calendar.MINUTE))
        val shadow = Colors.withAlpha(Color.BLACK, 0.35f)
        // alpha < 255 — время поверх объекта с фото (эффект глубины): без тени и даты.
        val ghost = alpha < 255
        for (line in spot.lines) {
            val text = when (line.part) {
                WallClockSpot.Part.TIME -> "$hh:$mm"
                WallClockSpot.Part.HOURS -> hh
                WallClockSpot.Part.MINUTES -> mm
            }
            val p = textPaint(Colors.withAlpha(if (line.accent) theme.accentColor else theme.textColor, alpha / 255f), line.size, tf)
            if (!ghost) p.setShadowLayer(line.size * 0.04f, 0f, line.size * 0.02f, shadow)
            canvas.drawText(text, line.cx, line.cy + (font.top + font.bottom) / 2 * line.size, p)
        }
        val d = spot.date
        if (d != null && wall.showDate && !ghost) {
            val p = textPaint(theme.accentColor, d.size, fonts(context).label).apply {
                textAlign = d.align
                setShadowLayer(d.size * 0.15f, 0f, 2f, shadow)
            }
            canvas.textCentered(dateString(millis, d.pattern), d.cx, d.cy, p)
        }
    }

    private fun dateString(millis: Long, pattern: String) =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)).replace(".", "")

    /**
     * Превью экрана блокировки: обои блокировки и крупные часы One UI сверху. С эффектом глубины —
     * как у «Адаптивных часов» One UI 8: крупнее, а главный объект фото стоит перед ними.
     */
    fun lockPreview(context: Context, theme: Theme, width: Int, height: Int): Bitmap {
        val bmp = lockBackground(context, theme, width, height)
        val canvas = Canvas(bmp)
        // Единица — от ширины телефона; на широком (планшет) экране — от высоты, часы по центру.
        val u = min(width.toFloat(), height * 0.6f) / 100f
        val cx = width / 2f
        val white = Color.WHITE
        val wall = theme.wallClock
        if (wall != null && wall.onLock) {
            // Часы SyncTheme и на экране блокировки — те же, что на рабочем столе, по фону блокировки.
            val lock = theme.copy(background = theme.lockScreenBackground(), dim = theme.lockScreenDim())
            val spot = wallClockSpot(context, lock, width, height)
            drawWallClock(context, canvas, lock, spot, previewMillis())
            if (wall.depth) foreground(context, lock.background, lock.dim, width, height)?.let {
                canvas.drawBitmap(it, 0f, 0f, null)
                it.recycle()
                drawWallClock(context, canvas, lock, spot, previewMillis(), alpha = WatchFaceXml.DEPTH_TIME_ALPHA)
            }
            return bmp
        }
        val depth = if (theme.lockDepth) foreground(context, theme.lockScreenBackground(), theme.lockScreenDim(), width, height) else null
        canvas.textCentered(dateString("EEE, d MMMM"), cx, 22 * u, textPaint(white, 4.6f * u))
        if (depth != null) {
            canvas.textCentered("10:08", cx, 46 * u, textPaint(white, 38 * u, typeface(FontWeight.BOLD)))
            canvas.drawBitmap(depth, 0f, 0f, null)
            depth.recycle()
        } else {
            canvas.textCentered("10:08", cx, 40 * u, textPaint(white, 26 * u, typeface(FontWeight.LIGHT)))
        }
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.BLACK, 0.35f) }
        canvas.drawCircle(cx - 36 * u, height - 14 * u, 7 * u, icon)
        canvas.drawCircle(cx + 36 * u, height - 14 * u, 7 * u, icon)
        return bmp
    }
}
