package app.synctheme.faces

import java.util.Calendar

/**
 * Что показывает циферблат в данный момент: время и данные мини-виджетов
 * (null — неизвестно, мини-виджет не рисуется).
 */
data class FaceState(
    /** Момент времени (для даты) и часовой пояс устройства. */
    val millis: Long,
    val hour: Int,
    val minute: Int,
    /** Секунды с долями: 0..59.999. */
    val second: Float,
    val is24h: Boolean = true,
    val battery: Int? = null,
    val steps: Int? = null,
    /** Режим Always-On. */
    val ambient: Boolean = false,
) {
    val hour12 get() = if (hour % 12 == 0) 12 else hour % 12

    /** Часы так, как их показывает TimeText с форматом «hh» (12/24 — как в настройках часов). */
    val hourText get() = "%02d".format(if (is24h) hour else hour12)
    val minuteText get() = "%02d".format(minute)
    val timeText get() = "$hourText:$minuteText"

    /** Углы стрелок, как в выражениях циферблата WFF. 0° — 12 часов. */
    val hourAngle get() = ((hour % 12) + minute / 60f) * 30f
    val minuteAngle get() = (minute + second.toInt() / 60f) * 6f
    val secondAngle get() = second.toInt() * 6f

    companion object {
        /** Состояние для превью: 10:08:32, как PREVIEW_TIME циферблата, и правдоподобные данные. */
        fun preview(is24h: Boolean = true, ambient: Boolean = false): FaceState = FaceState(
            millis = System.currentTimeMillis(), hour = 10, minute = 8, second = 32f, is24h = is24h,
            battery = 80, steps = 6241, ambient = ambient,
        )

        fun now(millis: Long, is24h: Boolean): FaceState {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            return FaceState(
                millis = millis,
                hour = c.get(Calendar.HOUR_OF_DAY),
                minute = c.get(Calendar.MINUTE),
                second = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f,
                is24h = is24h,
            )
        }
    }
}
