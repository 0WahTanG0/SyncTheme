package app.synctheme.mobile

import android.app.Application
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import app.synctheme.R
import app.synctheme.core.Presets
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.devices.MyDevices
import app.synctheme.mobile.devices.Watch
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.link.DeviceKind
import app.synctheme.mobile.link.LinkClient
import app.synctheme.mobile.link.LinkProtocol
import app.synctheme.mobile.link.LinkReceiver
import app.synctheme.mobile.link.Peer
import app.synctheme.mobile.link.LinkPush
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.install.InstallTarget
import app.synctheme.mobile.install.InstallerViewModel
import app.synctheme.mobile.install.WatchInstallerScreen
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.CompatScreen
import app.synctheme.mobile.ui.SubjectEditorScreen
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import app.synctheme.mobile.tv.TvScreen
import androidx.compose.ui.graphics.asImageBitmap
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetConfigScreen
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ThemeMode
import app.synctheme.core.ClockStyle
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.core.DialStyle
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

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
        withVm: (MainViewModel) -> Unit = {},
        prepare: (Application) -> Unit = {},
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, appearance)
        ThemeRepository.get(app).setDraft(Presets.all[0])
        // По умолчанию — только телефон; вкладка «Часы» есть, когда часы включены.
        DeviceSettings.setWatch(app, tab == Tab.WATCH)
        LinkStore.reload()
        prepare(app)
        val vm = MainViewModel(app)
        withVm(vm)
        if (tab == Tab.WATCH) vm.wantStylePreviews()
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

    /** Одна тема с фото: как есть, светлая и тёмная версии. */
    @Test
    fun themeModes() {
        for (mode in listOf(ThemeMode.ORIGINAL, ThemeMode.LIGHT, ThemeMode.DARK)) {
            screen("screen_mode_${mode.name.lowercase()}", Tab.WALLPAPER, Appearance(AppStyle.GLASS, AppMode.DARK), expand = true) { app ->
                val repo = ThemeRepository.get(app)
                val w = 540
                val h = 1170
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                // Вечернее небо и тёмные холмы — как обычное фото на обоях.
                paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF1B2A4A.toInt(), 0xFF7A4B7E.toInt(), 0xFFF29E6D.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
                paint.color = 0xFFFFE2B0.toInt()
                c.drawCircle(w * 0.62f, h * 0.62f, w * 0.12f, paint)
                paint.color = 0xFF221A2E.toInt()
                c.drawPath(android.graphics.Path().apply { moveTo(0f, h * 0.7f); quadTo(w * 0.3f, h * 0.6f, w * 0.55f, h * 0.72f); quadTo(w * 0.8f, h * 0.66f, w.toFloat(), h * 0.74f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }, paint)
                repo.photoFile("modes.jpg").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                repo.setDraft(
                    Presets.all[0].copy(
                        background = Background(BackgroundType.PHOTO, listOf(0xFF1B2A4A.toInt(), 0xFFF29E6D.toInt()), photoFile = "modes.jpg"),
                        textColor = 0xFFFFFFFF.toInt(), accentColor = 0xFFF29E6D.toInt(), dim = 0.15f, mode = mode,
                    ),
                )
            }
        }
    }

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

    /** «Обои» целиком: часы на обоях с эффектом глубины. */
    @Test
    @Config(qualifiers = "w412dp-h3000dp-560dpi")
    fun wallpaperFeatures() = screen("screen_wallpaper_features", Tab.WALLPAPER, Appearance(AppStyle.NEON, AppMode.DARK), expand = true) { app ->
        ThemeRepository.get(app).setDraft(Presets.all[1].copy(wallClock = WallClock(WallClockLayout.STACKED, depth = false)))
    }

    /** «Часы» целиком: глубина и Always-On. */
    @Test
    @Config(qualifiers = "w412dp-h3000dp-560dpi")
    fun watchFeatures() = screen("screen_watch_features", Tab.WATCH, Appearance(AppStyle.EXPRESSIVE, AppMode.DARK), expand = true) { app ->
        ThemeRepository.get(app).setDraft(Presets.all[2].copy(clockStyle = ClockStyle.LIQUID))
    }

    /** Экран блокировки: свои часы SyncTheme за объектом фото — одинаково на любом телефоне. */
    @Test
    @Config(qualifiers = "w412dp-h2000dp-560dpi")
    fun lockDepth() = screen("screen_lock_depth", Tab.WALLPAPER, Appearance(AppStyle.GLASS, AppMode.DARK), lock = true, expand = true) { app ->
        val t = Presets.all[1]
        // Фото нет — рисуется градиент, но раздел ведёт себя как с фото.
        ThemeRepository.get(app).setDraft(t.copy(
            background = t.background.copy(type = BackgroundType.PHOTO, photoFile = "none.jpg"),
            wallClock = WallClock(depth = true, onLock = true),
        ))
    }

    /** «Ещё»: мои устройства и инструкция. */
    @Test
    @Config(qualifiers = "w412dp-h2200dp-560dpi")
    fun moreFeatures() = screen("screen_more_features", Tab.MORE, Appearance(AppStyle.GLASS, AppMode.LIGHT))

    /** «Ещё» с включёнными часами. */
    @Test
    @Config(qualifiers = "w412dp-h2400dp-560dpi")
    fun moreWithWatch() = screen("screen_more_watch", Tab.MORE, Appearance(AppStyle.EXPRESSIVE, AppMode.DARK)) { app ->
        DeviceSettings.setWatch(app, true)
    }

    /** Планшет: ждёт тему с телефона. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletReceiver() = screen("screen_tablet", Tab.MORE, Appearance(AppStyle.GLASS, AppMode.DARK)) { app ->
        LinkStore.setReceiving(app, true)
    }

    /** Телефон просит подключиться: на планшете — «Подключить / Отклонить» и код сверки. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun linkRequestOnTablet() {
        try {
            screen("screen_link_request", Tab.MORE, Appearance(AppStyle.GLASS, AppMode.DARK)) { app ->
                LinkStore.setReceiving(app, true)
                LinkReceiver.start(app)
                val port = LinkReceiver.port
                thread(isDaemon = true) { runCatching { runBlocking { LinkClient.link(app, "127.0.0.1", port, null) { _, _ -> } } } }
                val deadline = System.currentTimeMillis() + 10_000
                while (LinkReceiver.state.value.request == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
            }
        } finally {
            LinkReceiver.answer(false)
            LinkReceiver.stop(ApplicationProvider.getApplicationContext())
        }
    }

    /** Телефон подключает телевизор: тот же код, что на экране телевизора, и ожидание «Подключить». */
    @Test
    fun linkingDialog() {
        val server = fakeDevice("Tuvio 4K TV", DeviceKind.TV)
        try {
            screen("screen_linking", Tab.MORE, Appearance(AppStyle.EXPRESSIVE, AppMode.DARK), withVm = { vm ->
                vm.linkDevice("127.0.0.1", server.localPort, "Tuvio 4K TV", null)
            })
        } finally {
            server.close()
        }
    }

    /** Подключены часы, планшет и телевизор — все рядом в превью, состояние — в шапке. */
    @Test
    @Config(qualifiers = "w412dp-h1200dp-560dpi")
    fun allDevices() = screen("screen_devices", Tab.WIDGETS, Appearance(AppStyle.GLASS, AppMode.DARK)) { app ->
        DeviceSettings.setWatch(app, true)
        LinkStore.savePeer(app, Peer("tab1", "Galaxy Tab S9 FE", DeviceKind.TABLET, ByteArray(32), host = "192.168.1.20", port = 47231))
        LinkStore.savePeer(app, Peer("tv1", "Tuvio 4K TV", DeviceKind.TV, ByteArray(32), host = "192.168.1.40", port = 47231))
        LinkStore.setPush(app, "tab1", LinkPush(true, "Тема «Аврора» на месте", System.currentTimeMillis()))
        LinkStore.setPush(app, "tv1", LinkPush(true, "Тема «Аврора» на месте", System.currentTimeMillis()))
    }

    /** «Ещё» → «Устройства»: часы, планшет и телевизор, один пока не ответил. */
    @Test
    @Config(qualifiers = "w412dp-h2000dp-560dpi")
    fun devicesList() = screen("screen_devices_list", Tab.MORE, Appearance(AppStyle.EXPRESSIVE, AppMode.LIGHT)) { app ->
        DeviceSettings.setWatch(app, true)
        LinkStore.savePeer(app, Peer("tab1", "Galaxy Tab S9 FE", DeviceKind.TABLET, ByteArray(32), host = "192.168.1.20", port = 47231))
        LinkStore.savePeer(app, Peer("tv1", "Tuvio 4K TV", DeviceKind.TV, ByteArray(32), host = "192.168.1.40", port = 47231))
        LinkStore.setPush(app, "tab1", LinkPush(false, "Не ответил — тема уйдёт, когда он появится в сети", System.currentTimeMillis(), pending = true))
        LinkStore.setPush(app, "tv1", LinkPush(true, "Тема «Аврора» на месте", System.currentTimeMillis()))
    }

    /** Телевизор: экран SyncTheme для пульта и сама заставка в 1080p. */
    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun tvScreens() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        ThemeRepository.get(app).setApplied(Presets.all[0])
        LinkStore.setReceiving(app, true)
        LinkStore.setPhone(app, Peer("phone", "Galaxy S25+", DeviceKind.PHONE, ByteArray(32)))
        LinkReceiver.start(app)
        val dir = System.getProperty("previewsDir")!!
        val preview = ThemeRenderer.tvFrame(app, Presets.all[0], 960, 540).asImageBitmap()
        try {
            captureRoboImage(File(dir, "screen_tv.png").absolutePath) { TvScreen(preview) }
        } finally {
            LinkReceiver.stop(app)
        }
        val frame = ThemeRenderer.tvFrame(app, Presets.all[0], 1920, 1080)
        File(dir, "tv_screensaver.png").outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Устройство, которое ответило на «подключиться» своим ключом и ждёт нажатия «Подключить» —
     * чтобы снять экран телефона в это время.
     */
    private fun fakeDevice(name: String, kind: DeviceKind): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream().buffered())
                    val out = DataOutputStream(socket.getOutputStream().buffered())
                    LinkProtocol.read(input)
                    val own = LinkProtocol.keyPair().public.encoded
                    LinkProtocol.write(out, LinkProtocol.KEY, ByteArrayOutputStream().also { b ->
                        DataOutputStream(b).apply {
                            writeInt(own.size)
                            write(own)
                            writeUTF(name)
                            writeUTF(kind.name)
                            writeUTF("fake-$name")
                        }
                    }.toByteArray())
                    // Ждём, пока тест не закроет сервер.
                    runCatching { input.read() }
                }
            }
        }
        return server
    }

    /** Телефон боком: меню слева, превью и «Применить» — рядом с настройками. */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land-560dpi")
    fun landscapePhone() = screen("screen_land_phone", Tab.WALLPAPER, Appearance(AppStyle.GLASS, AppMode.DARK)) { app ->
        DeviceSettings.setWatch(app, true)
    }

    /** Планшет в альбомной ориентации. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun landscapeTablet() = screen("screen_land_tablet", Tab.WIDGETS, Appearance(AppStyle.NEON, AppMode.DARK))

    /** Планшет в портрете: колонка по центру. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun portraitTablet() = screen("screen_tablet_portrait", Tab.WALLPAPER, Appearance(AppStyle.EXPRESSIVE, AppMode.LIGHT))

    @Test
    fun widgetConfigScreen() {
        AppearanceStore.set(ApplicationProvider.getApplicationContext(), Appearance(AppStyle.GLASS, AppMode.DARK))
        val theme = Presets.all[0]
        val out = File(System.getProperty("previewsDir")!!, "screen_widget_config.png")
        captureRoboImage(out.absolutePath) {
            AppTheme(theme.accentColor) {
                WidgetConfigScreen(WidgetKind.WEATHER, WidgetConfig(style = "paper", opacity = 0.5f), theme, onCancel = {}) {}
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

    /** Часы нашлись по Bluetooth: модель известна — сразу шаги для неё, без списка. */
    @Test
    @Config(qualifiers = "w412dp-h1600dp-560dpi")
    fun installerDetectedWatch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        MyDevices.setWatch(app, Watch.GALAXY7)
        val vm = InstallerViewModel(app)
        // Без поиска в сети: его индикатор крутится бесконечно, и снимок не дождался бы покоя.
        vm.setManual(true)
        val out = File(System.getProperty("previewsDir")!!, "screen_installer_detected.png")
        captureRoboImage(out.absolutePath) { WatchInstallerScreen(vm, Presets.all[0].accentColor, onClose = {}) }
    }

    /** Проверка совместимости при первом запуске. */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-560dpi")
    fun compatScreen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        captureRoboImage(File(System.getProperty("previewsDir")!!, "screen_compat.png").absolutePath) {
            AppTheme(Presets.all[0].accentColor) { CompatScreen {} }
        }
    }

    /** Без «Проф. версии» — только телефон: в «Настройках» нет устройств, на главном экране — один телефон. */
    @Test
    fun phoneOnly() = screen("screen_more_phone_only", Tab.MORE, Appearance(AppStyle.GLASS, AppMode.DARK)) { app ->
        DeviceSettings.setPro(app, false)
    }

    /** Редактор объекта для глубины: обводка пальцем. */
    @Test
    fun subjectEditor() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        val photo = "subject_photo.jpg"
        val bmp = android.graphics.Bitmap.createBitmap(600, 1000, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(0xFF2B6CB0.toInt())
            drawCircle(300f, 600f, 200f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDD6B20.toInt() })
        }
        ThemeRepository.get(app).photoFile(photo).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        val dir = System.getProperty("previewsDir")!!
        captureRoboImage(File(dir, "screen_subject_editor.png").absolutePath) {
            AppTheme(Presets.all[0].accentColor) { SubjectEditorScreen(photo) {} }
        }
    }

    /** Установка на телевизор: шаги для Google TV, адрес введён вручную. */
    @Test
    @Config(qualifiers = "w412dp-h2000dp-560dpi")
    fun installerTv() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        val vm = InstallerViewModel(app)
        vm.setTarget(InstallTarget.TV)
        vm.setManual(true)
        vm.edit(host = "192.168.1.40")
        val out = File(System.getProperty("previewsDir")!!, "screen_installer_tv.png")
        captureRoboImage(out.absolutePath) { WatchInstallerScreen(vm, Presets.all[0].accentColor, onClose = {}) }
    }

    /** Установка на часы и настройка виджета — телефон боком. */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land-560dpi")
    fun landscapeDialogs() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        val vm = InstallerViewModel(app)
        val dir = System.getProperty("previewsDir")!!
        captureRoboImage(File(dir, "screen_installer_land.png").absolutePath) { WatchInstallerScreen(vm, Presets.all[0].accentColor, onClose = {}) }
        val theme = Presets.all[0]
        captureRoboImage(File(dir, "screen_widget_config_land.png").absolutePath) {
            AppTheme(theme.accentColor) {
                WidgetConfigScreen(WidgetKind.CLOCK, WidgetConfig(style = "glass"), theme, onCancel = {}) {}
            }
        }
    }
}
