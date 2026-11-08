package app.synctheme.mobile.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.ColorUtils

/** Стиль значков приложений — под оформление SyncTheme. */
enum class IconStyle(val title: String, val note: String) {
    CLAY("Мята", "Светлая матовая плитка, значок — графитом"),
    MINT("Мятная", "Мятная плитка, значок — тёмно-зелёным"),
    GRAPHITE("Графит", "Тёмная плитка — для тёмных обоев"),
    SOFT("Мягкий", "Серая выпуклая плитка, как в «Мягком» виде"),
    THEME("Цвет темы", "Плитка цвета ваших обоев"),
}

/**
 * Значки приложений в стиле оформления. Значок — адаптивный (108 × 108, видна середина 72 × 72,
 * форму — круг, «сквиркл» — задаёт лаунчер), поэтому стоит на рабочем столе как родной.
 *
 * Рисунок берётся из монохромного слоя значка (Android 13+, «тематические значки») — его рисуют
 * цветом стиля с лёгким тиснением. Если такого слоя нет, но передний слой — значок на прозрачном
 * фоне, берётся его силуэт. Иначе значок целиком становится двухцветным медальоном в цветах стиля.
 */
object IconStyler {
    /** Цвета стиля: лицевая сторона плитки, рисунок, светлый край медальона. */
    class Tones(val face: Int, val glyph: Int, val light: Int, val dark: Boolean)

    fun tones(style: IconStyle, accent: Int): Tones = when (style) {
        IconStyle.CLAY -> Tones(0xFFF3F1ED.toInt(), 0xFF2E2E2E.toInt(), 0xFFFBFAF8.toInt(), false)
        IconStyle.MINT -> Tones(0xFFA8DCC3.toInt(), 0xFF203229.toInt(), 0xFFD8F1E5.toInt(), false)
        IconStyle.GRAPHITE -> Tones(0xFF303135.toInt(), 0xFFEEEDE9.toInt(), 0xFF8C8D92.toInt(), true)
        IconStyle.SOFT -> Tones(0xFFE3E4E8.toInt(), 0xFF4A4C55.toInt(), 0xFFF6F6F8.toInt(), false)
        IconStyle.THEME -> {
            // Слишком яркий или тёмный акцент смягчаем, чтобы плитка оставалась матовой.
            val face = ColorUtils.blendARGB(accent or 0xFF000000.toInt(), 0xFFF3F1ED.toInt(), 0.12f)
            val dark = ColorUtils.calculateLuminance(face) < 0.45
            Tones(face, if (dark) 0xFFFFFFFF.toInt() else 0xFF1E1E1E.toInt(), ColorUtils.blendARGB(face, 0xFFFFFFFF.toInt(), 0.55f), dark)
        }
    }

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Значок приложения component («пакет/активити») в стиле; px — сторона всего адаптивного значка. */
    fun render(context: Context, component: String, style: IconStyle, accent: Int, px: Int = 432): Bitmap? {
        val key = "$component|$style|$accent|$px"
        cache.get(key)?.let { return it }
        val cn = ComponentName.unflattenFromString(component) ?: return null
        // Свой экземпляр: значки рисуются параллельно, а у одного Drawable размеры общие.
        val icon = runCatching { context.packageManager.getActivityIcon(cn).let { it.constantState?.newDrawable(context.resources)?.mutate() ?: it } }
            .getOrNull() ?: return null
        return runCatching { render(icon, style, accent, px) }.getOrNull()?.also { cache.put(key, it) }
    }

    /** Как значок выглядит на рабочем столе: видимая середина, обрезанная «сквирклом». */
    fun preview(context: Context, component: String, style: IconStyle, accent: Int, px: Int): Bitmap? {
        val full = render(context, component, style, accent, px * 3 / 2) ?: return null
        return visible(full, px)
    }

    fun visible(full: Bitmap, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.32f, px * 0.32f, Path.Direction.CW) }
        c.clipPath(clip)
        val inset = full.width / 6f
        c.drawBitmap(full, android.graphics.Rect(inset.toInt(), inset.toInt(), (full.width - inset).toInt(), (full.height - inset).toInt()),
            RectF(0f, 0f, px.toFloat(), px.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun render(icon: Drawable, style: IconStyle, accent: Int, px: Int): Bitmap {
        val t = tones(style, accent)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val u = px / 108f
        drawPlate(c, px, u, t, style)
        val glyph = glyphOf(icon, px)
        if (glyph != null) drawGlyph(c, glyph, t, u) else drawMedallion(c, icon, px, u, t)
        return out
    }

    /** Плитка: светлее вверху, темнее внизу и к краям видимой части — выпуклая и матовая. */
    private fun drawPlate(c: Canvas, px: Int, u: Float, t: Tones, style: IconStyle) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = ColorUtils.blendARGB(t.face, 0xFFFFFFFF.toInt(), if (t.dark) 0.10f else 0.30f)
        val bottom = ColorUtils.blendARGB(t.face, 0xFF000000.toInt(), if (t.dark) 0.18f else 0.09f)
        p.shader = if (style == IconStyle.SOFT) {
            LinearGradient(18 * u, 18 * u, 90 * u, 90 * u, intArrayOf(top, t.face, bottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 18 * u, 0f, 90 * u, intArrayOf(top, t.face, t.face, bottom), floatArrayOf(0f, 0.38f, 0.62f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
        // Края видимой части чуть темнее — объём без жёсткой рамки, при любой форме значка.
        p.shader = RadialGradient(54 * u, 48 * u, 50 * u, intArrayOf(0, 0, ColorUtils.setAlphaComponent(0xFF000000.toInt(), if (t.dark) 70 else 26)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
        // Блик по верху.
        p.shader = LinearGradient(0f, 18 * u, 0f, 36 * u, ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), if (t.dark) 30 else 110), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
    }

    /**
     * Рисунок значка — маска во весь адаптивный значок: монохромный слой (Android 13+) или силуэт
     * переднего слоя, если он прозрачный вокруг рисунка. null — подходящего рисунка нет.
     */
    private fun glyphOf(icon: Drawable, px: Int): Bitmap? {
        if (icon !is AdaptiveIconDrawable) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            icon.monochrome?.let { mono -> return drawLayer(mono, px) }
        }
        val fg = icon.foreground ?: return null
        val layer = drawLayer(fg, px)
        // Доля закрашенного в безопасной зоне: значок во всю плитку силуэтом не станет.
        val r = px * 33f / 108f
        val cx = px / 2f
        var inside = 0
        var filled = 0
        val step = (px / 54).coerceAtLeast(1)
        var y = 0
        while (y < px) {
            var x = 0
            while (x < px) {
                val dx = x - cx
                val dy = y - cx
                if (dx * dx + dy * dy <= r * r) {
                    inside++
                    if ((layer.getPixel(x, y) ushr 24) > 128) filled++
                }
                x += step
            }
            y += step
        }
        return layer.takeIf { inside > 0 && filled.toFloat() / inside in 0.04f..0.45f }
    }

    private fun drawLayer(layer: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, px, px)
        layer.draw(Canvas(bmp))
        return bmp
    }

    /** Рисунок цветом стиля с тиснением: светлая кромка снизу (на тёмной плитке — тень сверху). */
    private fun drawGlyph(c: Canvas, glyph: Bitmap, t: Tones, u: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = PorterDuffColorFilter(if (t.dark) 0x66000000 else 0xB3FFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
        c.drawBitmap(glyph, 0f, if (t.dark) -1.1f * u else 1.1f * u, p)
        p.colorFilter = PorterDuffColorFilter(t.glyph, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(glyph, 0f, 0f, p)
    }

    /** Значок целиком — двухцветным медальоном в цветах стиля, в вдавленной лунке. */
    private fun drawMedallion(c: Canvas, icon: Drawable, px: Int, u: Float, t: Tones) {
        val cx = px / 2f
        val r = 23 * u
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Лунка: тень сверху внутри и светлая кромка снизу.
        p.color = ColorUtils.setAlphaComponent(if (t.dark) 0xFF000000.toInt() else 0xFF5A4E42.toInt(), if (t.dark) 120 else 60)
        c.drawCircle(cx, cx - 0.9f * u, r + 1.4f * u, p)
        p.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), if (t.dark) 28 else 150)
        c.drawCircle(cx, cx + 0.9f * u, r + 1.4f * u, p)
        // Сам значок: оттенки серого → от цвета рисунка к светлому цвету стиля.
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val bc = Canvas(bmp)
        bc.clipPath(Path().apply { addCircle(cx, cx, r, Path.Direction.CW) })
        if (icon is AdaptiveIconDrawable) {
            val half = r * 1.5f
            for (layer in listOfNotNull(icon.background, icon.foreground)) {
                layer.setBounds((cx - half).toInt(), (cx - half).toInt(), (cx + half).toInt(), (cx + half).toInt())
                layer.draw(bc)
            }
        } else {
            icon.setBounds((cx - r).toInt(), (cx - r).toInt(), (cx + r).toInt(), (cx + r).toInt())
            icon.draw(bc)
        }
        val dark = if (t.dark) ColorUtils.blendARGB(t.face, 0xFF000000.toInt(), 0.35f) else t.glyph
        val light = if (t.dark) t.glyph else t.light
        p.colorFilter = ColorMatrixColorFilter(duotone(dark, light))
        c.drawBitmap(bmp, 0f, 0f, p)
    }

    /**
     * Матрица: яркость пикселя → цвет между dark и light. Контраст усилен: у цветных значков яркости
     * близки (красный, жёлтый, зелёный), без этого медальон выходит ровно-серым.
     */
    private fun duotone(dark: Int, light: Int, contrast: Float = 1.9f): ColorMatrix {
        val d = floatArrayOf(android.graphics.Color.red(dark).toFloat(), android.graphics.Color.green(dark).toFloat(), android.graphics.Color.blue(dark).toFloat())
        val l = floatArrayOf(android.graphics.Color.red(light).toFloat(), android.graphics.Color.green(light).toFloat(), android.graphics.Color.blue(light).toFloat())
        val w = floatArrayOf(0.299f, 0.587f, 0.114f)
        val m = FloatArray(20)
        for (ch in 0..2) {
            val k = (l[ch] - d[ch]) / 255f
            for (i in 0..2) m[ch * 5 + i] = k * contrast * w[i]
            m[ch * 5 + 4] = d[ch] + k * 128f * (1 - contrast)
        }
        m[18] = 1f
        return ColorMatrix(m)
    }
}
