package app.synctheme.mobile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.SyncThemeScreen
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Скриншот главного экрана (Roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class ScreenshotTest {
    @Test
    fun mainScreen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ThemeRepository.get(app).setDraft(Presets.all[0])
        val vm = MainViewModel(app)
        Thread.sleep(1500)
        val out = File(System.getProperty("previewsDir")!!, "screen_main.png")
        captureRoboImage(out.absolutePath) { SyncThemeScreen(vm) }
    }
}
