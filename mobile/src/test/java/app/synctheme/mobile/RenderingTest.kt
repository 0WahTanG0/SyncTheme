package app.synctheme.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.SizeF
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
import app.synctheme.core.DigitFont
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.widget.WidgetAlign
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import org.junit.Assert.assertEquals
import app.synctheme.mobile.widget.TimeImages
import android.view.ViewGroup
import app.synctheme.mobile.widget.WidgetSize
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Рендерит превью, обои и виджеты в PNG и собирает циферблат так же, как на телефоне. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class RenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }

    private fun Bitmap.save(name: String) = File(out, name).outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun rendersPreviewsForAllPresets() {
        for (theme in Presets.all) {
            ThemeRenderer.phonePreview(context, theme, 540, 1170).save("${theme.id}_phone.png")
            val bg = ThemeRenderer.watchBackground(context, theme)
            ThemeRenderer.watchPreview(context, theme.watchSettings(), bg, 450).save("${theme.id}_watch.png")
            ThemeRenderer.lockPreview(context, theme, 540, 1170).save("${theme.id}_lock.png")
        }
    }

    /**
     * Все виды × стили × размеры: apply() вызывает методы через рефлексию и упадёт, если метод
     * нельзя вызывать из RemoteViews. Для просмотра собирается лист «стили по строкам, размеры по столбцам».
     */
    @Test
    fun allWidgetStylesRender() {
        val weather = WeatherRepository.parseForecast(
            javaClass.classLoader!!.getResource("forecast.json")!!.readText(), "Москва", System.currentTimeMillis(),
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        android.provider.Settings.Global.putString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME, "Galaxy S25+")
        // Батарея как на телефоне: 64 % и заряжается — видно цвет зарядки.
        shadowOf(context.getSystemService(BatteryManager::class.java)).apply {
            setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 64)
            setIsCharging(true)
        }
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
                .putExtra(BatteryManager.EXTRA_TEMPERATURE, 312).putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD),
        )
        for (theme in listOf(Presets.all[0], Presets.all.first { it.id == "sakura" })) {
            for (kind in WidgetKind.entries) {
                // В Robolectric нет установленных приложений — значки заменятся заглушками, подписи — именами пакетов.
                val apps = if (kind == WidgetKind.APPS) (1..8).map { "com.example.app$it/.Main" } else emptyList()
                val variants = kind.styles.map { it.title to WidgetConfig(style = it.id, opacity = 0.45f, apps = apps) } +
                    listOf("Свои настройки" to WidgetConfig(style = kind.defaultStyle, align = WidgetAlign.START, opacity = 0.85f,
                        colorIcons = false, title = "Избранное", apps = apps.take(3), sky = true, scene = false))
                val gap = (12 * d).toInt()
                val cellW = WidgetSize.entries.map { (it.previewW * d).toInt() }
                val rowH = (WidgetSize.entries.maxOf { it.previewH } * d).toInt()
                val sheet = Bitmap.createBitmap(cellW.sum() + gap * (cellW.size + 1), (rowH + gap) * variants.size + gap, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                canvas.drawColor(0xFF202024.toInt())
                variants.forEachIndexed { r, (_, config) ->
                    var x = gap
                    WidgetSize.entries.forEachIndexed { c, size ->
                        val views = WidgetRenderer.buildSize(context, kind, config, theme, size, weather)
                        // Часы ставят время, только когда прикреплены к окну.
                        val host = FrameLayout(activity)
                        activity.setContentView(host)
                        val view = views.apply(activity, host)
                        host.addView(view)
                        shadowOf(Looper.getMainLooper()).idle()
                        val bmp = render(view, size.previewW, size.previewH, ThemeRenderer.background(context, theme, cellW[c], (size.previewH * d).toInt()))
                        canvas.drawBitmap(bmp, x.toFloat(), (gap + r * (rowH + gap)).toFloat(), null)
                        x += cellW[c] + gap
                    }
                }
                sheet.save("widgets_${theme.id}_${kind.name.lowercase()}.png")
                // Все размеры сразу — так виджет уходит в лаунчер.
                val all = WidgetRenderer.build(context, kind, variants.first().second, theme, weather)
                assertTrue(all.apply(activity, FrameLayout(activity)) != null)
            }
        }
    }

    /** Пять стилей виджетов рядом — каждый как рабочий стол телефона со всеми видами виджетов. */
    @Test
    fun widgetStylesShowcase() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val weather = WeatherRepository.parseForecast(
            javaClass.classLoader!!.getResource("forecast.json")!!.readText(), "Москва", System.currentTimeMillis(),
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 72)
        // Место на «экране» (dp): вид, размер, x, y, ширина, высота.
        val slots = listOf(
            listOf(WidgetKind.CLOCK, WidgetSize.WIDE, 16, 70, 380, 170),
            listOf(WidgetKind.WEATHER, WidgetSize.M, 16, 256, 182, 182),
            listOf(WidgetKind.CALENDAR, WidgetSize.M, 214, 256, 182, 182),
            listOf(WidgetKind.BATTERY, WidgetSize.STRIP, 16, 454, 380, 84),
            listOf(WidgetKind.CLOCK, WidgetSize.M, 16, 554, 182, 182),
            listOf(WidgetKind.APPS, WidgetSize.M, 214, 554, 182, 182),
            listOf(WidgetKind.CALENDAR, WidgetSize.STRIP, 16, 752, 380, 84),
        )
        for (theme in listOf(Presets.all[0], Presets.all.first { it.id == "sakura" })) {
            val pw = (412 * d).toInt()
            val ph = (860 * d).toInt()
            val gap = (24 * d).toInt()
            val styles = WidgetKind.CLOCK.styles
            val sheet = Bitmap.createBitmap(styles.size * (pw + gap) + gap, ph + 2 * gap, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(0xFF111114.toInt())
            val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 22 * d
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            styles.forEachIndexed { i, style ->
                val screen = ThemeRenderer.background(context, theme, pw, ph)
                val c = Canvas(screen)
                c.drawText(style.title, 20 * d, 44 * d, label)
                for (slot in slots) {
                    val kind = slot[0] as WidgetKind
                    val size = slot[1] as WidgetSize
                    val (x, y, w, h) = slot.drop(2).map { it as Int }
                    val apps = if (kind == WidgetKind.APPS) (1..8).map { "com.example.app$it/.Main" } else emptyList()
                    val views = WidgetRenderer.buildSize(
                        context, kind, WidgetConfig(style = style.id, opacity = 0.45f, apps = apps), theme, size, weather, SizeF(w.toFloat(), h.toFloat()),
                    )
                    val host = FrameLayout(activity)
                    activity.setContentView(host)
                    val view = views.apply(activity, host)
                    host.addView(view)
                    shadowOf(Looper.getMainLooper()).idle()
                    val bg = Bitmap.createBitmap(screen, (x * d).toInt(), (y * d).toInt(), (w * d).toInt(), (h * d).toInt())
                    c.drawBitmap(render(view, w, h, bg), x * d, y * d, null)
                }
                canvas.drawBitmap(screen, (gap + i * (pw + gap)).toFloat(), gap.toFloat(), null)
            }
            sheet.save("widget_styles_${theme.id}.png")
        }
    }

    /** Небо: часы в разное время суток (сверху) и погода в разную погоду (снизу). */
    @Test
    fun widgetSkies() {
        val forecast = WeatherRepository.parseForecast(
            javaClass.classLoader!!.getResource("forecast.json")!!.readText(), "Москва", System.currentTimeMillis(),
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        val theme = Presets.all[0]
        val size = WidgetSize.M
        val cw = (size.previewW * d).toInt()
        val ch = (size.previewH * d).toInt()
        val gap = (12 * d).toInt()
        val hours = listOf(6, 12, 18, 20, 23)
        // Ясно днём и ночью, облачно, дождь, снег, гроза.
        val weathers = listOf(0 to true, 0 to false, 3 to true, 63 to true, 73 to true, 95 to false)
        val cols = maxOf(hours.size, weathers.size)
        val sheet = Bitmap.createBitmap(cols * (cw + gap) + gap, 2 * (ch + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        fun draw(views: android.widget.RemoteViews, r: Int, c: Int) {
            val host = FrameLayout(activity)
            activity.setContentView(host)
            val view = views.apply(activity, host)
            host.addView(view)
            shadowOf(Looper.getMainLooper()).idle()
            val bmp = render(view, size.previewW, size.previewH, ThemeRenderer.background(context, theme, cw, ch))
            canvas.drawBitmap(bmp, (gap + c * (cw + gap)).toFloat(), (gap + r * (ch + gap)).toFloat(), null)
        }
        val saved = WidgetRenderer.hourNow
        try {
            hours.forEachIndexed { c, h ->
                WidgetRenderer.hourNow = { h }
                draw(WidgetRenderer.buildSize(context, WidgetKind.CLOCK, WidgetConfig(style = "glass", sky = true, opacity = 0.6f), theme, size, forecast), 0, c)
            }
        } finally {
            WidgetRenderer.hourNow = saved
        }
        weathers.forEachIndexed { c, (code, day) ->
            val w = forecast.copy(now = forecast.now.copy(code = code, isDay = day))
            draw(WidgetRenderer.buildSize(context, WidgetKind.WEATHER, WidgetConfig(style = "poster", opacity = 0.6f), theme, size, w), 1, c)
        }
        sheet.save("widget_skies.png")
    }

    /**
     * Шрифты цифр в каждом стиле цифровых часов: строки — шрифты, столбцы — стили. Цифры — картинкой
     * выбранным шрифтом (лаунчер шрифты приложения в TextClock не загружает): проверяется, что во всех
     * размерах картинка на месте, а TextClock спрятан.
     */
    @Test
    fun widgetClockFonts() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        val theme = Presets.all[0]
        val styles = WidgetKind.CLOCK.styles
        for (size in WidgetSize.entries) for (style in styles) for (font in DigitFont.entries) {
            val config = WidgetConfig(style = style.id, font = font.name, align = WidgetAlign.entries[font.ordinal % 3])
            val view = WidgetRenderer.buildSize(context, WidgetKind.CLOCK, config, theme, size).apply(activity, FrameLayout(activity))
            val images = mutableListOf<View>()
            fun walk(v: View) {
                if (v is android.widget.ImageView && v.id != View.NO_ID && TimeImages[v.id] != null && v.visibility == View.VISIBLE) images += v
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(view)
            val clocks = images.map { TimeImages[it.id]!!.clock }
            if (font.res == null) {
                assertTrue("$style $size $font: системный шрифт — без картинок", images.isEmpty())
            } else {
                assertTrue("$style $size $font: цифры картинкой", images.isNotEmpty())
                clocks.forEach { assertEquals("$style $size $font: TextClock спрятан", View.GONE, view.findViewById<View>(it).visibility) }
            }
        }
        for (size in listOf(WidgetSize.M, WidgetSize.WIDE, WidgetSize.COLUMN)) {
            val cw = (size.previewW * d).toInt()
            val ch = (size.previewH * d).toInt()
            val gap = (10 * d).toInt()
            val sheet = Bitmap.createBitmap(styles.size * (cw + gap) + gap, DigitFont.entries.size * (ch + gap) + gap, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(0xFF202024.toInt())
            val wallpaper = ThemeRenderer.background(context, theme, cw, ch)
            DigitFont.entries.forEachIndexed { r, font ->
                styles.forEachIndexed { c, style ->
                    val config = WidgetConfig(style = style.id, opacity = 0.45f, font = font.name)
                    val host = FrameLayout(activity)
                    activity.setContentView(host)
                    val view = WidgetRenderer.buildSize(context, WidgetKind.CLOCK, config, theme, size).apply(activity, host)
                    host.addView(view)
                    shadowOf(Looper.getMainLooper()).idle()
                    canvas.drawBitmap(render(view, size.previewW, size.previewH, wallpaper), (gap + c * (cw + gap)).toFloat(), (gap + r * (ch + gap)).toFloat(), null)
                }
            }
            sheet.save("clock_fonts_${size.key}.png")
        }
    }

    /** Каждый шрифт цифр в нескольких стилях циферблата: строки — стили, столбцы — шрифты. */
    @Test
    fun watchFonts() {
        val size = 220
        val gap = 14
        val styles = listOf(ClockStyle.DIGITAL, ClockStyle.BOLD, ClockStyle.COLUMNS, ClockStyle.DIAGONAL, ClockStyle.DRUM)
        val fonts = DigitFont.entries
        val sheet = Bitmap.createBitmap(fonts.size * (size + gap) + gap, styles.size * (size + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        styles.forEachIndexed { r, style ->
            fonts.forEachIndexed { c, font ->
                val theme = Presets.all[0].copy(clockStyle = style, digitFont = font)
                val bg = ThemeRenderer.watchBackground(context, theme)
                val preview = ThemeRenderer.watchPreview(context, theme.watchSettings(), bg, size)
                canvas.drawBitmap(preview, (gap + c * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        sheet.save("watch_fonts.png")
    }

    /** Все стили циферблата на двух темах — лист для просмотра. */
    @Test
    fun allWatchStyles() {
        val size = 300
        val gap = 20
        val styles = ClockStyle.entries
        val themes = listOf(Presets.all[0], Presets.all.first { it.id == "sakura" }, Presets.all[4])
        val sheet = Bitmap.createBitmap(styles.size * (size + gap) + gap, themes.size * (size + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        themes.forEachIndexed { r, base ->
            styles.forEachIndexed { c, style ->
                val theme = base.copy(clockStyle = style)
                val bg = ThemeRenderer.watchBackground(context, theme)
                val preview = ThemeRenderer.watchPreview(context, theme.watchSettings(), bg, size)
                canvas.drawBitmap(preview, (gap + c * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        sheet.save("watch_styles.png")
    }

    @Test
    fun buildsValidWatchFaceOnDevice() = runBlocking {
        for (theme in listOf(Presets.all[0], Presets.all[2], Presets.all[4])) {
            val result = WatchFaceBuilder(context).build(theme)
            println("Built ${theme.id}: ${result.apk.size / 1024} KB, token=${result.token.take(12)}…")
            assertTrue(result.token.isNotEmpty())
            File(out, "${theme.id}_watchface.apk").writeBytes(result.apk)
        }
        // Каждый стиль, шрифт и вид делений проходит тот же валидатор, что на телефоне.
        val base = Presets.all[1]
        ClockStyle.entries.forEachIndexed { i, style ->
            val font = DigitFont.entries[i % DigitFont.entries.size]
            val dial = DialStyle.entries[i % DialStyle.entries.size]
            val t = base.copy(watch = base.followPhone().copy(clockStyle = style, digitFont = font, dialStyle = dial, complications = i % 2 == 0, showSeconds = i % 3 != 0))
            assertTrue(style.name, WatchFaceBuilder(context).build(t).token.isNotEmpty())
        }
    }

    private fun render(view: View, wDp: Int, hDp: Int, background: Bitmap): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(background, null, android.graphics.Rect(0, 0, w, h), null)
        view.draw(canvas)
        return bmp
    }

}
