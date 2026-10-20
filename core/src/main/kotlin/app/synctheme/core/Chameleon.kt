package app.synctheme.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Режим «Хамелеона»: тема сама меняется по времени суток и/или погоде. */
@Serializable
enum class ChameleonMode(val title: String) {
    OFF("Выключен"), TIME("По времени суток"), WEATHER("По погоде"), BOTH("Время и погода");

    val byTime get() = this == TIME || this == BOTH
    val byWeather get() = this == WEATHER || this == BOTH
}

/** Часть суток. */
@Serializable
enum class DayPart(val title: String) {
    MORNING("Утро"), DAY("День"), EVENING("Вечер"), NIGHT("Ночь");

    companion object {
        /** Утро 5–11, день 11–17, вечер 17–22, ночь 22–5. */
        fun of(hour: Int): DayPart = when (hour) {
            in 5..10 -> MORNING
            in 11..16 -> DAY
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

/** Небо за окном. */
@Serializable
enum class Sky(val title: String) {
    CLEAR("Ясно"), CLOUDS("Облачно"), RAIN("Дождь"), SNOW("Снег"), STORM("Гроза"), FOG("Туман");

    companion object {
        /** По коду погоды WMO (его отдаёт Open-Meteo). */
        fun of(code: Int): Sky = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDS
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDS
        }
    }
}

/** Что сейчас за окном; null — эта часть не учитывается. */
@Serializable
data class Mood(val part: DayPart? = null, val sky: Sky? = null) {
    val title: String get() = listOfNotNull(part?.title, sky?.title?.lowercase()).joinToString(", ")
}

/**
 * Подстраивает цвета темы под время суток и погоду: оттенок и яркость фона и акцента,
 * мягкий цветной слой поверх фото. Цвет цифр не трогаем — он подобран под читаемость.
 */
object Chameleon {
    /** Как настроение меняет цвета: к какому оттенку тянуть и насколько, множители насыщенности и яркости. */
    private class Shift(
        val hue: Float, val pull: Float, val saturation: Float, val value: Float,
        /** Слой поверх фона (ARGB). */
        val tint: Int, val dim: Float,
    )

    private fun shift(part: DayPart): Shift? = when (part) {
        DayPart.MORNING -> Shift(32f, 0.22f, 0.95f, 1.08f, 0x55FFB27A, -0.04f)
        DayPart.DAY -> null
        DayPart.EVENING -> Shift(338f, 0.25f, 1.05f, 0.9f, 0x60FF6A3D, 0.04f)
        DayPart.NIGHT -> Shift(228f, 0.35f, 0.85f, 0.62f, 0x70203A8A, 0.2f)
    }

    private fun shift(sky: Sky): Shift? = when (sky) {
        Sky.CLEAR -> Shift(45f, 0.06f, 1.12f, 1.03f, 0x30FFE08A, 0f)
        Sky.CLOUDS -> Shift(215f, 0.08f, 0.7f, 0.95f, 0x40A0A8B8, 0.03f)
        Sky.RAIN -> Shift(208f, 0.3f, 0.8f, 0.86f, 0x5A3C6E9E, 0.08f)
        Sky.SNOW -> Shift(200f, 0.2f, 0.6f, 1.1f, 0x55DDEEFF, -0.05f)
        Sky.STORM -> Shift(265f, 0.3f, 0.9f, 0.7f, 0x664A2F7A, 0.12f)
        Sky.FOG -> Shift(210f, 0.05f, 0.5f, 1f, 0x55C8C8C8, 0f)
    }

    fun adapt(theme: Theme, mood: Mood): Theme {
        val shifts = listOfNotNull(mood.part?.let(::shift), mood.sky?.let(::shift))
        if (shifts.isEmpty()) return theme
        fun color(c: Int, strength: Float = 1f) = shifts.fold(c) { acc, s -> apply(acc, s, strength) }
        fun bg(b: Background) = b.copy(colors = b.colors.map { color(it) }, tint = shifts.map { it.tint }.reduce(::blend))
        val dim = shifts.sumOf { it.dim.toDouble() }.toFloat()
        fun dimmed(d: Float) = (d + dim).coerceIn(0f, 0.8f)
        return theme.copy(
            background = bg(theme.background),
            accentColor = color(theme.accentColor, 0.5f),
            dim = dimmed(theme.dim),
            lockBackground = theme.lockBackground?.let(::bg),
            lockDim = theme.lockDim?.let(::dimmed),
            watch = theme.watch?.let { w ->
                w.copy(background = w.background?.let(::bg), accentColor = color(w.accentColor, 0.5f), dim = dimmed(w.dim))
            },
        )
    }

    private fun apply(argb: Int, s: Shift, strength: Float): Int {
        val (h, sat, v) = hsv(argb)
        // Цвета на «другом конце» круга оттенков почти не поворачиваем: иначе жёлтый по пути
        // к синему проходит через зелёный.
        val d = shortest(h, s.hue)
        val pull = s.pull * strength * (1 - abs(d) / 200f)
        val hue = h + d * pull
        val saturation = sat * (1 + (s.saturation - 1) * strength)
        val value = v * (1 + (s.value - 1) * strength)
        return fromHsv(argb ushr 24, (hue + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    }

    /** Кратчайший поворот по кругу оттенков от from к to (−180..180). */
    private fun shortest(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** Смешивает два слоя-оттенка поровну (и по цвету, и по силе). */
    private fun blend(a: Int, b: Int): Int {
        fun ch(c: Int, shift: Int) = c shr shift and 0xFF
        fun mid(shift: Int) = (ch(a, shift) + ch(b, shift)) / 2
        return (maxOf(ch(a, 24), ch(b, 24)) shl 24) or (mid(16) shl 16) or (mid(8) shl 8) or mid(0)
    }

    fun hsv(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Triple((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
    }

    fun fromHsv(alpha: Int, h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
