package app.synctheme.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Шрифты цифр на выбор и шрифты прошлых версий. */
object DigitFonts {
    /** Шесть своих шрифтов, разных по типу: системный тонкий остаётся только «как задумано» у некоторых стилей. */
    val choices: List<DigitFont> = DigitFont.entries.filter { it.res != null }

    /**
     * Шрифты прошлых версий (до 1.21 — 17 шрифтов, до 1.28 — шрифты Google Fonts) становятся самым
     * похожим по характеру из нынешних — сохранённые темы и виджеты не ломаются.
     */
    private val legacy = mapOf(
        "NARROW" to DigitFont.COLUMN, "WIDE" to DigitFont.COLUMN, "GROTESK" to DigitFont.COLUMN,
        "SERIF" to DigitFont.STRETCH, "ITALIC" to DigitFont.STRETCH, "HAND" to DigitFont.STRETCH,
        "BUBBLE" to DigitFont.OUTLINE, "ROUND" to DigitFont.OUTLINE, "RETRO" to DigitFont.OUTLINE, "TUBE" to DigitFont.OUTLINE,
        "DOTS" to DigitFont.TECHNO, "PIXEL" to DigitFont.TECHNO, "MONO" to DigitFont.TECHNO, "SPACE" to DigitFont.TECHNO,
    )

    /** Шрифт по имени, в том числе из прошлых версий; null — имя неизвестно. */
    fun of(name: String?): DigitFont? = name?.let { n -> DigitFont.entries.firstOrNull { it.name == n } ?: legacy[n] }
}

/** Шрифт пишется именем; прежние имена при чтении заменяются на похожий шрифт ([DigitFonts.of]). */
object DigitFontSerializer : KSerializer<DigitFont> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.synctheme.core.DigitFont", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DigitFont) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): DigitFont = DigitFonts.of(decoder.decodeString()) ?: DigitFont.COLUMN
}
