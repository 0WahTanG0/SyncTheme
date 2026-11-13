package app.synctheme.mobile

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Presets
import app.synctheme.core.WallClock
import app.synctheme.mobile.data.ThemePalettes
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.SamplePhotos
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Блок «Цвета» на главном экране: палитры из фона читаются на нём, касание меняет цвета темы. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2400dp-560dpi")
class ColorsBlockTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** Тёмное фото, светлое фото и почти серое: у каждого — несколько разных палитр, и все читаются. */
    @Test
    fun palettesAreReadableAndDistinct() {
        val cases = listOf(
            ThemePalettes.Swatches(0xFF1E2A44.toInt(), vibrant = 0xFFE0703A.toInt(), lightVibrant = 0xFFF2B37A.toInt(), darkVibrant = 0xFF7A2E14.toInt(),
                muted = 0xFF5B6B88.toInt(), lightMuted = 0xFFB8C2D6.toInt(), darkMuted = 0xFF1A2236.toInt()),
            ThemePalettes.Swatches(0xFFEDE6DA.toInt(), vibrant = 0xFF2E8A66.toInt(), lightVibrant = 0xFF8FD3B4.toInt(), muted = 0xFF9B8E7A.toInt(),
                lightMuted = 0xFFD9CFC0.toInt(), darkMuted = 0xFF4A4036.toInt()),
            ThemePalettes.Swatches(0xFF777777.toInt()),
        )
        for (s in cases) {
            val choices = ThemePalettes.choices(s)
            assertTrue("палитр: ${choices.size}", choices.size >= 3)
            assertEquals(choices.size, choices.map { it.text to it.accent }.distinct().size)
            val bg = s.dominant or 0xFF000000.toInt()
            for (c in choices) {
                assertTrue("${c.title}: цифры не читаются", ColorUtils.calculateContrast(c.text, bg) >= 3.0)
                assertTrue("${c.title}: акцент не читается", ColorUtils.calculateContrast(c.accent, bg) >= 2.9)
            }
        }
    }

    @Test
    fun tappingPaletteChangesThemeColors() {
        AppearanceStore.set(app, Appearance(AppStyle.CLAY, AppMode.LIGHT))
        val repo = ThemeRepository.get(app)
        val bmp = SamplePhotos.draw(sea = true)
        repo.photoFile("colors.jpg").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        val theme = repo.save(
            Presets.all[0].copy(
                name = "Море",
                background = Background(BackgroundType.PHOTO, listOf(0xFF1B2A4A.toInt()), photoFile = "colors.jpg"),
                textColor = 0xFF123456.toInt(), accentColor = 0xFF654321.toInt(), wallClock = WallClock(depth = false),
            ),
        )
        repo.setDraft(theme)
        val vm = MainViewModel(app)
        compose.setContent { SyncThemeScreen(vm, Tab.WALLPAPER) }
        // Палитры считаются в фоне — ждём их.
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            Thread.sleep(200)
            compose.waitForIdle()
            if (vm.colorOptions.value != null) return@repeat
        }
        val options = vm.colorOptions.value!!
        assertTrue(options.choices.size >= 3)
        compose.onNodeWithText("Цвета").assertExists()
        assertTrue(compose.onAllNodesWithContentDescription("Палитра «Из фото»").fetchSemanticsNodes().isNotEmpty())
        val juicy = options.choices[1]
        compose.onAllNodesWithContentDescription("Палитра «${juicy.title}»")[0].performClick()
        compose.waitForIdle()
        assertEquals(juicy.text, vm.theme.value.textColor)
        assertEquals(juicy.accent, vm.theme.value.accentColor)
        assertNotEquals(0xFF654321.toInt(), vm.theme.value.accentColor)
        compose.onRoot().captureRoboImage(File(System.getProperty("previewsDir")!!, "screen_colors.png").absolutePath)
    }
}
