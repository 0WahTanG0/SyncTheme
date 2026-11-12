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
        assertEquals(7, DigitFonts.choices.size)
        // Шрифты до 1.21 и шрифты Google Fonts до 1.28 становятся похожими своими.
        assertEquals(DigitFont.ECHO, DigitFonts.of("GROTESK"))
        assertEquals(DigitFont.ECHO, DigitFonts.of("NARROW"))
        assertEquals(DigitFont.NEON, DigitFonts.of("TUBE"))
        assertEquals(DigitFont.NEON, DigitFonts.of("RETRO"))
        assertEquals(DigitFont.HALFTONE, DigitFonts.of("PIXEL"))
        assertEquals(DigitFont.HALFTONE, DigitFonts.of("DOTS"))
        assertEquals(DigitFont.STRETCH, DigitFonts.of("SERIF"))
        assertEquals(DigitFont.VOLUME, DigitFonts.of("BUBBLE"))
        // Тема 1.27 со шрифтом, которого больше нет, читается и получает похожий.
        val old = Presets.all[0].copy(digitFont = DigitFont.ECHO).toJson().replace("\"ECHO\"", "\"HAND\"")
        assertEquals(DigitFont.STRETCH, Theme.fromJson(old).digitFont)
        // Совсем неизвестное имя — шрифт по умолчанию, тема всё равно читается.
        val unknown = Presets.all[0].copy(digitFont = DigitFont.ECHO).toJson().replace("\"ECHO\"", "\"WHATEVER\"")
        assertEquals(DigitFont.STRETCH, Theme.fromJson(unknown).digitFont)
    }

    @Test
    fun oldWidgetStylesBecomeNewOnes() {
        assertEquals(listOf("mint", "glass", "poster", "neon", "paper", "board"), WidgetKind.CLOCK.styles.map { it.id })
        assertEquals("poster", WidgetKind.CLOCK.style("bold").id)
        assertEquals("paper", WidgetKind.CLOCK.style("analog_minimal").id)
        assertEquals("board", WidgetKind.CLOCK.style("diag").id)
        assertEquals("glass", WidgetKind.APPS.style("capsule").id)
        // «Лёд» и «Сияние» (1.25) стали «Мятой» и «Неоном».
        assertEquals("mint", WidgetKind.WEATHER.style("ice").id)
        assertEquals("neon", WidgetKind.BATTERY.style("glow").id)
        WidgetConfigStore.set(app, 41, WidgetConfig(style = "minimal", font = "MONO"))
        val config = WidgetConfigStore.get(app, 41, WidgetKind.CLOCK, 0.4f)
        assertEquals("paper", config.style)
        assertEquals(DigitFont.HALFTONE, config.digitFont())
    }
}
