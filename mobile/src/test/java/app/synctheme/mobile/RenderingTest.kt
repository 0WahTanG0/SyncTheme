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
import app.synctheme.mobile.widget.AnalogClockWidget
import app.synctheme.mobile.widget.BatteryWidget
import app.synctheme.mobile.widget.ClockWidget
import app.synctheme.mobile.widget.DateWidget
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/** Рендерит превью, обои и виджеты в PNG и собирает циферблат так же, как на телефоне. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }

    private fun Bitmap.save(name: String) = File(out, name).outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun rendersPreviewsForAllPresets() {
        for (theme in Presets.all) {
            ThemeRenderer.phonePreview(context, theme, 540, 1170).save("${theme.id}_phone.png")
            val bg = ThemeRenderer.watchBackground(context, theme)
            ThemeRenderer.watchPreview(theme, bg, 450).save("${theme.id}_watch.png")
        }
    }

    @Test
    fun widgetsInflateWithThemeColors() {
        for (theme in listOf(Presets.all[0], Presets.all[1].copy(clockStyle = ClockStyle.STACKED), Presets.all[6])) {
            ThemeRepository.get(context).setApplied(theme)
            val widgets = listOf(
                Triple("clock", ClockWidget().build(context, theme), 360 to 180),
                Triple("analog", AnalogClockWidget().build(context, theme), 170 to 170),
                Triple("date", DateWidget().build(context, theme), 170 to 170),
                Triple("battery", BatteryWidget().build(context, theme), 170 to 170),
            )
            for ((name, views, size) in widgets) {
                // apply() вызывает методы через рефлексию — упадёт, если метод нельзя вызывать из RemoteViews.
                val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
                val host = FrameLayout(activity)
                activity.setContentView(host)
                val view = views.apply(activity, host)
                host.addView(view)
                shadowOf(Looper.getMainLooper()).idle()
                render(view, size.first, size.second).save("widget_${theme.id}_$name.png")
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

    private fun render(view: View, wDp: Int, hDp: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF3A3A44.toInt())
        view.draw(canvas)
        return bmp
    }

}
