package app.synctheme.mobile.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import app.synctheme.mobile.render.Colors

/**
 * Полоска «мин–макс» дня в прогнозе: дорожка — общий разброс за неделю, на ней отрезок дня
 * в цветах температуры (от голубого к оранжевому), у сегодняшнего дня — точка «сейчас».
 * Картинка растягивается по ширине строки (fitXY), поэтому рисуется в вытянутых пропорциях.
 */
object RangeBar {
    private const val W = 240
    private const val H = 14

    /** Температура → цвет: −15° синий, 0° голубой, 10° мятный, 20° жёлтый, 30° оранжевый, 40° красный. */
    private val stops = listOf(
        -15.0 to 0xFF5E8BFF.toInt(), 0.0 to 0xFF5AC8FA.toInt(), 10.0 to 0xFF63E6BE.toInt(),
        20.0 to 0xFFFFD43B.toInt(), 30.0 to 0xFFFF8A3D.toInt(), 40.0 to 0xFFFF4D4D.toInt(),
    )

    fun color(t: Double): Int {
        if (t <= stops.first().first) return stops.first().second
        if (t >= stops.last().first) return stops.last().second
        val i = stops.indexOfLast { it.first <= t }
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        return ColorUtils.blendARGB(c0, c1, ((t - t0) / (t1 - t0)).toFloat())
    }

    fun render(min: Double, max: Double, low: Double, high: Double, now: Double?, textColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = H / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Colors.withAlpha(textColor, 0.16f)
        c.drawRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), r, r, paint)
        val span = (high - low).takeIf { it > 0.1 } ?: 1.0
        fun x(t: Double) = (r + ((t - low) / span).toFloat().coerceIn(0f, 1f) * (W - 2 * r))
        val x0 = x(min) - r
        val x1 = maxOf(x(max) + r, x0 + H)
        paint.color = 0xFFFFFFFF.toInt() // прозрачность дорожки не должна гасить отрезок
        paint.shader = LinearGradient(x0, 0f, x1, 0f, color(min), color(max), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x0, 0f, x1, H.toFloat()), r, r, paint)
        paint.shader = null
        if (now != null) {
            // Точка «сейчас»: светлая с тёмной обводкой — видна на любом отрезке.
            val nx = x(now.coerceIn(min, max))
            paint.color = 0x99000000.toInt()
            c.drawCircle(nx, r, r, paint)
            paint.color = 0xFFFFFFFF.toInt()
            c.drawCircle(nx, r, r * 0.62f, paint)
        }
        return bmp
    }
}
