package app.synctheme.mobile

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.icons.IconStyle
import app.synctheme.mobile.icons.IconStyler
import app.synctheme.mobile.ui.AppMode
import app.synctheme.mobile.ui.AppStyle
import app.synctheme.mobile.ui.Appearance
import app.synctheme.mobile.ui.AppearanceStore
import app.synctheme.mobile.ui.IconsScreen
import app.synctheme.mobile.widget.AppCatalog
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Значки приложений в стиле оформления: рисунок из монохромного слоя, силуэт или медальон. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-560dpi")
class IconsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val out = File(System.getProperty("previewsDir")!!).also { it.mkdirs() }

    private fun Bitmap.save(name: String) = File(out, name).outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun layer(px: Int, draw: Canvas.(Float) -> Unit): Drawable {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(bmp).draw(px / 108f)
        return BitmapDrawable(app.resources, bmp)
    }

    /** Значки «как у настоящих приложений»: цветной фон и белый рисунок, у части — монохромный слой. */
    private fun fakeIcons(): List<Triple<String, String, Drawable>> {
        val px = 324
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        fun phone(c: Canvas, u: Float, p: Paint) {
            val path = Path().apply {
                moveTo(40 * u, 36 * u); lineTo(50 * u, 34 * u); lineTo(56 * u, 46 * u); lineTo(50 * u, 52 * u)
                quadTo(56 * u, 62 * u, 64 * u, 66 * u); lineTo(70 * u, 60 * u); lineTo(80 * u, 66 * u); lineTo(78 * u, 76 * u)
                quadTo(50 * u, 80 * u, 36 * u, 44 * u); close()
            }
            c.drawPath(path, p)
        }
        fun bubble(c: Canvas, u: Float, p: Paint) {
            c.drawRoundRect(32 * u, 36 * u, 76 * u, 68 * u, 14 * u, 14 * u, p)
            c.drawPath(Path().apply { moveTo(40 * u, 64 * u); lineTo(36 * u, 76 * u); lineTo(52 * u, 66 * u); close() }, p)
        }
        fun camera(c: Canvas, u: Float, p: Paint) {
            c.drawRoundRect(30 * u, 40 * u, 78 * u, 72 * u, 8 * u, 8 * u, p)
            c.drawRect(44 * u, 34 * u, 62 * u, 42 * u, p)
            c.drawCircle(54 * u, 56 * u, 9 * u, Paint(p).apply { color = Color.TRANSPARENT; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
        }
        fun note(c: Canvas, u: Float, p: Paint) {
            c.drawCircle(46 * u, 68 * u, 8 * u, p)
            c.drawRect(51 * u, 36 * u, 55 * u, 68 * u, p)
            c.drawRect(51 * u, 36 * u, 70 * u, 42 * u, p)
        }
        fun mail(c: Canvas, u: Float, p: Paint) {
            c.drawRoundRect(30 * u, 38 * u, 78 * u, 70 * u, 6 * u, 6 * u, p)
            val v = Paint(p).apply { color = 0xFFE5534B.toInt(); style = Paint.Style.STROKE; strokeWidth = 4 * u }
            c.drawPath(Path().apply { moveTo(32 * u, 41 * u); lineTo(54 * u, 58 * u); lineTo(76 * u, 41 * u) }, v)
        }
        fun icon(bg: Int, glyph: (Canvas, Float, Paint) -> Unit, mono: Boolean): Drawable {
            val fg = layer(px) { u -> glyph(this, u, white) }
            val back = ColorDrawable(bg)
            return if (mono) AdaptiveIconDrawable(back, fg, layer(px) { u -> glyph(this, u, black) }) else AdaptiveIconDrawable(back, fg)
        }
        // Значок во всю плитку (как у браузера) — станет медальоном.
        val full = AdaptiveIconDrawable(ColorDrawable(0xFFFFFFFF.toInt()), layer(px) { u ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            listOf(0xFFEA4335, 0xFFFBBC05, 0xFF34A853).forEachIndexed { i, c ->
                p.color = c.toInt(); drawArc(22 * u, 22 * u, 86 * u, 86 * u, -90f + i * 120f, 120f, true, p)
            }
            p.color = Color.WHITE; drawCircle(54 * u, 54 * u, 15 * u, p)
            p.color = 0xFF4285F4.toInt(); drawCircle(54 * u, 54 * u, 12 * u, p)
        })
        return listOf(
            Triple("com.example.phone", "Телефон", icon(0xFF34A853.toInt(), ::phone, mono = true)),
            Triple("com.example.chat", "Сообщения", icon(0xFF1A73E8.toInt(), ::bubble, mono = true)),
            Triple("com.example.camera", "Камера", icon(0xFF5F6368.toInt(), ::camera, mono = false)),
            Triple("com.example.browser", "Браузер", full),
            Triple("com.example.music", "Музыка", icon(0xFFE91E63.toInt(), ::note, mono = true)),
            Triple("com.example.mail", "Почта", icon(0xFFFFFFFF.toInt(), ::mail, mono = false)),
            Triple("com.example.maps", "Карты", icon(0xFF0F9D58.toInt(), { c, u, p -> c.drawCircle(54 * u, 48 * u, 14 * u, p); c.drawPath(Path().apply { moveTo(42 * u, 54 * u); lineTo(54 * u, 76 * u); lineTo(66 * u, 54 * u); close() }, p) }, mono = false)),
            Triple("com.example.notes", "Заметки", icon(0xFFFBBC04.toInt(), { c, u, p -> repeat(3) { i -> c.drawRoundRect(36 * u, (40 + i * 11) * u, 72 * u, (45 + i * 11) * u, 3 * u, 3 * u, p) } }, mono = true)),
        )
    }

    private fun installFakeApps(): List<String> {
        val pm = shadowOf(app.packageManager)
        return fakeIcons().map { (pkg, label, icon) ->
            val cn = ComponentName(pkg, "$pkg.Main")
            pm.addActivityIfNotPresent(cn).apply { nonLocalizedLabel = label; applicationInfo.nonLocalizedLabel = label }
            pm.addIntentFilterForActivity(cn, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            pm.addActivityIcon(cn, icon)
            cn.flattenToString()
        }
    }

    @Test
    fun everyStyleHasItsOwnPlate() {
        val icon = fakeIcons()[0].third
        val faces = IconStyle.entries.map { style ->
            val bmp = IconStyler.render(icon, style, 0xFFF29E6D.toInt(), 216)
            assertEquals(216, bmp.width)
            bmp.getPixel(20, 108)
        }
        assertEquals(IconStyle.entries.size, faces.distinct().size)
    }

    /** Лист: строки — стили, столбцы — приложения; так значки выглядят на рабочем столе. */
    @Test
    fun iconSheet() {
        val apps = installFakeApps()
        assertEquals(apps.size, AppCatalog.launchable(app).size)
        val size = 150
        val gap = 30
        val sheet = Bitmap.createBitmap(gap + apps.size * (size + gap), gap + IconStyle.entries.size * (size + gap), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF6E8FB8.toInt())
        IconStyle.entries.forEachIndexed { r, style ->
            apps.forEachIndexed { i, component ->
                val bmp = IconStyler.preview(app, component, style, 0xFFF29E6D.toInt(), size)
                assertNotNull(bmp)
                c.drawBitmap(bmp!!, (gap + i * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        sheet.save("icons_sheet.png")
        // Разные стили — разные картинки.
        assertNotEquals(sheet.getPixel(gap + 5, gap + size / 2), sheet.getPixel(gap + 5, gap + size + gap + size / 2))
    }

    @get:org.junit.Rule
    val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test
    fun iconsScreen() {
        installFakeApps()
        AppearanceStore.set(app, Appearance(AppStyle.CLAY, AppMode.LIGHT))
        ThemeRepository.get(app).setDraft(Presets.all[0])
        compose.setContent { IconsScreen(Presets.all[0].accentColor) {} }
        // Значки рисуются в фоне — ждём их, затем выбираем «Частые».
        repeat(6) {
            compose.mainClock.advanceTimeBy(400)
            Thread.sleep(250)
            compose.waitForIdle()
        }
        compose.onNodeWithText("Частые").performClick()
        repeat(3) {
            compose.mainClock.advanceTimeBy(400)
            Thread.sleep(200)
            compose.waitForIdle()
        }
        compose.onNodeWithText("Поставить на рабочий стол · 4").assertExists()
        compose.onRoot().captureRoboImage(File(out, "screen_icons.png").absolutePath)
    }
}
