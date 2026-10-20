package app.synctheme.mobile.widget

import app.synctheme.R

// Сгенерировано scripts/gen_widgets.py — не редактировать вручную.

/**
 * Разметка виджета: ресурс, есть ли подложка-фигура акцентного цвета и все элементы с ролями.
 * roles — по два символа на элемент: роль (m — основной текст, b — подложка …) и признак
 * выравнивания (l — текст, L — контейнер, . — нет).
 */
class WidgetLayout(val res: Int, val accentBackground: Boolean, val roles: String, val ids: IntArray) {
    private val present = ids.toHashSet()

    fun has(id: Int) = id in present
}

object WidgetLayouts {
    private val cache = HashMap<String, WidgetLayout?>()

    /** Ключ — «вид_стиль_размер», например «wx_blob_m». */
    @Synchronized
    operator fun get(key: String): WidgetLayout? = cache.getOrPut(key) {
        clock(key) ?: cal(key) ?: wx(key) ?: bat(key) ?: apps(key)
    }

    private fun clock(key: String): WidgetLayout? = when (key) {
        "clock_bold_s" -> WidgetLayout(R.layout.w_clock_bold_s, false, "..b..Lmlal", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.box_al, R.id.m_hours_al, R.id.a_minutes_al))
        "clock_bold_strip" -> WidgetLayout(R.layout.w_clock_bold_strip, false, "..b.m.a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time, R.id.a_weekday, R.id.m_date))
        "clock_bold_m" -> WidgetLayout(R.layout.w_clock_bold_m, false, "..b..Lmlalsl", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.box_al, R.id.m_hours_al, R.id.a_minutes_al, R.id.s_date_al))
        "clock_bold_wide" -> WidgetLayout(R.layout.w_clock_bold_wide, false, "..b.m.a.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time, R.id.a_ampm, R.id.s_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_bold_l" -> WidgetLayout(R.layout.w_clock_bold_l, false, "..b..Lmlalml..S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.box_al, R.id.m_hours_al, R.id.a_minutes_al, R.id.m_date_al, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_blob_s" -> WidgetLayout(R.layout.w_clock_blob_s, true, "..B.o.", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.o_time))
        "clock_blob_strip" -> WidgetLayout(R.layout.w_clock_blob_strip, false, "..b.m.P.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time, R.id.bo_date))
        "clock_blob_m" -> WidgetLayout(R.layout.w_clock_blob_m, true, "..B.o.n.", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.o_time, R.id.n_date))
        "clock_blob_wide" -> WidgetLayout(R.layout.w_clock_blob_wide, false, "..b.h.o.a.m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.o_time, R.id.a_weekday, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_blob_l" -> WidgetLayout(R.layout.w_clock_blob_l, false, "..b.h.o.m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.o_time, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_glass_s" -> WidgetLayout(R.layout.w_clock_glass_s, false, "..g.mlsl", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_time_al, R.id.s_date_al))
        "clock_glass_strip" -> WidgetLayout(R.layout.w_clock_glass_strip, false, "..g.m.F.a.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_time, R.id.if_vline, R.id.a_weekday, R.id.m_date))
        "clock_glass_m" -> WidgetLayout(R.layout.w_clock_glass_m, false, "..g..LA.s.mlsl", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.box_al, R.id.ia_dot, R.id.s_weekday, R.id.m_time_al, R.id.s_date_al))
        "clock_glass_wide" -> WidgetLayout(R.layout.w_clock_glass_wide, false, "..g..LA.s.mlF.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.box_al, R.id.ia_dot, R.id.s_weekday, R.id.m_time_al, R.id.if_vline, R.id.m_date, R.id.s_month, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_glass_l" -> WidgetLayout(R.layout.w_clock_glass_l, false, "..g..LA.s.mlmlF...S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.box_al, R.id.ia_dot, R.id.s_weekday, R.id.m_time_al, R.id.m_date_al, R.id.if_line, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_neon_s" -> WidgetLayout(R.layout.w_clock_neon_s, false, "..b.x.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_time))
        "clock_neon_strip" -> WidgetLayout(R.layout.w_clock_neon_strip, false, "..b.x.m.a.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_time, R.id.a_date))
        "clock_neon_m" -> WidgetLayout(R.layout.w_clock_neon_m, false, "..b.x.m.a.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_time, R.id.a_date))
        "clock_neon_wide" -> WidgetLayout(R.layout.w_clock_neon_wide, false, "..b.x.a...A.a.m.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.a_weekday, R.id.row_alarm, R.id.ia_alarm, R.id.a_alarm, R.id.m_time, R.id.s_date))
        "clock_neon_l" -> WidgetLayout(R.layout.w_clock_neon_l, false, "..b.x.a.x.m.s...A.a.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.a_weekday, R.id.fx_g2, R.id.m_time, R.id.s_date, R.id.row_alarm, R.id.ia_alarm, R.id.a_alarm))
        "clock_minimal_s" -> WidgetLayout(R.layout.w_clock_minimal_s, false, "..b.mlal", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time_al, R.id.a_date_al))
        "clock_minimal_strip" -> WidgetLayout(R.layout.w_clock_minimal_strip, false, "..b.m.F.a.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time, R.id.if_vline, R.id.a_date))
        "clock_minimal_m" -> WidgetLayout(R.layout.w_clock_minimal_m, false, "..b.mlF.al", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time_al, R.id.if_line, R.id.a_date_al))
        "clock_minimal_wide" -> WidgetLayout(R.layout.w_clock_minimal_wide, false, "..b.m.F.a.m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time, R.id.if_vline, R.id.a_weekday, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_minimal_l" -> WidgetLayout(R.layout.w_clock_minimal_l, false, "..b.mlF.a...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_time_al, R.id.if_line, R.id.a_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_blob_s" -> WidgetLayout(R.layout.w_clock_analog_blob_s, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_blob_s_ns" -> WidgetLayout(R.layout.w_clock_analog_blob_s_ns, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_blob_strip" -> WidgetLayout(R.layout.w_clock_analog_blob_strip, false, "..b...a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_blob_strip_ns" -> WidgetLayout(R.layout.w_clock_analog_blob_strip_ns, false, "..b...a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_blob_m" -> WidgetLayout(R.layout.w_clock_analog_blob_m, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_blob_m_ns" -> WidgetLayout(R.layout.w_clock_analog_blob_m_ns, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_blob_wide" -> WidgetLayout(R.layout.w_clock_analog_blob_wide, false, "..b...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_blob_wide_ns" -> WidgetLayout(R.layout.w_clock_analog_blob_wide_ns, false, "..b...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_blob_l" -> WidgetLayout(R.layout.w_clock_analog_blob_l, false, "..b...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_blob_l_ns" -> WidgetLayout(R.layout.w_clock_analog_blob_l_ns, false, "..b...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_neon_s" -> WidgetLayout(R.layout.w_clock_analog_neon_s, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_neon_s_ns" -> WidgetLayout(R.layout.w_clock_analog_neon_s_ns, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_neon_strip" -> WidgetLayout(R.layout.w_clock_analog_neon_strip, false, "..b.x...a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_neon_strip_ns" -> WidgetLayout(R.layout.w_clock_analog_neon_strip_ns, false, "..b.x...a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_neon_m" -> WidgetLayout(R.layout.w_clock_analog_neon_m, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_neon_m_ns" -> WidgetLayout(R.layout.w_clock_analog_neon_m_ns, false, "....", intArrayOf(R.id.widget_root, R.id.analog))
        "clock_analog_neon_wide" -> WidgetLayout(R.layout.w_clock_analog_neon_wide, false, "..b.x...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_neon_wide_ns" -> WidgetLayout(R.layout.w_clock_analog_neon_wide_ns, false, "..b.x...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_neon_l" -> WidgetLayout(R.layout.w_clock_analog_neon_l, false, "..b.x...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_neon_l_ns" -> WidgetLayout(R.layout.w_clock_analog_neon_l_ns, false, "..b.x...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_numbers_s" -> WidgetLayout(R.layout.w_clock_analog_numbers_s, false, "..g...", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog))
        "clock_analog_numbers_s_ns" -> WidgetLayout(R.layout.w_clock_analog_numbers_s_ns, false, "..g...", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog))
        "clock_analog_numbers_strip" -> WidgetLayout(R.layout.w_clock_analog_numbers_strip, false, "..g...a.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_numbers_strip_ns" -> WidgetLayout(R.layout.w_clock_analog_numbers_strip_ns, false, "..g...a.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_numbers_m" -> WidgetLayout(R.layout.w_clock_analog_numbers_m, false, "..g...", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog))
        "clock_analog_numbers_m_ns" -> WidgetLayout(R.layout.w_clock_analog_numbers_m_ns, false, "..g...", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog))
        "clock_analog_numbers_wide" -> WidgetLayout(R.layout.w_clock_analog_numbers_wide, false, "..g...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_numbers_wide_ns" -> WidgetLayout(R.layout.w_clock_analog_numbers_wide_ns, false, "..g...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_numbers_l" -> WidgetLayout(R.layout.w_clock_analog_numbers_l, false, "..g...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_numbers_l_ns" -> WidgetLayout(R.layout.w_clock_analog_numbers_l_ns, false, "..g...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_minimal_s" -> WidgetLayout(R.layout.w_clock_analog_minimal_s, false, "..b...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog))
        "clock_analog_minimal_s_ns" -> WidgetLayout(R.layout.w_clock_analog_minimal_s_ns, false, "..b...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog))
        "clock_analog_minimal_strip" -> WidgetLayout(R.layout.w_clock_analog_minimal_strip, false, "..b...a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_minimal_strip_ns" -> WidgetLayout(R.layout.w_clock_analog_minimal_strip_ns, false, "..b...a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date))
        "clock_analog_minimal_m" -> WidgetLayout(R.layout.w_clock_analog_minimal_m, false, "..b...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog))
        "clock_analog_minimal_m_ns" -> WidgetLayout(R.layout.w_clock_analog_minimal_m_ns, false, "..b...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog))
        "clock_analog_minimal_wide" -> WidgetLayout(R.layout.w_clock_analog_minimal_wide, false, "..b...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_minimal_wide_ns" -> WidgetLayout(R.layout.w_clock_analog_minimal_wide_ns, false, "..b...a.m.s...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.a_weekday, R.id.m_date, R.id.s_time, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_minimal_l" -> WidgetLayout(R.layout.w_clock_analog_minimal_l, false, "..b...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        "clock_analog_minimal_l_ns" -> WidgetLayout(R.layout.w_clock_analog_minimal_l_ns, false, "..b...m...S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.analog, R.id.m_date, R.id.row_alarm, R.id.is_alarm, R.id.s_alarm))
        else -> null
    }

    private fun cal(key: String): WidgetLayout? = when (key) {
        "cal_bold_s" -> WidgetLayout(R.layout.w_cal_bold_s, false, "..b.alml", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday_al, R.id.m_day_al))
        "cal_bold_strip" -> WidgetLayout(R.layout.w_cal_bold_strip, false, "..b.m.a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_day, R.id.a_weekday, R.id.m_ev0_title))
        "cal_bold_m" -> WidgetLayout(R.layout.w_cal_bold_m, false, "..b.a.m.s.F.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_line, R.id.m_ev0_title))
        "cal_bold_wide" -> WidgetLayout(R.layout.w_cal_bold_wide, false, "..b.a.m.s.F.....m.s.....m.s.....m.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_vline, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.s_ev0_time, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev1_time, R.id.row_ev2, R.id.ed_2, R.id.m_ev2_title, R.id.s_ev2_time, R.id.s_ev_empty))
        "cal_bold_l" -> WidgetLayout(R.layout.w_cal_bold_l, false, "..b.a.s.s.s.s.s.s.s...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m.F.....m.....m.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_month, R.id.s_wd0, R.id.s_wd1, R.id.s_wd2, R.id.s_wd3, R.id.s_wd4, R.id.s_wd5, R.id.s_wd6, R.id.tb_0, R.id.m_cd0, R.id.tb_1, R.id.m_cd1, R.id.tb_2, R.id.m_cd2, R.id.tb_3, R.id.m_cd3, R.id.tb_4, R.id.m_cd4, R.id.tb_5, R.id.m_cd5, R.id.tb_6, R.id.m_cd6, R.id.tb_7, R.id.m_cd7, R.id.tb_8, R.id.m_cd8, R.id.tb_9, R.id.m_cd9, R.id.tb_10, R.id.m_cd10, R.id.tb_11, R.id.m_cd11, R.id.tb_12, R.id.m_cd12, R.id.tb_13, R.id.m_cd13, R.id.tb_14, R.id.m_cd14, R.id.tb_15, R.id.m_cd15, R.id.tb_16, R.id.m_cd16, R.id.tb_17, R.id.m_cd17, R.id.tb_18, R.id.m_cd18, R.id.tb_19, R.id.m_cd19, R.id.tb_20, R.id.m_cd20, R.id.tb_21, R.id.m_cd21, R.id.tb_22, R.id.m_cd22, R.id.tb_23, R.id.m_cd23, R.id.tb_24, R.id.m_cd24, R.id.tb_25, R.id.m_cd25, R.id.tb_26, R.id.m_cd26, R.id.tb_27, R.id.m_cd27, R.id.tb_28, R.id.m_cd28, R.id.tb_29, R.id.m_cd29, R.id.tb_30, R.id.m_cd30, R.id.tb_31, R.id.m_cd31, R.id.tb_32, R.id.m_cd32, R.id.tb_33, R.id.m_cd33, R.id.tb_34, R.id.m_cd34, R.id.tb_35, R.id.m_cd35, R.id.tb_36, R.id.m_cd36, R.id.tb_37, R.id.m_cd37, R.id.tb_38, R.id.m_cd38, R.id.tb_39, R.id.m_cd39, R.id.tb_40, R.id.m_cd40, R.id.tb_41, R.id.m_cd41, R.id.if_line, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev_empty))
        "cal_blob_s" -> WidgetLayout(R.layout.w_cal_blob_s, true, "..B.n.o.", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.n_weekday, R.id.o_day))
        "cal_blob_strip" -> WidgetLayout(R.layout.w_cal_blob_strip, false, "..b.h.o.a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.o_day, R.id.a_weekday, R.id.m_ev0_title))
        "cal_blob_m" -> WidgetLayout(R.layout.w_cal_blob_m, false, "..b.h.n.o.s.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.n_weekday, R.id.o_day, R.id.s_month, R.id.m_ev0_title))
        "cal_blob_wide" -> WidgetLayout(R.layout.w_cal_blob_wide, false, "..b.a.h.o.s.....m.s.....m.s.....m.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday, R.id.sh_a, R.id.o_day, R.id.s_month, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.s_ev0_time, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev1_time, R.id.row_ev2, R.id.ed_2, R.id.m_ev2_title, R.id.s_ev2_time, R.id.s_ev_empty))
        "cal_blob_l" -> WidgetLayout(R.layout.w_cal_blob_l, false, "..b.a.s.s.s.s.s.s.s...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m.F.....m.....m.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_month, R.id.s_wd0, R.id.s_wd1, R.id.s_wd2, R.id.s_wd3, R.id.s_wd4, R.id.s_wd5, R.id.s_wd6, R.id.tb_0, R.id.m_cd0, R.id.tb_1, R.id.m_cd1, R.id.tb_2, R.id.m_cd2, R.id.tb_3, R.id.m_cd3, R.id.tb_4, R.id.m_cd4, R.id.tb_5, R.id.m_cd5, R.id.tb_6, R.id.m_cd6, R.id.tb_7, R.id.m_cd7, R.id.tb_8, R.id.m_cd8, R.id.tb_9, R.id.m_cd9, R.id.tb_10, R.id.m_cd10, R.id.tb_11, R.id.m_cd11, R.id.tb_12, R.id.m_cd12, R.id.tb_13, R.id.m_cd13, R.id.tb_14, R.id.m_cd14, R.id.tb_15, R.id.m_cd15, R.id.tb_16, R.id.m_cd16, R.id.tb_17, R.id.m_cd17, R.id.tb_18, R.id.m_cd18, R.id.tb_19, R.id.m_cd19, R.id.tb_20, R.id.m_cd20, R.id.tb_21, R.id.m_cd21, R.id.tb_22, R.id.m_cd22, R.id.tb_23, R.id.m_cd23, R.id.tb_24, R.id.m_cd24, R.id.tb_25, R.id.m_cd25, R.id.tb_26, R.id.m_cd26, R.id.tb_27, R.id.m_cd27, R.id.tb_28, R.id.m_cd28, R.id.tb_29, R.id.m_cd29, R.id.tb_30, R.id.m_cd30, R.id.tb_31, R.id.m_cd31, R.id.tb_32, R.id.m_cd32, R.id.tb_33, R.id.m_cd33, R.id.tb_34, R.id.m_cd34, R.id.tb_35, R.id.m_cd35, R.id.tb_36, R.id.m_cd36, R.id.tb_37, R.id.m_cd37, R.id.tb_38, R.id.m_cd38, R.id.tb_39, R.id.m_cd39, R.id.tb_40, R.id.m_cd40, R.id.tb_41, R.id.m_cd41, R.id.if_line, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev_empty))
        "cal_glass_s" -> WidgetLayout(R.layout.w_cal_glass_s, false, "..g.alml", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.a_weekday_al, R.id.m_day_al))
        "cal_glass_strip" -> WidgetLayout(R.layout.w_cal_glass_strip, false, "..g.m.a.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_day, R.id.a_weekday, R.id.m_ev0_title))
        "cal_glass_m" -> WidgetLayout(R.layout.w_cal_glass_m, false, "..g.a.m.s.F.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_line, R.id.m_ev0_title))
        "cal_glass_wide" -> WidgetLayout(R.layout.w_cal_glass_wide, false, "..g.a.m.s.F.....m.s.....m.s.....m.s.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_vline, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.s_ev0_time, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev1_time, R.id.row_ev2, R.id.ed_2, R.id.m_ev2_title, R.id.s_ev2_time, R.id.s_ev_empty))
        "cal_glass_l" -> WidgetLayout(R.layout.w_cal_glass_l, false, "..g.a.s.s.s.s.s.s.s...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m.F.....m.....m.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.a_month, R.id.s_wd0, R.id.s_wd1, R.id.s_wd2, R.id.s_wd3, R.id.s_wd4, R.id.s_wd5, R.id.s_wd6, R.id.tb_0, R.id.m_cd0, R.id.tb_1, R.id.m_cd1, R.id.tb_2, R.id.m_cd2, R.id.tb_3, R.id.m_cd3, R.id.tb_4, R.id.m_cd4, R.id.tb_5, R.id.m_cd5, R.id.tb_6, R.id.m_cd6, R.id.tb_7, R.id.m_cd7, R.id.tb_8, R.id.m_cd8, R.id.tb_9, R.id.m_cd9, R.id.tb_10, R.id.m_cd10, R.id.tb_11, R.id.m_cd11, R.id.tb_12, R.id.m_cd12, R.id.tb_13, R.id.m_cd13, R.id.tb_14, R.id.m_cd14, R.id.tb_15, R.id.m_cd15, R.id.tb_16, R.id.m_cd16, R.id.tb_17, R.id.m_cd17, R.id.tb_18, R.id.m_cd18, R.id.tb_19, R.id.m_cd19, R.id.tb_20, R.id.m_cd20, R.id.tb_21, R.id.m_cd21, R.id.tb_22, R.id.m_cd22, R.id.tb_23, R.id.m_cd23, R.id.tb_24, R.id.m_cd24, R.id.tb_25, R.id.m_cd25, R.id.tb_26, R.id.m_cd26, R.id.tb_27, R.id.m_cd27, R.id.tb_28, R.id.m_cd28, R.id.tb_29, R.id.m_cd29, R.id.tb_30, R.id.m_cd30, R.id.tb_31, R.id.m_cd31, R.id.tb_32, R.id.m_cd32, R.id.tb_33, R.id.m_cd33, R.id.tb_34, R.id.m_cd34, R.id.tb_35, R.id.m_cd35, R.id.tb_36, R.id.m_cd36, R.id.tb_37, R.id.m_cd37, R.id.tb_38, R.id.m_cd38, R.id.tb_39, R.id.m_cd39, R.id.tb_40, R.id.m_cd40, R.id.tb_41, R.id.m_cd41, R.id.if_line, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev_empty))
        "cal_neon_s" -> WidgetLayout(R.layout.w_cal_neon_s, false, "..b.x.a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.a_weekday, R.id.m_day))
        "cal_neon_strip" -> WidgetLayout(R.layout.w_cal_neon_strip, false, "..b.x.m.a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_day, R.id.a_weekday, R.id.m_ev0_title))
        "cal_neon_m" -> WidgetLayout(R.layout.w_cal_neon_m, false, "..b.x.x.a.m.s.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.fx_g2, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.m_ev0_title))
        "cal_neon_wide" -> WidgetLayout(R.layout.w_cal_neon_wide, false, "..b.x.a.m.s.F.....m.s.....m.s.....m.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_vline, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.s_ev0_time, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev1_time, R.id.row_ev2, R.id.ed_2, R.id.m_ev2_title, R.id.s_ev2_time, R.id.s_ev_empty))
        "cal_neon_l" -> WidgetLayout(R.layout.w_cal_neon_l, false, "..b.x.a.s.s.s.s.s.s.s...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m.F.....m.....m.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.a_month, R.id.s_wd0, R.id.s_wd1, R.id.s_wd2, R.id.s_wd3, R.id.s_wd4, R.id.s_wd5, R.id.s_wd6, R.id.tb_0, R.id.m_cd0, R.id.tb_1, R.id.m_cd1, R.id.tb_2, R.id.m_cd2, R.id.tb_3, R.id.m_cd3, R.id.tb_4, R.id.m_cd4, R.id.tb_5, R.id.m_cd5, R.id.tb_6, R.id.m_cd6, R.id.tb_7, R.id.m_cd7, R.id.tb_8, R.id.m_cd8, R.id.tb_9, R.id.m_cd9, R.id.tb_10, R.id.m_cd10, R.id.tb_11, R.id.m_cd11, R.id.tb_12, R.id.m_cd12, R.id.tb_13, R.id.m_cd13, R.id.tb_14, R.id.m_cd14, R.id.tb_15, R.id.m_cd15, R.id.tb_16, R.id.m_cd16, R.id.tb_17, R.id.m_cd17, R.id.tb_18, R.id.m_cd18, R.id.tb_19, R.id.m_cd19, R.id.tb_20, R.id.m_cd20, R.id.tb_21, R.id.m_cd21, R.id.tb_22, R.id.m_cd22, R.id.tb_23, R.id.m_cd23, R.id.tb_24, R.id.m_cd24, R.id.tb_25, R.id.m_cd25, R.id.tb_26, R.id.m_cd26, R.id.tb_27, R.id.m_cd27, R.id.tb_28, R.id.m_cd28, R.id.tb_29, R.id.m_cd29, R.id.tb_30, R.id.m_cd30, R.id.tb_31, R.id.m_cd31, R.id.tb_32, R.id.m_cd32, R.id.tb_33, R.id.m_cd33, R.id.tb_34, R.id.m_cd34, R.id.tb_35, R.id.m_cd35, R.id.tb_36, R.id.m_cd36, R.id.tb_37, R.id.m_cd37, R.id.tb_38, R.id.m_cd38, R.id.tb_39, R.id.m_cd39, R.id.tb_40, R.id.m_cd40, R.id.tb_41, R.id.m_cd41, R.id.if_line, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev_empty))
        "cal_minimal_s" -> WidgetLayout(R.layout.w_cal_minimal_s, false, "..b.alml", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday_al, R.id.m_day_al))
        "cal_minimal_strip" -> WidgetLayout(R.layout.w_cal_minimal_strip, false, "..b.m.a.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_day, R.id.a_weekday, R.id.m_ev0_title))
        "cal_minimal_m" -> WidgetLayout(R.layout.w_cal_minimal_m, false, "..b.a.m.s.F.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_line, R.id.m_ev0_title))
        "cal_minimal_wide" -> WidgetLayout(R.layout.w_cal_minimal_wide, false, "..b.a.m.s.F.....m.s.....m.s.....m.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_weekday, R.id.m_day, R.id.s_month, R.id.if_vline, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.s_ev0_time, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev1_time, R.id.row_ev2, R.id.ed_2, R.id.m_ev2_title, R.id.s_ev2_time, R.id.s_ev_empty))
        "cal_minimal_l" -> WidgetLayout(R.layout.w_cal_minimal_l, false, "..b.a.s.s.s.s.s.s.s...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m...m.F.....m.....m.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_month, R.id.s_wd0, R.id.s_wd1, R.id.s_wd2, R.id.s_wd3, R.id.s_wd4, R.id.s_wd5, R.id.s_wd6, R.id.tb_0, R.id.m_cd0, R.id.tb_1, R.id.m_cd1, R.id.tb_2, R.id.m_cd2, R.id.tb_3, R.id.m_cd3, R.id.tb_4, R.id.m_cd4, R.id.tb_5, R.id.m_cd5, R.id.tb_6, R.id.m_cd6, R.id.tb_7, R.id.m_cd7, R.id.tb_8, R.id.m_cd8, R.id.tb_9, R.id.m_cd9, R.id.tb_10, R.id.m_cd10, R.id.tb_11, R.id.m_cd11, R.id.tb_12, R.id.m_cd12, R.id.tb_13, R.id.m_cd13, R.id.tb_14, R.id.m_cd14, R.id.tb_15, R.id.m_cd15, R.id.tb_16, R.id.m_cd16, R.id.tb_17, R.id.m_cd17, R.id.tb_18, R.id.m_cd18, R.id.tb_19, R.id.m_cd19, R.id.tb_20, R.id.m_cd20, R.id.tb_21, R.id.m_cd21, R.id.tb_22, R.id.m_cd22, R.id.tb_23, R.id.m_cd23, R.id.tb_24, R.id.m_cd24, R.id.tb_25, R.id.m_cd25, R.id.tb_26, R.id.m_cd26, R.id.tb_27, R.id.m_cd27, R.id.tb_28, R.id.m_cd28, R.id.tb_29, R.id.m_cd29, R.id.tb_30, R.id.m_cd30, R.id.tb_31, R.id.m_cd31, R.id.tb_32, R.id.m_cd32, R.id.tb_33, R.id.m_cd33, R.id.tb_34, R.id.m_cd34, R.id.tb_35, R.id.m_cd35, R.id.tb_36, R.id.m_cd36, R.id.tb_37, R.id.m_cd37, R.id.tb_38, R.id.m_cd38, R.id.tb_39, R.id.m_cd39, R.id.tb_40, R.id.m_cd40, R.id.tb_41, R.id.m_cd41, R.id.if_line, R.id.row_ev0, R.id.ed_0, R.id.m_ev0_title, R.id.row_ev1, R.id.ed_1, R.id.m_ev1_title, R.id.s_ev_empty))
        else -> null
    }

    private fun wx(key: String): WidgetLayout? = when (key) {
        "wx_bold_s" -> WidgetLayout(R.layout.w_wx_bold_s, false, "..b...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.wx_now, R.id.m_temp))
        "wx_bold_strip" -> WidgetLayout(R.layout.w_wx_bold_strip, false, "..b...m.m.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.wx_now, R.id.m_temp, R.id.m_city, R.id.a_desc, R.id.s_range))
        "wx_bold_m" -> WidgetLayout(R.layout.w_wx_bold_m, false, "..b.a...m.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_city, R.id.wx_now, R.id.m_temp, R.id.s_desc))
        "wx_bold_wide" -> WidgetLayout(R.layout.w_wx_bold_wide, false, "..b.m.a...m.s...s...m...s...m...s...m...s...m...s...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp))
        "wx_bold_l" -> WidgetLayout(R.layout.w_wx_bold_l, false, "..b.m.a...m.s...s...m...s...m...s...m...s...m...s...m.F...s...s.m...s...s.m...s...s.m...s...s.m...s...s.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp, R.id.if_line, R.id.cell_d0, R.id.s_d0_day, R.id.wx_d0, R.id.s_d0_min, R.id.m_d0_max, R.id.cell_d1, R.id.s_d1_day, R.id.wx_d1, R.id.s_d1_min, R.id.m_d1_max, R.id.cell_d2, R.id.s_d2_day, R.id.wx_d2, R.id.s_d2_min, R.id.m_d2_max, R.id.cell_d3, R.id.s_d3_day, R.id.wx_d3, R.id.s_d3_min, R.id.m_d3_max, R.id.cell_d4, R.id.s_d4_day, R.id.wx_d4, R.id.s_d4_min, R.id.m_d4_max))
        "wx_blob_s" -> WidgetLayout(R.layout.w_wx_blob_s, true, "..B...o.", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.wxo_now, R.id.o_temp))
        "wx_blob_strip" -> WidgetLayout(R.layout.w_wx_blob_strip, false, "..b...m.m.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.wx_now, R.id.m_temp, R.id.m_city, R.id.a_desc, R.id.s_range))
        "wx_blob_m" -> WidgetLayout(R.layout.w_wx_blob_m, false, "..b.h...o.m.a.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.wxo_now, R.id.o_temp, R.id.m_city, R.id.a_desc))
        "wx_blob_wide" -> WidgetLayout(R.layout.w_wx_blob_wide, false, "..b.m.a...m.s...s...m...s...m...s...m...s...m...s...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp))
        "wx_blob_l" -> WidgetLayout(R.layout.w_wx_blob_l, false, "..b.m.a...m.s...s...m...s...m...s...m...s...m...s...m.F...s...s.m...s...s.m...s...s.m...s...s.m...s...s.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp, R.id.if_line, R.id.cell_d0, R.id.s_d0_day, R.id.wx_d0, R.id.s_d0_min, R.id.m_d0_max, R.id.cell_d1, R.id.s_d1_day, R.id.wx_d1, R.id.s_d1_min, R.id.m_d1_max, R.id.cell_d2, R.id.s_d2_day, R.id.wx_d2, R.id.s_d2_min, R.id.m_d2_max, R.id.cell_d3, R.id.s_d3_day, R.id.wx_d3, R.id.s_d3_min, R.id.m_d3_max, R.id.cell_d4, R.id.s_d4_day, R.id.wx_d4, R.id.s_d4_min, R.id.m_d4_max))
        "wx_glass_s" -> WidgetLayout(R.layout.w_wx_glass_s, false, "..g...m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.wx_now, R.id.m_temp))
        "wx_glass_strip" -> WidgetLayout(R.layout.w_wx_glass_strip, false, "..g...m.m.a.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.wx_now, R.id.m_temp, R.id.m_city, R.id.a_desc, R.id.s_range))
        "wx_glass_m" -> WidgetLayout(R.layout.w_wx_glass_m, false, "..g...m.m.a.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.wx_now, R.id.m_temp, R.id.m_city, R.id.a_desc))
        "wx_glass_wide" -> WidgetLayout(R.layout.w_wx_glass_wide, false, "..g.m.a...m.s...s...m...s...m...s...m...s...m...s...m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp))
        "wx_glass_l" -> WidgetLayout(R.layout.w_wx_glass_l, false, "..g.m.a...m.s...s...m...s...m...s...m...s...m...s...m.F...s...s.m...s...s.m...s...s.m...s...s.m...s...s.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp, R.id.if_line, R.id.cell_d0, R.id.s_d0_day, R.id.wx_d0, R.id.s_d0_min, R.id.m_d0_max, R.id.cell_d1, R.id.s_d1_day, R.id.wx_d1, R.id.s_d1_min, R.id.m_d1_max, R.id.cell_d2, R.id.s_d2_day, R.id.wx_d2, R.id.s_d2_min, R.id.m_d2_max, R.id.cell_d3, R.id.s_d3_day, R.id.wx_d3, R.id.s_d3_min, R.id.m_d3_max, R.id.cell_d4, R.id.s_d4_day, R.id.wx_d4, R.id.s_d4_min, R.id.m_d4_max))
        "wx_neon_s" -> WidgetLayout(R.layout.w_wx_neon_s, false, "..b.x...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.wx_now, R.id.m_temp))
        "wx_neon_strip" -> WidgetLayout(R.layout.w_wx_neon_strip, false, "..b.x...m.m.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.wx_now, R.id.m_temp, R.id.m_city, R.id.a_desc, R.id.s_range))
        "wx_neon_m" -> WidgetLayout(R.layout.w_wx_neon_m, false, "..b.x...m.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.wx_now, R.id.m_temp, R.id.a_city, R.id.s_desc))
        "wx_neon_wide" -> WidgetLayout(R.layout.w_wx_neon_wide, false, "..b.x.m.a...m.s...s...m...s...m...s...m...s...m...s...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp))
        "wx_neon_l" -> WidgetLayout(R.layout.w_wx_neon_l, false, "..b.x.m.a...m.s...s...m...s...m...s...m...s...m...s...m.F...s...s.m...s...s.m...s...s.m...s...s.m...s...s.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp, R.id.if_line, R.id.cell_d0, R.id.s_d0_day, R.id.wx_d0, R.id.s_d0_min, R.id.m_d0_max, R.id.cell_d1, R.id.s_d1_day, R.id.wx_d1, R.id.s_d1_min, R.id.m_d1_max, R.id.cell_d2, R.id.s_d2_day, R.id.wx_d2, R.id.s_d2_min, R.id.m_d2_max, R.id.cell_d3, R.id.s_d3_day, R.id.wx_d3, R.id.s_d3_min, R.id.m_d3_max, R.id.cell_d4, R.id.s_d4_day, R.id.wx_d4, R.id.s_d4_min, R.id.m_d4_max))
        "wx_minimal_s" -> WidgetLayout(R.layout.w_wx_minimal_s, false, "..b...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.wx_now, R.id.m_temp))
        "wx_minimal_strip" -> WidgetLayout(R.layout.w_wx_minimal_strip, false, "..b...m.m.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.wx_now, R.id.m_temp, R.id.m_city, R.id.a_desc, R.id.s_range))
        "wx_minimal_m" -> WidgetLayout(R.layout.w_wx_minimal_m, false, "..b.m.F.a...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_temp, R.id.if_line, R.id.a_city, R.id.wx_now))
        "wx_minimal_wide" -> WidgetLayout(R.layout.w_wx_minimal_wide, false, "..b.m.a...m.s...s...m...s...m...s...m...s...m...s...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp))
        "wx_minimal_l" -> WidgetLayout(R.layout.w_wx_minimal_l, false, "..b.m.a...m.s...s...m...s...m...s...m...s...m...s...m.F...s...s.m...s...s.m...s...s.m...s...s.m...s...s.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_city, R.id.a_desc, R.id.wx_now, R.id.m_temp, R.id.s_range, R.id.cell_h0, R.id.s_h0_time, R.id.wx_h0, R.id.m_h0_temp, R.id.cell_h1, R.id.s_h1_time, R.id.wx_h1, R.id.m_h1_temp, R.id.cell_h2, R.id.s_h2_time, R.id.wx_h2, R.id.m_h2_temp, R.id.cell_h3, R.id.s_h3_time, R.id.wx_h3, R.id.m_h3_temp, R.id.cell_h4, R.id.s_h4_time, R.id.wx_h4, R.id.m_h4_temp, R.id.if_line, R.id.cell_d0, R.id.s_d0_day, R.id.wx_d0, R.id.s_d0_min, R.id.m_d0_max, R.id.cell_d1, R.id.s_d1_day, R.id.wx_d1, R.id.s_d1_min, R.id.m_d1_max, R.id.cell_d2, R.id.s_d2_day, R.id.wx_d2, R.id.s_d2_min, R.id.m_d2_max, R.id.cell_d3, R.id.s_d3_day, R.id.wx_d3, R.id.s_d3_min, R.id.m_d3_max, R.id.cell_d4, R.id.s_d4_day, R.id.wx_d4, R.id.s_d4_min, R.id.m_d4_max))
        else -> null
    }

    private fun bat(key: String): WidgetLayout? = when (key) {
        "bat_bold_s" -> WidgetLayout(R.layout.w_bat_bold_s, false, "..b.m.p.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.pb_bar))
        "bat_bold_strip" -> WidgetLayout(R.layout.w_bat_bold_strip, false, "..b.m.p.S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.pb_bar, R.id.is_bolt, R.id.s_state))
        "bat_bold_m" -> WidgetLayout(R.layout.w_bat_bold_m, false, "..b.m.p.A.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.pb_bar, R.id.ia_bolt, R.id.a_state, R.id.s_remain))
        "bat_bold_wide" -> WidgetLayout(R.layout.w_bat_bold_wide, false, "..b.m.a.M.m.s.p.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.a_title, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.pb_bar, R.id.s_temp, R.id.s_health))
        "bat_bold_l" -> WidgetLayout(R.layout.w_bat_bold_l, false, "..b.a.m.M.m.s.p.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_title, R.id.m_pct, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.pb_bar, R.id.s_temp, R.id.s_health))
        "bat_blob_s" -> WidgetLayout(R.layout.w_bat_blob_s, true, "..B.o.N.n.", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.o_pct, R.id.in_bolt, R.id.n_state))
        "bat_blob_strip" -> WidgetLayout(R.layout.w_bat_blob_strip, false, "..b.m.p.S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.pb_bar, R.id.is_bolt, R.id.s_state))
        "bat_blob_m" -> WidgetLayout(R.layout.w_bat_blob_m, false, "..b.h.o.A.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.o_pct, R.id.ia_bolt, R.id.a_state, R.id.s_remain))
        "bat_blob_wide" -> WidgetLayout(R.layout.w_bat_blob_wide, false, "..b.h.o.a.M.m.s.p.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a, R.id.o_pct, R.id.a_title, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.pb_bar, R.id.s_temp, R.id.s_health))
        "bat_blob_l" -> WidgetLayout(R.layout.w_bat_blob_l, false, "..b.a.h.o.M.m.s.F.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_title, R.id.sh_a, R.id.o_pct, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.if_line, R.id.s_temp, R.id.s_health))
        "bat_glass_s" -> WidgetLayout(R.layout.w_bat_glass_s, false, "..g.p.m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.pb_ring, R.id.m_pct))
        "bat_glass_strip" -> WidgetLayout(R.layout.w_bat_glass_strip, false, "..g.m.p.S.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_pct, R.id.pb_bar, R.id.is_bolt, R.id.s_state))
        "bat_glass_m" -> WidgetLayout(R.layout.w_bat_glass_m, false, "..g.p.m.A.a.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.pb_ring, R.id.m_pct, R.id.ia_bolt, R.id.a_state, R.id.s_remain))
        "bat_glass_wide" -> WidgetLayout(R.layout.w_bat_glass_wide, false, "..g.p.m.a.M.m.s.p.s.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.pb_ring, R.id.m_pct, R.id.a_title, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.pb_bar, R.id.s_temp, R.id.s_health))
        "bat_glass_l" -> WidgetLayout(R.layout.w_bat_glass_l, false, "..g.a.p.m.M.m.s.F.s.s.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.a_title, R.id.pb_ring, R.id.m_pct, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.if_line, R.id.s_temp, R.id.s_health))
        "bat_neon_s" -> WidgetLayout(R.layout.w_bat_neon_s, false, "..b.p.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.pb_ring, R.id.m_pct))
        "bat_neon_strip" -> WidgetLayout(R.layout.w_bat_neon_strip, false, "..b.x.m.p.S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_pct, R.id.pb_bar, R.id.is_bolt, R.id.s_state))
        "bat_neon_m" -> WidgetLayout(R.layout.w_bat_neon_m, false, "..b.x.p.m.A.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.pb_ring, R.id.m_pct, R.id.ia_bolt, R.id.a_state, R.id.s_remain))
        "bat_neon_wide" -> WidgetLayout(R.layout.w_bat_neon_wide, false, "..b.x.p.m.a.M.m.s.p.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.pb_ring, R.id.m_pct, R.id.a_title, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.pb_bar, R.id.s_temp, R.id.s_health))
        "bat_neon_l" -> WidgetLayout(R.layout.w_bat_neon_l, false, "..b.x.a.p.m.M.m.s.F.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.a_title, R.id.pb_ring, R.id.m_pct, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.if_line, R.id.s_temp, R.id.s_health))
        "bat_minimal_s" -> WidgetLayout(R.layout.w_bat_minimal_s, false, "..b.p.m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.pb_ring, R.id.m_pct))
        "bat_minimal_strip" -> WidgetLayout(R.layout.w_bat_minimal_strip, false, "..b.m.p.S.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.pb_bar, R.id.is_bolt, R.id.s_state))
        "bat_minimal_m" -> WidgetLayout(R.layout.w_bat_minimal_m, false, "..b.p.m.A.a.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.pb_ring, R.id.m_pct, R.id.ia_bolt, R.id.a_state, R.id.s_remain))
        "bat_minimal_wide" -> WidgetLayout(R.layout.w_bat_minimal_wide, false, "..b.m.a.M.m.s.p.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_pct, R.id.a_title, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.pb_bar, R.id.s_temp, R.id.s_health))
        "bat_minimal_l" -> WidgetLayout(R.layout.w_bat_minimal_l, false, "..b.a.p.m.M.m.s.F.s.s.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.a_title, R.id.pb_ring, R.id.m_pct, R.id.im_bolt, R.id.m_state, R.id.s_remain, R.id.if_line, R.id.s_temp, R.id.s_health))
        else -> null
    }

    private fun apps(key: String): WidgetLayout? = when (key) {
        "apps_flower_s" -> WidgetLayout(R.layout.w_apps_flower_s, true, "..B.................", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_flower_strip" -> WidgetLayout(R.layout.w_apps_flower_strip, false, "..b.....................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3, R.id.cell_ap4, R.id.ap_4))
        "apps_flower_m" -> WidgetLayout(R.layout.w_apps_flower_m, true, "..B.m.................", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.m_title, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_flower_wide" -> WidgetLayout(R.layout.w_apps_flower_wide, false, "..b.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_flower_l" -> WidgetLayout(R.layout.w_apps_flower_l, false, "..b.h.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a2, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_cookie_s" -> WidgetLayout(R.layout.w_apps_cookie_s, true, "..B.................", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_cookie_strip" -> WidgetLayout(R.layout.w_apps_cookie_strip, false, "..b.....................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3, R.id.cell_ap4, R.id.ap_4))
        "apps_cookie_m" -> WidgetLayout(R.layout.w_apps_cookie_m, true, "..B.m.................", intArrayOf(R.id.widget_root, R.id.bg_a, R.id.m_title, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_cookie_wide" -> WidgetLayout(R.layout.w_apps_cookie_wide, false, "..b.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_cookie_l" -> WidgetLayout(R.layout.w_apps_cookie_l, false, "..b.h.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.sh_a2, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_glass_s" -> WidgetLayout(R.layout.w_apps_glass_s, false, "..g.................", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_glass_strip" -> WidgetLayout(R.layout.w_apps_glass_strip, false, "..g.....................", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3, R.id.cell_ap4, R.id.ap_4))
        "apps_glass_m" -> WidgetLayout(R.layout.w_apps_glass_m, false, "..g.m.................", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_title, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_glass_wide" -> WidgetLayout(R.layout.w_apps_glass_wide, false, "..g.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_glass_l" -> WidgetLayout(R.layout.w_apps_glass_l, false, "..g.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_g, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_neon_s" -> WidgetLayout(R.layout.w_apps_neon_s, false, "..b.x...x.....x.....x.....x...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.cell_ap0, R.id.fx_ap0, R.id.ap_0, R.id.cell_ap1, R.id.fx_ap1, R.id.ap_1, R.id.cell_ap2, R.id.fx_ap2, R.id.ap_2, R.id.cell_ap3, R.id.fx_ap3, R.id.ap_3))
        "apps_neon_strip" -> WidgetLayout(R.layout.w_apps_neon_strip, false, "..b.x...x.....x.....x.....x.....x...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.cell_ap0, R.id.fx_ap0, R.id.ap_0, R.id.cell_ap1, R.id.fx_ap1, R.id.ap_1, R.id.cell_ap2, R.id.fx_ap2, R.id.ap_2, R.id.cell_ap3, R.id.fx_ap3, R.id.ap_3, R.id.cell_ap4, R.id.fx_ap4, R.id.ap_4))
        "apps_neon_m" -> WidgetLayout(R.layout.w_apps_neon_m, false, "..b.x.m...x.....x.....x.....x...", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_title, R.id.cell_ap0, R.id.fx_ap0, R.id.ap_0, R.id.cell_ap1, R.id.fx_ap1, R.id.ap_1, R.id.cell_ap2, R.id.fx_ap2, R.id.ap_2, R.id.cell_ap3, R.id.fx_ap3, R.id.ap_3))
        "apps_neon_wide" -> WidgetLayout(R.layout.w_apps_neon_wide, false, "..b.x.m.....x...m...x...m...x...m...x...m.....x...m...x...m...x...m...x...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.fx_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.fx_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.fx_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.fx_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.fx_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.fx_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.fx_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.fx_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_neon_l" -> WidgetLayout(R.layout.w_apps_neon_l, false, "..b.x.m.....x...m...x...m...x...m...x...m.....x...m...x...m...x...m...x...m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.fx_g, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.fx_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.fx_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.fx_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.fx_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.fx_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.fx_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.fx_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.fx_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_capsule_s" -> WidgetLayout(R.layout.w_apps_capsule_s, false, "..b.................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_capsule_strip" -> WidgetLayout(R.layout.w_apps_capsule_strip, false, "..b.....................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3, R.id.cell_ap4, R.id.ap_4))
        "apps_capsule_m" -> WidgetLayout(R.layout.w_apps_capsule_m, false, "..b.m.................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_capsule_wide" -> WidgetLayout(R.layout.w_apps_capsule_wide, false, "..b.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_capsule_l" -> WidgetLayout(R.layout.w_apps_capsule_l, false, "..b.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_minimal_s" -> WidgetLayout(R.layout.w_apps_minimal_s, false, "..b.................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_minimal_strip" -> WidgetLayout(R.layout.w_apps_minimal_strip, false, "..b.....................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3, R.id.cell_ap4, R.id.ap_4))
        "apps_minimal_m" -> WidgetLayout(R.layout.w_apps_minimal_m, false, "..b.m.................", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.cell_ap0, R.id.ap_0, R.id.cell_ap1, R.id.ap_1, R.id.cell_ap2, R.id.ap_2, R.id.cell_ap3, R.id.ap_3))
        "apps_minimal_wide" -> WidgetLayout(R.layout.w_apps_minimal_wide, false, "..b.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        "apps_minimal_l" -> WidgetLayout(R.layout.w_apps_minimal_l, false, "..b.m.......m.....m.....m.....m.......m.....m.....m.....m.", intArrayOf(R.id.widget_root, R.id.bg_s, R.id.m_title, R.id.row_apps0, R.id.cell_ap0, R.id.ap_0, R.id.m_ap0_label, R.id.cell_ap1, R.id.ap_1, R.id.m_ap1_label, R.id.cell_ap2, R.id.ap_2, R.id.m_ap2_label, R.id.cell_ap3, R.id.ap_3, R.id.m_ap3_label, R.id.row_apps1, R.id.cell_ap4, R.id.ap_4, R.id.m_ap4_label, R.id.cell_ap5, R.id.ap_5, R.id.m_ap5_label, R.id.cell_ap6, R.id.ap_6, R.id.m_ap6_label, R.id.cell_ap7, R.id.ap_7, R.id.m_ap7_label))
        else -> null
    }
}

/** Слот данных → все элементы, которые его показывают (в разных стилях — в разных ролях). */
object WidgetSlots {
    private val map: Map<String, IntArray> = mapOf(
        "hours" to intArrayOf(R.id.m_hours_al),
        "minutes" to intArrayOf(R.id.a_minutes_al),
        "weekday" to intArrayOf(R.id.a_weekday_al, R.id.a_weekday, R.id.n_weekday, R.id.s_weekday),
        "day" to intArrayOf(R.id.m_day_al, R.id.m_day, R.id.o_day),
        "wx_now" to intArrayOf(R.id.wx_now),
        "temp" to intArrayOf(R.id.m_temp, R.id.s_temp, R.id.o_temp),
        "pct" to intArrayOf(R.id.m_pct, R.id.o_pct),
        "pb_bar" to intArrayOf(R.id.pb_bar),
        "time" to intArrayOf(R.id.m_time, R.id.o_time, R.id.m_time_al, R.id.s_time),
        "date" to intArrayOf(R.id.m_date, R.id.s_date_al, R.id.s_date, R.id.m_date_al, R.id.bo_date, R.id.n_date, R.id.a_date, R.id.a_date_al),
        "ev0_title" to intArrayOf(R.id.m_ev0_title),
        "city" to intArrayOf(R.id.m_city, R.id.a_city),
        "desc" to intArrayOf(R.id.a_desc, R.id.s_desc),
        "range" to intArrayOf(R.id.s_range),
        "i_bolt" to intArrayOf(R.id.is_bolt, R.id.ia_bolt, R.id.im_bolt, R.id.in_bolt),
        "state" to intArrayOf(R.id.s_state, R.id.a_state, R.id.m_state, R.id.n_state),
        "month" to intArrayOf(R.id.s_month, R.id.a_month),
        "i_line" to intArrayOf(R.id.if_line),
        "remain" to intArrayOf(R.id.s_remain),
        "ampm" to intArrayOf(R.id.a_ampm),
        "i_alarm" to intArrayOf(R.id.is_alarm, R.id.ia_alarm),
        "alarm" to intArrayOf(R.id.s_alarm, R.id.a_alarm),
        "row_alarm" to intArrayOf(R.id.row_alarm),
        "i_vline" to intArrayOf(R.id.if_vline),
        "ed_0" to intArrayOf(R.id.ed_0),
        "ev0_time" to intArrayOf(R.id.s_ev0_time),
        "row_ev0" to intArrayOf(R.id.row_ev0),
        "ed_1" to intArrayOf(R.id.ed_1),
        "ev1_title" to intArrayOf(R.id.m_ev1_title),
        "ev1_time" to intArrayOf(R.id.s_ev1_time),
        "row_ev1" to intArrayOf(R.id.row_ev1),
        "ed_2" to intArrayOf(R.id.ed_2),
        "ev2_title" to intArrayOf(R.id.m_ev2_title),
        "ev2_time" to intArrayOf(R.id.s_ev2_time),
        "row_ev2" to intArrayOf(R.id.row_ev2),
        "ev_empty" to intArrayOf(R.id.s_ev_empty),
        "h0_time" to intArrayOf(R.id.s_h0_time),
        "wx_h0" to intArrayOf(R.id.wx_h0),
        "h0_temp" to intArrayOf(R.id.m_h0_temp),
        "cell_h0" to intArrayOf(R.id.cell_h0),
        "h1_time" to intArrayOf(R.id.s_h1_time),
        "wx_h1" to intArrayOf(R.id.wx_h1),
        "h1_temp" to intArrayOf(R.id.m_h1_temp),
        "cell_h1" to intArrayOf(R.id.cell_h1),
        "h2_time" to intArrayOf(R.id.s_h2_time),
        "wx_h2" to intArrayOf(R.id.wx_h2),
        "h2_temp" to intArrayOf(R.id.m_h2_temp),
        "cell_h2" to intArrayOf(R.id.cell_h2),
        "h3_time" to intArrayOf(R.id.s_h3_time),
        "wx_h3" to intArrayOf(R.id.wx_h3),
        "h3_temp" to intArrayOf(R.id.m_h3_temp),
        "cell_h3" to intArrayOf(R.id.cell_h3),
        "h4_time" to intArrayOf(R.id.s_h4_time),
        "wx_h4" to intArrayOf(R.id.wx_h4),
        "h4_temp" to intArrayOf(R.id.m_h4_temp),
        "cell_h4" to intArrayOf(R.id.cell_h4),
        "health" to intArrayOf(R.id.s_health),
        "title" to intArrayOf(R.id.a_title, R.id.m_title),
        "wd0" to intArrayOf(R.id.s_wd0),
        "wd1" to intArrayOf(R.id.s_wd1),
        "wd2" to intArrayOf(R.id.s_wd2),
        "wd3" to intArrayOf(R.id.s_wd3),
        "wd4" to intArrayOf(R.id.s_wd4),
        "wd5" to intArrayOf(R.id.s_wd5),
        "wd6" to intArrayOf(R.id.s_wd6),
        "tb_0" to intArrayOf(R.id.tb_0),
        "cd0" to intArrayOf(R.id.m_cd0),
        "tb_1" to intArrayOf(R.id.tb_1),
        "cd1" to intArrayOf(R.id.m_cd1),
        "tb_2" to intArrayOf(R.id.tb_2),
        "cd2" to intArrayOf(R.id.m_cd2),
        "tb_3" to intArrayOf(R.id.tb_3),
        "cd3" to intArrayOf(R.id.m_cd3),
        "tb_4" to intArrayOf(R.id.tb_4),
        "cd4" to intArrayOf(R.id.m_cd4),
        "tb_5" to intArrayOf(R.id.tb_5),
        "cd5" to intArrayOf(R.id.m_cd5),
        "tb_6" to intArrayOf(R.id.tb_6),
        "cd6" to intArrayOf(R.id.m_cd6),
        "tb_7" to intArrayOf(R.id.tb_7),
        "cd7" to intArrayOf(R.id.m_cd7),
        "tb_8" to intArrayOf(R.id.tb_8),
        "cd8" to intArrayOf(R.id.m_cd8),
        "tb_9" to intArrayOf(R.id.tb_9),
        "cd9" to intArrayOf(R.id.m_cd9),
        "tb_10" to intArrayOf(R.id.tb_10),
        "cd10" to intArrayOf(R.id.m_cd10),
        "tb_11" to intArrayOf(R.id.tb_11),
        "cd11" to intArrayOf(R.id.m_cd11),
        "tb_12" to intArrayOf(R.id.tb_12),
        "cd12" to intArrayOf(R.id.m_cd12),
        "tb_13" to intArrayOf(R.id.tb_13),
        "cd13" to intArrayOf(R.id.m_cd13),
        "tb_14" to intArrayOf(R.id.tb_14),
        "cd14" to intArrayOf(R.id.m_cd14),
        "tb_15" to intArrayOf(R.id.tb_15),
        "cd15" to intArrayOf(R.id.m_cd15),
        "tb_16" to intArrayOf(R.id.tb_16),
        "cd16" to intArrayOf(R.id.m_cd16),
        "tb_17" to intArrayOf(R.id.tb_17),
        "cd17" to intArrayOf(R.id.m_cd17),
        "tb_18" to intArrayOf(R.id.tb_18),
        "cd18" to intArrayOf(R.id.m_cd18),
        "tb_19" to intArrayOf(R.id.tb_19),
        "cd19" to intArrayOf(R.id.m_cd19),
        "tb_20" to intArrayOf(R.id.tb_20),
        "cd20" to intArrayOf(R.id.m_cd20),
        "tb_21" to intArrayOf(R.id.tb_21),
        "cd21" to intArrayOf(R.id.m_cd21),
        "tb_22" to intArrayOf(R.id.tb_22),
        "cd22" to intArrayOf(R.id.m_cd22),
        "tb_23" to intArrayOf(R.id.tb_23),
        "cd23" to intArrayOf(R.id.m_cd23),
        "tb_24" to intArrayOf(R.id.tb_24),
        "cd24" to intArrayOf(R.id.m_cd24),
        "tb_25" to intArrayOf(R.id.tb_25),
        "cd25" to intArrayOf(R.id.m_cd25),
        "tb_26" to intArrayOf(R.id.tb_26),
        "cd26" to intArrayOf(R.id.m_cd26),
        "tb_27" to intArrayOf(R.id.tb_27),
        "cd27" to intArrayOf(R.id.m_cd27),
        "tb_28" to intArrayOf(R.id.tb_28),
        "cd28" to intArrayOf(R.id.m_cd28),
        "tb_29" to intArrayOf(R.id.tb_29),
        "cd29" to intArrayOf(R.id.m_cd29),
        "tb_30" to intArrayOf(R.id.tb_30),
        "cd30" to intArrayOf(R.id.m_cd30),
        "tb_31" to intArrayOf(R.id.tb_31),
        "cd31" to intArrayOf(R.id.m_cd31),
        "tb_32" to intArrayOf(R.id.tb_32),
        "cd32" to intArrayOf(R.id.m_cd32),
        "tb_33" to intArrayOf(R.id.tb_33),
        "cd33" to intArrayOf(R.id.m_cd33),
        "tb_34" to intArrayOf(R.id.tb_34),
        "cd34" to intArrayOf(R.id.m_cd34),
        "tb_35" to intArrayOf(R.id.tb_35),
        "cd35" to intArrayOf(R.id.m_cd35),
        "tb_36" to intArrayOf(R.id.tb_36),
        "cd36" to intArrayOf(R.id.m_cd36),
        "tb_37" to intArrayOf(R.id.tb_37),
        "cd37" to intArrayOf(R.id.m_cd37),
        "tb_38" to intArrayOf(R.id.tb_38),
        "cd38" to intArrayOf(R.id.m_cd38),
        "tb_39" to intArrayOf(R.id.tb_39),
        "cd39" to intArrayOf(R.id.m_cd39),
        "tb_40" to intArrayOf(R.id.tb_40),
        "cd40" to intArrayOf(R.id.m_cd40),
        "tb_41" to intArrayOf(R.id.tb_41),
        "cd41" to intArrayOf(R.id.m_cd41),
        "d0_day" to intArrayOf(R.id.s_d0_day),
        "wx_d0" to intArrayOf(R.id.wx_d0),
        "d0_min" to intArrayOf(R.id.s_d0_min),
        "d0_max" to intArrayOf(R.id.m_d0_max),
        "cell_d0" to intArrayOf(R.id.cell_d0),
        "d1_day" to intArrayOf(R.id.s_d1_day),
        "wx_d1" to intArrayOf(R.id.wx_d1),
        "d1_min" to intArrayOf(R.id.s_d1_min),
        "d1_max" to intArrayOf(R.id.m_d1_max),
        "cell_d1" to intArrayOf(R.id.cell_d1),
        "d2_day" to intArrayOf(R.id.s_d2_day),
        "wx_d2" to intArrayOf(R.id.wx_d2),
        "d2_min" to intArrayOf(R.id.s_d2_min),
        "d2_max" to intArrayOf(R.id.m_d2_max),
        "cell_d2" to intArrayOf(R.id.cell_d2),
        "d3_day" to intArrayOf(R.id.s_d3_day),
        "wx_d3" to intArrayOf(R.id.wx_d3),
        "d3_min" to intArrayOf(R.id.s_d3_min),
        "d3_max" to intArrayOf(R.id.m_d3_max),
        "cell_d3" to intArrayOf(R.id.cell_d3),
        "d4_day" to intArrayOf(R.id.s_d4_day),
        "wx_d4" to intArrayOf(R.id.wx_d4),
        "d4_min" to intArrayOf(R.id.s_d4_min),
        "d4_max" to intArrayOf(R.id.m_d4_max),
        "cell_d4" to intArrayOf(R.id.cell_d4),
        "wxo_now" to intArrayOf(R.id.wxo_now),
        "i_dot" to intArrayOf(R.id.ia_dot),
        "pb_ring" to intArrayOf(R.id.pb_ring),
        "analog" to intArrayOf(R.id.analog),
        "ap_0" to intArrayOf(R.id.ap_0),
        "cell_ap0" to intArrayOf(R.id.cell_ap0),
        "ap_1" to intArrayOf(R.id.ap_1),
        "cell_ap1" to intArrayOf(R.id.cell_ap1),
        "ap_2" to intArrayOf(R.id.ap_2),
        "cell_ap2" to intArrayOf(R.id.cell_ap2),
        "ap_3" to intArrayOf(R.id.ap_3),
        "cell_ap3" to intArrayOf(R.id.cell_ap3),
        "ap_4" to intArrayOf(R.id.ap_4),
        "cell_ap4" to intArrayOf(R.id.cell_ap4),
        "ap0_label" to intArrayOf(R.id.m_ap0_label),
        "ap1_label" to intArrayOf(R.id.m_ap1_label),
        "ap2_label" to intArrayOf(R.id.m_ap2_label),
        "ap3_label" to intArrayOf(R.id.m_ap3_label),
        "row_apps0" to intArrayOf(R.id.row_apps0),
        "ap4_label" to intArrayOf(R.id.m_ap4_label),
        "ap_5" to intArrayOf(R.id.ap_5),
        "ap5_label" to intArrayOf(R.id.m_ap5_label),
        "cell_ap5" to intArrayOf(R.id.cell_ap5),
        "ap_6" to intArrayOf(R.id.ap_6),
        "ap6_label" to intArrayOf(R.id.m_ap6_label),
        "cell_ap6" to intArrayOf(R.id.cell_ap6),
        "ap_7" to intArrayOf(R.id.ap_7),
        "ap7_label" to intArrayOf(R.id.m_ap7_label),
        "cell_ap7" to intArrayOf(R.id.cell_ap7),
        "row_apps1" to intArrayOf(R.id.row_apps1),
    )

    operator fun get(slot: String): IntArray = map[slot] ?: IntArray(0)
}
