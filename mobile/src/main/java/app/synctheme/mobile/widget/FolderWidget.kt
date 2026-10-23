package app.synctheme.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.synctheme.R
import app.synctheme.core.Theme
import app.synctheme.mobile.render.Colors
import app.synctheme.mobile.util.Compat
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * «Приложения» в виде папки: спокойный значок или невидимое место на рабочем столе.
 * Нажатие раскрывает [FolderActivity] из того места, где стоит виджет.
 */
object FolderWidget {
    fun views(context: Context, config: WidgetConfig, theme: Theme, dp: SizeF, widgetId: Int): RemoteViews {
        val title = config.title.trim()
        val rv = if (config.folder == FolderMode.HIDDEN) {
            RemoteViews(context.packageName, R.layout.wf_hidden)
        } else {
            RemoteViews(context.packageName, R.layout.wf_folder).also { rv ->
                val titleDp = if (title.isNotEmpty()) 20f else 0f
                // Значок занимает большую часть клетки, но не разрастается на большом виджете.
                val iconDp = (min(dp.width, dp.height - titleDp) * 0.74f).coerceIn(28f, 72f)
                val px = (iconDp * context.resources.displayMetrics.density).roundToInt()
                rv.setImageViewBitmap(R.id.folder_icon, glyph(context, config, theme, px))
                // До Android 12 размер значка задаёт разметка (56 dp).
                if (Compat.modernWidgets) {
                    rv.setViewLayoutWidth(R.id.folder_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                    rv.setViewLayoutHeight(R.id.folder_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                }
                rv.setViewVisibility(R.id.folder_title, if (title.isEmpty()) View.GONE else View.VISIBLE)
                if (title.isNotEmpty()) {
                    rv.setTextViewText(R.id.folder_title, title)
                    rv.setTextColor(R.id.folder_title, config.textColor ?: theme.textColor)
                }
            }
        }
        rv.setContentDescription(R.id.widget_root, "Папка" + if (title.isEmpty()) "" else ": $title")
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) rv.setOnClickPendingIntent(R.id.widget_root, open(context, widgetId))
        return rv
    }

    /**
     * Нажатие на виджет открывает папку. PendingIntent изменяемый: лаунчер дописывает в него
     * место виджета на экране (sourceBounds) — оттуда папка и раскрывается.
     */
    fun open(context: Context, widgetId: Int): PendingIntent = PendingIntent.getActivity(
        context, 7000 + widgetId,
        Intent(context, FolderActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Значок папки: фигура стиля и четыре точки или силуэта приложений одного цвета — без цветных
     * значков, чтобы не спорить с обоями и другими значками рабочего стола.
     */
    fun glyph(context: Context, config: WidgetConfig, theme: Theme, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = px.toFloat()
        val main = config.textColor ?: theme.textColor
        val accent = config.accentColor ?: theme.accentColor
        val surface = Colors.widgetSurface(theme)
        val opacity = config.opacity.coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Цвет рисунка: на заметной акцентной фигуре — контрастный к ней, иначе — цвет текста.
        var mark = main
        when (config.style) {
            "glass" -> {
                val r = RectF(s * 0.04f, s * 0.04f, s * 0.96f, s * 0.96f)
                paint.color = Colors.withAlpha(ColorUtils.blendARGB(surface, 0xFFFFFFFF.toInt(), 0.18f), opacity)
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.018f
                paint.color = Colors.withAlpha(0xFFFFFFFF.toInt(), 0.18f + 0.3f * opacity)
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, paint)
            }
            "neon" -> {
                paint.color = Colors.withAlpha(0xFF0B0B12.toInt(), opacity)
                c.drawCircle(s / 2, s / 2, s * 0.44f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.024f
                paint.color = accent
                paint.maskFilter = BlurMaskFilter(s * 0.04f, BlurMaskFilter.Blur.SOLID)
                c.drawCircle(s / 2, s / 2, s * 0.42f, paint)
                paint.maskFilter = null
                mark = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.35f)
            }
            "flower", "cookie" -> {
                val shape = context.getDrawable(if (config.style == "flower") R.drawable.wsh_clover else R.drawable.wsh_cookie)!!.mutate()
                shape.setTint(accent)
                shape.alpha = (opacity * 255).roundToInt()
                shape.setBounds(0, 0, px, px)
                shape.draw(c)
                if (opacity >= 0.5f) mark = if (Colors.isLight(accent)) 0xFF141414.toInt() else 0xFFFFFFFF.toInt()
            }
            "capsule" -> {
                paint.color = Colors.withAlpha(ColorUtils.blendARGB(surface, accent, 0.3f) or 0xFF000000.toInt(), opacity)
                c.drawCircle(s / 2, s / 2, s * 0.46f, paint)
            }
            else -> { // «Минимал»: еле заметный круг и тонкий контур.
                paint.color = Colors.withAlpha(surface, opacity * 0.8f)
                c.drawCircle(s / 2, s / 2, s * 0.45f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.012f
                paint.color = Colors.withAlpha(main, 0.28f)
                c.drawCircle(s / 2, s / 2, s * 0.45f, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.maskFilter = null
        paint.color = Colors.withAlpha(mark, 0.92f)
        val apps = config.apps.ifEmpty { AppCatalog.defaults(context) }.take(4)
        val step = s * 0.135f
        val centers = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).map { (dx, dy) -> s / 2 + dx * step to s / 2 + dy * step }
        when (config.glyph) {
            FolderGlyph.DOTS -> centers.forEach { (cx, cy) -> c.drawCircle(cx, cy, s * 0.054f, paint) }
            FolderGlyph.ICONS -> {
                // Настоящие значки приложений, как в папке лаунчера: рядом с другими значками смотрится своим.
                val side = (s * 0.22f).roundToInt()
                val round = config.style != "glass"
                val iconStep = s * 0.125f
                listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).forEachIndexed { i, (dx, dy) ->
                    val cx = s / 2 + dx * iconStep
                    val cy = s / 2 + dy * iconStep
                    val icon = apps.getOrNull(i)?.let { AppCatalog.icon(context, it, side, round) }
                    if (icon != null) c.drawBitmap(icon, null, RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
                    else c.drawCircle(cx, cy, s * 0.054f, paint)
                }
            }
            FolderGlyph.SILHOUETTES -> centers.forEachIndexed { i, (cx, cy) ->
                val drawn = apps.getOrNull(i)?.let { silhouette(context, c, it, cx, cy, s * 0.12f, paint.color) } == true
                // Нет одноцветного значка — плитка-контур того же размера, а не точка: силуэты не путаются с «Точками».
                if (!drawn) tile(c, cx, cy, s * 0.085f, paint, outline = true)
            }
            FolderGlyph.TILES -> centers.forEach { (cx, cy) -> tile(c, cx, cy, s * 0.095f, paint, outline = false) }
            FolderGlyph.PETALS -> {
                // Четыре лепестка вокруг середины — как цветок, повёрнутый на 45°.
                val petal = RectF(-s * 0.075f, -s * 0.2f, s * 0.075f, -s * 0.03f)
                for (k in 0 until 4) {
                    c.save()
                    c.rotate(45f + 90f * k, s / 2, s / 2)
                    c.translate(s / 2, s / 2)
                    c.drawOval(petal, paint)
                    c.restore()
                }
                c.drawCircle(s / 2, s / 2, s * 0.035f, paint)
            }
            FolderGlyph.SPARKLE -> {
                star(c, s * 0.46f, s * 0.54f, s * 0.2f, paint)
                star(c, s * 0.66f, s * 0.32f, s * 0.08f, paint)
            }
            FolderGlyph.ORBIT -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.03f
                c.drawCircle(s / 2, s / 2, s * 0.2f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(s / 2, s / 2, s * 0.075f, paint)
                c.drawCircle(s / 2 + s * 0.2f * 0.707f, s / 2 - s * 0.2f * 0.707f, s * 0.045f, paint)
            }
            FolderGlyph.LETTER -> {
                // Первая буква названия папки (или первого приложения) — спокойная монограмма.
                val letter = (config.title.trim().ifEmpty { apps.firstOrNull()?.let { AppCatalog.label(context, it) }.orEmpty() })
                    .firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "A"
                val text = Paint(paint).apply {
                    typeface = runCatching { context.resources.getFont(R.font.manrope_700) }.getOrNull()
                    textAlign = Paint.Align.CENTER
                    textSize = s * 0.42f
                }
                val bounds = android.graphics.Rect().also { text.getTextBounds(letter, 0, letter.length, it) }
                c.drawText(letter, s / 2, s / 2 - bounds.exactCenterY(), text)
            }
        }
        return bmp
    }

    /** Скруглённая плитка — как значок приложения в сетке. */
    private fun tile(c: Canvas, cx: Float, cy: Float, half: Float, paint: Paint, outline: Boolean) {
        val r = RectF(cx - half, cy - half, cx + half, cy + half)
        if (outline) {
            val stroke = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = half * 0.28f }
            r.inset(stroke.strokeWidth / 2, stroke.strokeWidth / 2)
            c.drawRoundRect(r, half * 0.6f, half * 0.6f, stroke)
        } else {
            c.drawRoundRect(r, half * 0.6f, half * 0.6f, paint)
        }
    }

    /** Четырёхлучевая звёздочка с вогнутыми сторонами. */
    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val k = r * 0.16f
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx + k, cy - k, cx + r, cy)
            quadTo(cx + k, cy + k, cx, cy + r)
            quadTo(cx - k, cy + k, cx - r, cy)
            quadTo(cx - k, cy - k, cx, cy - r)
            close()
        }
        c.drawPath(path, paint)
    }

    /**
     * Одноцветный значок приложения: слой «тематических значков» Android 13+, а если его нет — передний
     * слой значка (сам рисунок без фона), залитый одним цветом.
     */
    private fun silhouette(context: Context, canvas: Canvas, component: String, cx: Float, cy: Float, half: Float, color: Int): Boolean {
        val icon = runCatching { context.packageManager.getActivityIcon(ComponentName.unflattenFromString(component)!!) }.getOrNull()
        val adaptive = icon as? AdaptiveIconDrawable ?: return false
        val mono = (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) adaptive.monochrome else null)?.mutate()
            ?: return foreground(canvas, adaptive, cx, cy, half, color)
        // Слои адаптивного значка шире видимой части в полтора раза.
        val r = half * 1.5f
        mono.setBounds((cx - r).roundToInt(), (cy - r).roundToInt(), (cx + r).roundToInt(), (cy + r).roundToInt())
        mono.setTint(color)
        mono.draw(canvas)
        return true
    }

    /** Передний слой как силуэт; если он закрывает почти всю площадь (рисунок во весь значок) — не годится. */
    private fun foreground(canvas: Canvas, icon: AdaptiveIconDrawable, cx: Float, cy: Float, half: Float, color: Int): Boolean {
        val fg = icon.foreground ?: return false
        val side = (half * 3).roundToInt().coerceAtLeast(8)
        val layer = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        fg.setBounds(0, 0, side, side)
        fg.draw(Canvas(layer))
        // Видимая часть значка — центральные 2/3 слоя.
        val from = side / 6
        val to = side - from
        var covered = 0
        for (y in from until to step 2) for (x in from until to step 2) if (layer.getPixel(x, y) ushr 24 > 128) covered++
        val total = ((to - from + 1) / 2) * ((to - from + 1) / 2)
        if (covered == 0 || covered > total * 0.85f) {
            layer.recycle()
            return false
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        val r = half * 1.5f
        canvas.drawBitmap(layer, null, RectF(cx - r, cy - r, cx + r, cy + r), paint)
        layer.recycle()
        return true
    }
}
