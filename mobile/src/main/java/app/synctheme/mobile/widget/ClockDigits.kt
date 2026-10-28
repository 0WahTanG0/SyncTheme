package app.synctheme.mobile.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.format.DateFormat
import app.synctheme.core.DigitFont
import app.synctheme.mobile.util.Compat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Цифры часов картинкой. Лаунчер собирает виджет в «ограниченном» контексте, где TextView не загружает
 * шрифты из ресурсов приложения, — TextClock показал бы системный шрифт. Поэтому время рисуется здесь
 * выбранным шрифтом, а [ClockTicker] обновляет картинку в начале каждой минуты.
 */
internal object ClockDigits {
    /** Картинки — для шрифтов из ресурсов, на Android 12+ (размеры элементов виджета меняются с него) и если обновления раз в минуту разрешены. */
    fun enabled(context: Context, font: DigitFont): Boolean = font.res != null && Compat.modernWidgets && ClockTicker.canTick(context)

    /** Текст, который показал бы TextClock с форматом [format] (см. [TimeImage]). */
    fun text(context: Context, format: Char, now: Long = System.currentTimeMillis()): String {
        val h24 = DateFormat.is24HourFormat(context)
        val pattern = when (format) {
            'h' -> if (h24) "HH" else "hh"
            'm' -> "mm"
            's' -> if (h24) "HH mm" else "hh mm"
            'w' -> if (h24) "HH\nmm" else "hh\nmm"
            else -> if (h24) "H:mm" else "h:mm"
        }
        return SimpleDateFormat(pattern, Locale.ROOT).format(Date(now))
    }

    /**
     * Строка текста как в TextView без отступов шрифта: высота — от ascent до descent, ширина — по ширине
     * текста. Тогда картинка, вписанная в место TextClock, совпадает с тем, как он подобрал бы кегль.
     * Одноцветная маска (ALPHA_8): цвет даёт фильтр ImageView. [glow] — свечение «Неона».
     * [tight] — без полей шрифта, только сами цифры (на плашках «Табло» они занимают плашку целиком).
     */
    fun render(context: Context, font: DigitFont, text: String, glow: Boolean, maxHeightPx: Int, tight: Boolean = false): Bitmap {
        val lines = text.split('\n')
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = VerticalFit.typeface(context, font)
            textSize = 100f
        }
        if (tight && lines.size == 1) return renderTight(paint, font, text, glow, maxHeightPx)
        val probe = paint.fontMetrics
        val unit = probe.descent - probe.ascent
        // Две строки («чч» над «мм») — с тем же сжатием межстрочья, что в разметке.
        val mult = if (lines.size > 1) (1.08f * font.digitHeight / font.line).coerceIn(0.6f, 1f) else 1f
        val rows = 1 + (lines.size - 1) * mult
        paint.textSize = 100f * maxHeightPx / (unit * rows)
        val fm = paint.fontMetrics
        val line = fm.descent - fm.ascent
        val spans = lines.map { inkSpan(paint, it, glow) }
        val width = max(1, ceil(spans.maxOf { it.second - it.first }).toInt())
        val height = max(1, ceil(line * rows).toInt())
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bmp)
        fun draw(alpha: Int) {
            paint.alpha = alpha
            lines.forEachIndexed { i, s ->
                val (left, right) = spans[i]
                canvas.drawText(s, (width - (right - left)) / 2 - left, -fm.ascent + i * line * mult, paint)
            }
        }
        if (glow) {
            paint.maskFilter = BlurMaskFilter(paint.textSize * 0.08f, BlurMaskFilter.Blur.NORMAL)
            draw(140)
            paint.maskFilter = null
        }
        draw(255)
        return bmp
    }

    /** Одна строка: высота картинки — высота цифр с небольшим запасом, цифры по центру. */
    private fun renderTight(paint: Paint, font: DigitFont, text: String, glow: Boolean, heightPx: Int): Bitmap {
        paint.textSize = 0.88f * heightPx / font.digitHeight
        val size = paint.textSize
        val (left, right) = inkSpan(paint, text, glow)
        val width = max(1, ceil(right - left).toInt())
        val height = max(1, heightPx)
        val baseline = height / 2f + (font.top + font.bottom) / 2f * size
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bmp)
        if (glow) {
            paint.maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
            paint.alpha = 140
            canvas.drawText(text, -left, baseline, paint)
            paint.maskFilter = null
        }
        paint.alpha = 255
        canvas.drawText(text, -left, baseline, paint)
        return bmp
    }

    /**
     * Где строка оставляет «чернила» по горизонтали: от начала до конца самих цифр, а не только по ширине
     * текста — наклонные цифры («Рукопись») выходят за неё, и картинка обрезала бы последнюю цифру.
     * Со свечением — с запасом на его размытие.
     */
    private fun inkSpan(paint: Paint, text: String, glow: Boolean): Pair<Float, Float> {
        val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val pad = paint.textSize * (if (glow) 0.16f else 0.02f)
        return minOf(0f, bounds.left.toFloat()) - pad to maxOf(paint.measureText(text), bounds.right.toFloat()) + pad
    }
}

/**
 * Обновление часов с картинкой раз в минуту: точный будильник без пробуждения (RTC) — при выключенном
 * экране он ждёт и срабатывает сразу, как экран включат. Нет таких часов — будильник снимается.
 */
object ClockTicker {
    private const val ACTION = "app.synctheme.CLOCK_TICK"

    fun canTick(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pending(context)
        if (!needed(context)) {
            am.cancel(pending)
            return
        }
        val now = System.currentTimeMillis()
        runCatching { am.setExact(AlarmManager.RTC, now - now % 60_000 + 60_000 + 150, pending) }
    }

    /** Есть часы со шрифтом из ресурсов — им нужны картинки. */
    private fun needed(context: Context): Boolean = WidgetUpdater.ids(context, WidgetKind.CLOCK).any { id ->
        ClockDigits.enabled(context, WidgetUpdater.config(context, id, WidgetKind.CLOCK).digitFont())
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7100, Intent(context, ClockTickReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Новая минута, смена времени или часового пояса, перезагрузка — перерисовать цифры часов. */
class ClockTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        WidgetUpdater.update(context, WidgetKind.CLOCK)
    }

    private companion object {
        val ACTIONS = setOf(
            "app.synctheme.CLOCK_TICK", Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
