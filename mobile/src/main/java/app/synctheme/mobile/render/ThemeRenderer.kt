package app.synctheme.mobile.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import app.synctheme.R
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
        if (dim > 0f) canvas.drawColor(Colors.withAlpha(Color.BLACK, dim.coerceIn(0f, 0.8f)))
        return bmp
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

    private fun drawCenterCrop(canvas: Canvas, photo: Bitmap, w: Float, h: Float) {
        val scale = max(w / photo.width, h / photo.height)
        val srcW = w / scale
        val srcH = h / scale
        val left = (photo.width - srcW) / 2
        val top = (photo.height - srcH) / 2
        val src = Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        canvas.drawBitmap(photo, src, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    // ---------- Превью циферблата (приближение того, что нарисует Wear OS) ----------

    fun watchPreview(context: Context, theme: WatchSettings, watchBg: Bitmap, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(sizePx / WATCH, sizePx / WATCH)
        val circle = Path().apply { addCircle(WATCH / 2, WATCH / 2, WATCH / 2, Path.Direction.CW) }
        canvas.clipPath(circle)
        canvas.drawBitmap(watchBg, null, RectF(0f, 0f, WATCH, WATCH), Paint(Paint.FILTER_BITMAP_FLAG))
        val fonts = Fonts(context)
        when (theme.clockStyle) {
            ClockStyle.DIGITAL -> drawDigital(canvas, theme, fonts)
            ClockStyle.STACKED -> drawStacked(canvas, theme, fonts)
            ClockStyle.ANALOG -> drawAnalog(canvas, theme)
            ClockStyle.BOLD -> drawBold(canvas, theme, fonts)
            ClockStyle.ORBIT -> drawOrbit(canvas, theme, fonts)
            ClockStyle.NEON -> drawNeon(canvas, theme, fonts)
            ClockStyle.BLOB -> drawBlob(canvas, theme, fonts)
            ClockStyle.GLASS -> drawGlass(canvas, theme, fonts)
            ClockStyle.MINIMAL -> drawMinimal(canvas, theme, fonts)
            ClockStyle.DRUM -> drawDrum(canvas, theme, fonts)
            ClockStyle.COLUMNS -> drawColumns(canvas, theme, fonts)
            ClockStyle.PLANETS -> drawPlanets(canvas, theme, fonts)
            ClockStyle.MATRIX -> drawMatrix(canvas, theme, fonts)
            ClockStyle.LIQUID -> drawLiquid(canvas, theme, fonts)
            ClockStyle.DIAGONAL -> drawDiagonal(canvas, theme, fonts)
        }
        return bmp
    }

    /** Те же шрифты, что лежат в шаблоне циферблата. */
    private class Fonts(private val context: Context) {
        private val res = context.resources
        val round: Typeface = res.getFont(R.font.nunito_900)
        val label: Typeface = res.getFont(R.font.onest_600)
        private val digits = HashMap<DigitFont, Typeface>()

        @SuppressLint("DiscouragedApi")
        fun of(font: DigitFont): Typeface = digits.getOrPut(font) {
            val id = font.res?.let { res.getIdentifier(it, "font", context.packageName) } ?: 0
            if (id != 0) res.getFont(id) else Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }
    }

    /** Цифры шрифтом font, вписанные в maxW × maxH, с центром цифр в (cx, cy) — как WatchFaceXml.digits. */
    private fun Canvas.digits(text: String, cx: Float, cy: Float, maxW: Int, maxH: Int, font: DigitFont, color: Int, f: Fonts) {
        val size = WatchFaceXml.fit(font, text.replace(Regex("[0-9]"), "0"), maxW, maxH)
        val paint = textPaint(color, size, f.of(font))
        drawText(text, cx, cy + (font.top + font.bottom) / 2 * size, paint)
    }

    // Превью «живых» стилей: время 10:08:32, как в PREVIEW_TIME циферблата.
    private const val PREVIEW_HOUR = 304f // (10 + 8/60) * 30
    private const val PREVIEW_MINUTE = 51.2f // (8 + 32/60) * 6
    private const val PREVIEW_SECOND = 192f // 32 * 6

    private fun ringArc(canvas: Canvas, r: Float, sweep: Float, color: Int, width: Float) {
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; this.color = color
        }
        canvas.drawArc(RectF(c - r, c - r, c + r, c + r), -90f, sweep, false, p)
    }

    private fun ringArc(canvas: Canvas, r: Float, start: Float, end: Float, color: Int, width: Float) {
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; this.color = color
        }
        canvas.drawArc(RectF(c - r, c - r, c + r, c + r), -90f + start, end - start, false, p)
    }

    private fun dot(canvas: Canvas, r: Float, angle: Float, color: Int, size: Float) {
        val a = Math.toRadians(angle.toDouble())
        val c = WATCH / 2
        canvas.drawCircle(c + r * sin(a).toFloat(), c - r * cos(a).toFloat(), size / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawBold(canvas: Canvas, w: WatchSettings, f: Fonts) {
        if (w.showSeconds) secondsRing(canvas, w)
        val font = w.digits() ?: DigitFont.NARROW
        canvas.digits("10", 225f, 136f, 300, 170, font, w.textColor, f)
        canvas.digits("08", 225f, 318f, 300, 170, font, w.accentColor, f)
        if (w.showDate) canvas.textCentered(dateString("EEE d"), 378f, 225f, textPaint(w.accentColor, 24f, f.label))
        if (w.complications) complication(canvas, w, 63f, 225f, 39f, true, "80%")
    }

    private fun drawOrbit(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val track = Colors.withAlpha(w.textColor, 0x26 / 255f)
        ringArc(canvas, 166f, 360f, track, 12f)
        ringArc(canvas, 166f, PREVIEW_HOUR, Colors.withAlpha(w.accentColor, 0.8f), 12f)
        ringArc(canvas, 190f, 360f, track, 8f)
        ringArc(canvas, 190f, PREVIEW_MINUTE, w.textColor, 8f)
        dot(canvas, 190f, PREVIEW_MINUTE, w.accentColor, 18f)
        if (w.showSeconds) {
            ringArc(canvas, 212f, 360f, track, 4f)
            ringArc(canvas, 212f, PREVIEW_SECOND, w.accentColor, 4f)
            dot(canvas, 212f, PREVIEW_SECOND, w.textColor, 12f)
        }
        if (w.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 138f, textPaint(w.accentColor, 26f, f.label))
        canvas.digits("10:08", 225f, 222f, 280, 76, w.digits() ?: DigitFont.SPACE, w.textColor, f)
        if (w.complications) {
            complication(canvas, w, 185f, 323f, 33f, true, "80%")
            complication(canvas, w, 265f, 323f, 33f, false, "6241")
        }
    }

    private fun drawNeon(canvas: Canvas, w0: WatchSettings, f: Fonts) {
        val w = if (WatchFaceXml.isLight(w0.textColor)) w0 else w0.copy(textColor = Color.WHITE)
        for ((width, alpha) in listOf(22f to 0x10, 14f to 0x22, 8f to 0x50)) {
            ringArc(canvas, 210f, 360f, Colors.withAlpha(w.accentColor, alpha / 255f), width)
        }
        ringArc(canvas, 210f, 360f, w.accentColor, 3.5f)
        for (h in 0 until 12) {
            dot(canvas, 186f, h * 30f, if (h % 3 == 0) w.textColor else Colors.withAlpha(w.textColor, 0.6f), if (h % 3 == 0) 14f else 6f)
        }
        if (w.showSeconds) {
            for ((len, alpha) in listOf(48 to 0x26, 28 to 0x55, 12 to 0xAA)) {
                ringArc(canvas, 210f, PREVIEW_SECOND - len, PREVIEW_SECOND, Colors.withAlpha(Color.WHITE, alpha / 255f), 6f)
            }
            dot(canvas, 210f, PREVIEW_SECOND, Color.WHITE, 11f)
        }
        val halo = Colors.withAlpha(w.accentColor, 0x66 / 255f)
        val font = w.digits() ?: DigitFont.TUBE
        canvas.digits("10:08", 222f, 225f, 300, 84, font, halo, f)
        canvas.digits("10:08", 228f, 231f, 300, 84, font, halo, f)
        canvas.digits("10:08", 225f, 228f, 300, 84, font, w.textColor, f)
        if (w.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 136f, textPaint(w.accentColor, 24f, f.label))
        if (w.complications) {
            complication(canvas, w, 184f, 334f, 34f, true, "80%")
            complication(canvas, w, 266f, 334f, 34f, false, "6241")
        }
    }

    private fun drawBlob(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val on = WatchFaceXml.onAccent(w.accentColor)
        if (w.showDate) canvas.textCentered(dateString("d"), 340f, 225f, textPaint(on, 34f, f.round))
        if (w.complications) complication(canvas, w, 225f, 339f, 39f, true, "80%")
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = on }
        fun hand(angle: Float, length: Float, tail: Float, width: Float) {
            val a = Math.toRadians(angle.toDouble())
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            p.strokeWidth = width
            canvas.drawLine(c - tail * s, c + tail * co, c + length * s, c - length * co, p)
        }
        hand(PREVIEW_HOUR, 104f, 8f, 28f)
        hand(PREVIEW_MINUTE, 168f, 8f, 20f)
        if (w.showSeconds) hand(PREVIEW_SECOND, 182f, 37f, 6f)
        p.style = Paint.Style.FILL
        canvas.drawCircle(c, c, 18f, p)
        p.color = w.accentColor
        canvas.drawCircle(c, c, 7f, p)
    }

    private fun drawGlass(canvas: Canvas, w: WatchSettings, f: Fonts) {
        if (w.showSeconds) {
            ringArc(canvas, 172f, PREVIEW_SECOND, w.accentColor, 5f)
            dot(canvas, 172f, PREVIEW_SECOND, w.accentColor, 13f)
        }
        if (w.showDate) canvas.textCentered(dateString("EEE, d MMM"), 225f, 140f, textPaint(Colors.withAlpha(w.textColor, 0.8f), 25f, f.label))
        canvas.digits("10:08", 225f, 222f, 280, 84, w.digits() ?: DigitFont.ITALIC, w.textColor, f)
        if (w.complications) {
            complication(canvas, w, 186f, 322f, 34f, true, "80%")
            complication(canvas, w, 264f, 322f, 34f, false, "6241")
        }
    }

    private fun drawMinimal(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val c = WATCH / 2
        val edge = 222f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 60) {
            val hour = i % 5 == 0
            val len = if (hour) 16f else 7f
            p.strokeWidth = if (hour) 3f else 1.5f
            p.color = Colors.withAlpha(w.textColor, if (hour) 0.7f else 0.25f)
            val a = Math.toRadians(i * 6.0)
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            canvas.drawLine(c + edge * s, c - edge * co, c + (edge - len) * s, c - (edge - len) * co, p)
        }
        dot(canvas, 196f, PREVIEW_MINUTE, w.accentColor, 16f)
        if (w.showSeconds) dot(canvas, 180f, PREVIEW_SECOND, Colors.withAlpha(w.textColor, 0.6f), 7f)
        canvas.digits("10:08", 225f, 220f, 320, 90, w.digits() ?: DigitFont.THIN, w.textColor, f)
        if (w.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 308f, textPaint(w.accentColor, 22f, f.label))
        if (w.complications) {
            complication(canvas, w, 187f, 361f, 31f, true, "80%")
            complication(canvas, w, 263f, 361f, 31f, false, "6241")
        }
    }

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

    private fun secondsRing(canvas: Canvas, theme: WatchSettings) {
        val oval = RectF(4.5f, 4.5f, WATCH - 4.5f, WATCH - 4.5f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        p.color = Colors.withAlpha(theme.accentColor, 0x40 / 255f)
        canvas.drawArc(oval, 0f, 360f, false, p)
        p.color = theme.accentColor
        p.strokeWidth = 6f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(oval, -90f, 32 * 6f, false, p)
    }

    private fun complication(canvas: Canvas, theme: WatchSettings, cx: Float, cy: Float, r: Float, ranged: Boolean, label: String) {
        val disc = if (WatchFaceXml.isLight(theme.textColor)) 0x66000000 else 0x80FFFFFF.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = disc }
        canvas.drawCircle(cx, cy, r, p)
        if (ranged) {
            val arc = RectF(cx - r + 5, cy - r + 5, cx + r - 5, cy + r - 5)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Colors.withAlpha(theme.textColor, 0x40 / 255f)
            canvas.drawArc(arc, -230f, 280f, false, p)
            p.color = theme.accentColor
            canvas.drawArc(arc, -230f, 280f * 0.8f, false, p)
        }
        canvas.textCentered(label, cx, cy, textPaint(theme.textColor, r * 0.46f, Typeface.create(Typeface.DEFAULT, 600, false)))
    }

    private fun drawDigital(canvas: Canvas, theme: WatchSettings, f: Fonts) {
        if (theme.showSeconds) secondsRing(canvas, theme)
        if (theme.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 114f, textPaint(theme.accentColor, 30f))
        val font = theme.digits()
        if (font != null) canvas.digits("10:08", 225f, 215f, 380, 110, font, theme.textColor, f)
        else canvas.textCentered("10:08", 225f, 215f, textPaint(theme.textColor, 132f, typeface(theme.fontWeight)))
        if (theme.complications) {
            complication(canvas, theme, 165f, 348f, 48f, true, "80%")
            complication(canvas, theme, 285f, 348f, 48f, false, "6241")
        }
    }

    private fun drawStacked(canvas: Canvas, theme: WatchSettings, f: Fonts) {
        if (theme.showSeconds) secondsRing(canvas, theme)
        val font = theme.digits()
        if (font != null) {
            canvas.digits("10", 225f, 143f, 240, 130, font, theme.textColor, f)
            canvas.digits("08", 225f, 297f, 240, 130, font, theme.accentColor, f)
        } else {
            val tf = typeface(theme.fontWeight)
            canvas.textCentered("10", 225f, 143f, textPaint(theme.textColor, 168f, tf))
            canvas.textCentered("08", 225f, 297f, textPaint(theme.accentColor, 168f, tf))
        }
        if (theme.showDate) canvas.textCentered(dateString("EEE d"), 225f, 400f, textPaint(theme.textColor, 26f))
        if (theme.complications) {
            complication(canvas, theme, 63f, 222f, 45f, true, "80%")
            complication(canvas, theme, 387f, 222f, 45f, false, "6241")
        }
    }

    private fun drawAnalog(canvas: Canvas, theme: WatchSettings) {
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        val edge = 222f
        for (i in 0 until 60) {
            val main = i % 15 == 0
            val hour = i % 5 == 0
            val len: Float
            when (theme.dialStyle) {
                DialStyle.TICKS -> {
                    len = if (main) 30f else if (hour) 20f else 7f
                    p.strokeWidth = if (main) 8f else if (hour) 5f else 2f
                    p.color = when {
                        main -> theme.accentColor
                        hour -> Colors.withAlpha(theme.textColor, 0.9f)
                        else -> Colors.withAlpha(theme.textColor, 0.5f)
                    }
                }
                DialStyle.NUMBERS -> {
                    len = if (hour) 8f else 5f
                    p.strokeWidth = if (hour) 3f else 1.5f
                    p.color = Colors.withAlpha(theme.textColor, if (hour) 0.7f else 0.4f)
                }
                DialStyle.MINIMAL -> {
                    if (!main) continue
                    len = 26f
                    p.strokeWidth = 7f
                    p.color = theme.accentColor
                }
            }
            val a = Math.toRadians(i * 6.0)
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            canvas.drawLine(c + edge * s, c - edge * co, c + (edge - len) * s, c - (edge - len) * co, p)
        }
        if (theme.dialStyle == DialStyle.NUMBERS) {
            val tf = typeface(theme.fontWeight)
            for (h in 1..12) {
                val a = Math.toRadians(h * 30.0)
                val col = if (h % 3 == 0) theme.accentColor else theme.textColor
                canvas.textCentered("$h", c + 186 * sin(a).toFloat(), c - 186 * cos(a).toFloat(), textPaint(col, 38f, tf))
            }
            if (theme.showDate) canvas.textCentered(dateString("EEE d"), 225f, 132f, textPaint(theme.accentColor, 26f))
            if (theme.complications) {
                complication(canvas, theme, 158f, 304f, 42f, true, "80%")
                complication(canvas, theme, 292f, 304f, 42f, false, "6241")
            }
        } else {
        if (theme.showDate) canvas.textCentered(dateString("EEE d"), 328f, 225f, textPaint(theme.accentColor, 26f))
        if (theme.complications) {
            complication(canvas, theme, 110f, 225f, 48f, true, "80%")
            complication(canvas, theme, 225f, 340f, 48f, false, "6241")
        }
        }
        fun hand(angle: Float, length: Float, tail: Float, width: Float, color: Int, offset: Float) {
            val a = Math.toRadians(angle.toDouble())
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            p.color = color
            p.strokeWidth = width
            canvas.drawLine(c - tail * s + offset, c + tail * co + offset, c + length * s + offset, c - length * co + offset, p)
        }
        val shadow = 0x59000000
        for ((color, offset) in listOf(shadow to 3f, 0 to 0f)) {
            val shadowPass = offset > 0
            hand(304f, 130f, 20f, 18f, if (shadowPass) color else theme.textColor, offset)
            hand(51.2f, 190f, 20f, 12f, if (shadowPass) color else theme.textColor, offset)
            if (theme.showSeconds) hand(192f, 200f, 40f, 6f, if (shadowPass) color else theme.accentColor, offset)
        }
        p.style = Paint.Style.FILL
        p.color = theme.accentColor
        canvas.drawCircle(c, c, 14f, p)
        p.color = Color.BLACK
        canvas.drawCircle(c, c, 5f, p)
    }

    private fun pointOn(r: Float, angle: Float): Pair<Float, Float> {
        val a = Math.toRadians(angle.toDouble())
        return WATCH / 2 + r * sin(a).toFloat() to WATCH / 2 - r * cos(a).toFloat()
    }

    private fun drawDrum(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val font = w.digits() ?: DigitFont.MONO
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Colors.withAlpha(w.accentColor, 0.2f)
        canvas.drawRoundRect(RectF(296f, 191f, 446f, 259f), 34f, 34f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = w.accentColor
        canvas.drawRoundRect(RectF(296f, 191f, 446f, 259f), 34f, 34f, p)
        val minuteTurn = 90f - PREVIEW_MINUTE
        val hourTurn = 90f - PREVIEW_HOUR
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        p.color = Colors.withAlpha(w.textColor, 0x55 / 255f)
        for (m in 0 until 60) {
            if (m % 5 == 0) continue
            val (x1, y1) = pointOn(219f, m * 6f + minuteTurn)
            val (x2, y2) = pointOn(212f, m * 6f + minuteTurn)
            canvas.drawLine(x1, y1, x2, y2, p)
        }
        fun label(text: String, angle: Float, r: Float, maxW: Int, maxH: Int, color: Int) {
            val (x, y) = pointOn(r, angle)
            canvas.save()
            canvas.rotate(angle - 90f, x, y)
            canvas.digits(text, x, y, maxW, maxH, font, color, f)
            canvas.restore()
        }
        for (m in 0 until 60 step 5) label("%02d".format(m), m * 6f + minuteTurn, 190f, 34, 20, w.textColor)
        for (h in 0 until 12) label("${if (h == 0) 12 else h}", h * 30f + hourTurn, 124f, 54, 32, if (h % 3 == 0) w.accentColor else w.textColor)
        if (w.showSeconds) {
            ringArc(canvas, 62f, 360f, Colors.withAlpha(w.textColor, 0x26 / 255f), 4f)
            ringArc(canvas, 62f, PREVIEW_SECOND, w.accentColor, 4f)
            dot(canvas, 62f, PREVIEW_SECOND, w.textColor, 10f)
        }
        if (w.showDate) {
            canvas.textCentered(dateString("EEE"), 225f, 211f, textPaint(w.accentColor, 22f, f.label))
            canvas.textCentered(dateString("d"), 225f, 240f, textPaint(w.textColor, 30f, f.label))
        }
    }

    private fun drawColumns(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val font = w.digits() ?: DigitFont.SERIF
        val faded = Colors.withAlpha(w.textColor, 0x59 / 255f)
        canvas.digits("10", 140f, 225f, 150, 116, font, w.textColor, f)
        canvas.digits("08", 310f, 225f, 150, 116, font, w.accentColor, f)
        canvas.digits("09", 140f, 92f, 90, 46, font, faded, f)
        canvas.digits("11", 140f, 358f, 90, 46, font, faded, f)
        canvas.digits("07", 310f, 92f, 90, 46, font, faded, f)
        canvas.digits("09", 310f, 358f, 90, 46, font, faded, f)
        if (w.showSeconds) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(w.textColor, 0x26 / 255f) }
            canvas.drawRoundRect(RectF(221f, 150f, 229f, 300f), 4f, 4f, p)
            p.color = w.accentColor
            canvas.drawRoundRect(RectF(221f, 150f, 229f, 150f + 8 + 32 * 2.4f), 4f, 4f, p)
        }
        if (w.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 409f, textPaint(w.accentColor, 22f, f.label))
    }

    private fun drawPlanets(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val font = w.digits() ?: DigitFont.WIDE
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Colors.withAlpha(w.textColor, 0.2f) }
        canvas.drawCircle(c, c, 105f, p)
        canvas.drawCircle(c, c, 178f, p)
        p.style = Paint.Style.FILL
        p.color = w.accentColor
        canvas.drawCircle(c, c, 9f, p)
        fun planet(r: Float, radius: Float, angle: Float, fill: Int, textColor: Int, text: String) {
            val (x, y) = pointOn(r - radius, angle)
            val (sx, sy) = pointOn(12f, angle)
            val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = Colors.withAlpha(Color.WHITE, 0.25f); strokeCap = Paint.Cap.ROUND }
            canvas.drawLine(sx, sy, x, y, ray)
            val (px, py) = pointOn(r, angle)
            canvas.drawCircle(px, py, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            canvas.digits(text, px, py, (radius * 2 * 0.72f).toInt(), (radius * 2 * 0.42f).toInt(), font, textColor, f)
        }
        planet(105f, 44f, PREVIEW_HOUR, w.accentColor, WatchFaceXml.onAccent(w.accentColor), "10")
        planet(178f, 36f, PREVIEW_MINUTE, w.textColor, WatchFaceXml.onAccent(w.textColor), "08")
        if (w.showSeconds) {
            ringArc(canvas, 214f, PREVIEW_SECOND - 30, PREVIEW_SECOND, Colors.withAlpha(w.accentColor, 0.4f), 4f)
            dot(canvas, 214f, PREVIEW_SECOND, w.accentColor, 12f)
        }
        if (w.showDate) canvas.textCentered(dateString("EEE d"), 225f, 268f, textPaint(Colors.withAlpha(w.textColor, 0.8f), 22f, f.label))
    }

    private fun drawMatrix(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 60) {
            val (x, y) = pointOn(207f, i * 6f)
            p.color = when {
                !w.showSeconds -> Colors.withAlpha(w.textColor, 0.4f)
                i <= 32 -> w.accentColor
                else -> Colors.withAlpha(w.accentColor, 60 / 255f)
            }
            canvas.drawCircle(x, y, if (i % 5 == 0) 5f else 3f, p)
        }
        canvas.digits("10:08", 225f, 212f, 330, 112, w.digits() ?: DigitFont.DOTS, w.textColor, f)
        if (w.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 308f, textPaint(w.accentColor, 24f, f.label))
        if (w.complications) {
            complication(canvas, w, 187f, 365f, 31f, true, "80%")
            complication(canvas, w, 263f, 365f, 31f, false, "6241")
        }
    }

    private fun drawLiquid(canvas: Canvas, w: WatchSettings, f: Fonts) {
        val level = 33 * 7.5f
        val fluid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(w.accentColor, 0x8C / 255f) }
        canvas.drawRect(0f, WATCH - level, WATCH, WATCH, fluid)
        canvas.drawOval(RectF(-90f, 430f - level, 480f, 470f - level), fluid)
        val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.withAlpha(Color.WHITE, 0.4f) }
        for ((k, bx) in listOf(0 to 120, 1 to 210, 2 to 300, 3 to 165)) {
            val r = 6f + k * 2
            val y = (440f - ((32 * 7 + k * 17) % 60) * 6f).coerceIn(456f - level, 440f)
            canvas.drawCircle(bx + r, y + r, r, bubble)
        }
        val font = w.digits() ?: DigitFont.BUBBLE
        canvas.digits("10:08", 228f, 213f, 330, 110, font, 0x40000000, f)
        canvas.digits("10:08", 225f, 210f, 330, 110, font, w.textColor, f)
        if (w.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 128f, textPaint(w.textColor, 26f, f.label))
        if (w.complications) complication(canvas, w, 225f, 335f, 39f, true, "80%")
    }

    private fun drawDiagonal(canvas: Canvas, w: WatchSettings, f: Fonts) {
        if (w.showSeconds) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; color = Colors.withAlpha(w.textColor, 0.2f) }
            canvas.drawLine(108f, 342f, 342f, 108f, p)
            p.color = w.accentColor
            val t = 32 * 234f / 59
            canvas.drawCircle(108f + t, 342f - t, 8f, p)
        }
        val font = w.digits() ?: DigitFont.BLOCK
        canvas.digits("10", 165f, 165f, 210, 140, font, w.textColor, f)
        canvas.digits("08", 288f, 292f, 210, 140, font, w.accentColor, f)
        if (w.showDate) canvas.textCentered(dateString("EEE d"), 355f, 135f, textPaint(w.accentColor, 22f, f.label))
        if (w.complications) complication(canvas, w, 100f, 300f, 38f, true, "80%")
    }

    // ---------- Превью телефона: обои + виджеты ----------

    fun phonePreview(context: Context, theme: Theme, width: Int, height: Int): Bitmap {
        val bmp = background(context, theme, width, height)
        val canvas = Canvas(bmp)
        val u = width / 100f
        val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Colors.withAlpha(Colors.widgetSurface(theme), theme.widgetOpacity)
        }
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

        // Дата 2x2 и батарея 2x2.
        val date = RectF(6 * u, 57 * u, 47 * u, 98 * u)
        canvas.drawRoundRect(date, 7 * u, 7 * u, surface)
        canvas.textCentered(dateString("EEEE").uppercase(), date.centerX(), date.top + 8 * u, textPaint(theme.accentColor, 3.6f * u))
        canvas.textCentered(dateString("d"), date.centerX(), date.centerY() + 2 * u, textPaint(theme.textColor, 19 * u, typeface(FontWeight.LIGHT)))
        canvas.textCentered(dateString("LLLL"), date.centerX(), date.bottom - 7 * u, textPaint(theme.textColor, 3.6f * u))

        val battery = RectF(53 * u, 57 * u, 94 * u, 98 * u)
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
