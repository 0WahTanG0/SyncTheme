package app.synctheme.mobile.render

import androidx.core.graphics.ColorUtils
import app.synctheme.core.Theme

object Colors {
    fun withAlpha(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).toInt())

    fun isLight(color: Int) = ColorUtils.calculateLuminance(color) > 0.5

    /** Цвет подложки виджетов: тёмный оттенок фона для светлого текста и наоборот. */
    fun widgetSurface(theme: Theme): Int {
        val base = theme.background.colors.firstOrNull() ?: 0xFF000000.toInt()
        val target = if (isLight(theme.textColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        return ColorUtils.blendARGB(base, target, 0.55f) or 0xFF000000.toInt()
    }
}
