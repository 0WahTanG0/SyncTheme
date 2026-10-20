package app.synctheme.mobile

import android.app.Application
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import app.synctheme.R
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.install.InstallerViewModel
import app.synctheme.mobile.install.WatchInstallerScreen
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetConfigScreen
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.core.ClockStyle
import app.synctheme.core.LiveData
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.mobile.ui.MatchColorsDialog
import app.synctheme.mobile.ui.ThemeColors
import androidx.compose.ui.graphics.asImageBitmap
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
    private fun screen(
        name: String,
        tab: Tab,
        appearance: Appearance = Appearance(AppStyle.GLASS, AppMode.DARK),
        lock: Boolean = false,
        expand: Boolean = false,
        prepare: (Application) -> Unit = {},
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, appearance)
        ThemeRepository.get(app).setDraft(Presets.all[0])
        prepare(app)
        val vm = MainViewModel(app)
        if (tab == Tab.WATCH) vm.wantStylePreviews()
        if (expand) vm.wantMoodPreviews()
        // Превью рендерятся в фоне после debounce на главном потоке — даём им время.
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            Thread.sleep(500)
        }
        val out = File(System.getProperty("previewsDir")!!, "$name.png")
        captureRoboImage(out.absolutePath) { SyncThemeScreen(vm, tab, initialLock = lock, expandSections = expand) }
    }

    @Test
    fun mainScreen() = screen("screen_main", Tab.WALLPAPER)

    @Test
    fun widgetsTab() = screen("screen_widgets", Tab.WIDGETS)

    @Test
    fun moreTab() = screen("screen_more", Tab.MORE)

    /** Каждое оформление в светлом и тёмном режиме. */
    @Test
    fun appearances() {
        for (style in AppStyle.entries) for (mode in listOf(AppMode.DARK, AppMode.LIGHT)) {
            screen("look_${style.name.lowercase()}_${mode.name.lowercase()}", Tab.WALLPAPER, Appearance(style, mode))
        }
    }

    @Test
    fun lockTab() = screen("screen_lock", Tab.WALLPAPER, Appearance(AppStyle.NEON, AppMode.DARK), lock = true) { app ->
        val t = Presets.all[0]
        ThemeRepository.get(app).setDraft(t.copy(lockBackground = Presets.all[1].background, lockDim = 0.2f))
    }

    @Test
    fun watchTab() = screen("screen_watch", Tab.WATCH, Appearance(AppStyle.EXPRESSIVE, AppMode.LIGHT)) { app ->
        val t = Presets.all[0]
        ThemeRepository.get(app).setDraft(t.copy(watch = t.followPhone().copy(clockStyle = ClockStyle.ANALOG, dialStyle = DialStyle.NUMBERS)))
    }

    /** Вкладка «Часы» целиком (высокий экран): стиль, шрифт цифр и проверка времени. */
    @Test
    @Config(qualifiers = "w412dp-h2400dp-560dpi")
    fun watchTabLong() = screen("screen_watch_long", Tab.WATCH, Appearance(AppStyle.GLASS, AppMode.DARK)) { app ->
        val t = Presets.all[0]
        ThemeRepository.get(app).setDraft(t.copy(watch = t.followPhone().copy(clockStyle = ClockStyle.DRUM)))
    }

    /** «Обои» целиком: часы на обоях, «Хамелеон», «В тон вещи». */
    @Test
    @Config(qualifiers = "w412dp-h3000dp-560dpi")
    fun wallpaperFeatures() = screen("screen_wallpaper_features", Tab.WALLPAPER, Appearance(AppStyle.NEON, AppMode.DARK), expand = true) { app ->
        app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putString("chameleon", "BOTH").apply()
        ThemeRepository.get(app).setDraft(Presets.all[1].copy(wallClock = WallClock(WallClockLayout.STACKED, depth = false)))
    }

    /** «Часы» с живыми данными и глубиной. */
    @Test
    @Config(qualifiers = "w412dp-h3000dp-560dpi")
    fun watchFeatures() = screen("screen_watch_features", Tab.WATCH, Appearance(AppStyle.EXPRESSIVE, AppMode.DARK), expand = true) { app ->
        val t = Presets.all[2]
        ThemeRepository.get(app).setDraft(
            t.copy(clockStyle = ClockStyle.LIQUID, watchLive = LiveData(steps = true, pulse = true, battery = true)),
        )
    }

    /** «Ещё»: другие устройства и картинки для Apple. */
    @Test
    @Config(qualifiers = "w412dp-h2200dp-560dpi")
    fun moreFeatures() = screen("screen_more_features", Tab.MORE, Appearance(AppStyle.GLASS, AppMode.LIGHT))

    /** Диалог «В тон вещи» на снимке-заглушке. */
    @Test
    fun matchDialog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        val bmp = android.graphics.Bitmap.createBitmap(600, 800, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(0xFFE8E2D6.toInt())
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF1E5631.toInt()
        c.drawRoundRect(200f, 0f, 400f, 800f, 40f, 40f, p)
        p.color = 0xFF222222.toInt()
        c.drawCircle(300f, 400f, 170f, p)
        p.color = 0xFF3A6B4A.toInt()
        c.drawCircle(300f, 400f, 140f, p)
        val swatches = ThemeColors.swatches(bmp)
        val photo = MainViewModel.MatchPhoto(bmp, bmp.asImageBitmap(), swatches)
        val out = File(System.getProperty("previewsDir")!!, "screen_match.png")
        captureRoboImage(out.absolutePath) {
            AppTheme(Presets.all[0].accentColor) { MatchColorsDialog(photo, {}, { _, _ -> }) }
        }
    }

    @Test
    fun widgetConfigScreen() {
        AppearanceStore.set(ApplicationProvider.getApplicationContext(), Appearance(AppStyle.GLASS, AppMode.DARK))
        val theme = Presets.all[0]
        val out = File(System.getProperty("previewsDir")!!, "screen_widget_config.png")
        captureRoboImage(out.absolutePath) {
            AppTheme(theme.accentColor) {
                WidgetConfigScreen(WidgetKind.WEATHER, WidgetConfig(style = "blob", opacity = 0.5f), theme, onCancel = {}) {}
            }
        }
    }

    /** Иконка приложения: фон + передний слой в маске-«сквиркле» и одноцветный слой. */
    @Test
    fun launcherIcon() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val size = 432
        val out = android.graphics.Bitmap.createBitmap(size * 2 + 40, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val mask = android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.3f, size * 0.3f, android.graphics.Path.Direction.CW)
        }
        // Слои 108dp, видимая часть — центральные 72dp.
        fun layer(res: Int, tint: Int? = null) = app.getDrawable(res)!!.apply {
            val pad = size / 4
            setBounds(-pad, -pad, size + pad, size + pad)
            tint?.let { setTint(it) }
        }
        canvas.save(); canvas.clipPath(mask)
        layer(R.drawable.ic_launcher_background).draw(canvas)
        layer(R.drawable.ic_launcher_foreground).draw(canvas)
        canvas.restore()
        canvas.translate(size + 40f, 0f)
        canvas.save(); canvas.clipPath(mask)
        canvas.drawColor(0xFF2B2F3A.toInt())
        layer(R.drawable.ic_launcher_monochrome, 0xFFB9C6FF.toInt()).draw(canvas)
        canvas.restore()
        File(System.getProperty("previewsDir")!!, "launcher_icon.png").outputStream().use {
            out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun installerScreen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        val vm = InstallerViewModel(app)
        vm.setManual(true)
        vm.edit(host = "192.168.1.50", pairPort = "41234", connectPort = "37515", code = "482913")
        val out = File(System.getProperty("previewsDir")!!, "screen_installer.png")
        captureRoboImage(out.absolutePath) { WatchInstallerScreen(vm, Presets.all[0].accentColor, onClose = {}) }
    }
}
