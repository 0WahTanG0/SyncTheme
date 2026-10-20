package app.synctheme.core

import kotlinx.serialization.Serializable

// Сгенерировано scripts/gen_fonts.py — не редактировать вручную.

/**
 * Шрифт цифр часов (виджеты и циферблат). res — имя ресурса в res/font приложения и шаблона
 * циферблата; null — системный тонкий шрифт. Метрики — в долях кегля: ascent/descent строки,
 * верх и низ цифр над базовой линией, ширина цифры и двоеточия.
 */
@Serializable
enum class DigitFont(
    val res: String?, val title: String,
    val ascent: Float, val descent: Float, val top: Float, val bottom: Float, val digit: Float, val colon: Float,
) {
    NARROW("oswald_700", "Узкий", 1.193f, 0.289f, 0.822f, -0.015f, 0.550f, 0.278f),
    WIDE("unbounded_700", "Широкий", 0.995f, 0.245f, 0.766f, -0.016f, 0.925f, 0.328f),
    ROUND("nunito_900", "Округлый", 1.011f, 0.353f, 0.716f, -0.011f, 0.600f, 0.272f),
    GROTESK("manrope_800", "Гротеск", 1.066f, 0.300f, 0.735f, -0.015f, 0.674f, 0.357f),
    BLOCK("bungee", "Блоки", 1.020f, 0.300f, 0.735f, -0.015f, 0.727f, 0.384f),
    OUTLINE("bungee_outline", "Контур", 1.020f, 0.300f, 0.745f, -0.025f, 0.727f, 0.384f),
    DOTS("doto", "Точки", 0.950f, 0.250f, 0.691f, 0.009f, 0.600f, 0.600f),
    TUBE("tilt_neon", "Неон", 0.990f, 0.272f, 0.690f, -0.011f, 0.592f, 0.260f),
    PIXEL("pixelify", "Пиксели", 0.920f, 0.280f, 0.638f, -0.011f, 0.603f, 0.249f),
    SERIF("dm_serif", "Антиква", 1.036f, 0.335f, 0.649f, -0.020f, 0.538f, 0.300f),
    ITALIC("instrument_italic", "Курсив", 0.990f, 0.310f, 0.745f, -0.009f, 0.461f, 0.299f),
    BUBBLE("dynapuff", "Пузыри", 0.965f, 0.235f, 0.712f, -0.018f, 0.681f, 0.270f),
    MONO("space_mono", "Моно", 1.120f, 0.361f, 0.714f, -0.014f, 0.612f, 0.612f),
    STENCIL("stencil", "Трафарет", 0.986f, 0.214f, 0.809f, -0.009f, 0.512f, 0.243f),
    SPACE("orbitron", "Космос", 1.011f, 0.243f, 0.721f, 0.000f, 0.834f, 0.242f),
    RETRO("monoton", "Ретро", 1.155f, 0.401f, 0.844f, -0.035f, 0.843f, 0.263f),
    HAND("caveat", "Рукопись", 0.960f, 0.300f, 0.588f, -0.049f, 0.471f, 0.198f),
    THIN(null, "Тонкий", 0.928f, 0.244f, 0.711f, -0.010f, 0.562f, 0.240f);

    /** Высота строки в долях кегля. */
    val line get() = ascent + descent

    /** Высота цифр в долях кегля. */
    val digitHeight get() = top - bottom

    /** Ширина текста из цифр и двоеточий в долях кегля. */
    fun width(text: String) = text.sumOf { (if (it == ':') colon else digit).toDouble() }.toFloat()
}
