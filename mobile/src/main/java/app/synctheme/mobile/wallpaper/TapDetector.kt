package app.synctheme.mobile.wallpaper

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Короткое нажатие среди касаний, которые система дублирует обоям под рабочим столом: палец
 * почти не сдвинулся и быстро отпущен. Пролистывание страниц, свайпы и долгие нажатия — не нажатие.
 */
class TapDetector(private val slop: Float, private val maxMillis: Long = 400) {
    private var downX = 0f
    private var downY = 0f
    private var downAt = -1L
    private var moved = false

    /** Возвращает место нажатия, когда палец отпущен, иначе null. */
    fun onEvent(action: Int, x: Float, y: Float, time: Long): PointF? {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                downAt = time
                moved = false
            }
            MotionEvent.ACTION_MOVE -> if (hypot(x - downX, y - downY) > slop) moved = true
            // Второй палец — это щипок или жест, а не нажатие.
            MotionEvent.ACTION_POINTER_DOWN -> moved = true
            MotionEvent.ACTION_CANCEL -> downAt = -1
            MotionEvent.ACTION_UP -> {
                val tap = downAt >= 0 && !moved && time - downAt <= maxMillis && hypot(x - downX, y - downY) <= slop
                downAt = -1
                if (tap) return PointF(downX, downY)
            }
        }
        return null
    }
}
