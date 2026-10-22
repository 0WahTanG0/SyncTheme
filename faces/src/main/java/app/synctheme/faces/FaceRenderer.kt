package app.synctheme.faces

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
import app.synctheme.core.DigitFont
import app.synctheme.core.FontWeight
import app.synctheme.core.WatchFaceXml
import app.synctheme.core.WatchFaceXml.Layer
import app.synctheme.core.TimeShift
import app.synctheme.core.WatchSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Шрифты циферблата из res/font модуля (те же файлы лежат в шаблоне WFF). */
class FaceFonts(private val context: Context) {
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

/**
 * Рисует циферблат на Canvas так же, как его описывает WatchFaceXml (те же слои: обычный,
 * «время поверх объекта» и Always-On) — для превью на телефоне. Координаты — в «точках» 450×450.
 */
object FaceRenderer {
    const val SIZE = WatchFaceXml.SIZE.toFloat()
    private const val C = SIZE / 2

    /** Картинка циферблата sizePx × sizePx; shift — сдвиг блока времени (см. WatchFaceXml.generate). */
    fun render(
        context: Context, w: WatchSettings, bg: Bitmap?, fg: Bitmap?, state: FaceState, sizePx: Int,
        fonts: FaceFonts = FaceFonts(context), shift: TimeShift? = w.timeShift,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(sizePx / SIZE, sizePx / SIZE)
        draw(canvas, w, bg, fg, state, fonts, shift)
        return bmp
    }

    /** Рисует циферблат на canvas, уже масштабированный к 450×450. */
    fun draw(canvas: Canvas, w: WatchSettings, bg: Bitmap?, fg: Bitmap?, state: FaceState, fonts: FaceFonts, shift: TimeShift? = w.timeShift) {
        val moved = WatchFaceXml.activeShift(w, shift)
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(C, C, C, Path.Direction.CW) })
        canvas.drawColor(Color.BLACK)
        if (state.ambient) {
            // Always-On: фона, объекта с фото и мини-виджетов нет, время — светлыми цветами.
            Frame(canvas, WatchFaceXml.forLayer(w, Layer.AMBIENT), state, fonts, Layer.AMBIENT, null).style()
            canvas.restore()
            return
        }
        bg?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG)) }
        val full = Frame(canvas, w, state, fonts, Layer.FULL, moved)
        full.style()
        if (w.depth && fg != null) {
            canvas.drawBitmap(fg, null, RectF(0f, 0f, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
            // Время поверх объекта — полупрозрачное, чтобы его было видно сквозь объект.
            canvas.saveLayerAlpha(0f, 0f, SIZE, SIZE, WatchFaceXml.DEPTH_TIME_ALPHA)
            Frame(canvas, WatchFaceXml.forLayer(w, Layer.TIME), state, fonts, Layer.TIME, moved).style()
            canvas.restore()
        }
        // Мини-виджеты — поверх слоя глубины, как в WatchFaceXml.
        full.complications.forEach { it() }
        canvas.restore()
    }

    /**
     * Только блок времени стиля (цифры и дата) на своём месте, без фона и украшений, — чтобы знать,
     * где он и сколько места занимает.
     */
    fun drawTimeBlock(canvas: Canvas, w: WatchSettings, state: FaceState, fonts: FaceFonts) {
        Frame(canvas, WatchFaceXml.forLayer(w, Layer.TIME).copy(showDate = w.showDate), state, fonts, Layer.TIME, null).style()
    }

    /** Где стоят мини-виджеты стиля (круги в точках 450×450) — время на них лучше не двигать. */
    fun complicationAreas(context: Context, w: WatchSettings, fonts: FaceFonts): List<RectF> {
        if (!w.complications) return emptyList()
        val scratch = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val frame = Frame(Canvas(scratch), w, FaceState.preview(), fonts, Layer.FULL, null)
        frame.style()
        scratch.recycle()
        return frame.complicationAreas
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** Один слой кадра: всё, что нужно функциям стилей. */
    private class Frame(
        val canvas: Canvas, val w: WatchSettings, val s: FaceState, val f: FaceFonts, val layer: Layer, val shift: TimeShift?,
    ) {
        /** Мини-виджеты откладываются и рисуются после слоя глубины. */
        val complications = mutableListOf<() -> Unit>()
        val complicationAreas = mutableListOf<RectF>()

        /** Блок времени — сдвинутый и масштабированный, как группа «timeBlock» в WatchFaceXml. */
        inline fun block(body: () -> Unit) {
            val sh = shift ?: return body()
            val (px, py) = WatchFaceXml.timePivot(w.clockStyle)
            canvas.save()
            canvas.translate(sh.dx, sh.dy)
            canvas.scale(sh.scale, sh.scale, px, py)
            body()
            canvas.restore()
        }
        val full get() = layer == Layer.FULL
        val ambient get() = layer == Layer.AMBIENT

        fun style() = when (w.clockStyle) {
            ClockStyle.DIGITAL -> digital()
            ClockStyle.STACKED -> stacked()
            ClockStyle.ANALOG -> analog()
            ClockStyle.BOLD -> bold()
            ClockStyle.ORBIT -> orbit()
            ClockStyle.NEON -> neon()
            ClockStyle.BLOB -> blob()
            ClockStyle.GLASS -> glass()
            ClockStyle.MINIMAL -> minimal()
            ClockStyle.DRUM -> drum()
            ClockStyle.COLUMNS -> columns()
            ClockStyle.PLANETS -> planets()
            ClockStyle.MATRIX -> matrix()
            ClockStyle.LIQUID -> liquid()
            ClockStyle.DIAGONAL -> diagonal()
        }

        // ------------------------------------------------------------ примитивы

        fun textPaint(color: Int, size: Float, tf: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = tf
                textAlign = Paint.Align.CENTER
            }

        fun textCentered(text: String, cx: Float, cy: Float, paint: Paint) {
            val fm = paint.fontMetrics
            canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, paint)
        }

        /** Цифры шрифтом font, вписанные в maxW × maxH, с центром цифр в (cx, cy) — как WatchFaceXml.digits. */
        fun digits(text: String, cx: Float, cy: Float, maxW: Int, maxH: Int, font: DigitFont, color: Int) {
            val size = WatchFaceXml.fit(font, text.replace(Regex("[0-9]"), "0"), maxW, maxH)
            val paint = textPaint(color, size, f.of(font))
            canvas.drawText(text, cx, cy + (font.top + font.bottom) / 2 * size, paint)
        }

        fun date(pattern: String) =
            SimpleDateFormat(pattern, Locale.getDefault()).format(Date(s.millis)).replace(".", "")

        fun typeface(weight: FontWeight): Typeface = Typeface.create(
            Typeface.DEFAULT,
            when (weight) {
                FontWeight.THIN -> 100
                FontWeight.LIGHT -> 300
                FontWeight.REGULAR -> 400
                FontWeight.BOLD -> 700
            },
            false,
        )

        fun ringArc(r: Float, start: Float, end: Float, color: Int, width: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; this.color = color
            }
            canvas.drawArc(RectF(C - r, C - r, C + r, C + r), -90f + start, end - start, false, p)
        }

        fun dot(r: Float, angle: Float, color: Int, size: Float) {
            val (x, y) = pointOn(r, angle)
            canvas.drawCircle(x, y, size / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }

        fun pointOn(r: Float, angle: Float): Pair<Float, Float> {
            val a = Math.toRadians(angle.toDouble())
            return C + r * sin(a).toFloat() to C - r * cos(a).toFloat()
        }

        fun secondsRing() {
            val oval = RectF(4.5f, 4.5f, SIZE - 4.5f, SIZE - 4.5f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
            p.color = withAlpha(w.accentColor, 0x40)
            canvas.drawArc(oval, 0f, 360f, false, p)
            p.color = w.accentColor
            p.strokeWidth = 6f
            p.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(oval, -90f, s.secondAngle, false, p)
        }

        /** Мини-виджет: заряд (с дугой) или шаги — в стиле темы. Рисуется после слоя глубины. */
        fun complication(cx: Float, cy: Float, r: Float, ranged: Boolean, text: Int = w.textColor) {
            complicationAreas += RectF(cx - r, cy - r, cx + r, cy + r)
            if (ranged && s.battery == null || !ranged && s.steps == null) return
            complications += {
                val disc = if (WatchFaceXml.isLight(text)) 0x66000000 else 0x80FFFFFF.toInt()
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = disc }
                canvas.drawCircle(cx, cy, r, p)
                val label = if (ranged) "${s.battery}%" else "${s.steps}"
                if (ranged) {
                    val arc = RectF(cx - r + 5, cy - r + 5, cx + r - 5, cy + r - 5)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 6f
                    p.strokeCap = Paint.Cap.ROUND
                    p.color = withAlpha(text, 0x40)
                    canvas.drawArc(arc, -230f, 280f, false, p)
                    p.color = w.accentColor
                    canvas.drawArc(arc, -230f, 280f * ((s.battery ?: 0) / 100f), false, p)
                }
                textCentered(label, cx, cy, textPaint(text, r * 0.46f, Typeface.create(Typeface.DEFAULT, 600, false)))
            }
        }

        // ------------------------------------------------------------ стили

        fun digital() {
            if (w.showSeconds) secondsRing()
            block {
                if (w.showDate) textCentered(date("EEE d MMM"), 225f, 114f, textPaint(w.accentColor, 30f))
                val font = w.digits()
                if (font != null) digits(s.timeText, 225f, 215f, 380, 110, font, w.textColor)
                else textCentered(s.timeText, 225f, 215f, textPaint(w.textColor, 132f, typeface(w.fontWeight)))
            }
            if (w.complications) {
                complication(165f, 348f, 48f, true)
                complication(285f, 348f, 48f, false)
            }
        }

        fun stacked() {
            if (w.showSeconds) secondsRing()
            block {
                val font = w.digits()
                if (font != null) {
                    digits(s.hourText, 225f, 143f, 240, 130, font, w.textColor)
                    digits(s.minuteText, 225f, 297f, 240, 130, font, w.accentColor)
                } else {
                    val tf = typeface(w.fontWeight)
                    textCentered(s.hourText, 225f, 143f, textPaint(w.textColor, 168f, tf))
                    textCentered(s.minuteText, 225f, 297f, textPaint(w.accentColor, 168f, tf))
                }
                if (w.showDate) textCentered(date("EEE d"), 225f, 400f, textPaint(w.textColor, 26f))
            }
            if (w.complications) {
                complication(63f, 222f, 45f, true)
                complication(387f, 222f, 45f, false)
            }
        }

        fun analog() {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
            val edge = 222f
            if (layer != Layer.TIME) {
                for (i in 0 until 60) {
                    val main = i % 15 == 0
                    val hour = i % 5 == 0
                    if (ambient && !hour) continue
                    val len: Float
                    when (w.dialStyle) {
                        DialStyle.TICKS -> {
                            len = if (main) 30f else if (hour) 20f else 7f
                            p.strokeWidth = if (main) (if (ambient) 4f else 8f) else if (hour) (if (ambient) 3f else 5f) else 2f
                            p.color = when {
                                main -> w.accentColor
                                hour -> withAlpha(w.textColor, if (ambient) 0.7f else 0.9f)
                                else -> withAlpha(w.textColor, 0.5f)
                            }
                        }
                        DialStyle.NUMBERS -> {
                            len = if (hour) 8f else 5f
                            p.strokeWidth = if (hour) 3f else 1.5f
                            p.color = withAlpha(w.textColor, if (hour) 0.7f else 0.4f)
                        }
                        DialStyle.MINIMAL -> {
                            if (!main) continue
                            len = 26f
                            p.strokeWidth = if (ambient) 4f else 7f
                            p.color = w.accentColor
                        }
                    }
                    val (x1, y1) = pointOn(edge, i * 6f)
                    val (x2, y2) = pointOn(edge - len, i * 6f)
                    canvas.drawLine(x1, y1, x2, y2, p)
                }
            }
            if (w.dialStyle == DialStyle.NUMBERS) {
                if (layer != Layer.TIME) {
                    val tf = typeface(w.fontWeight)
                    for (h in 1..12) {
                        val (x, y) = pointOn(186f, h * 30f)
                        textCentered("$h", x, y, textPaint(if (h % 3 == 0) w.accentColor else w.textColor, 38f, tf))
                    }
                }
                if (w.showDate) textCentered(date("EEE d"), 225f, 132f, textPaint(w.accentColor, 26f))
                if (w.complications) {
                    complication(158f, 304f, 42f, true)
                    complication(292f, 304f, 42f, false)
                }
            } else {
                if (w.showDate) textCentered(date("EEE d"), 328f, 225f, textPaint(w.accentColor, 26f))
                if (w.complications) {
                    complication(110f, 225f, 48f, true)
                    complication(225f, 340f, 48f, false)
                }
            }
            fun hand(angle: Float, length: Float, tail: Float, width: Float, color: Int, offset: Float) {
                val a = Math.toRadians(angle.toDouble())
                val sn = sin(a).toFloat()
                val co = cos(a).toFloat()
                p.color = color
                p.strokeWidth = width
                canvas.drawLine(C - tail * sn + offset, C + tail * co + offset, C + length * sn + offset, C - length * co + offset, p)
            }
            // Тени стрелок — только в обычном режиме.
            val passes = if (full) listOf(true, false) else listOf(false)
            for (shadow in passes) {
                val off = if (shadow) 3f else 0f
                val sh = 0x59000000
                hand(s.hourAngle, 130f, 20f, 18f, if (shadow) sh else w.textColor, off)
                hand(s.minuteAngle, 190f, 20f, 12f, if (shadow) sh else w.textColor, off)
                if (w.showSeconds) hand(s.secondAngle, 200f, 40f, 6f, if (shadow) sh else w.accentColor, off)
            }
            p.style = Paint.Style.FILL
            p.color = w.accentColor
            canvas.drawCircle(C, C, 14f, p)
            p.color = Color.BLACK
            canvas.drawCircle(C, C, 5f, p)
        }

        fun bold() {
            if (w.showSeconds) secondsRing()
            val font = w.digits() ?: DigitFont.NARROW
            block {
                digits(s.hourText, 225f, 136f, 300, 170, font, w.textColor)
                digits(s.minuteText, 225f, 318f, 300, 170, font, w.accentColor)
                if (w.showDate) textCentered(date("EEE d"), 378f, 225f, textPaint(w.accentColor, 24f, f.label))
            }
            if (w.complications) complication(63f, 225f, 39f, true)
        }

        fun orbit() {
            val track = withAlpha(w.textColor, 0x26)
            if (layer != Layer.TIME) {
                if (ambient) {
                    ringArc(166f, 0f, s.hourAngle, withAlpha(w.accentColor, 0x99), 4f)
                    ringArc(190f, 0f, s.minuteAngle, withAlpha(w.textColor, 0x99), 3f)
                } else {
                    ringArc(166f, 0f, 360f, track, 12f)
                    ringArc(166f, 0f, s.hourAngle, withAlpha(w.accentColor, 0.8f), 12f)
                    ringArc(190f, 0f, 360f, track, 8f)
                    ringArc(190f, 0f, s.minuteAngle, w.textColor, 8f)
                    dot(190f, s.minuteAngle, w.accentColor, 18f)
                }
            }
            if (w.showSeconds) {
                ringArc(212f, 0f, 360f, track, 4f)
                ringArc(212f, 0f, s.secondAngle, w.accentColor, 4f)
                dot(212f, s.secondAngle, w.textColor, 12f)
            }
            block {
                if (w.showDate) textCentered(date("EEE d MMM"), 225f, 138f, textPaint(w.accentColor, 26f, f.label))
                digits(s.timeText, 225f, 222f, 280, 76, w.digits() ?: DigitFont.SPACE, w.textColor)
            }
            if (w.complications) {
                complication(185f, 323f, 33f, true)
                complication(265f, 323f, 33f, false)
            }
        }

        fun neon() {
            val text = if (WatchFaceXml.isLight(w.textColor)) w.textColor else Color.WHITE
            if (full) {
                for ((width, alpha) in listOf(22f to 0x10, 14f to 0x22, 8f to 0x50)) {
                    ringArc(210f, 0f, 360f, withAlpha(w.accentColor, alpha), width)
                }
            }
            if (layer != Layer.TIME) {
                ringArc(210f, 0f, 360f, if (ambient) withAlpha(w.accentColor, 0x80) else w.accentColor, if (ambient) 2f else 3.5f)
                for (h in 0 until 12) {
                    if (ambient && h % 3 != 0) continue
                    dot(186f, h * 30f, if (h % 3 == 0) text else withAlpha(text, 0.6f), if (h % 3 == 0) 14f else 6f)
                }
            }
            if (w.showSeconds) {
                val head = s.secondAngle
                for ((len, alpha) in listOf(48 to 0x26, 28 to 0x55, 12 to 0xAA)) {
                    ringArc(210f, head - len, head, withAlpha(Color.WHITE, alpha), 6f)
                }
                dot(210f, head, Color.WHITE, 11f)
            }
            val font = w.digits() ?: DigitFont.TUBE
            block {
                if (full) {
                    val halo = withAlpha(w.accentColor, 0x66)
                    digits(s.timeText, 222f, 225f, 300, 84, font, halo)
                    digits(s.timeText, 228f, 231f, 300, 84, font, halo)
                }
                digits(s.timeText, 225f, 228f, 300, 84, font, text)
                if (w.showDate) textCentered(date("EEE d MMM"), 225f, 136f, textPaint(w.accentColor, 24f, f.label))
            }
            if (w.complications) {
                complication(184f, 334f, 34f, true, text)
                complication(266f, 334f, 34f, false, text)
            }
        }

        fun blob() {
            val on = if (ambient) w.textColor else WatchFaceXml.onAccent(w.accentColor)
            if (ambient) {
                for (h in 0 until 12) dot(160f, h * 30f, withAlpha(w.accentColor, 0xB3), if (h % 3 == 0) 12f else 6f)
            }
            if (w.showDate) textCentered(date("d"), 340f, 225f, textPaint(on, 34f, f.round))
            if (w.complications) complication(225f, 339f, 39f, true)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = on }
            fun hand(angle: Float, length: Float, tail: Float, width: Float) {
                val a = Math.toRadians(angle.toDouble())
                val sn = sin(a).toFloat()
                val co = cos(a).toFloat()
                p.strokeWidth = width
                canvas.drawLine(C - tail * sn, C + tail * co, C + length * sn, C - length * co, p)
            }
            hand(s.hourAngle, 104f, 8f, 28f)
            hand(s.minuteAngle, 168f, 8f, 20f)
            if (w.showSeconds) hand(s.secondAngle, 182f, 37f, 6f)
            p.style = Paint.Style.FILL
            canvas.drawCircle(C, C, 18f, p)
            p.color = if (ambient) Color.BLACK else w.accentColor
            canvas.drawCircle(C, C, 7f, p)
        }

        fun glass() {
            block {
                if (w.showSeconds) {
                    ringArc(172f, 0f, s.secondAngle, w.accentColor, 5f)
                    dot(172f, s.secondAngle, w.accentColor, 13f)
                }
                if (w.showDate) textCentered(date("EEE, d MMM"), 225f, 140f, textPaint(withAlpha(w.textColor, 0.8f), 25f, f.label))
                digits(s.timeText, 225f, 222f, 280, 84, w.digits() ?: DigitFont.ITALIC, w.textColor)
            }
            if (w.complications) {
                complication(186f, 322f, 34f, true)
                complication(264f, 322f, 34f, false)
            }
        }

        fun minimal() {
            if (layer != Layer.TIME) {
                val edge = 222f
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
                for (i in 0 until 60) {
                    val hour = i % 5 == 0
                    if (ambient && !hour) continue
                    val len = if (hour) 16f else 7f
                    p.strokeWidth = if (hour) (if (ambient) 2f else 3f) else 1.5f
                    p.color = withAlpha(w.textColor, if (hour) 0.7f else 0.25f)
                    val (x1, y1) = pointOn(edge, i * 6f)
                    val (x2, y2) = pointOn(edge - len, i * 6f)
                    canvas.drawLine(x1, y1, x2, y2, p)
                }
                dot(196f, s.minuteAngle, w.accentColor, if (ambient) 10f else 16f)
            }
            if (w.showSeconds) dot(180f, s.secondAngle, withAlpha(w.textColor, 0.6f), 7f)
            block {
                digits(s.timeText, 225f, 220f, 320, 90, w.digits() ?: DigitFont.THIN, w.textColor)
                if (w.showDate) textCentered(date("EEE d MMM"), 225f, 308f, textPaint(w.accentColor, 22f, f.label))
            }
            if (w.complications) {
                complication(187f, 361f, 31f, true)
                complication(263f, 361f, 31f, false)
            }
        }

        fun drum() {
            val font = w.digits() ?: DigitFont.MONO
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val window = RectF(296f, 191f, 446f, 259f)
            if (full) {
                p.color = withAlpha(w.accentColor, 0.2f)
                canvas.drawRoundRect(window, 34f, 34f, p)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = if (full) 3f else 2f
            p.color = w.accentColor
            canvas.drawRoundRect(window, 34f, 34f, p)
            val minuteTurn = 90f - s.minuteAngle
            val hourTurn = 90f - s.hourAngle
            if (full) {
                p.strokeWidth = 2f
                p.strokeCap = Paint.Cap.ROUND
                p.color = withAlpha(w.textColor, 0x55)
                for (m in 0 until 60) {
                    if (m % 5 == 0) continue
                    val (x1, y1) = pointOn(219f, m * 6f + minuteTurn)
                    val (x2, y2) = pointOn(212f, m * 6f + minuteTurn)
                    canvas.drawLine(x1, y1, x2, y2, p)
                }
            }
            fun label(text: String, angle: Float, r: Float, maxW: Int, maxH: Int, color: Int) {
                val (x, y) = pointOn(r, angle)
                canvas.save()
                canvas.rotate(angle - 90f, x, y)
                digits(text, x, y, maxW, maxH, font, color)
                canvas.restore()
            }
            for (m in 0 until 60 step 5) label("%02d".format(m), m * 6f + minuteTurn, 190f, 34, 20, w.textColor)
            for (h in 0 until 12) label("${if (h == 0) 12 else h}", h * 30f + hourTurn, 124f, 54, 32, if (h % 3 == 0) w.accentColor else w.textColor)
            if (w.showSeconds) {
                ringArc(62f, 0f, 360f, withAlpha(w.textColor, 0x26), 4f)
                ringArc(62f, 0f, s.secondAngle, w.accentColor, 4f)
                dot(62f, s.secondAngle, w.textColor, 10f)
            }
            if (w.showDate) {
                textCentered(date("EEE"), 225f, 211f, textPaint(w.accentColor, 22f, f.label))
                textCentered(date("d"), 225f, 240f, textPaint(w.textColor, 30f, f.label))
            }
        }

        fun columns() = block {
            val font = w.digits() ?: DigitFont.SERIF
            val h = s.hour
            val m = s.minute
            digits("%02d".format(h), 140f, 225f, 150, 116, font, w.textColor)
            digits("%02d".format(m), 310f, 225f, 150, 116, font, w.accentColor)
            if (full) {
                val faded = withAlpha(w.textColor, 0x59)
                digits("%02d".format((h + 23) % 24), 140f, 92f, 90, 46, font, faded)
                digits("%02d".format((h + 1) % 24), 140f, 358f, 90, 46, font, faded)
                digits("%02d".format((m + 59) % 60), 310f, 92f, 90, 46, font, faded)
                digits("%02d".format((m + 1) % 60), 310f, 358f, 90, 46, font, faded)
            }
            if (w.showSeconds) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(w.textColor, 0x26) }
                canvas.drawRoundRect(RectF(221f, 150f, 229f, 300f), 4f, 4f, p)
                p.color = w.accentColor
                canvas.drawRoundRect(RectF(221f, 150f, 229f, 150f + 8 + s.second.toInt() * 2.4f), 4f, 4f, p)
            }
            if (w.showDate) textCentered(date("EEE d MMM"), 225f, 409f, textPaint(w.accentColor, 22f, f.label))
        }

        fun planets() {
            val font = w.digits() ?: DigitFont.WIDE
            if (layer != Layer.TIME) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(w.textColor, if (ambient) 0.25f else 0.2f) }
                canvas.drawCircle(C, C, 105f, p)
                canvas.drawCircle(C, C, 178f, p)
                p.style = Paint.Style.FILL
                p.color = w.accentColor
                canvas.drawCircle(C, C, 9f, p)
            }
            fun planet(r: Float, radius: Float, angle: Float, fill: Int, textColor: Int, text: String) {
                if (full) {
                    val (x, y) = pointOn(r - radius, angle)
                    val (sx, sy) = pointOn(12f, angle)
                    val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = withAlpha(Color.WHITE, 0.25f); strokeCap = Paint.Cap.ROUND }
                    canvas.drawLine(sx, sy, x, y, ray)
                }
                val (px, py) = pointOn(r, angle)
                val body = Paint(Paint.ANTI_ALIAS_FLAG)
                if (ambient) {
                    body.color = Color.BLACK
                    canvas.drawCircle(px, py, radius - 2, body)
                    body.style = Paint.Style.STROKE
                    body.strokeWidth = 3f
                    body.color = fill
                    canvas.drawCircle(px, py, radius - 2, body)
                } else {
                    body.color = fill
                    canvas.drawCircle(px, py, radius - 2, body)
                }
                digits(text, px, py, (radius * 2 * 0.72f).toInt(), (radius * 2 * 0.42f).toInt(), font, textColor)
            }
            planet(105f, 44f, s.hourAngle, w.accentColor, if (ambient) w.accentColor else WatchFaceXml.onAccent(w.accentColor), "${s.hour}")
            planet(178f, 36f, s.minuteAngle, w.textColor, if (ambient) w.textColor else WatchFaceXml.onAccent(w.textColor), s.minuteText)
            if (w.showSeconds) {
                ringArc(214f, s.secondAngle - 30, s.secondAngle, withAlpha(w.accentColor, 0.4f), 4f)
                dot(214f, s.secondAngle, w.accentColor, 12f)
            }
            if (w.showDate) textCentered(date("EEE d"), 225f, 268f, textPaint(withAlpha(w.textColor, 0.8f), 22f, f.label))
        }

        fun matrix() {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (full) {
                val second = s.second.toInt()
                for (i in 0 until 60) {
                    val (x, y) = pointOn(207f, i * 6f)
                    p.color = when {
                        !w.showSeconds -> withAlpha(w.textColor, 0.4f)
                        i <= second -> w.accentColor
                        else -> withAlpha(w.accentColor, 60)
                    }
                    canvas.drawCircle(x, y, if (i % 5 == 0) 5f else 3f, p)
                }
            } else if (ambient) {
                p.color = withAlpha(w.accentColor, 0x99)
                for (h in 0 until 12) {
                    val (x, y) = pointOn(207f, h * 30f)
                    canvas.drawCircle(x, y, 3f, p)
                }
            }
            block {
                digits(s.timeText, 225f, 212f, 330, 112, w.digits() ?: DigitFont.DOTS, w.textColor)
                if (w.showDate) textCentered(date("EEE d MMM"), 225f, 308f, textPaint(w.accentColor, 24f, f.label))
            }
            if (w.complications) {
                complication(187f, 365f, 31f, true)
                complication(263f, 365f, 31f, false)
            }
        }

        fun liquid() {
            val second = s.second.toInt()
            val font = w.digits() ?: DigitFont.BUBBLE
            if (full) {
                val level = (second + 1) * 7.5f
                val fluid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(w.accentColor, 0x8C) }
                canvas.drawRect(0f, SIZE - level, SIZE, SIZE, fluid)
                val wave = if (second % 2 == 0) -90f else -30f
                canvas.drawOval(RectF(wave, 430f - level, wave + 570f, 470f - level), fluid)
                // Пузырьки поднимаются до поверхности; пока «воды» почти нет, их не видно.
                val top = 456f - level
                if (top <= 440f) {
                    val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.WHITE, 0.4f) }
                    for ((k, bx) in listOf(0 to 120, 1 to 210, 2 to 300, 3 to 165)) {
                        val r = 6f + k * 2
                        val y = (440f - ((second * 7 + k * 17) % 60) * 6f).coerceIn(top, 440f)
                        canvas.drawCircle(bx + r, y + r, r, bubble)
                    }
                }
            } else if (ambient) {
                canvas.drawLine(40f, 330f, 410f, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(w.accentColor, 0x80); strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
                })
            }
            block {
                if (full) digits(s.timeText, 228f, 213f, 330, 110, font, 0x40000000)
                digits(s.timeText, 225f, 210f, 330, 110, font, w.textColor)
                if (w.showDate) textCentered(date("EEE d MMM"), 225f, 128f, textPaint(w.textColor, 26f, f.label))
            }
            if (w.complications) complication(225f, 335f, 39f, true)
        }

        fun diagonal() {
            block { diagonalBlock() }
            if (w.complications) complication(100f, 300f, 38f, true)
        }

        fun diagonalBlock() {
            if (w.showSeconds) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; color = withAlpha(w.textColor, 0.2f) }
                canvas.drawLine(108f, 342f, 342f, 108f, p)
                p.color = w.accentColor
                val t = s.second.toInt() * 234f / 59
                canvas.drawCircle(108f + t, 342f - t, 8f, p)
            }
            val font = w.digits() ?: DigitFont.BLOCK
            digits(s.hourText, 165f, 165f, 210, 140, font, w.textColor)
            digits(s.minuteText, 288f, 292f, 210, 140, font, w.accentColor)
            if (w.showDate) textCentered(date("EEE d"), 355f, 135f, textPaint(w.accentColor, 22f, f.label))
        }
    }
}
