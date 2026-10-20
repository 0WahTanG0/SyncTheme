package app.synctheme.mobile.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.synctheme.R
import app.synctheme.core.DigitFont
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Что показывает вертикальный виджет: от этого зависит, сколько строк поместится. */
internal class VerticalContent(
    /** Текст главного значения («11°», «89%»); у часов и календаря не нужен — там цифры. */
    val main: String = "00",
    /** Сколько строк списка есть: часы прогноза, события, приложения. */
    val rows: Int = 0,
    val alarm: String? = null,
    /** Подсказка вместо событий: нет доступа к календарю или событий нет. */
    val hint: String? = null,
    /** Описание погоды: в высоком виджете — в две строки по словам. */
    val desc: String? = null,
    val charging: Boolean = false,
    val remain: Boolean = false,
    val title: Boolean = false,
)

/**
 * Столбики и высокие виджеты — зеркало полосок 4×1: там главное значение во всю высоту, здесь — во всю
 * ширину. Кегль подбирается по настоящему размеру виджета и метрикам шрифта, а пустые поля шрифта над
 * и под цифрами срезаются отрицательными отступами — поэтому цифры, подписи и строки стоят одной
 * плотной группой. Строки, которые не помещаются целиком, прячутся, а не обрезаются.
 */
internal object VerticalFit {
    /** Доля ширины под главное значение: запас, чтобы округление кегля не дало многоточие. */
    private const val FILL = 0.94f

    /** Промежуток между часами и минутами — в долях кегля. */
    private const val HOURS_GAP = 0.1f

    /** Полоса заряда короче этого не рисуется. */
    private const val MIN_BAR = 28f

    private val typefaces = HashMap<String, Typeface>()
    private val metrics = HashMap<String, Em>()

    /** Размеры текста в долях кегля: ширина, поля над и под «чернилами», высота «чернил». */
    private class Em(val advance: Float, val above: Float, val below: Float, val ink: Float)

    /** Главный шрифт стиля — тот же, что в разметке (FONTS["main"] в gen_widgets.py). */
    private fun mainFont(family: String): DigitFont = when (family) {
        "bold" -> DigitFont.NARROW
        "blob" -> DigitFont.ROUND
        "glass" -> DigitFont.GROTESK
        "neon" -> DigitFont.WIDE
        else -> DigitFont.THIN
    }

    private fun typeface(context: Context, font: DigitFont): Typeface = synchronized(typefaces) {
        typefaces.getOrPut(font.name) {
            val res = font.res?.let { context.resources.getIdentifier(it, "font", context.packageName) } ?: 0
            if (res == 0) Typeface.create("sans-serif-thin", Typeface.NORMAL)
            else runCatching { context.resources.getFont(res) }.getOrDefault(Typeface.DEFAULT_BOLD)
        }
    }

    /**
     * Метрики текста. Для цифр (digits = true) — самая широкая пара цифр и общая высота всех цифр:
     * часы, минуты и число месяца меняются, а кегль должен подходить всем.
     */
    private fun measure(context: Context, font: DigitFont, text: String, digits: Boolean): Em = synchronized(metrics) {
        metrics.getOrPut("${font.name}|$digits|${if (digits) "" else text}") {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = typeface(context, font)
                textSize = 100f
            }
            val fm = p.fontMetrics
            val ink = if (digits) "0123456789" else text
            val bounds = Rect().also { p.getTextBounds(ink, 0, ink.length, it) }
            val advance = if (digits) (0..9).maxOf { p.measureText(it.toString()) } * 2 else p.measureText(text)
            Em(advance / 100f, (bounds.top - fm.ascent) / 100f, (fm.descent - bounds.bottom) / 100f, bounds.height() / 100f)
        }
    }

    fun apply(
        context: Context, rv: RemoteViews, layout: WidgetLayout, kind: WidgetKind, family: String, digit: DigitFont,
        size: WidgetSize, dp: SizeF, content: VerticalContent,
    ) {
        if (!layout.has(R.id.box_v)) return
        val spec = VSpecs.of(size)
        val dm = context.resources.displayMetrics
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm) / dm.density
        val w = dp.width - 2 * spec.padH
        val h = dp.height - 2 * spec.padV
        val tall = size == WidgetSize.TALL
        val divBlock = 2 * spec.div + 1

        fun main(id: Int, s: Float, em: Em, top: Float, bottom: Float = 0f) {
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, s)
            rv.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, top - em.above * s, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutMargin(id, RemoteViews.MARGIN_BOTTOM, bottom - em.below * s, TypedValue.COMPLEX_UNIT_DIP)
        }
        fun show(id: Int, visible: Boolean) {
            if (layout.has(id)) rv.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
        }
        /** Кегль, при котором текст ровно по ширине и не выше [maxInk]. */
        fun fit(em: Em, maxInk: Float) = min(w * FILL / em.advance, maxInk / em.ink).coerceIn(8f, 400f)

        when (kind) {
            WidgetKind.CLOCK -> {
                val em = measure(context, digit, "", digits = true)
                val divider = layout.has(R.id.if_line)
                val labels = 2 * sp(spec.labelBox) + spec.gap + if (divider) divBlock else spec.div
                val alarmH = spec.gap + sp(spec.alarmSp) + spec.alarmDp
                fun size(alarm: Boolean) = fit(em, (h - labels - if (alarm) alarmH else 0f) / (2 + HOURS_GAP / em.ink))
                // Будильник — только если с ним цифры остаются крупными.
                val alarm = content.alarm != null && size(true) > 0.85f * size(false)
                val s = size(alarm)
                main(R.id.m_hours, s, em, top = spec.gap)
                main(R.id.m_minutes, s, em, top = HOURS_GAP * s)
                show(R.id.row_alarm, alarm)
                // В узком столбике — только время будильника, без дня.
                if (alarm && !tall) rv.setTextViewText(R.id.s_alarm, content.alarm!!.substringAfterLast(' '))
            }
            WidgetKind.CALENDAR -> {
                val em = measure(context, mainFont(family), "", digits = true)
                val head = 2 * sp(spec.labelBox) + spec.gap
                val s = fit(em, h - head)
                main(R.id.m_day, s, em, top = spec.gap, bottom = 2f)
                var left = h - head - em.ink * s - 2f - divBlock
                val rowH = sp(spec.eventSp) + spec.eventDp
                val rows = min(content.rows, max(0, floor(left / rowH).toInt()))
                for (i in 0 until 3) show(listOf(R.id.row_ev0, R.id.row_ev1, R.id.row_ev2)[i], i < rows)
                left -= rows * rowH
                val hint = content.hint != null && layout.has(R.id.s_ev_empty) && left >= sp(spec.hintSp)
                show(R.id.s_ev_empty, hint)
                if (hint) rv.setTextViewText(R.id.s_ev_empty, content.hint!!)
                show(R.id.if_line, rows > 0 || hint)
            }
            WidgetKind.WEATHER -> {
                val em = measure(context, mainFont(family), content.main, digits = false)
                val head = spec.icon + spec.gap + sp(spec.smallBox) + if (tall) sp(spec.cityBox) + sp(spec.descBox) else 0f
                val s = fit(em, h - head)
                main(R.id.m_temp, s, em, top = spec.gap, bottom = 2f)
                // «Переменная облачность» — по слову в строке: кегль уменьшится, только если слово не влезает.
                content.desc?.let { if (tall) rv.setTextViewText(R.id.a_desc, it.replaceFirst(' ', '\n')) }
                val left = h - head - em.ink * s - 2f - divBlock
                val rowH = sp(spec.hourSp) + spec.hourDp
                val rows = min(content.rows, max(0, floor(left / rowH).toInt()))
                HOURS.forEachIndexed { i, id -> show(id, i < rows) }
                show(R.id.if_line, rows > 0)
            }
            WidgetKind.BATTERY -> {
                val em = measure(context, mainFont(family), content.main, digits = false)
                val bolt = if (!tall && content.charging) 14f else 0f
                val state = sp(spec.stateBox)
                var extras = if (tall) sp(spec.extraBox) * (1 + if (content.remain) 1 else 0) else 0f
                // Подробности (температура, «полная через…») — только если полосе остаётся место.
                if (h - bolt - state - extras - divBlock - MIN_BAR < h * 0.3f) extras = 0f
                val s = fit(em, min(h * 0.32f, h - bolt - state - extras))
                main(R.id.m_pct, s, em, top = 0f)
                show(R.id.box_vbar, h - bolt - state - extras - em.ink * s - 2 * spec.div >= MIN_BAR)
                show(R.id.s_remain, extras > 0 && content.remain)
                show(R.id.s_temp, extras > 0)
            }
            WidgetKind.APPS -> {
                // Значок не шире своей колонки: в высоком виджете их две в ряд.
                val side = min(spec.appIcon, (if (tall) w / 2 else w) - 6f)
                APP_ICONS.forEach { id ->
                    rv.setViewLayoutWidth(id, side, TypedValue.COMPLEX_UNIT_DIP)
                    rv.setViewLayoutHeight(id, side, TypedValue.COMPLEX_UNIT_DIP)
                }
                if (layout.has(R.id.fx_ap0)) APP_RINGS.forEach { id ->
                    rv.setViewLayoutWidth(id, side + 8, TypedValue.COMPLEX_UNIT_DIP)
                    rv.setViewLayoutHeight(id, side + 8, TypedValue.COMPLEX_UNIT_DIP)
                }
                val icon = side + spec.appGap
                if (tall) {
                    val title = if (content.title) sp(spec.titleBox) else 0f
                    val rows = min((content.rows + 1) / 2, max(1, floor((h - title) / icon).toInt()))
                    VAPP_ROWS.forEachIndexed { r, id -> show(id, r < rows) }
                    APPS.forEachIndexed { i, id -> show(id, i < content.rows && i < rows * 2) }
                } else {
                    val n = min(content.rows, max(1, floor(h / icon).toInt()))
                    APPS.forEachIndexed { i, id -> show(id, i < n) }
                }
            }
        }
    }

    private val HOURS = intArrayOf(R.id.cell_h0, R.id.cell_h1, R.id.cell_h2, R.id.cell_h3, R.id.cell_h4, R.id.cell_h5)
    private val APPS = intArrayOf(R.id.cell_ap0, R.id.cell_ap1, R.id.cell_ap2, R.id.cell_ap3, R.id.cell_ap4, R.id.cell_ap5, R.id.cell_ap6, R.id.cell_ap7)
    private val VAPP_ROWS = intArrayOf(R.id.row_vap0, R.id.row_vap1, R.id.row_vap2, R.id.row_vap3)
    private val APP_ICONS = intArrayOf(R.id.ap_0, R.id.ap_1, R.id.ap_2, R.id.ap_3, R.id.ap_4, R.id.ap_5, R.id.ap_6, R.id.ap_7)
    private val APP_RINGS = intArrayOf(R.id.fx_ap0, R.id.fx_ap1, R.id.fx_ap2, R.id.fx_ap3, R.id.fx_ap4, R.id.fx_ap5, R.id.fx_ap6, R.id.fx_ap7)
}
