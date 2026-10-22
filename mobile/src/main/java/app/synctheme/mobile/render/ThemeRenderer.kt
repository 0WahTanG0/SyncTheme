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
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
import app.synctheme.core.DigitFont
import app.synctheme.core.FontWeight
import app.synctheme.core.Theme
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

    /** @param crop часть фото в кадре (доли); null — середина фото, как обычно у обоев. */
    fun background(context: Context, bg: Background, dim: Float, width: Int, height: Int, crop: RectF? = null): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, bg, width.toFloat(), height.toFloat(), photo(context, bg), crop)
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
    fun foreground(context: Context, bg: Background, dim: Float, width: Int, height: Int, crop: RectF? = null): Bitmap? {
        val name = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO } ?: return null
        val mask = SubjectMask.load(context, name) ?: return null
        val photo = photo(context, bg) ?: return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Обрезка в долях: у маски те же пропорции, что у фото, поэтому она ложится точно на объект.
        val c = crop ?: Crop.center(photo.width, photo.height, width.toFloat(), height.toFloat())
        drawCrop(canvas, photo, c, width.toFloat(), height.toFloat())
        darken(canvas, dim)
        val cut = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        drawCrop(canvas, mask, c, width.toFloat(), height.toFloat(), cut)
        return bmp
    }

    /** Слой глубины для часов (450×450, как фон циферблата и в том же кадре фото). */
    fun watchForeground(context: Context, theme: Theme): Bitmap? =
        foreground(context, theme.watchBackground(), theme.watchSettings().dim, WatchFaceXml.SIZE, WatchFaceXml.SIZE, watchCrop(context, theme))

    private val watchCrops = LinkedHashMap<String, RectF>()

    /**
     * Кадр фото для циферблата с глубиной: объект со своей стороны от времени и закрывает лишь край
     * цифр (см. [WatchFraming]). null — глубина выключена или объекта нет: фото по центру.
     */
    fun watchCrop(context: Context, theme: Theme): RectF? {
        val w = theme.watchSettings()
        val bg = theme.watchBackground()
        if (!w.depth) return null
        val name = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO } ?: return null
        val file = SubjectMask.file(context, name)
        if (!file.exists()) return null
        val time = WatchFaceXml.forLayer(w, WatchFaceXml.Layer.TIME)
        val key = "$name|${file.lastModified()}|${time.copy(textColor = 0, accentColor = 0, background = null).hashCode()}"
        synchronized(watchCrops) { watchCrops[key]?.let { return RectF(it) } }
        val mask = SubjectMask.load(context, name) ?: return null
        val crop = WatchFraming.crop(mask, timeInk(context, w))
        synchronized(watchCrops) {
            watchCrops[key] = RectF(crop)
            while (watchCrops.size > 8) watchCrops.remove(watchCrops.keys.first())
        }
        return crop
    }

    /** Где на циферблате время: слой «время» стиля в нескольких моментах (стрелки и «планеты» движутся). */
    private fun timeInk(context: Context, w: WatchSettings): FloatArray {
        val n = WatchFraming.INK_GRID
        val ink = FloatArray(n * n)
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val px = IntArray(n * n)
        for ((hour, minute) in listOf(10 to 8, 3 to 15, 6 to 40, 12 to 0, 8 to 50, 1 to 25)) {
            bmp.eraseColor(Color.TRANSPARENT)
            canvas.save()
            canvas.scale(n / WATCH, n / WATCH)
            FaceRenderer.drawTime(canvas, w, FaceState(System.currentTimeMillis(), hour, minute, 0f), fonts(context))
            canvas.restore()
            bmp.getPixels(px, 0, n, 0, 0, n, n)
            for (i in px.indices) ink[i] = max(ink[i], (px[i] ushr 24) / 255f)
        }
        bmp.recycle()
        return ink
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
        val bmp = background(context, theme.watchBackground(), w.dim, WatchFaceXml.SIZE, WatchFaceXml.SIZE, watchCrop(context, theme))
        drawWatchDecor(Canvas(bmp), w)
        return bmp
    }

    private fun drawWatchDecor(canvas: Canvas, w: WatchSettings) {
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

    private fun drawBackground(canvas: Canvas, bg: Background, w: Float, h: Float, photo: Bitmap?, crop: RectF? = null) {
        val colors = bg.colors.ifEmpty { listOf(Color.BLACK) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        when (bg.type) {
            BackgroundType.SOLID -> canvas.drawColor(colors.first())
            BackgroundType.PHOTO -> if (photo != null) {
                drawCrop(canvas, photo, crop ?: Crop.center(photo.width, photo.height, w, h), w, h)
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

    fun watchPreview(context: Context, theme: WatchSettings, watchBg: Bitmap, sizePx: Int, foreground: Bitmap? = null): Bitmap =
        FaceRenderer.render(context, theme, watchBg, foreground, FaceState.preview(DateFormat.is24HourFormat(context)), sizePx, fonts(context))

    /** Превью циферблата темы целиком: фон, слой глубины (если включён) и время. */
    fun watchPreview(context: Context, theme: Theme, sizePx: Int): Bitmap {
        val bg = watchBackground(context, theme)
        val fg = if (theme.watchSettings().depth) watchForeground(context, theme) else null
        return watchPreview(context, theme.watchSettings(), bg, sizePx, fg).also {
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

    fun phonePreview(context: Context, theme: Theme, width: Int, height: Int): Bitmap {
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
        } else {
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

    private fun drawPreviewWidgets(canvas: Canvas, theme: Theme, top: Float, u: Float, surface: Paint) {
        val date = RectF(6 * u, top, 47 * u, top + 41 * u)
        canvas.drawRoundRect(date, 7 * u, 7 * u, surface)
        canvas.textCentered(dateString("EEEE").uppercase(), date.centerX(), date.top + 8 * u, textPaint(theme.accentColor, 3.6f * u))
        canvas.textCentered(dateString("d"), date.centerX(), date.centerY() + 2 * u, textPaint(theme.textColor, 19 * u, typeface(FontWeight.LIGHT)))
        canvas.textCentered(dateString("LLLL"), date.centerX(), date.bottom - 7 * u, textPaint(theme.textColor, 3.6f * u))

        val battery = RectF(53 * u, top, 94 * u, top + 41 * u)
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
        val bg = theme.background
        val photo = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO }
        val mask = if (wall.depth && photo != null) SubjectMask.load(context, photo) else null
        if (mask != null) {
            val grid = MaskGrid.of(mask, Crop.center(mask.width, mask.height, w, h), 72, (72 * h / w).roundToInt())
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

    /** Прямоугольник, который занимают часы на обоях (для превью: куда не ставить виджеты). */
    private fun wallClockArea(theme: Theme, spot: WallClockSpot): RectF? {
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
        val u = width / 100f
        val white = Color.WHITE
        val depth = if (theme.lockDepth) foreground(context, theme.lockScreenBackground(), theme.lockScreenDim(), width, height) else null
        canvas.textCentered(dateString("EEE, d MMMM"), 50 * u, 22 * u, textPaint(white, 4.6f * u))
        if (depth != null) {
            canvas.textCentered("10:08", 50 * u, 46 * u, textPaint(white, 38 * u, typeface(FontWeight.BOLD)))
            canvas.drawBitmap(depth, 0f, 0f, null)
            depth.recycle()
        } else {
            canvas.textCentered("10:08", 50 * u, 40 * u, textPaint(white, 26 * u, typeface(FontWeight.LIGHT)))
        }
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.BLACK, 0.35f) }
        canvas.drawCircle(14 * u, height - 14 * u, 7 * u, icon)
        canvas.drawCircle(86 * u, height - 14 * u, 7 * u, icon)
        return bmp
    }
}
