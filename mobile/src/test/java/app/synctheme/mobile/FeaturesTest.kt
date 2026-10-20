package app.synctheme.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Chameleon
import app.synctheme.core.ClockStyle
import app.synctheme.core.DayPart
import app.synctheme.core.LiveData
import app.synctheme.core.Mood
import app.synctheme.core.Presets
import app.synctheme.core.Sky
import app.synctheme.core.Theme
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.faces.FaceRenderer
import app.synctheme.faces.FaceState
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.ExportKind
import app.synctheme.mobile.render.ExportKit
import app.synctheme.mobile.render.SubjectMask
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.ThemeColors
import app.synctheme.mobile.watch.WatchFaceBuilder
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Эффект глубины, живые данные, «Хамелеон», «В тон вещи», классический циферблат и картинки для Apple. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class FeaturesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }

    private fun Bitmap.save(name: String) = File(out, name).outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

    private val photo = "photo_test.jpg"

    /** «Фото»: небо, горы и «человек» — оранжевый силуэт в центре, его и найдёт подменный распознаватель. */
    @Before
    fun photo() {
        val w = 1200
        val h = 1600
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2B6CB0.toInt(), 0xFFF6AD55.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFF2D3748.toInt()
        c.drawPath(android.graphics.Path().apply { moveTo(0f, 1100f); lineTo(400f, 700f); lineTo(800f, 1000f); lineTo(1200f, 650f); lineTo(1200f, 1600f); lineTo(0f, 1600f); close() }, p)
        subject(c, w, h, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDD6B20.toInt() })
        ThemeRepository.get(context).photoFile(photo).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        SubjectMask.file(context, photo).delete()
        // Вместо ML Kit: маска — тот же силуэт.
        SubjectMask.segmenter = { _, input ->
            Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also {
                subject(Canvas(it), input.width, input.height, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            }
        }
    }

    /** Силуэт: голова и плечи в нижней половине кадра. */
    private fun subject(c: Canvas, w: Int, h: Int, p: Paint) {
        c.drawCircle(w * 0.5f, h * 0.36f, w * 0.13f, p)
        c.drawRoundRect(RectF(w * 0.27f, h * 0.46f, w * 0.73f, h * 1.05f), w * 0.2f, w * 0.2f, p)
    }

    private fun photoTheme(): Theme = Presets.all[0].copy(
        background = Background(BackgroundType.PHOTO, listOf(0xFF1A202C.toInt(), 0xFFDD6B20.toInt()), photoFile = photo),
        dim = 0.1f, accentColor = 0xFFFFD166.toInt(),
    )

    @Test
    fun depthOnWatchAndWallpaper() = runBlocking<Unit> {
        SubjectMask.ensure(context, photo)
        assertTrue(SubjectMask.exists(context, photo))
        val base = photoTheme()
        // Телефон: часы на обоях в трёх раскладках, с глубиной и без.
        val variants = listOf(
            WallClock(WallClockLayout.TOP, depth = true), WallClock(WallClockLayout.STACKED, depth = true),
            WallClock(WallClockLayout.CENTER, depth = true), WallClock(WallClockLayout.STACKED, depth = false),
        )
        val pw = 270
        val ph = 585
        val sheet = Bitmap.createBitmap(variants.size * (pw + 16) + 16, ph + 32, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF202024.toInt())
        variants.forEachIndexed { i, v ->
            val preview = ThemeRenderer.phonePreview(context, base.copy(wallClock = v), 540, 1170)
            c.drawBitmap(preview, null, RectF(16f + i * (pw + 16), 16f, 16f + i * (pw + 16) + pw, 16f + ph), null)
        }
        sheet.save("depth_wallpaper.png")

        // Часы: слой глубины в нескольких стилях.
        val styles = listOf(ClockStyle.BOLD, ClockStyle.STACKED, ClockStyle.DIGITAL, ClockStyle.DIAGONAL, ClockStyle.LIQUID)
        val size = 260
        val watchSheet = Bitmap.createBitmap(styles.size * (size + 16) + 16, size * 2 + 48, Bitmap.Config.ARGB_8888)
        val wc = Canvas(watchSheet)
        wc.drawColor(0xFF202024.toInt())
        styles.forEachIndexed { i, style ->
            for ((row, depth) in listOf(false, true).withIndex()) {
                val t = base.copy(clockStyle = style, watchDepth = depth)
                val fg = ThemeRenderer.watchForeground(context, t)
                assertNotNull(fg)
                wc.drawBitmap(ThemeRenderer.watchPreview(context, t, size), 16f + i * (size + 16), 16f + row * (size + 16), null)
            }
        }
        watchSheet.save("depth_watch.png")
    }

    @Test
    fun liveDataOnAllStyles() {
        val size = 220
        val gap = 14
        val styles = ClockStyle.entries
        val sheet = Bitmap.createBitmap(styles.size * (size + gap) + gap, 3 * (size + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        val theme = Presets.all[0]
        val w = theme.watchSettings()
        // Строки: без данных; все данные при 62% шагов и 80% заряда; 15% заряда и 95% шагов.
        val rows = listOf(
            w to FaceState.preview(),
            w.copy(live = LiveData(steps = true, pulse = true, battery = true)) to FaceState.preview(),
            w.copy(live = LiveData(steps = true, pulse = true, battery = true)) to FaceState.preview().copy(battery = 15, stepPercent = 95, beatOverride = 0.1f),
        )
        rows.forEachIndexed { r, (settings, state) ->
            styles.forEachIndexed { c, style ->
                val s = settings.copy(clockStyle = style)
                val bg = ThemeRenderer.watchBackground(context, theme.copy(clockStyle = style))
                val face = FaceRenderer.render(context, s, bg, null, state, size)
                canvas.drawBitmap(face, (gap + c * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        sheet.save("watch_live.png")
    }

    /** Классический циферблат (Wear OS 3–5) в разное время и в режиме Always-On. */
    @Test
    fun classicFaceAtDifferentTimes() {
        val size = 200
        val gap = 12
        val times = listOf(Triple(0, 0, 0), Triple(7, 30, 5), Triple(13, 45, 59), Triple(23, 59, 58))
        val styles = ClockStyle.entries
        val sheet = Bitmap.createBitmap(styles.size * (size + gap) + gap, (times.size + 1) * (size + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        val theme = Presets.all[2]
        times.forEachIndexed { r, (h, m, s) ->
            val millis = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, s) }.timeInMillis
            styles.forEachIndexed { c, style ->
                val t = theme.copy(clockStyle = style)
                val bg = ThemeRenderer.watchBackground(context, t)
                val state = FaceState.now(millis, is24h = r % 2 == 0).copy(battery = 64)
                canvas.drawBitmap(FaceRenderer.render(context, t.watchSettings(), bg, null, state, size), (gap + c * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        styles.forEachIndexed { c, style ->
            val t = theme.copy(clockStyle = style)
            val state = FaceState.preview().copy(ambient = true)
            canvas.drawBitmap(FaceRenderer.render(context, t.watchSettings(), null, null, state, size), (gap + c * (size + gap)).toFloat(), (gap + times.size * (size + gap)).toFloat(), null)
        }
        sheet.save("watch_classic.png")
    }

    @Test
    fun chameleonChangesColorsNotDigits() {
        val themes = listOf(Presets.all[0], Presets.all[1], photoTheme())
        val moods = DayPart.entries.map { Mood(part = it) } + listOf(Sky.RAIN, Sky.SNOW, Sky.STORM).map { Mood(DayPart.DAY, it) }
        val w = 120
        val h = 260
        val sheet = Bitmap.createBitmap(moods.size * (w + 10) + 10, themes.size * (h + 10) + 10, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        themes.forEachIndexed { r, theme ->
            moods.forEachIndexed { c, mood ->
                val v = Chameleon.adapt(theme, mood)
                assertEquals(theme.textColor, v.textColor)
                if (mood != Mood(DayPart.DAY)) assertNotEquals("${theme.id} $mood", theme.background, v.background)
                canvas.drawBitmap(ThemeRenderer.background(context, v, w, h), (10 + c * (w + 10)).toFloat(), (10 + r * (h + 10)).toFloat(), null)
            }
        }
        sheet.save("chameleon.png")
        // Ночь темнее дня.
        val night = Chameleon.adapt(Presets.all[1], Mood(DayPart.NIGHT))
        assertTrue(night.dim > Presets.all[1].dim)
    }

    @Test
    fun themeFromClothingColor() {
        val colors = listOf(0xFF1E5631.toInt(), 0xFFB22222.toInt(), 0xFF2F3640.toInt(), 0xFFF5DEB3.toInt(), 0xFF6C5CE7.toInt())
        val w = 120
        val h = 260
        val sheet = Bitmap.createBitmap(colors.size * (w + 10) + 10, h + 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        colors.forEachIndexed { i, color ->
            val t = ThemeColors.fromColor(Presets.all[0], color, colors)
            assertTrue(Chameleon.hsv(t.accentColor).third >= 0.85f)
            canvas.drawBitmap(ThemeRenderer.phonePreview(context, t, w, h), (10 + i * (w + 10)).toFloat(), 10f, null)
            canvas.drawCircle(10 + i * (w + 10) + w / 2f, h + 35f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        sheet.save("match_colors.png")
    }

    @Test
    fun exportsImagesForAppleAndOtherWatches() = runBlocking<Unit> {
        val files = ExportKit.render(context, photoTheme(), ExportKind.entries.toSet())
        assertEquals(7, files.size)
        val iphone = files.first { it.name == "SyncTheme-iPhone.jpg" }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(iphone.path, opts)
        assertEquals(1290, opts.outWidth)
        assertEquals(2796, opts.outHeight)
        val watch = BitmapFactory.decodeFile(files.first { it.name == "SyncTheme-AppleWatch.jpg" }.path)
        assertEquals(410, watch.width)
        watch.save("export_apple_watch.png")
        assertNotNull(ExportKit.shareIntent(context, files))
    }

    /** Циферблат с глубиной и живыми данными собирается и проходит валидатор Watch Face Push на «телефоне». */
    @Test
    fun buildsWatchFaceWithDepthAndLiveData() = runBlocking<Unit> {
        SubjectMask.ensure(context, photo)
        val theme = photoTheme().copy(clockStyle = ClockStyle.BOLD, watchDepth = true, watchLive = LiveData(steps = true, pulse = true, battery = true))
        val result = WatchFaceBuilder(context).build(theme)
        assertTrue(result.token.isNotEmpty())
        assertNotNull(result.foreground)
        assertTrue(result.background.isNotEmpty())
    }
}
