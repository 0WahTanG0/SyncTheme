package app.synctheme.mobile.render

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
import app.synctheme.core.FontWeight
import app.synctheme.core.Theme
import app.synctheme.core.WatchFaceXml
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

    private var photoCache: Pair<String, Bitmap>? = null

    @Synchronized
    private fun photo(context: Context, theme: Theme): Bitmap? {
        val name = theme.background.photoFile?.takeIf { theme.background.type == BackgroundType.PHOTO } ?: return null
        photoCache?.let { (cached, bmp) -> if (cached == name) return bmp }
        val file = ThemeRepository.get(context).photoFile(name)
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        photoCache = name to bmp
        return bmp
    }

    fun background(context: Context, theme: Theme, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, theme.background, width.toFloat(), height.toFloat(), photo(context, theme))
        if (theme.dim > 0f) canvas.drawColor(Colors.withAlpha(Color.BLACK, theme.dim.coerceIn(0f, 0.8f)))
        return bmp
    }

    /** Квадратный фон для часов (фото обрезается по центру). */
    fun watchBackground(context: Context, theme: Theme): Bitmap =
        background(context, theme, WatchFaceXml.SIZE, WatchFaceXml.SIZE)

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

    fun watchPreview(theme: Theme, watchBg: Bitmap, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(sizePx / WATCH, sizePx / WATCH)
        val circle = Path().apply { addCircle(WATCH / 2, WATCH / 2, WATCH / 2, Path.Direction.CW) }
        canvas.clipPath(circle)
        canvas.drawBitmap(watchBg, null, RectF(0f, 0f, WATCH, WATCH), Paint(Paint.FILTER_BITMAP_FLAG))
        when (theme.clockStyle) {
            ClockStyle.DIGITAL -> drawDigital(canvas, theme)
            ClockStyle.STACKED -> drawStacked(canvas, theme)
            ClockStyle.ANALOG -> drawAnalog(canvas, theme)
        }
        return bmp
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

    private fun secondsRing(canvas: Canvas, theme: Theme) {
        val oval = RectF(8f, 8f, WATCH - 8f, WATCH - 8f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        p.color = Colors.withAlpha(theme.accentColor, 0x40 / 255f)
        canvas.drawArc(oval, 0f, 360f, false, p)
        p.color = theme.accentColor
        p.strokeWidth = 6f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(oval, -90f, 32 * 6f, false, p)
    }

    private fun complication(canvas: Canvas, theme: Theme, cx: Float, cy: Float, r: Float, ranged: Boolean, label: String) {
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

    private fun drawDigital(canvas: Canvas, theme: Theme) {
        if (theme.showSeconds) secondsRing(canvas, theme)
        if (theme.showDate) canvas.textCentered(dateString("EEE d MMM"), 225f, 114f, textPaint(theme.accentColor, 30f))
        canvas.textCentered("10:08", 225f, 215f, textPaint(theme.textColor, 132f, typeface(theme.fontWeight)))
        if (theme.watchComplications) {
            complication(canvas, theme, 165f, 348f, 48f, true, "80%")
            complication(canvas, theme, 285f, 348f, 48f, false, "6241")
        }
    }

    private fun drawStacked(canvas: Canvas, theme: Theme) {
        if (theme.showSeconds) secondsRing(canvas, theme)
        val tf = typeface(theme.fontWeight)
        canvas.textCentered("10", 225f, 143f, textPaint(theme.textColor, 168f, tf))
        canvas.textCentered("08", 225f, 297f, textPaint(theme.accentColor, 168f, tf))
        if (theme.showDate) canvas.textCentered(dateString("EEE d"), 225f, 400f, textPaint(theme.textColor, 26f))
        if (theme.watchComplications) {
            complication(canvas, theme, 63f, 222f, 45f, true, "80%")
            complication(canvas, theme, 387f, 222f, 45f, false, "6241")
        }
    }

    private fun drawAnalog(canvas: Canvas, theme: Theme) {
        val c = WATCH / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 60) {
            val main = i % 15 == 0
            val hour = i % 5 == 0
            val len = if (main) 30f else if (hour) 20f else 7f
            p.strokeWidth = if (main) 8f else if (hour) 5f else 2f
            p.color = when {
                main -> theme.accentColor
                hour -> Colors.withAlpha(theme.textColor, 0.9f)
                else -> Colors.withAlpha(theme.textColor, 0.5f)
            }
            val a = Math.toRadians(i * 6.0)
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            canvas.drawLine(c + 214 * s, c - 214 * co, c + (214 - len) * s, c - (214 - len) * co, p)
        }
        if (theme.showDate) canvas.textCentered(dateString("EEE d"), 328f, 225f, textPaint(theme.accentColor, 26f))
        if (theme.watchComplications) {
            complication(canvas, theme, 110f, 225f, 48f, true, "80%")
            complication(canvas, theme, 225f, 340f, 48f, false, "6241")
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
}
