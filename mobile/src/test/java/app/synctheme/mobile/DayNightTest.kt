package app.synctheme.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Presets
import app.synctheme.core.ThemeMode
import app.synctheme.core.WallClock
import app.synctheme.core.resolved
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.render.SubjectMask
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import app.synctheme.mobile.widget.WidgetSize
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/** Светлая и тёмная версии темы и «Тема из фото» в один шаг. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayNightTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun lightAndDarkVariants() {
        val base = Presets.all[0]
        assertEquals(base, base.resolved(night = true))
        val dark = base.copy(mode = ThemeMode.DARK).resolved(night = false)
        val light = base.copy(mode = ThemeMode.LIGHT).resolved(night = true)
        assertEquals(Color.WHITE, dark.textColor)
        assertTrue(ColorUtils.calculateLuminance(light.textColor) < 0.1)
        // Градиент светлой версии светлее тёмной.
        assertTrue(ColorUtils.calculateLuminance(light.background.colors[0]) > ColorUtils.calculateLuminance(dark.background.colors[0]))
        // Готовая версия больше не меняется.
        assertEquals(dark, dark.resolved(night = false))
        // «Как в системе» — по оформлению телефона.
        val auto = base.copy(mode = ThemeMode.AUTO)
        assertEquals(Color.WHITE, auto.resolved(night = true).textColor)
        assertEquals(light.textColor, auto.resolved(night = false).textColor)
        // Тёмное фото в светлой версии — под светлой вуалью, светлое — без затемнения.
        val photo = base.copy(background = Background(BackgroundType.PHOTO, listOf(Color.BLACK), photoFile = "x.jpg"), textColor = Color.WHITE, dim = 0.2f)
        assertTrue(photo.copy(mode = ThemeMode.LIGHT).resolved(false).dim < 0f)
        assertEquals(0f, photo.copy(textColor = 0xFF1A1A1A.toInt(), mode = ThemeMode.LIGHT).resolved(false).dim)
        assertTrue(photo.copy(mode = ThemeMode.DARK).resolved(false).dim >= 0.38f)
    }

    /** У темы «Как в системе» в виджете оба набора цветов: лаунчер сам переключает их вместе с телефоном. */
    @Test
    fun widgetFollowsPhoneDarkMode() {
        val theme = Presets.all[0].copy(mode = ThemeMode.AUTO)
        val views = WidgetRenderer.buildSize(app, WidgetKind.WEATHER, WidgetConfig(style = "glass"), theme, WidgetSize.M, weather = null)
        fun colors(): List<Int> = buildList {
            fun walk(v: View) {
                if (v is TextView && v.visibility == View.VISIBLE) add(v.currentTextColor)
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(views.apply(app, FrameLayout(app)))
        }
        RuntimeEnvironment.setQualifiers("notnight")
        assertTrue(!DayNight.isNight(app))
        val day = colors()
        RuntimeEnvironment.setQualifiers("night")
        assertTrue(DayNight.isNight(app))
        val night = colors()
        assertNotEquals(day, night)
        assertTrue(night.all { Color.alpha(it) == 0 || ColorUtils.calculateLuminance(it or 0xFF000000.toInt()) > 0.5 })
        RuntimeEnvironment.setQualifiers("notnight")
    }

    @Test
    fun themeFromPhotoInOneStep() {
        val repo = ThemeRepository.get(app)
        repo.setDraft(Presets.all[0].copy(lockBackground = Background(BackgroundType.SOLID, listOf(Color.RED)), wallClock = WallClock()))
        val before = repo.saved.value.size
        val file = File(app.cacheDir, "sunset.png")
        val bmp = Bitmap.createBitmap(300, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(250, 236, 210)) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Вместо ML Kit: объект — круг в центре кадра.
        SubjectMask.segmenter = { _, input ->
            Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawCircle(input.width / 2f, input.height * 0.6f, input.width * 0.3f, Paint().apply { color = Color.WHITE })
            }
        }
        val vm = MainViewModel(app)
        vm.themeFromPhoto(Uri.fromFile(file))
        repeat(60) {
            shadowOf(Looper.getMainLooper()).idle()
            if (repo.saved.value.size > before) return@repeat
            Thread.sleep(100)
        }
        assertEquals(before + 1, repo.saved.value.size)
        val t = vm.theme.value
        assertEquals(repo.saved.value.first().id, t.id)
        assertTrue(t.name.startsWith("Из фото"))
        assertEquals(BackgroundType.PHOTO, t.background.type)
        // Блокировка — тем же фото; на светлом фото цифры тёмные и затемнения нет.
        assertNull(t.lockBackground)
        assertTrue("цифры ${Integer.toHexString(t.textColor)}, фон ${t.background.colors.map(Integer::toHexString)}", ColorUtils.calculateLuminance(t.textColor) < 0.1)
        assertEquals(0f, t.dim)
        // Часы на обоях были — объект нашёлся, и часы сразу за ним.
        assertTrue(t.wallClock?.depth == true)
    }
}
