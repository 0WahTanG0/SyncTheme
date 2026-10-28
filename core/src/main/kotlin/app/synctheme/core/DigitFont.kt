package app.synctheme.core

import kotlinx.serialization.Serializable

// Сгенерировано scripts/gen_fonts.py — не редактировать вручную.

/**
 * Шрифт цифр часов (виджеты и циферблат). res — имя ресурса в res/font приложения и шаблона
 * циферблата; null — системный тонкий шрифт. Метрики — в долях кегля: ascent/descent строки,
 * верх и низ цифр над базовой линией, ширина цифры и двоеточия.
 */
@Serializable(with = DigitFontSerializer::class)
enum class DigitFont(
    val res: String?, val title: String,
    val ascent: Float, val descent: Float, val top: Float, val bottom: Float, val digit: Float, val colon: Float,
) {
    NARROW("oswald_700", "Узкий", 1.193f, 0.289f, 0.822f, -0.015f, 0.550f, 0.278f),
    SERIF("dm_serif", "Антиква", 1.036f, 0.335f, 0.649f, -0.020f, 0.538f, 0.300f),
    HAND("caveat", "Рукопись", 0.960f, 0.300f, 0.588f, -0.049f, 0.471f, 0.198f),
    DOTS("doto", "Точки", 0.950f, 0.250f, 0.691f, 0.009f, 0.600f, 0.600f),
    BUBBLE("dynapuff", "Пузыри", 0.965f, 0.235f, 0.712f, -0.018f, 0.681f, 0.270f),
    RETRO("monoton", "Ретро", 1.155f, 0.401f, 0.844f, -0.035f, 0.843f, 0.263f),
    THIN(null, "Тонкий", 0.928f, 0.244f, 0.711f, -0.010f, 0.562f, 0.240f);

    /** Высота строки в долях кегля. */
    val line get() = ascent + descent

    /** Высота цифр в долях кегля. */
    val digitHeight get() = top - bottom

    /** Ширина текста из цифр и двоеточий в долях кегля. */
    fun width(text: String) = text.sumOf { (if (it == ':') colon else digit).toDouble() }.toFloat()
}
