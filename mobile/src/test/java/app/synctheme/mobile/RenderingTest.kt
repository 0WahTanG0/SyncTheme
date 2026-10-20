package app.synctheme.mobile

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.ClockStyle
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.widget.WidgetAlign
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
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
            ThemeRenderer.watchPreview(theme.watchSettings(), bg, 450).save("${theme.id}_watch.png")
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
        for (theme in listOf(Presets.all[0], Presets.all[6])) {
            for (kind in WidgetKind.entries) {
                // В Robolectric нет установленных приложений — значки заменятся заглушками, подписи — именами пакетов.
                val apps = if (kind == WidgetKind.APPS) (1..8).map { "com.example.app$it/.Main" } else emptyList()
                val variants = kind.styles.map { it.title to WidgetConfig(style = it.id, opacity = 0.45f, apps = apps) } +
                    listOf("Свои настройки" to WidgetConfig(style = kind.defaultStyle, align = WidgetAlign.START, opacity = 0.85f,
                        showSeconds = false, colorIcons = false, title = "Избранное", apps = apps.take(3)))
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

    @Test
    fun buildsValidWatchFaceOnDevice() = runBlocking {
        for (theme in listOf(Presets.all[0], Presets.all[2], Presets.all[4])) {
            val result = WatchFaceBuilder(context).build(theme)
            println("Built ${theme.id}: ${result.apk.size / 1024} KB, token=${result.token.take(12)}…")
            assertTrue(result.token.isNotEmpty())
            File(out, "${theme.id}_watchface.apk").writeBytes(result.apk)
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
