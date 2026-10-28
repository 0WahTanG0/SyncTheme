package app.synctheme.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Шрифты цифр на выбор и шрифты прошлых версий. */
object DigitFonts {
    /** Шесть шрифтов, разных по форме: системный тонкий остаётся только «как задумано» у некоторых стилей. */
    val choices: List<DigitFont> = DigitFont.entries.filter { it.res != null }

    /**
     * До 1.21 шрифтов было 17, многие отличались лишь насыщенностью и шириной. Каждый прежний
     * становится самым похожим по форме из шести — сохранённые темы и виджеты не ломаются.
     */
    private val legacy = mapOf(
        "WIDE" to DigitFont.NARROW, "GROTESK" to DigitFont.NARROW, "BLOCK" to DigitFont.NARROW,
        "STENCIL" to DigitFont.NARROW, "SPACE" to DigitFont.NARROW,
        "ROUND" to DigitFont.BUBBLE,
        "OUTLINE" to DigitFont.RETRO, "TUBE" to DigitFont.RETRO,
        "PIXEL" to DigitFont.DOTS, "MONO" to DigitFont.DOTS,
        "ITALIC" to DigitFont.SERIF,
    )

    /** Шрифт по имени, в том числе из прошлых версий; null — имя неизвестно. */
    fun of(name: String?): DigitFont? = name?.let { n -> DigitFont.entries.firstOrNull { it.name == n } ?: legacy[n] }
}

/** Шрифт пишется именем; прежние имена при чтении заменяются на похожий шрифт ([DigitFonts.of]). */
object DigitFontSerializer : KSerializer<DigitFont> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.synctheme.core.DigitFont", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DigitFont) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): DigitFont = DigitFonts.of(decoder.decodeString()) ?: DigitFont.NARROW
}
