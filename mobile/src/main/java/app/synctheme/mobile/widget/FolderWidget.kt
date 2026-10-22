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
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.synctheme.R
import app.synctheme.core.Theme
import app.synctheme.mobile.render.Colors
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
                rv.setViewLayoutWidth(R.id.folder_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(R.id.folder_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
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
        centers.forEachIndexed { i, (cx, cy) ->
            val drawn = config.glyph == FolderGlyph.SILHOUETTES && apps.getOrNull(i)?.let { silhouette(context, c, it, cx, cy, s * 0.12f, paint.color) } == true
            if (!drawn) c.drawCircle(cx, cy, s * 0.054f, paint)
        }
        return bmp
    }

    /** Одноцветный значок приложения (тот же, что у «тематических значков» Android 13+). */
    private fun silhouette(context: Context, canvas: Canvas, component: String, cx: Float, cy: Float, half: Float, color: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val icon = runCatching { context.packageManager.getActivityIcon(ComponentName.unflattenFromString(component)!!) }.getOrNull()
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome?.mutate() ?: return false
        // Слои адаптивного значка шире видимой части в полтора раза.
        val r = half * 1.5f
        mono.setBounds((cx - r).roundToInt(), (cy - r).roundToInt(), (cx + r).roundToInt(), (cy + r).roundToInt())
        mono.setTint(color)
        mono.draw(canvas)
        return true
    }
}
