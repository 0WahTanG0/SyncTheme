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
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetConfigScreen
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
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
    private fun screen(name: String, tab: Tab, prepare: (Application) -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ThemeRepository.get(app).setDraft(Presets.all[0])
        prepare(app)
        val vm = MainViewModel(app)
        // Превью рендерятся в фоне после debounce на главном потоке — даём им время.
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            Thread.sleep(500)
        }
        val out = File(System.getProperty("previewsDir")!!, "$name.png")
        captureRoboImage(out.absolutePath) { SyncThemeScreen(vm, tab) }
    }

    @Test
    fun mainScreen() = screen("screen_main", Tab.THEME)

    @Test
    fun widgetsTab() = screen("screen_widgets", Tab.WIDGETS)

    @Test
    fun lockTab() = screen("screen_lock", Tab.LOCK) { app ->
        val t = Presets.all[0]
        ThemeRepository.get(app).setDraft(t.copy(lockBackground = Presets.all[1].background, lockDim = 0.2f))
    }

    @Test
    fun watchTab() = screen("screen_watch", Tab.WATCH) { app ->
        val t = Presets.all[0]
        ThemeRepository.get(app).setDraft(t.copy(watch = t.followPhone().copy(clockStyle = ClockStyle.ANALOG, dialStyle = DialStyle.NUMBERS)))
    }

    @Test
    fun widgetConfigScreen() {
        val theme = Presets.all[0]
        val out = File(System.getProperty("previewsDir")!!, "screen_widget_config.png")
        captureRoboImage(out.absolutePath) {
            AppTheme(theme.accentColor) {
                WidgetConfigScreen(WidgetKind.CLOCK, WidgetConfig(style = "side", opacity = 0.5f), theme, onCancel = {}) {}
            }
        }
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
