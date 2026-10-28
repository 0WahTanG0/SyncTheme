package app.synctheme.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.util.SizeF
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.synctheme.core.Presets
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.widget.FolderActivity
import app.synctheme.mobile.widget.FolderAnim
import app.synctheme.mobile.widget.FolderApp
import app.synctheme.mobile.widget.FolderGlyph
import app.synctheme.mobile.widget.FolderMode
import app.synctheme.mobile.widget.FolderDrawOverlay
import app.synctheme.mobile.widget.FolderLineOverlay
import app.synctheme.mobile.widget.FolderLines
import app.synctheme.mobile.widget.LineShapes
import app.synctheme.mobile.widget.defaultBend
import app.synctheme.mobile.widget.FolderOverlay
import app.synctheme.mobile.widget.FolderShape
import app.synctheme.mobile.widget.FolderWidget
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import app.synctheme.mobile.widget.WidgetSize
import app.synctheme.mobile.widget.folderColumns
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Приложения» папкой: значок, невидимый виджет и пять анимаций раскрытия. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-560dpi")
class FolderTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val out = File(System.getProperty("previewsDir")!!).apply { mkdirs() }
    private val theme = Presets.all[0]
    private val names = listOf("Телефон", "Сообщения", "Камера", "Галерея", "Браузер", "Музыка", "Карты", "Заметки", "Почта")
    private val hues = listOf(140f, 200f, 30f, 280f, 220f, 340f, 100f, 50f, 0f)

    /** Значки-заглушки разных цветов: в Robolectric нет установленных приложений. */
    private fun apps(n: Int) = (0 until n).map { i ->
        val bmp = Bitmap.createBitmap(196, 196, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.HSVToColor(floatArrayOf(hues[i % hues.size], 0.65f, 0.9f)) }
        Canvas(bmp).apply {
            drawCircle(98f, 98f, 98f, p)
            p.color = Color.WHITE
            drawRoundRect(64f, 64f, 132f, 132f, 18f, 18f, p)
        }
        FolderApp("com.example.app$i/.Main", names[i % names.size], bmp.asImageBitmap())
    }

    @Test
    fun animationFrames() {
        val w = 1442
        val h = 3202
        val wallpaper = ThemeRenderer.background(app, theme, w / 2, h / 2).asImageBitmap()
        // Виджет-папка в левом нижнем углу рабочего стола.
        val origin = Rect(110, 2280, 390, 2560)
        val list = apps(9)
        var state by mutableStateOf(Triple(FolderAnim.REVEAL, 0f, "glass"))
        compose.setContent {
            val (anim, p, style) = state
            Box(Modifier.fillMaxSize()) {
                Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                FolderOverlay(WidgetConfig(style = style, folder = FolderMode.ICON, animation = anim, title = "Избранное"), theme, list, origin, p)
            }
        }
        val steps = listOf(0.12f, 0.3f, 0.55f, 0.8f, 1f)
        val styles = listOf("glass", "poster", "neon", "paper", "board")
        // Кадры: строки — анимации, столбцы — моменты раскрытия (лист собирается из файлов).
        FolderAnim.entries.forEachIndexed { r, anim ->
            steps.forEachIndexed { c, p ->
                state = Triple(anim, p, styles[r])
                compose.waitForIdle()
                compose.onRoot().captureRoboImage(File(out, "folder_${anim.name.lowercase()}_$c.png").absolutePath)
            }
        }
    }

    /** S-образная линия снизу слева вверх направо — как нарисованная пальцем. */
    private val sCurve = (0..30).flatMap { k ->
        val t = k / 30f
        listOf(0.15f + 0.7f * t, 0.84f - 0.56f * t + 0.1f * kotlin.math.sin(2.2f * Math.PI.toFloat() * t))
    }

    /** Папка вдоль линии: строки — линия и анимация, столбцы — моменты раскрытия. */
    @Test
    fun lineFrames() {
        val w = 1442
        val h = 3202
        val wallpaper = ThemeRenderer.background(app, theme, w / 2, h / 2).asImageBitmap()
        val origin = Rect(110, 2280, 390, 2560)
        val list = apps(8)
        val rows = listOf(
            FolderShape.ARC to FolderAnim.SPRING,
            FolderShape.WAVE to FolderAnim.FAN,
            FolderShape.CUSTOM to FolderAnim.CASCADE,
            FolderShape.RING to FolderAnim.SPIRAL,
            FolderShape.COLUMN to FolderAnim.REVEAL,
        )
        var state by mutableStateOf(Triple(rows[0], 0f, false))
        compose.setContent {
            val (row, p, dim) = state
            Box(Modifier.fillMaxSize()) {
                Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                FolderLineOverlay(
                    WidgetConfig(style = "glass", folder = FolderMode.ICON, shape = row.first, animation = row.second, line = sCurve, dim = dim),
                    theme, list, origin, p,
                )
            }
        }
        val steps = listOf(0.15f, 0.35f, 0.6f, 0.85f, 1f)
        rows.forEachIndexed { r, row ->
            steps.forEachIndexed { c, p ->
                state = Triple(row, p, r == 3)
                compose.waitForIdle()
                compose.onRoot().captureRoboImage(File(out, "folder_line_${r}_$c.png").absolutePath)
            }
        }
    }

    /** Значки ложатся вдоль линии через равные промежутки и не вылезают за экран. */
    @Test
    fun lineGeometry() {
        val screen = Size(1440f, 3120f)
        val dp = 3.5f
        // Своя линия: значки от начала к концу, последний — в конце линии.
        val custom = FolderLines.layout(FolderShape.CUSTOM, sCurve, 6, Offset.Zero, screen, dp, true)
        val path = custom.path!!
        assertEquals(path.length, custom.stops.last(), 1f)
        val gaps = custom.stops.toList().zipWithNext { a, b -> b - a }
        assertTrue("равные промежутки: $gaps", gaps.all { kotlin.math.abs(it - gaps[0]) < 1f })
        assertEquals(FolderLines.points(sCurve, screen).first(), custom.origin)
        // Готовые линии от виджета в левом нижнем углу: вверх и вправо, всё на экране.
        val widget = Offset(250f, 2400f)
        for (shape in listOf(FolderShape.ARC, FolderShape.COLUMN, FolderShape.ROW, FolderShape.WAVE, FolderShape.RING)) {
            val geo = FolderLines.layout(shape, emptyList(), 12, widget, screen, dp, true)
            geo.targets.forEach { t ->
                assertTrue("$shape: $t на экране", t.x in 0f..screen.width && t.y in 0f..screen.height)
            }
            if (shape != FolderShape.ROW && shape != FolderShape.RING) assertTrue("$shape: вверх от виджета", geo.targets.last().y < widget.y)
        }
        // Линия, нарисованная пальцем, хранится в долях экрана и не длиннее 64 точек.
        val drawn = (0..300).map { Offset(100f + it * 3f, 2000f - it * 4f + 60f * kotlin.math.sin(it / 20f)) }
        val norm = FolderLines.normalize(drawn, screen)
        assertTrue(norm.size in 16..128 && norm.all { it in 0f..1f })
    }

    /** Своя линия рисуется пальцем поверх рабочего стола: изогнутая линия сохраняется в настройку виджета. */
    @Test
    fun drawsLineWithFinger() {
        var result: List<Float>? = null
        val config = WidgetConfig(style = "glass", folder = FolderMode.ICON, apps = (0 until 7).map { "com.example.app$it/.Main" })
        // За прозрачным окном на телефоне — рабочий стол; здесь вместо него обои с виджетами.
        val home = ThemeRenderer.phonePreview(app, theme, 721, 1601).asImageBitmap()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Image(home, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                FolderDrawOverlay(config, theme, onCancel = {}) { result = it }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Холст линии").performTouchInput {
            down(Offset(width * 0.18f, height * 0.86f))
            for (k in 1..40) {
                val t = k / 40f
                moveTo(Offset(width * (0.18f + 0.62f * t), height * (0.86f - 0.62f * t + 0.12f * kotlin.math.sin(6f * t))))
            }
            up()
        }
        compose.waitForIdle()
        Thread.sleep(800)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "screen_folder_line.png").absolutePath)
        compose.onNodeWithText("Готово").performClick()
        val line = result
        assertNotNull("линия сохранена", line)
        assertTrue("точек: ${line!!.size}", line.size >= 16)
        // Изогнутая: точки линии заметно отходят от прямой между её концами.
        val pts = line.chunked(2)
        val a = pts.first()
        val b = pts.last()
        val chord = kotlin.math.hypot(b[0] - a[0], b[1] - a[1])
        val bend = pts.maxOf { m -> kotlin.math.abs((b[0] - a[0]) * (m[1] - a[1]) - (b[1] - a[1]) * (m[0] - a[0])) / chord }
        assertTrue("изгиб: $bend", bend > 0.03f)
    }

    /** Ровная дуга по трём точкам, изгиб по средней точке и сглаживание дрожащей руки. */
    @Test
    fun arcsAndSmoothing() {
        val a = Offset(100f, 1000f)
        val b = Offset(700f, 400f)
        val arc = LineShapes.arc(a, b, 0.3f)
        assertTrue((arc.first() - a).getDistance() < 1f && (arc.last() - b).getDistance() < 1f)
        val mid = LineShapes.arcMiddle(a, b, 0.3f)
        assertTrue("дуга проходит через середину", arc.minOf { (it - mid).getDistance() } < 12f)
        assertEquals(0.3f, LineShapes.bendFor(a, b, mid), 1e-3f)
        // Точки дуги — на одной окружности: расстояния до центра равны.
        val c = run {
            val (p, q, r) = Triple(arc[0], arc[24], arc[47])
            val d = 2 * (p.x * (q.y - r.y) + q.x * (r.y - p.y) + r.x * (p.y - q.y))
            val ux = ((p.x * p.x + p.y * p.y) * (q.y - r.y) + (q.x * q.x + q.y * q.y) * (r.y - p.y) + (r.x * r.x + r.y * r.y) * (p.y - q.y)) / d
            val uy = ((p.x * p.x + p.y * p.y) * (r.x - q.x) + (q.x * q.x + q.y * q.y) * (p.x - r.x) + (r.x * r.x + r.y * r.y) * (q.x - p.x)) / d
            Offset(ux, uy)
        }
        val radii = arc.map { (it - c).getDistance() }
        assertTrue("ровная дуга: ${radii.min()}…${radii.max()}", radii.max() - radii.min() < 2f)
        // Без изгиба — прямая.
        assertTrue(LineShapes.arc(a, b, 0f).all { LineShapes.distanceToSegment(it, a, b) < 0.5f })

        // Дрожащая прямая линия → две опорные точки, ровная; изогнутая — изгибы остаются, но точек немного.
        val shaky = (0..200).map { Offset(100f + it * 3f, 1500f - it * 2f + if (it % 2 == 0) 6f else -6f) }
        assertEquals(2, LineShapes.curve(shaky, 3.5f).keys.size)
        val wave = (0..300).map { Offset(100f + it * 3f, 2000f - it * 4f + 120f * kotlin.math.sin(it / 25f)) }
        val curve = LineShapes.curve(wave, 3.5f)
        assertTrue("опорных точек: ${curve.keys.size}", curve.keys.size in 4..8)
        val smooth = curve.points()
        assertTrue("кривая проходит рядом с рисунком", wave.maxOf { w -> smooth.minOf { (it - w).getDistance() } } < 40f)
        assertEquals(curve.keys.size - 1, curve.smoother().keys.size)
    }

    /** Инструмент «Дуга»: проводим от начала к концу, потом тянем середину — изгиб сильнее. */
    @Test
    fun drawsArcAndBendsIt() {
        var result: List<Float>? = null
        val config = WidgetConfig(style = "glass", folder = FolderMode.ICON, apps = (0 until 5).map { "com.example.app$it/.Main" })
        val home = ThemeRenderer.phonePreview(app, theme, 721, 1601).asImageBitmap()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Image(home, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                FolderDrawOverlay(config, theme, onCancel = {}) { result = it }
            }
        }
        compose.onNodeWithText("Дуга").performClick()
        compose.waitForIdle()
        var a = Offset.Zero
        var b = Offset.Zero
        var size = Size.Zero
        compose.onNodeWithContentDescription("Холст линии").performTouchInput {
            size = Size(width.toFloat(), height.toFloat())
            a = Offset(width * 0.22f, height * 0.78f)
            b = Offset(width * 0.78f, height * 0.36f)
            down(a)
            for (k in 1..20) moveTo(a + (b - a) * (k / 20f))
            up()
        }
        compose.waitForIdle()
        val mid = LineShapes.arcMiddle(a, b, defaultBend(a, b, size))
        val target = LineShapes.arcMiddle(a, b, 0.35f * kotlin.math.sign(defaultBend(a, b, size)))
        compose.onNodeWithContentDescription("Холст линии").performTouchInput {
            down(mid)
            for (k in 1..10) moveTo(mid + (target - mid) * (k / 10f))
            up()
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "screen_folder_arc.png").absolutePath)
        compose.onNodeWithText("Готово").performClick()
        val pts = result!!.chunked(2).map { Offset(it[0] * size.width, it[1] * size.height) }
        val chord = (b - a).getDistance()
        val bend = pts.maxOf { LineShapes.distanceToSegment(it, a, b) } / chord
        assertEquals("изгиб после правки", 0.35f, bend, 0.05f)
    }

    /** Значок папки во всех стилях с каждым рисунком и невидимый виджет — собираются и открывают папку. */
    @Test
    fun folderWidget() {
        val styles = WidgetKind.APPS.styles.map { it.id }
        val px = 240
        val sheet = Bitmap.createBitmap(styles.size * (px + 20) + 20, FolderGlyph.entries.size * (px + 20) + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawBitmap(ThemeRenderer.background(app, theme, sheet.width, sheet.height), 0f, 0f, null)
        FolderGlyph.entries.forEachIndexed { r, glyph ->
            styles.forEachIndexed { c, style ->
                val config = WidgetConfig(style = style, folder = FolderMode.ICON, glyph = glyph, opacity = 0.45f, apps = listOf("a/.A", "b/.B"))
                canvas.drawBitmap(FolderWidget.glyph(app, config, theme, px), (20 + c * (px + 20)).toFloat(), (20 + r * (px + 20)).toFloat(), null)
            }
        }
        File(out, "folder_icons.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        for (mode in listOf(FolderMode.ICON, FolderMode.HIDDEN)) {
            val config = WidgetConfig(style = "glass", folder = mode, title = "Избранное")
            val views = WidgetRenderer.build(app, WidgetKind.APPS, config, theme, sizes = listOf(SizeF(70f, 70f), SizeF(150f, 70f)), widgetId = 42)
            assertNotNull(views.apply(activity, FrameLayout(activity)))
            assertNotNull(WidgetRenderer.buildSize(app, WidgetKind.APPS, config, theme, WidgetSize.S).apply(activity, FrameLayout(activity)))
        }
        assertEquals(listOf(1, 2, 2, 2, 3, 3, 4), listOf(1, 2, 3, 4, 5, 9, 12).map(::folderColumns))
    }

    /** Папка открывается как окно (из настройки виджета — со своими настройками) и не остаётся висеть, когда ушли. */
    @Test
    fun folderActivityOpensAndCloses() {
        val config = WidgetConfig(style = "neon", folder = FolderMode.ICON, animation = FolderAnim.SPIRAL)
        val intent = FolderActivity.preview(app, config, Rect(100, 2000, 300, 2200))
        val controller = Robolectric.buildActivity(FolderActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop()
        assertTrue("папка закрылась", controller.get().isFinishing)
    }
}
