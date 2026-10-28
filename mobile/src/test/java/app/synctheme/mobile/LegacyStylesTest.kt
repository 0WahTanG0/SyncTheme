package app.synctheme.mobile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.DigitFont
import app.synctheme.core.DigitFonts
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetConfigStore
import app.synctheme.mobile.widget.WidgetKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** После обновления сохранённые темы и виджеты прошлых версий получают похожие новые стили и шрифты. */
@RunWith(RobolectricTestRunner::class)
class LegacyStylesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun oldFontsBecomeSimilarOnes() {
        assertEquals(6, DigitFonts.choices.size)
        assertEquals(DigitFont.NARROW, DigitFonts.of("GROTESK"))
        assertEquals(DigitFont.RETRO, DigitFonts.of("TUBE"))
        assertEquals(DigitFont.DOTS, DigitFonts.of("PIXEL"))
        assertEquals(DigitFont.SERIF, DigitFonts.of("ITALIC"))
        assertEquals(DigitFont.BUBBLE, DigitFonts.of("ROUND"))
        // Тема 1.20 со шрифтами, которых больше нет, читается и получает похожие.
        val old = Presets.all[0].copy(digitFont = DigitFont.HAND).toJson().replace("\"HAND\"", "\"STENCIL\"")
        assertEquals(DigitFont.NARROW, Theme.fromJson(old).digitFont)
    }

    @Test
    fun oldWidgetStylesBecomeNewOnes() {
        assertEquals(listOf("glass", "poster", "neon", "paper", "board"), WidgetKind.CLOCK.styles.map { it.id })
        assertEquals("poster", WidgetKind.CLOCK.style("bold").id)
        assertEquals("paper", WidgetKind.CLOCK.style("analog_minimal").id)
        assertEquals("board", WidgetKind.CLOCK.style("diag").id)
        assertEquals("glass", WidgetKind.APPS.style("capsule").id)
        WidgetConfigStore.set(app, 41, WidgetConfig(style = "minimal", font = "MONO"))
        val config = WidgetConfigStore.get(app, 41, WidgetKind.CLOCK, 0.4f)
        assertEquals("paper", config.style)
        assertEquals(DigitFont.DOTS, config.digitFont())
    }
}
