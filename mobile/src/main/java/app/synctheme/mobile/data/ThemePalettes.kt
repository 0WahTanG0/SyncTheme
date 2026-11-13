package app.synctheme.mobile.data

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/** Пара цветов темы: цифры (и подписи виджетов) и акцент (кольца, полосы, выделения). */
data class ColorChoice(val title: String, val text: Int, val accent: Int)

/**
 * Палитры для блока «Цвета»: из фона темы — фото или градиента — получается несколько разных по
 * характеру пар «цифры + акцент». Каждая подгоняется так, чтобы читаться на этом фоне.
 */
object ThemePalettes {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK_DARK = 0xFF141414.toInt()

    /** Цвета фона, из которых собираются палитры; dominant — общий тон (по нему проверяется читаемость). */
    class Swatches(
        val dominant: Int,
        val vibrant: Int? = null,
        val lightVibrant: Int? = null,
        val darkVibrant: Int? = null,
        val muted: Int? = null,
        val lightMuted: Int? = null,
        val darkMuted: Int? = null,
    )

    /** Цвета уже нарисованного фона (с затемнением) — тем, что видно на экране. */
    fun swatches(background: Bitmap): Swatches {
        val p = Palette.from(background).generate()
        return Swatches(
            dominant = average(background),
            vibrant = p.vibrantSwatch?.rgb, lightVibrant = p.lightVibrantSwatch?.rgb, darkVibrant = p.darkVibrantSwatch?.rgb,
            muted = p.mutedSwatch?.rgb, lightMuted = p.lightMutedSwatch?.rgb, darkMuted = p.darkMutedSwatch?.rgb,
        )
    }

    fun choices(s: Swatches): List<ColorChoice> {
        val bg = opaque(s.dominant)
        val light = ColorUtils.calculateLuminance(bg) > 0.5
        val ink = if (light) INK_DARK else WHITE

        /**
         * Цвет, который читается на фоне: светлее на тёмном фоне или темнее на светлом. Меняется только
         * светлота — оттенок и сочность остаются, поэтому палитры не сливаются в одинаково блёклые.
         */
        fun readable(color: Int, min: Double = 3.0): Int {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(opaque(color), hsl)
            var out = opaque(color)
            var steps = 0
            while (ColorUtils.calculateContrast(out, bg) < min && steps < 30) {
                hsl[2] = if (light) (hsl[2] - 0.035f).coerceAtLeast(0f) else (hsl[2] + 0.035f).coerceAtMost(1f)
                out = ColorUtils.HSLToColor(hsl)
                steps++
            }
            return out
        }

        val vivid = listOfNotNull(s.vibrant, s.lightVibrant, s.darkVibrant, s.muted)
        // «Из фото» — как при создании темы: сочный цвет снимка, который на нём читается.
        val auto = readable(
            vivid.firstOrNull { c -> ColorUtils.calculateLuminance(c).let { if (light) it < 0.5 else it > 0.12 } }
                ?: s.vibrant ?: s.lightVibrant ?: s.muted ?: complement(bg, light),
        )
        // «Сочная» — самый яркий цвет снимка другого оттенка, чем у «Из фото» (красный парус на синем море),
        // а если других оттенков нет — просто самый яркий.
        val swatches = listOfNotNull(s.vibrant, s.lightVibrant, s.darkVibrant, s.muted, s.lightMuted, s.darkMuted)
        val juicy = readable(
            swatches.filter { hueDistance(it, auto) > 35f && saturation(it) > 0.3f }.maxByOrNull { saturation(it) }
                ?: vivid.maxByOrNull { saturation(it) } ?: auto,
        )
        val pastel = readable(s.lightMuted ?: s.lightVibrant ?: ColorUtils.blendARGB(auto, WHITE, 0.4f))
        val pastelText = readable(
            if (light) ColorUtils.blendARGB(s.darkMuted ?: auto, BLACK, 0.55f) else ColorUtils.blendARGB(s.lightMuted ?: auto, WHITE, 0.72f), 4.5,
        )
        val toneText = readable(if (light) ColorUtils.blendARGB(auto, BLACK, 0.72f) else ColorUtils.blendARGB(auto, WHITE, 0.8f), 4.5)
        val all = listOf(
            ColorChoice("Из фото", ink, auto),
            ColorChoice("Сочная", ink, juicy),
            ColorChoice("Пастель", pastelText, pastel),
            ColorChoice("Тон в тон", toneText, auto),
            ColorChoice("Контраст", ink, readable(complement(bg, light))),
            ColorChoice("Моно", ink, if (light) 0xFF2B2B2B.toInt() else 0xFFF0F0F0.toInt()),
        )
        // Похожие палитры не повторяются: на однотонном фото часть совпадёт.
        val out = mutableListOf<ColorChoice>()
        fun add(c: ColorChoice) {
            if (out.size < 6 && out.none { distance(it.text, c.text) + distance(it.accent, c.accent) < 60 }) out += c
        }
        all.forEach(::add)
        // У бесцветного снимка (серое, ч/б) своих цветов мало — добавляем акценты заданных оттенков.
        if (out.size < 4) {
            HUES.forEach { (title, hue) -> add(ColorChoice(title, ink, readable(ColorUtils.HSLToColor(floatArrayOf(hue, 0.75f, if (light) 0.42f else 0.62f))))) }
        }
        return out
    }

    private val HUES = listOf("Холодная" to 210f, "Ягодная" to 335f, "Мятная" to 155f, "Солнечная" to 45f)

    /** Противоположный по кругу цвет, насыщенный; у серого фона — тёплый оранжевый. */
    private fun complement(bg: Int, light: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(bg, hsl)
        val hue = if (hsl[1] < 0.12f) 24f else (hsl[0] + 180f) % 360f
        return ColorUtils.HSLToColor(floatArrayOf(hue, 0.78f, if (light) 0.42f else 0.62f))
    }

    private fun saturation(c: Int): Float = FloatArray(3).also { ColorUtils.colorToHSL(c, it) }[1]

    private fun hueDistance(a: Int, b: Int): Float {
        val ha = FloatArray(3).also { ColorUtils.colorToHSL(a, it) }[0]
        val hb = FloatArray(3).also { ColorUtils.colorToHSL(b, it) }[0]
        val d = Math.abs(ha - hb) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun distance(a: Int, b: Int): Int =
        Math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + Math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + Math.abs((a and 0xFF) - (b and 0xFF))

    private fun opaque(c: Int) = c or BLACK

    private fun average(bmp: Bitmap): Int {
        val one = Bitmap.createScaledBitmap(bmp, 1, 1, true)
        return opaque(one.getPixel(0, 0)).also { if (one != bmp) one.recycle() }
    }
}
