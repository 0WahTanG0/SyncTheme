package app.synctheme.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.DigitFont
import app.synctheme.core.Presets
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherDay
import app.synctheme.mobile.data.WeatherHour
import app.synctheme.mobile.data.WeatherNow
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.wallpaper.SystemColors
import app.synctheme.mobile.widget.NumberImages
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import app.synctheme.mobile.widget.WidgetSize
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Шрифт темы — у главных чисел всех виджетов; цвета системы — по акценту темы. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-560dpi")
class ThemeFontTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("previewsDir")!!).also { it.mkdirs() }

    private fun images(v: View, into: MutableList<ImageView> = mutableListOf()): List<ImageView> {
        if (v is ImageView && v.id != View.NO_ID && NumberImages[v.id] != null && v.visibility == View.VISIBLE) into += v
        if (v is ViewGroup) for (i in 0 until v.childCount) images(v.getChildAt(i), into)
        return into
    }

    /** С шрифтом темы число месяца, температура и заряд — картинкой во всех стилях и формах; без него — текстом. */
    @Test
    fun numbersUseThemeFont() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val themed = Presets.all[0].copy(digitFont = DigitFont.ECHO)
        val plain = Presets.all[0].copy(digitFont = null)
        for (kind in listOf(WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.BATTERY)) for (style in kind.styles) for (size in WidgetSize.entries) {
            val config = WidgetConfig(style = style.id)
            val withFont = WidgetRenderer.buildSize(context, kind, config, themed, size).apply(activity, FrameLayout(activity))
            val shown = images(withFont)
            shown.forEach { img ->
                assertEquals("$kind $style $size: текст спрятан", View.GONE, withFont.findViewById<View>(NumberImages[img.id]!!).visibility)
            }
            val withoutFont = WidgetRenderer.buildSize(context, kind, config, plain, size).apply(activity, FrameLayout(activity))
            assertTrue("$kind $style $size: без шрифта темы — текст", images(withoutFont).isEmpty())
        }
        // Хотя бы у крупного виджета каждого вида число действительно картинкой.
        for (kind in listOf(WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.BATTERY)) {
            val view = WidgetRenderer.buildSize(context, kind, WidgetConfig(style = kind.defaultStyle), themed, WidgetSize.M).apply(activity, FrameLayout(activity))
            assertTrue("$kind: число картинкой", images(view).isNotEmpty())
        }
    }

    /** Лист для глаз: виды × шрифты, стиль «Мята». */
    @Test
    fun widgetsInEveryFont() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        val size = WidgetSize.M
        val cw = (size.previewW * d).toInt()
        val ch = (size.previewH * d).toInt()
        val gap = (10 * d).toInt()
        val kinds = listOf(WidgetKind.CLOCK, WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.BATTERY)
        val fonts = app.synctheme.core.DigitFonts.choices
        val weather = WeatherData(
            place = "Москва", updatedAt = System.currentTimeMillis(),
            now = WeatherNow(temp = 21.4, feels = 20.0, humidity = 60, wind = 3.0, code = 1, isDay = true),
            hours = (0 until 6).map { WeatherHour("${10 + it}:00", 18.0 + it, 1, true) },
            days = (0 until 5).map { WeatherDay("2026-11-${14 + it}", 22.0 - it, 12.0 - it, 2) },
        )
        val sheet = Bitmap.createBitmap(kinds.size * (cw + gap) + gap, fonts.size * (ch + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        fonts.forEachIndexed { r, font ->
            val theme = Presets.all[0].copy(digitFont = font)
            val wallpaper = ThemeRenderer.background(context, theme, cw, ch)
            kinds.forEachIndexed { c, kind ->
                val host = FrameLayout(activity)
                activity.setContentView(host)
                val view = WidgetRenderer.buildSize(context, kind, WidgetConfig(style = kind.defaultStyle, opacity = 0.45f), theme, size, weather).apply(activity, host)
                host.addView(view)
                shadowOf(Looper.getMainLooper()).idle()
                view.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, cw, ch)
                val bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                cv.drawBitmap(wallpaper, 0f, 0f, null)
                view.draw(cv)
                canvas.drawBitmap(bmp, (gap + c * (cw + gap)).toFloat(), (gap + r * (ch + gap)).toFloat(), null)
            }
        }
        File(out, "widgets_theme_font.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Обои SyncTheme сообщают системе акцент темы — от него Android строит палитру телефона. */
    @Test
    fun systemColorsFollowAccent() {
        val theme = Presets.all[0].copy(accentColor = 0xFF2E8A66.toInt(), textColor = 0xFFFFFFFF.toInt())
        val colors = SystemColors.of(theme)
        assertEquals(0xFF2E8A66.toInt(), colors.primaryColor.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), colors.secondaryColor!!.toArgb())
        val other = SystemColors.key(theme.copy(accentColor = 0xFFE0703A.toInt()))
        assertTrue(other != SystemColors.key(theme))
    }
}
