package app.synctheme.core

import kotlinx.serialization.Serializable

// Сгенерировано scripts/gen_digit_fonts.py — не редактировать вручную.

/**
 * Шрифт цифр часов (виджеты, часы на обоях и циферблат). res — имя ресурса в res/font приложения
 * и шаблона циферблата; null — системный тонкий шрифт. Метрики — в долях кегля: ascent/descent строки,
 * верх и низ цифр над базовой линией, ширина цифры и двоеточия.
 */
@Serializable(with = DigitFontSerializer::class)
enum class DigitFont(
    val res: String?, val title: String,
    val ascent: Float, val descent: Float, val top: Float, val bottom: Float, val digit: Float, val colon: Float,
) {
    STRETCH("stretch", "Растянутый", 1.060f, 0.200f, 1.000f, 0.000f, 0.362f, 0.150f),
    COLUMN("column", "Колонна", 1.060f, 0.200f, 1.000f, 0.000f, 0.460f, 0.220f),
    OUTLINE("outline", "Контур", 1.060f, 0.200f, 1.000f, 0.000f, 0.696f, 0.290f),
    BLOCK("block", "Блок", 1.060f, 0.200f, 1.000f, 0.000f, 0.480f, 0.210f),
    TECHNO("techno", "Техно", 1.060f, 0.200f, 1.000f, 0.000f, 0.740f, 0.270f),
    STENCIL("stencil", "Трафарет", 1.060f, 0.200f, 1.000f, 0.000f, 0.600f, 0.250f),
    THIN(null, "Тонкий", 0.928f, 0.244f, 0.711f, -0.010f, 0.562f, 0.240f);

    /** Высота строки в долях кегля. */
    val line get() = ascent + descent

    /** Высота цифр в долях кегля. */
    val digitHeight get() = top - bottom

    /**
     * Во сколько раз выше обычной рамка для строки времени. Высоким узким цифрам («Растянутый»)
     * ширины хватает с запасом — их ограничивает высота, и в обычной рамке они вышли бы мелкими.
     */
    val tallness get() = if (digitHeight > 2 * digit) 2.1f else 1f

    /** Ширина текста из цифр и двоеточий в долях кегля. */
    fun width(text: String) = text.sumOf { (if (it == ':') colon else digit).toDouble() }.toFloat()
}
