package app.synctheme.mobile

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.MainViewModel
import app.synctheme.mobile.ui.SyncThemeScreen
import app.synctheme.mobile.ui.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * «Настройки» — шестерёнкой в шапке, а не в нижнем меню; убранных блоков на экранах нет:
 * «Яркость» и «Все настройки темы» на главном, «Город для погоды» в «Виджетах», плашка «Часы не найдены».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2000dp-420dpi")
class SettingsGearTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun open(tab: Tab): MainViewModel {
        AppearanceStore.set(app, Appearance(AppStyle.CLAY, AppMode.LIGHT))
        ThemeRepository.get(app).setDraft(Presets.all[0])
        val vm = MainViewModel(app)
        compose.setContent { SyncThemeScreen(vm, tab) }
        compose.waitForIdle()
        return vm
    }

    @Test
    fun gearOpensSettingsAndBackReturns() {
        open(Tab.WIDGETS)
        compose.onAllNodesWithText("Настройки").assertCountEquals(0)
        compose.onAllNodesWithText("Часы не найдены").assertCountEquals(0)
        compose.onAllNodesWithText("Город для погоды").assertCountEquals(0)
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        // Шапка раздела — «Настройки» с кнопкой «Назад»; «Иконок приложений» в нём больше нет.
        compose.onNodeWithText("Настройки").assertExists()
        compose.onAllNodesWithText("Иконки приложений").assertCountEquals(0)
        compose.onAllNodesWithText("Получать тему с другого телефона").assertCountEquals(0)
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Добавить виджет").assertExists()
    }

    @Test
    fun homeHasNoThemeSettings() {
        open(Tab.WALLPAPER)
        compose.onAllNodesWithText("Яркость").assertCountEquals(0)
        compose.onAllNodesWithText("Все настройки темы").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Настройки").assertCountEquals(1)
    }
}
