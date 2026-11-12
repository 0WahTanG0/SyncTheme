package app.synctheme.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Шрифты цифр на выбор и шрифты прошлых версий. */
object DigitFonts {
    /** Свои шрифты, у каждого своя идея; системный тонкий остаётся только «как задумано» у некоторых стилей. */
    val choices: List<DigitFont> = DigitFont.entries.filter { it.res != null }

    /**
     * Шрифты прошлых версий (до 1.21 — 17 шрифтов, до 1.28 — шрифты Google Fonts) становятся самым
     * похожим по характеру из нынешних — сохранённые темы и виджеты не ломаются.
     */
    private val legacy = mapOf(
        "NARROW" to DigitFont.ECHO, "WIDE" to DigitFont.ECHO, "GROTESK" to DigitFont.ECHO, "BLOCK" to DigitFont.ECHO,
        "SERIF" to DigitFont.STRETCH, "ITALIC" to DigitFont.STRETCH, "HAND" to DigitFont.STRETCH,
        "BUBBLE" to DigitFont.VOLUME, "ROUND" to DigitFont.VOLUME,
        "RETRO" to DigitFont.NEON, "TUBE" to DigitFont.NEON, "OUTLINE" to DigitFont.NEON,
        "DOTS" to DigitFont.HALFTONE, "PIXEL" to DigitFont.HALFTONE, "MONO" to DigitFont.HALFTONE,
        "STENCIL" to DigitFont.GLITCH, "SPACE" to DigitFont.GLITCH,
    )

    /** Шрифт по имени, в том числе из прошлых версий; null — имя неизвестно. */
    fun of(name: String?): DigitFont? = name?.let { n -> DigitFont.entries.firstOrNull { it.name == n } ?: legacy[n] }
}

/** Шрифт пишется именем; прежние имена при чтении заменяются на похожий шрифт ([DigitFonts.of]). */
object DigitFontSerializer : KSerializer<DigitFont> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.synctheme.core.DigitFont", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DigitFont) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): DigitFont = DigitFonts.of(decoder.decodeString()) ?: DigitFont.STRETCH
}
