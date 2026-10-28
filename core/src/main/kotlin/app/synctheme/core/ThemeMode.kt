package app.synctheme.core

import kotlin.math.pow
import kotlinx.serialization.Serializable

/**
 * Светлая и тёмная версия темы. [ORIGINAL] — как тема задумана; [AUTO] — светлая днём и тёмная, когда
 * на телефоне включена тёмная тема.
 */
@Serializable
enum class ThemeMode(val title: String) {
    ORIGINAL("Как есть"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    AUTO("Как в системе"),
}

/** Версия темы для светлого или тёмного оформления телефона ([night]). */
fun Theme.resolved(night: Boolean): Theme = when (mode) {
    ThemeMode.ORIGINAL -> this
    ThemeMode.LIGHT -> variant(dark = false)
    ThemeMode.DARK -> variant(dark = true)
    ThemeMode.AUTO -> variant(dark = night)
}

/**
 * Светлая версия: градиенты — пастельные, светлое фото — без затемнения, тёмное — под светлой вуалью
 * (затемнение меньше нуля), цифры тёмные, акцент поглубже. Тёмная: градиенты — приглушённые, фото
 * затемнено, цифры белые, акцент посветлее.
 */
private fun Theme.variant(dark: Boolean): Theme {
    fun bg(b: Background): Background =
        if (b.type == BackgroundType.PHOTO) b
        else b.copy(colors = b.colors.map { if (dark) ThemeColors.blend(it, 0xFF000000.toInt(), 0.45f) else ThemeColors.blend(it, 0xFFFFFFFF.toInt(), 0.55f) })
    fun accentFor(c: Int) = when {
        !dark && ThemeColors.luminance(c) > 0.45 -> ThemeColors.blend(c, 0xFF000000.toInt(), 0.35f)
        dark && ThemeColors.luminance(c) < 0.18 -> ThemeColors.blend(c, 0xFFFFFFFF.toInt(), 0.4f)
        else -> c
    }
    val text = if (dark) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
    // Тёмные цифры в самой теме — значит, фото светлое (цвет цифр подбирается под снимок).
    val lightPhoto = ThemeColors.luminance(textColor) < 0.4
    fun dimFor(base: Float, b: Background) = when {
        b.type != BackgroundType.PHOTO -> base
        dark -> maxOf(base, if (lightPhoto) 0.5f else 0.38f)
        lightPhoto -> 0f
        else -> -0.45f
    }
    return copy(
        background = bg(background),
        lockBackground = lockBackground?.let(::bg),
        dim = dimFor(dim, background),
        lockDim = lockDim?.let { dimFor(it, lockScreenBackground()) },
        textColor = text,
        accentColor = accentFor(accentColor),
        // Отдельные настройки часов — в той же версии.
        watch = watch?.let { w ->
            w.copy(
                background = w.background?.let(::bg), textColor = text, accentColor = accentFor(w.accentColor),
                dim = dimFor(w.dim, w.background ?: background),
            )
        },
        // Готовая версия: повторное resolved() её уже не меняет.
        mode = ThemeMode.ORIGINAL,
    )
}

/** Цвета без Android: смешивание и яркость (для светлой и тёмной версий темы). */
object ThemeColors {
    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Относительная яркость 0..1 (как в WCAG). */
    fun luminance(argb: Int): Double {
        fun lin(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    }
}
