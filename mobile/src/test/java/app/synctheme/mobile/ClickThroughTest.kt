package app.synctheme.mobile

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.link.Peer
import app.synctheme.mobile.link.DeviceKind
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.install.InstallTarget
import app.synctheme.mobile.install.InstallerViewModel
import app.synctheme.mobile.install.WatchInstallerScreen
import app.synctheme.mobile.widget.FolderMode
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetConfigScreen
import app.synctheme.mobile.widget.WidgetKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Каждая кнопка каждой вкладки: нажимаем по одной (экран каждый раз заново) — ничего не падает.
 * Высокий экран и раскрытые разделы — чтобы на экране были все кнопки сразу.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h4000dp-420dpi")
class ClickThroughTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun settle() {
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Нажимает по одной все кнопки экрана [content] (перед каждой — экран заново). */
    private var round by mutableIntStateOf(0)
    private var screen by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)

    private fun clickEach(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        // Содержимое теста задаётся один раз; дальше меняется только экран.
        if (screen == null) {
            compose.mainClock.autoAdvance = false
            compose.setContent { key(round) { screen?.invoke() } }
        }
        screen = content
        round++
        settle()
        val total = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        val failures = mutableListOf<String>()
        val clicked = mutableListOf<String>()
        for (i in 0 until total) {
            round++
            settle()
            val nodes = compose.onAllNodes(hasClickAction())
            val list = nodes.fetchSemanticsNodes()
            if (i >= list.size) break
            val node = list[i]
            val label = (node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.children.firstNotNullOfOrNull { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } })
                .orEmpty()
            clicked += label
            try {
                nodes[i].performClick()
                settle()
            } catch (e: Throwable) {
                failures += "«$label»: $e"
            }
        }
        println("$name — нажато ${clicked.size}: ${clicked.joinToString(" | ")}")
        assertTrue("ошибки на $name:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Настройка каждого виджета: стили, размеры, цвета, приложения, линия папки. */
    @Test
    fun widgetSettings() {
        AppearanceStore.set(app, Appearance(AppStyle.EXPRESSIVE, AppMode.LIGHT))
        val theme = Presets.all[0]
        for (k in WidgetKind.entries) clickEachKind(k, theme)
    }

    private fun clickEachKind(k: WidgetKind, theme: app.synctheme.core.Theme) {
        val config = if (k == WidgetKind.APPS) WidgetConfig(style = k.defaultStyle, folder = FolderMode.ICON, apps = listOf("a/.A", "b/.B")) else WidgetConfig(style = k.defaultStyle)
        clickEach("виджет $k") { AppTheme(theme.accentColor) { WidgetConfigScreen(k, config, theme, onCancel = {}) {} } }
    }

    /** Установка на часы и «Добавить устройство». */
    @Test
    fun installerAndDevices() {
        AppearanceStore.set(app, Appearance(AppStyle.NEON, AppMode.DARK))
        val installer = InstallerViewModel(app)
        clickEach("установка на часы") { WatchInstallerScreen(installer, Presets.all[0].accentColor, onClose = {}) }
        val tv = InstallerViewModel(app).apply { setTarget(InstallTarget.TV) }
        clickEach("установка на телевизор") { WatchInstallerScreen(tv, Presets.all[0].accentColor, onClose = {}) }
    }

    private fun clickAll(tab: Tab) {
        AppearanceStore.set(app, Appearance(AppStyle.GLASS, AppMode.DARK))
        ThemeRepository.get(app).setDraft(Presets.all[1])
        DeviceSettings.setWatch(app, true)
        LinkStore.reload()
        LinkStore.savePeer(app, Peer("tab1", "Galaxy Tab S9 FE", DeviceKind.TABLET, ByteArray(32), host = "127.0.0.1", port = 1))
        LinkStore.savePeer(app, Peer("tv1", "Tuvio 4K TV", DeviceKind.TV, ByteArray(32), host = "127.0.0.1", port = 1))
        val vm = MainViewModel(app)
        var round by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent { key(round) { SyncThemeScreen(vm, tab, expandSections = true) } }
        settle()
        val total = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue("на вкладке $tab есть кнопки", total > 3)
        val failures = mutableListOf<String>()
        val clicked = mutableListOf<String>()
        for (i in 0 until total) {
            round++
            settle()
            val nodes = compose.onAllNodes(hasClickAction())
            val list = nodes.fetchSemanticsNodes()
            if (i >= list.size) break
            val node = list[i]
            val label = (node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.children.firstNotNullOfOrNull { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } })
                .orEmpty()
            clicked += label
            try {
                nodes[i].performClick()
                settle()
            } catch (e: Throwable) {
                failures += "«$label»: $e"
            }
        }
        println("$tab — нажато ${clicked.size}: ${clicked.joinToString(" | ")}")
        assertTrue("ошибки на $tab:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun wallpaper() = clickAll(Tab.WALLPAPER)
    @Test fun themes() = clickAll(Tab.THEMES)
    @Test fun widgets() = clickAll(Tab.WIDGETS)
    @Test fun watch() = clickAll(Tab.WATCH)
    @Test fun settings() = clickAll(Tab.MORE)
}
