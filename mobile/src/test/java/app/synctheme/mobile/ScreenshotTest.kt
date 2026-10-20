package app.synctheme.mobile

import android.app.Application
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.install.InstallerViewModel
import app.synctheme.mobile.install.WatchInstallerScreen
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
@Config(qualifiers = "w412dp-h915dp-560dpi")
class ScreenshotTest {
    @Test
    fun mainScreen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ThemeRepository.get(app).setDraft(Presets.all[0])
        val vm = MainViewModel(app)
        // Превью рендерятся в фоне после debounce на главном потоке — даём им время.
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            Thread.sleep(500)
        }
        val out = File(System.getProperty("previewsDir")!!, "screen_main.png")
        captureRoboImage(out.absolutePath) { SyncThemeScreen(vm) }
    }

    @Test
    fun installerScreen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = InstallerViewModel(app)
        vm.setManual(true)
        vm.edit(host = "192.168.1.50", pairPort = "41234", connectPort = "37515", code = "482913")
        val out = File(System.getProperty("previewsDir")!!, "screen_installer.png")
        captureRoboImage(out.absolutePath) { WatchInstallerScreen(vm, Presets.all[0].accentColor, onClose = {}) }
    }
}
