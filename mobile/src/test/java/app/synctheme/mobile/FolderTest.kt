package app.synctheme.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.util.SizeF
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.synctheme.core.Presets
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.widget.FolderActivity
import app.synctheme.mobile.widget.FolderAnim
import app.synctheme.mobile.widget.FolderApp
import app.synctheme.mobile.widget.FolderGlyph
import app.synctheme.mobile.widget.FolderMode
import app.synctheme.mobile.widget.FolderOverlay
import app.synctheme.mobile.widget.FolderWidget
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import app.synctheme.mobile.widget.WidgetSize
import app.synctheme.mobile.widget.folderColumns
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Приложения» папкой: значок, невидимый виджет и пять анимаций раскрытия. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-560dpi")
class FolderTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }
    private val theme = Presets.all[0]
    private val names = listOf("Телефон", "Сообщения", "Камера", "Галерея", "Браузер", "Музыка", "Карты", "Заметки", "Почта")
    private val hues = listOf(140f, 200f, 30f, 280f, 220f, 340f, 100f, 50f, 0f)

    /** Значки-заглушки разных цветов: в Robolectric нет установленных приложений. */
    private fun apps(n: Int) = (0 until n).map { i ->
        val bmp = Bitmap.createBitmap(196, 196, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.HSVToColor(floatArrayOf(hues[i % hues.size], 0.65f, 0.9f)) }
        Canvas(bmp).apply {
            drawCircle(98f, 98f, 98f, p)
            p.color = Color.WHITE
            drawRoundRect(64f, 64f, 132f, 132f, 18f, 18f, p)
        }
        FolderApp("com.example.app$i/.Main", names[i % names.size], bmp.asImageBitmap())
    }

    @Test
    fun animationFrames() {
        val w = 1442
        val h = 3202
        val wallpaper = ThemeRenderer.background(app, theme, w / 2, h / 2).asImageBitmap()
        // Виджет-папка в левом нижнем углу рабочего стола.
        val origin = Rect(110, 2280, 390, 2560)
        val list = apps(9)
        var state by mutableStateOf(Triple(FolderAnim.REVEAL, 0f, "glass"))
        compose.setContent {
            val (anim, p, style) = state
            Box(Modifier.fillMaxSize()) {
                Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                FolderOverlay(WidgetConfig(style = style, folder = FolderMode.ICON, animation = anim, title = "Избранное"), theme, list, origin, p)
            }
        }
        val steps = listOf(0.12f, 0.3f, 0.55f, 0.8f, 1f)
        val styles = listOf("glass", "neon", "flower", "minimal", "cookie")
        // Кадры: строки — анимации, столбцы — моменты раскрытия (лист собирается из файлов).
        FolderAnim.entries.forEachIndexed { r, anim ->
            steps.forEachIndexed { c, p ->
                state = Triple(anim, p, styles[r])
                compose.waitForIdle()
                compose.onRoot().captureRoboImage(File(out, "folder_${anim.name.lowercase()}_$c.png").absolutePath)
            }
        }
    }

    /** Значок папки во всех стилях, точками и силуэтами, и невидимый виджет — собираются и открывают папку. */
    @Test
    fun folderWidget() {
        val styles = WidgetKind.APPS.styles.map { it.id }
        val px = 240
        val sheet = Bitmap.createBitmap(styles.size * (px + 20) + 20, 2 * (px + 20) + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawBitmap(ThemeRenderer.background(app, theme, sheet.width, sheet.height), 0f, 0f, null)
        FolderGlyph.entries.forEachIndexed { r, glyph ->
            styles.forEachIndexed { c, style ->
                val config = WidgetConfig(style = style, folder = FolderMode.ICON, glyph = glyph, opacity = 0.45f, apps = listOf("a/.A", "b/.B"))
                canvas.drawBitmap(FolderWidget.glyph(app, config, theme, px), (20 + c * (px + 20)).toFloat(), (20 + r * (px + 20)).toFloat(), null)
            }
        }
        File(out, "folder_icons.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        for (mode in listOf(FolderMode.ICON, FolderMode.HIDDEN)) {
            val config = WidgetConfig(style = "glass", folder = mode, title = "Избранное")
            val views = WidgetRenderer.build(app, WidgetKind.APPS, config, theme, sizes = listOf(SizeF(70f, 70f), SizeF(150f, 70f)), widgetId = 42)
            assertNotNull(views.apply(activity, FrameLayout(activity)))
            assertNotNull(WidgetRenderer.buildSize(app, WidgetKind.APPS, config, theme, WidgetSize.S).apply(activity, FrameLayout(activity)))
        }
        assertEquals(listOf(1, 2, 2, 2, 3, 3, 4), listOf(1, 2, 3, 4, 5, 9, 12).map(::folderColumns))
    }

    /** Папка открывается как окно (из настройки виджета — со своими настройками) и не остаётся висеть, когда ушли. */
    @Test
    fun folderActivityOpensAndCloses() {
        val config = WidgetConfig(style = "neon", folder = FolderMode.ICON, animation = FolderAnim.SPIRAL)
        val intent = FolderActivity.preview(app, config, Rect(100, 2000, 300, 2200))
        val controller = Robolectric.buildActivity(FolderActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop()
        assertTrue("папка закрылась", controller.get().isFinishing)
    }
}
