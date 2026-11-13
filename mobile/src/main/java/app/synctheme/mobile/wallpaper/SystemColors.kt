package app.synctheme.mobile.wallpaper

import android.app.WallpaperColors
import android.graphics.Color
import android.os.Build
import app.synctheme.core.Theme
import app.synctheme.mobile.render.Colors

/**
 * Цвета, которые обои SyncTheme сообщают системе. С Android 12 по ним телефон строит свою палитру
 * (Material You, «Цветовая палитра» One UI): шторка, клавиатура, системные кнопки и тематические
 * значки становятся в цвет темы — главным берётся акцент, а не угаданный по фото цвет.
 */
object SystemColors {
    fun of(theme: Theme): WallpaperColors {
        val primary = Color.valueOf(theme.accentColor or OPAQUE)
        val secondary = Color.valueOf(theme.textColor or OPAQUE)
        val tertiary = Color.valueOf((theme.background.colors.firstOrNull() ?: theme.accentColor) or OPAQUE)
        // Тёмные цифры — значит, фон светлый: системе можно ставить тёмный текст поверх обоев.
        val hints = if (Colors.isLight(theme.textColor)) WallpaperColors.HINT_SUPPORTS_DARK_THEME else WallpaperColors.HINT_SUPPORTS_DARK_TEXT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WallpaperColors(primary, secondary, tertiary, hints)
        else WallpaperColors(primary, secondary, tertiary)
    }

    /** Меняются ли цвета системы — при смене акцента, цвета цифр или фона. */
    fun key(theme: Theme): List<Int> = listOf(theme.accentColor, theme.textColor, theme.background.colors.firstOrNull() ?: 0)

    private const val OPAQUE = 0xFF000000.toInt()
}
