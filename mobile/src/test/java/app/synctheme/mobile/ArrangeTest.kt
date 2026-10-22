package app.synctheme.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Background
import app.synctheme.core.ClockPlace
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockStyle
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.core.TimeShift
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.ArrangeScreen
import app.synctheme.mobile.ui.ArrangeTarget
import app.synctheme.mobile.ui.MainViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Редактор расположения: часы, время и фото двигаются пальцем, результат попадает в тему. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-560dpi")
class ArrangeTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }
    private val photo = "photo_arrange.jpg"

    @Before
    fun setUp() {
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 1600f, 0xFF2B6CB0.toInt(), 0xFFF6AD55.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 1200f, 1600f, p)
        ThemeRepository.get(app).photoFile(photo).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun theme(): Theme = Presets.all[0].copy(
        background = Background(BackgroundType.PHOTO, listOf(0xFF1A202C.toInt()), photoFile = photo),
        wallClock = WallClock(WallClockLayout.TOP, depth = false),
        clockStyle = ClockStyle.DIGITAL,
    )

    private fun waitPreview() = compose.waitUntil(15_000) {
        compose.onAllNodesWithContentDescription("Расположение").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun movesHomeClockAndPhoto() {
        // Часы поменьше — им есть куда ехать в стороны.
        ThemeRepository.get(app).setDraft(theme().let { it.copy(wallClock = it.wallClock!!.copy(place = ClockPlace(0.5f, 0.25f, 0.5f))) })
        val vm = MainViewModel(app)
        compose.setContent { ArrangeScreen(vm, ArrangeTarget.HOME) {} }
        waitPreview()
        val before = ThemeRenderer.clockPlaceOf(app, vm.theme.value, 540, 1170)!!
        // Часы — влево и вниз.
        compose.onNodeWithContentDescription("Расположение").performTouchInput {
            swipe(center, center + Offset(-width * 0.25f, height * 0.3f), 300)
        }
        compose.waitForIdle()
        // Фото — вправо (кадр уезжает влево).
        compose.onNodeWithText("Фото").performClick()
        compose.onNodeWithContentDescription("Расположение").performTouchInput {
            swipe(center, center + Offset(width * 0.2f, 0f), 300)
        }
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "screen_arrange_home.png").absolutePath)
        compose.onNodeWithText("Готово").performClick()

        val t = vm.theme.value
        val place = t.wallClock?.place
        assertNotNull("часы передвинуты", place)
        assertTrue("левее: ${place!!.x} < ${before.x}", place.x < before.x - 0.1f)
        assertTrue("ниже: ${place.y} > ${before.y}", place.y > before.y + 0.1f)
        assertTrue("фото сдвинуто: ${t.background.focusX}", t.background.focusX < 0.5f)
    }

    /** Телефон боком: холст расположения вписывается по высоте, кнопки не уезжают. */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land-560dpi")
    fun landscape() {
        ThemeRepository.get(app).setDraft(theme())
        val vm = MainViewModel(app)
        compose.setContent { ArrangeScreen(vm, ArrangeTarget.HOME) {} }
        waitPreview()
        Thread.sleep(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "screen_arrange_land.png").absolutePath)
        compose.onNodeWithText("Готово").assertExists()
    }

    @Test
    fun movesWatchTime() {
        ThemeRepository.get(app).setDraft(theme())
        val vm = MainViewModel(app)
        compose.setContent { ArrangeScreen(vm, ArrangeTarget.WATCH) {} }
        waitPreview()
        compose.onNodeWithContentDescription("Расположение").performTouchInput {
            swipe(center, center + Offset(width * 0.2f, height * 0.15f), 300)
        }
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "screen_arrange_watch.png").absolutePath)
        compose.onNodeWithText("Готово").performClick()
        val shift = vm.theme.value.watchSettings().timeShift
        assertNotNull("время передвинуто", shift)
        assertTrue("вправо и вниз: $shift", shift!!.dx > 40 && shift.dy > 20)
        // Телефон при этом не тронут.
        assertEquals(null, vm.theme.value.wallClock?.place)
    }

    /** Часы и время не уезжают за край: у края они останавливаются. */
    @Test
    fun keepsClockOnScreen() {
        val t = theme()
        val placed = ThemeRenderer.clampPlace(t, ClockPlace(-0.4f, 1.3f, 1f), 540, 1170)
        assertTrue("x на экране: ${placed.x}", placed.x > 0.3f)
        assertTrue("y на экране: ${placed.y}", placed.y < 0.95f)
        val shift = ThemeRenderer.fitShift(app, t, TimeShift(400f, 0f, 1f))
        assertTrue("время внутри круга: $shift", shift.dx < 120f)
    }
}
