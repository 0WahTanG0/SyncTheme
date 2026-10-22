package app.synctheme.mobile

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Настройки» → «Устройства» → «Добавить устройство»: одно окно для часов, планшета и телефона. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-560dpi")
class AddDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }

    @Test
    fun opensOneDialogForAllDevices() {
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        ThemeRepository.get(app).setDraft(Presets.all[0])
        DeviceSettings.setWatch(app, false)
        LinkStore.reload()
        val vm = MainViewModel(app)
        compose.setContent { SyncThemeScreen(vm, Tab.MORE) }
        compose.onNodeWithText("Добавить устройство").performScrollTo()
        // В окне крутится индикатор поиска — время в тесте двигаем вручную, иначе Compose никогда не «простаивает».
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Добавить устройство").performClick()
        settle()
        // Ничего не нашлось — подсказки, как подключить часы и планшет.
        compose.onNodeWithText("Часы на Wear OS").assertExists()
        compose.onNodeWithText("Планшет или другой телефон").performClick()
        settle()
        compose.onNodeWithText("Адрес с его экрана").assertExists()
        compose.onNode(isDialog()).captureRoboImage(File(out, "screen_add_device.png").absolutePath)
    }

    private fun settle() {
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
