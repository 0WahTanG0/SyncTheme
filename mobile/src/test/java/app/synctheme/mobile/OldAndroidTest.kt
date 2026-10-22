package app.synctheme.mobile

import android.app.Activity
import android.app.Application
import android.util.SizeF
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.util.Compat
import app.synctheme.mobile.widget.FolderMode
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SyncTheme на Android 9: превью, обои и виджеты всех видов собираются без новых API
 * (виджет — одной разметкой под свой размер, без цветов через ColorStateList).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class OldAndroidTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun worksOnAndroid9() {
        assertFalse(Compat.modernWidgets)
        val (w, h) = Compat.screen(app)
        assertTrue(w in 1..h)
        val theme = Presets.all[0]
        assertNotNull(ThemeRenderer.phonePreview(app, theme, 270, 585))
        assertNotNull(ThemeRenderer.watchPreview(app, theme, 200))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        for (kind in WidgetKind.entries) {
            for (style in kind.styles) {
                val views = WidgetRenderer.build(app, kind, WidgetConfig(style = style.id), theme, sizes = listOf(SizeF(180f, 180f)), widgetId = 1)
                assertNotNull("${kind.name}/${style.id}", views.apply(activity, FrameLayout(activity)))
            }
        }
        val folder = WidgetRenderer.build(app, WidgetKind.APPS, WidgetConfig(style = "glass", folder = FolderMode.ICON), theme, widgetId = 2)
        assertNotNull(folder.apply(activity, FrameLayout(activity)))
    }
}
