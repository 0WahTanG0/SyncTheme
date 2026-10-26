package app.synctheme.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockStyle
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.core.WatchFaceXml
import app.synctheme.faces.FaceRenderer
import app.synctheme.faces.FaceState
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.render.ClockSide
import app.synctheme.mobile.render.Crop
import app.synctheme.mobile.render.MaskGrid
import app.synctheme.mobile.render.SubjectMask
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.widget.WidgetConfig
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetRenderer
import app.synctheme.mobile.widget.WidgetSize
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Эффект глубины, Always-On, виджеты любой формы. */
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

    private fun photoTheme(file: String = photo): Theme = Presets.all[0].copy(
        background = Background(BackgroundType.PHOTO, listOf(0xFF1A202C.toInt(), 0xFFDD6B20.toInt()), photoFile = file),
        dim = 0.1f, accentColor = 0xFFFFD166.toInt(),
    )

    /**
     * Эффект глубины: объект справа — часы слева, слева — справа, сверху — снизу, снизу — сверху,
     * и объект закрывает лишь край цифр. На часах-циферблате фото сдвигается так, что объект
     * остаётся со своей стороны от времени. Строки листа — положения объекта.
     */
    @Test
    fun clockOppositeTheSubject() = runBlocking<Unit> {
        // Распознаватель по цвету: объект — оранжевый силуэт.
        SubjectMask.segmenter = { _, input ->
            val px = IntArray(input.width * input.height)
            input.getPixels(px, 0, input.width, 0, 0, input.width, input.height)
            for (i in px.indices) {
                val c = px[i]
                val orange = Color.red(c) > 180 && Color.green(c) in 70..150 && Color.blue(c) < 90
                px[i] = if (orange) Color.WHITE else Color.TRANSPARENT
            }
            Bitmap.createBitmap(px, input.width, input.height, Bitmap.Config.ARGB_8888)
        }
        val cases = listOf(
            Triple("right", 0.74f to 0.5f, ClockSide.LEFT),
            Triple("left", 0.26f to 0.5f, ClockSide.RIGHT),
            Triple("top", 0.5f to 0.22f, ClockSide.BOTTOM),
            Triple("bottom", 0.5f to 0.72f, ClockSide.TOP),
        )
        val pw = 270
        val ph = 585
        val face = 200
        val styles = listOf(ClockStyle.DIGITAL, ClockStyle.BOLD, ClockStyle.ORBIT, ClockStyle.GLASS)
        val sheet = Bitmap.createBitmap(16 + (pw + 16) + styles.size * (face + 16), cases.size * (ph + 16) + 16, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        cases.forEachIndexed { r, (name, at, expected) ->
            val file = "photo_$name.jpg"
            val w = 1200
            val h = 1600
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2B6CB0.toInt(), 0xFF81E6D9.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            p.color = 0xFFDD6B20.toInt()
            val (cx, cy) = at
            c.drawCircle(cx * w, (cy - 0.1f) * h, w * 0.12f, p)
            c.drawRoundRect(RectF((cx - 0.2f) * w, (cy - 0.02f) * h, (cx + 0.2f) * w, (cy + 0.22f) * h), w * 0.15f, w * 0.15f, p)
            ThemeRepository.get(context).photoFile(file).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            SubjectMask.file(context, file).delete()
            SubjectMask.ensure(context, file)

            val theme = photoTheme(file).copy(wallClock = WallClock(depth = true))
            val spot = ThemeRenderer.wallClockSpot(context, theme, 540, 1170)
            assertEquals("$name: часы напротив объекта", expected, spot.side)
            assertTrue("$name: объект закрывает ${spot.overlap} цифр", spot.overlap in 0.04f..0.25f)
            val y = 16f + r * (ph + 16)
            canvas.drawBitmap(ThemeRenderer.phonePreview(context, theme, 540, 1170), null, RectF(16f, y, 16f + pw, y + ph), null)
            styles.forEachIndexed { i, style ->
                val t = photoTheme(file).copy(clockStyle = style, watchDepth = true)
                // На часах двигается время, а не фото: прочь от объекта.
                val shift = ThemeRenderer.watchTimeShift(context, t)
                assertNotNull("$name/$style: время сдвинуто", shift)
                // Блок времени — с противоположной стороны от объекта (центр объекта — по маске в кадре циферблата).
                val mask = SubjectMask.load(context, file)!!
                val (sx, sy) = MaskGrid.of(mask, Crop.of(mask.width, mask.height, 1f, 1f, t.watchBackground()), 90, 90).centroid()
                val (px, py) = WatchFaceXml.timePivot(style)
                val bx = px + shift!!.dx
                val by = py + shift.dy
                val opposite = when (expected) {
                    ClockSide.LEFT -> bx < sx * 450
                    ClockSide.RIGHT -> bx > sx * 450
                    ClockSide.TOP -> by < sy * 450
                    ClockSide.BOTTOM -> by > sy * 450
                }
                assertTrue("$name/$style: время напротив объекта ($shift)", opposite)
                canvas.drawBitmap(ThemeRenderer.watchPreview(context, t, face), 32f + pw + i * (face + 16), y + (ph - face) / 2f, null)
            }
            // У стрелок время в центре — его не двигаем, и фото тоже.
            assertEquals(null, ThemeRenderer.watchTimeShift(context, photoTheme(file).copy(clockStyle = ClockStyle.ANALOG, watchDepth = true)))
        }
        sheet.save("depth_positions.png")
    }

    /**
     * Телефон: часы на обоях без глубины (две раскладки), с глубиной (место — по объекту)
     * и экран блокировки с «адаптивными часами».
     */
    @Test
    fun depthOnPhone() = runBlocking<Unit> {
        SubjectMask.ensure(context, photo)
        assertTrue(SubjectMask.exists(context, photo))
        val base = photoTheme()
        val variants = listOf(
            WallClock(WallClockLayout.TOP, depth = false), WallClock(WallClockLayout.STACKED, depth = false), WallClock(depth = true),
        ).map { base.copy(wallClock = it) }
        val pw = 270
        val ph = 585
        val sheet = Bitmap.createBitmap((variants.size + 1) * (pw + 16) + 16, ph + 32, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF202024.toInt())
        variants.forEachIndexed { i, v ->
            c.drawBitmap(ThemeRenderer.phonePreview(context, v, 540, 1170), null, RectF(16f + i * (pw + 16), 16f, 16f + i * (pw + 16) + pw, 16f + ph), null)
        }
        val lock = ThemeRenderer.lockPreview(context, base.copy(lockDepth = true), 540, 1170)
        c.drawBitmap(lock, null, RectF(16f + 3 * (pw + 16), 16f, 16f + 3 * (pw + 16) + pw, 16f + ph), null)
        sheet.save("depth_phone.png")
        // Часы SyncTheme на экране блокировки: в превью блокировки — они, а не часы телефона.
        ThemeRenderer.lockPreview(context, base.copy(wallClock = WallClock(depth = true, onLock = true)), 540, 1170).save("lock_own_clock.png")
        // Фото экрана блокировки с глубиной ставится без затемнения.
        assertEquals(0f, base.copy(lockDepth = true).lockScreenDim())
    }

    /**
     * Все циферблаты: обычный вид, с объектом поверх цифр (время видно сквозь него) и Always-On.
     * Тёмная и светлая темы — в Always-On тёмные цифры светлой темы должны стать светлыми.
     */
    @Test
    fun watchFacesDepthAndAlwaysOn() = runBlocking<Unit> {
        SubjectMask.ensure(context, photo)
        val size = 200
        val gap = 12
        val styles = ClockStyle.entries
        val sakura = Presets.all.first { it.id == "sakura" }
        val rows = listOf(
            photoTheme() to false, photoTheme().copy(watchDepth = true) to false, photoTheme() to true,
            sakura to false, sakura to true,
        )
        val sheet = Bitmap.createBitmap(styles.size * (size + gap) + gap, rows.size * (size + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        rows.forEachIndexed { r, (base, ambient) ->
            styles.forEachIndexed { c, style ->
                val t = base.copy(clockStyle = style)
                val face = if (ambient) ThemeRenderer.watchAmbientPreview(context, t, size) else ThemeRenderer.watchPreview(context, t, size)
                canvas.drawBitmap(face, (gap + c * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        sheet.save("watch_depth_aod.png")
        // В Always-On цифры светлой темы — светлые.
        assertTrue(WatchFaceXml.isLight(WatchFaceXml.forLayer(sakura.watchSettings(), WatchFaceXml.Layer.AMBIENT).textColor))
    }

    /** Время в Always-On в разные часы — проверка, что рисуется без ошибок и читается. */
    @Test
    fun alwaysOnAtDifferentTimes() {
        val size = 160
        val gap = 10
        val styles = ClockStyle.entries
        val times = listOf(Triple(0, 0, 0), Triple(7, 30, 5), Triple(23, 59, 58))
        val sheet = Bitmap.createBitmap(styles.size * (size + gap) + gap, times.size * (size + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202024.toInt())
        times.forEachIndexed { r, (h, m, s) ->
            val millis = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, s) }.timeInMillis
            styles.forEachIndexed { c, style ->
                val t = Presets.all[2].copy(clockStyle = style)
                val state = FaceState.now(millis, is24h = true).copy(ambient = true)
                canvas.drawBitmap(FaceRenderer.render(context, t.watchSettings(), null, null, state, size), (gap + c * (size + gap)).toFloat(), (gap + r * (size + gap)).toFloat(), null)
            }
        }
        sheet.save("watch_aod_times.png")
    }

    @Test
    fun classifiesWidgetShapes() {
        // Столбики с рабочего стола пользователя: 1 колонка × 5 рядов и 2 колонки × 5 рядов.
        assertEquals(WidgetSize.COLUMN, WidgetSize.classify(41f, 625f))
        assertEquals(WidgetSize.TALL, WidgetSize.classify(96f, 625f))
        assertEquals(WidgetSize.NARROW, WidgetSize.classify(55f, 130f))
        assertEquals(WidgetSize.S, WidgetSize.classify(73f, 100f))
        assertEquals(WidgetSize.S, WidgetSize.classify(80f, 80f))
        assertEquals(WidgetSize.BAR, WidgetSize.classify(140f, 60f))
        assertEquals(WidgetSize.STRIP, WidgetSize.classify(330f, 60f))
        assertEquals(WidgetSize.WIDE, WidgetSize.classify(330f, 120f))
        assertEquals(WidgetSize.STRIP, WidgetSize.classify(330f, 90f))
        assertEquals(WidgetSize.M, WidgetSize.classify(150f, 160f))
        assertEquals(WidgetSize.L, WidgetSize.classify(300f, 330f))
    }

    /**
     * Виджеты всех видов в формах, как на рабочем столе: столбики, полоски, квадраты.
     * Строки — виды, столбцы — размеры (dp).
     */
    @Test
    fun widgetsInEveryShape() {
        val weather = WeatherRepository.parseForecast(
            javaClass.classLoader!!.getResource("forecast.json")!!.readText(), "Москва", System.currentTimeMillis(),
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        val shapes = listOf(41 to 400, 96 to 400, 60 to 150, 75 to 75, 140 to 60, 300 to 60, 150 to 150, 300 to 105, 300 to 300)
        val theme = Presets.all[0]
        for (family in listOf("bold", "glass", "neon", "blob", "minimal")) {
            val kinds = WidgetKind.entries
            val gap = (10 * d).toInt()
            val rowH = (400 * d).toInt()
            val sheetW = shapes.sumOf { (it.first * d).toInt() + gap } + gap
            val sheet = Bitmap.createBitmap(sheetW, kinds.size * (rowH + gap) + gap, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(0xFF202024.toInt())
            kinds.forEachIndexed { r, kind ->
                val style = kind.styles.firstOrNull { it.id == family }?.id
                    ?: if (kind == WidgetKind.APPS) mapOf("bold" to "flower", "blob" to "cookie").getValue(family) else kind.defaultStyle
                val apps = if (kind == WidgetKind.APPS) (1..8).map { "com.example.app$it/.Main" } else emptyList()
                var x = gap
                for ((w, h) in shapes) {
                    val size = WidgetSize.classify(w.toFloat(), h.toFloat())
                    val views = WidgetRenderer.buildSize(
                        context, kind, WidgetConfig(style = style, opacity = 0.5f, apps = apps), theme, size, weather, SizeF(w.toFloat(), h.toFloat()),
                    )
                    val host = FrameLayout(activity)
                    activity.setContentView(host)
                    val view = views.apply(activity, host)
                    host.addView(view)
                    shadowOf(Looper.getMainLooper()).idle()
                    val bmp = render(view, w, h, ThemeRenderer.background(context, theme, (w * d).toInt(), (h * d).toInt()))
                    canvas.drawBitmap(bmp, x.toFloat(), (gap + r * (rowH + gap)).toFloat(), null)
                    x += (w * d).toInt() + gap
                }
            }
            sheet.save("widgets_shapes_$family.png")
        }
    }

    /**
     * Вертикальные формы рядом с горизонтальной полоской, как на рабочем столе: полоска 4×1 (360×85),
     * столбики 1×2…1×5 и высокий 2×5. Строки — виды, столбцы — размеры (dp).
     */
    @Test
    fun verticalWidgetsMirrorStrips() {
        val weather = WeatherRepository.parseForecast(
            javaClass.classLoader!!.getResource("forecast.json")!!.readText(), "Москва", System.currentTimeMillis(),
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = context.resources.displayMetrics.density
        val shapes = listOf(360 to 85, 41 to 625, 60 to 150, 56 to 330, 85 to 365, 96 to 625)
        val theme = Presets.all[0]
        for (family in listOf("bold", "glass", "neon", "blob", "minimal")) {
            val kinds = WidgetKind.entries
            val gap = (10 * d).toInt()
            val rowH = (625 * d).toInt()
            val sheetW = shapes.sumOf { (it.first * d).toInt() + gap } + gap
            val sheet = Bitmap.createBitmap(sheetW, kinds.size * (rowH + gap) + gap, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(0xFF202024.toInt())
            kinds.forEachIndexed { r, kind ->
                val style = kind.styles.firstOrNull { it.id == family }?.id
                    ?: if (kind == WidgetKind.APPS) mapOf("bold" to "flower", "blob" to "cookie").getValue(family) else kind.defaultStyle
                val apps = if (kind == WidgetKind.APPS) (1..8).map { "com.example.app$it/.Main" } else emptyList()
                var x = gap
                for ((w, h) in shapes) {
                    val size = WidgetSize.classify(w.toFloat(), h.toFloat())
                    val views = WidgetRenderer.buildSize(
                        context, kind, WidgetConfig(style = style, opacity = 0.5f, apps = apps), theme, size, weather, SizeF(w.toFloat(), h.toFloat()),
                    )
                    val host = FrameLayout(activity)
                    activity.setContentView(host)
                    val view = views.apply(activity, host)
                    host.addView(view)
                    shadowOf(Looper.getMainLooper()).idle()
                    val bmp = render(view, w, h, ThemeRenderer.background(context, theme, (w * d).toInt(), (h * d).toInt()))
                    canvas.drawBitmap(bmp, x.toFloat(), (gap + r * (rowH + gap)).toFloat(), null)
                    x += (w * d).toInt() + gap
                }
            }
            sheet.save("widgets_vertical_$family.png")
        }
    }

    /** Циферблат с глубиной собирается и проходит валидатор Watch Face Push на «телефоне». */
    @Test
    fun buildsWatchFaceWithDepth() = runBlocking<Unit> {
        SubjectMask.ensure(context, photo)
        val theme = photoTheme().copy(clockStyle = ClockStyle.BOLD, watchDepth = true)
        val result = WatchFaceBuilder(context).build(theme)
        assertTrue(result.token.isNotEmpty())
        assertTrue(result.hasDepth)
        assertNotNull(ThemeRenderer.watchForeground(context, theme))
    }

    private fun render(view: View, wDp: Int, hDp: Int, background: Bitmap): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(background, null, android.graphics.Rect(0, 0, w, h), null)
        view.draw(canvas)
        return bmp
    }

    /** Нажатие на часы на обоях: попадает только по часам (с запасом под палец) и открывает выбранное приложение. */
    @Test
    fun wallClockTapOpensApp() {
        val theme = Presets.all[0].copy(wallClock = WallClock(WallClockLayout.TOP, depth = false, openApp = "com.example.notes/.Main"))
        val spot = ThemeRenderer.wallClockSpot(context, theme, 1080, 2340)
        val area = ThemeRenderer.wallClockArea(theme, spot)!!
        assertTrue("по цифрам", ThemeRenderer.wallClockHit(theme, spot, area.centerX(), area.centerY(), 40f))
        assertTrue("рядом, в пределах запаса", ThemeRenderer.wallClockHit(theme, spot, area.centerX(), area.bottom + 30f, 40f))
        assertTrue("внизу экрана — мимо", !ThemeRenderer.wallClockHit(theme, spot, 540f, 2200f, 40f))
        val intent = app.synctheme.mobile.widget.AppCatalog.wallClockIntent(context, theme.wallClock!!.openApp)!!
        assertEquals("com.example.notes", intent.component?.packageName)
    }

    /** Нажатие на обои: короткое и на месте — да; свайп, долгое нажатие и отменённое касание — нет. */
    @Test
    fun wallpaperTapDetector() {
        val tap = app.synctheme.mobile.wallpaper.TapDetector(slop = 30f)
        fun gesture(vararg events: Triple<Int, Float, Long>) = events.map { (a, x, t) -> tap.onEvent(a, x, 500f, t) }.last()
        val down = android.view.MotionEvent.ACTION_DOWN
        val move = android.view.MotionEvent.ACTION_MOVE
        val up = android.view.MotionEvent.ACTION_UP
        val cancel = android.view.MotionEvent.ACTION_CANCEL
        val hit = gesture(Triple(down, 100f, 0L), Triple(move, 108f, 50L), Triple(up, 110f, 120L))
        assertEquals(100f, hit!!.x, 0.01f)
        assertEquals(null, gesture(Triple(down, 100f, 0L), Triple(move, 400f, 80L), Triple(up, 400f, 160L)))
        assertEquals(null, gesture(Triple(down, 100f, 0L), Triple(up, 100f, 900L)))
        assertEquals(null, gesture(Triple(down, 100f, 0L), Triple(cancel, 100f, 50L), Triple(up, 100f, 100L)))
    }
}
