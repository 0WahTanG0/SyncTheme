package app.synctheme.mobile.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
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

    fun background(context: Context, bg: Background, dim: Float, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, bg, width.toFloat(), height.toFloat(), photo(context, bg))
        finish(canvas, bg, dim)
        return bmp
    }

    /** Оттенок «Хамелеона» и затемнение — одинаково для фона и для слоя глубины. */
    private fun finish(canvas: Canvas, bg: Background, dim: Float) {
        if (bg.tint ushr 24 != 0) {
            canvas.drawPaint(Paint().apply { color = bg.tint; blendMode = BlendMode.SOFT_LIGHT })
        }
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
        drawCenterCrop(canvas, photo, width.toFloat(), height.toFloat())
        finish(canvas, bg, dim)
        val cut = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        drawCenterCrop(canvas, mask, width.toFloat(), height.toFloat(), cut)
        return bmp
    }

    /** Слой глубины для часов (450×450, как фон циферблата). */
    fun watchForeground(context: Context, theme: Theme): Bitmap? =
        foreground(context, theme.watchBackground(), theme.watchSettings().dim, WatchFaceXml.SIZE, WatchFaceXml.SIZE)

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

    private fun drawBackground(canvas: Canvas, bg: Background, w: Float, h: Float, photo: Bitmap?) {
        val colors = bg.colors.ifEmpty { listOf(Color.BLACK) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        when (bg.type) {
            BackgroundType.SOLID -> canvas.drawColor(colors.first())
            BackgroundType.PHOTO -> if (photo != null) {
                drawCenterCrop(canvas, photo, w, h)
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

    private fun drawCenterCrop(
        canvas: Canvas, photo: Bitmap, w: Float, h: Float,
        paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
    ) {
        val scale = max(w / photo.width, h / photo.height)
        val srcW = w / scale
        val srcH = h / scale
        val left = (photo.width - srcW) / 2
        val top = (photo.height - srcH) / 2
        val src = Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        canvas.drawBitmap(photo, src, RectF(0f, 0f, w, h), paint)
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
        if (wall != null) {
            // Часы на обоях (живые обои) и объект с фото поверх них.
            drawWallClock(context, canvas, theme, width, height, previewMillis())
            if (wall.depth) foreground(context, theme.background, theme.dim, width, height)?.let {
                canvas.drawBitmap(it, 0f, 0f, null)
                it.recycle()
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

        // Дата 2x2 и батарея 2x2; с часами на обоях они ниже, над доком, — чтобы не закрывать часы.
        val top = if (wall != null) height - 64 * u else 57 * u
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

        // Док с иконками.
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.WHITE, 0.85f) }
        val dockY = height - 14 * u
        for (i in 0 until 4) canvas.drawCircle((20 + i * 20) * u, dockY, 6.5f * u, icon)
        return bmp
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
     * Часы на обоях телефона (рисуют живые обои SyncTheme): время шрифтом цифр темы,
     * дата цветом акцента, лёгкая тень для читаемости на фото.
     */
    fun drawWallClock(context: Context, canvas: Canvas, theme: Theme, width: Int, height: Int, millis: Long) {
        val wall = theme.wallClock ?: return
        val font = wallClockFont(theme)
        val tf = fonts(context).of(font)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val hours = if (DateFormat.is24HourFormat(context)) cal.get(java.util.Calendar.HOUR_OF_DAY)
            else (cal.get(java.util.Calendar.HOUR) + 11) % 12 + 1
        val hh = "%02d".format(hours)
        val mm = "%02d".format(cal.get(java.util.Calendar.MINUTE))
        val w = width.toFloat()
        val h = height.toFloat()
        val shadow = Colors.withAlpha(Color.BLACK, 0.35f)
        fun digitsAt(text: String, cy: Float, maxW: Float, maxH: Float, color: Int) {
            val size = WatchFaceXml.fit(font, text.replace(Regex("[0-9]"), "0"), maxW.toInt(), maxH.toInt())
            val p = textPaint(color, size, tf).apply { setShadowLayer(size * 0.04f, 0f, size * 0.02f, shadow) }
            canvas.drawText(text, w / 2, cy + (font.top + font.bottom) / 2 * size, p)
        }
        fun date(cy: Float, size: Float) {
            if (!wall.showDate) return
            val p = textPaint(theme.accentColor, size, fonts(context).label).apply { setShadowLayer(size * 0.15f, 0f, 2f, shadow) }
            canvas.textCentered(dateString(millis, "EEEE, d MMMM"), w / 2, cy, p)
        }
        when (wall.layout) {
            app.synctheme.core.WallClockLayout.TOP -> {
                date(h * 0.115f, w * 0.045f)
                digitsAt("$hh:$mm", h * 0.215f, w * 0.86f, h * 0.12f, theme.textColor)
            }
            app.synctheme.core.WallClockLayout.STACKED -> {
                date(h * 0.095f, w * 0.045f)
                digitsAt(hh, h * 0.2f, w * 0.78f, h * 0.15f, theme.textColor)
                digitsAt(mm, h * 0.37f, w * 0.78f, h * 0.15f, theme.accentColor)
            }
            app.synctheme.core.WallClockLayout.CENTER -> {
                digitsAt("$hh:$mm", h * 0.42f, w * 0.56f, h * 0.075f, theme.textColor)
                date(h * 0.48f, w * 0.04f)
            }
        }
    }

    private fun dateString(millis: Long, pattern: String) =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)).replace(".", "")

    /** Превью экрана блокировки: обои блокировки и крупные часы One UI сверху. */
    fun lockPreview(context: Context, theme: Theme, width: Int, height: Int): Bitmap {
        val bmp = lockBackground(context, theme, width, height)
        val canvas = Canvas(bmp)
        val u = width / 100f
        val white = Color.WHITE
        canvas.textCentered(dateString("EEE, d MMMM"), 50 * u, 22 * u, textPaint(white, 4.6f * u))
        canvas.textCentered("10:08", 50 * u, 40 * u, textPaint(white, 26 * u, typeface(FontWeight.LIGHT)))
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.BLACK, 0.35f) }
        canvas.drawCircle(14 * u, height - 14 * u, 7 * u, icon)
        canvas.drawCircle(86 * u, height - 14 * u, 7 * u, icon)
        return bmp
    }
}
