package app.synctheme.mobile.widget

import app.synctheme.R

// Сгенерировано scripts/gen_widget_resources.py — не редактировать вручную.

/** Идентификаторы повторяющихся элементов разметки виджетов. */
object WidgetIds {
    val CAL_DAYS = intArrayOf(R.id.cal_d0, R.id.cal_d1, R.id.cal_d2, R.id.cal_d3, R.id.cal_d4, R.id.cal_d5, R.id.cal_d6, R.id.cal_d7, R.id.cal_d8, R.id.cal_d9, R.id.cal_d10, R.id.cal_d11, R.id.cal_d12, R.id.cal_d13, R.id.cal_d14, R.id.cal_d15, R.id.cal_d16, R.id.cal_d17, R.id.cal_d18, R.id.cal_d19, R.id.cal_d20, R.id.cal_d21, R.id.cal_d22, R.id.cal_d23, R.id.cal_d24, R.id.cal_d25, R.id.cal_d26, R.id.cal_d27, R.id.cal_d28, R.id.cal_d29, R.id.cal_d30, R.id.cal_d31, R.id.cal_d32, R.id.cal_d33, R.id.cal_d34, R.id.cal_d35, R.id.cal_d36, R.id.cal_d37, R.id.cal_d38, R.id.cal_d39, R.id.cal_d40, R.id.cal_d41)
    val CAL_DAY_BGS = intArrayOf(R.id.cal_bg0, R.id.cal_bg1, R.id.cal_bg2, R.id.cal_bg3, R.id.cal_bg4, R.id.cal_bg5, R.id.cal_bg6, R.id.cal_bg7, R.id.cal_bg8, R.id.cal_bg9, R.id.cal_bg10, R.id.cal_bg11, R.id.cal_bg12, R.id.cal_bg13, R.id.cal_bg14, R.id.cal_bg15, R.id.cal_bg16, R.id.cal_bg17, R.id.cal_bg18, R.id.cal_bg19, R.id.cal_bg20, R.id.cal_bg21, R.id.cal_bg22, R.id.cal_bg23, R.id.cal_bg24, R.id.cal_bg25, R.id.cal_bg26, R.id.cal_bg27, R.id.cal_bg28, R.id.cal_bg29, R.id.cal_bg30, R.id.cal_bg31, R.id.cal_bg32, R.id.cal_bg33, R.id.cal_bg34, R.id.cal_bg35, R.id.cal_bg36, R.id.cal_bg37, R.id.cal_bg38, R.id.cal_bg39, R.id.cal_bg40, R.id.cal_bg41)
    val CAL_WEEKDAYS = intArrayOf(R.id.cal_wd0, R.id.cal_wd1, R.id.cal_wd2, R.id.cal_wd3, R.id.cal_wd4, R.id.cal_wd5, R.id.cal_wd6)
    val EV_ROWS = intArrayOf(R.id.ev_row0, R.id.ev_row1, R.id.ev_row2, R.id.ev_row3, R.id.ev_row4)
    val EV_DOTS = intArrayOf(R.id.ev_dot0, R.id.ev_dot1, R.id.ev_dot2, R.id.ev_dot3, R.id.ev_dot4)
    val EV_TIMES = intArrayOf(R.id.ev_time0, R.id.ev_time1, R.id.ev_time2, R.id.ev_time3, R.id.ev_time4)
    val EV_TITLES = intArrayOf(R.id.ev_title0, R.id.ev_title1, R.id.ev_title2, R.id.ev_title3, R.id.ev_title4)
    val H_TIMES = intArrayOf(R.id.h0_time, R.id.h1_time, R.id.h2_time, R.id.h3_time, R.id.h4_time, R.id.h5_time)
    val H_ICONS = intArrayOf(R.id.h0_icon, R.id.h1_icon, R.id.h2_icon, R.id.h3_icon, R.id.h4_icon, R.id.h5_icon)
    val H_TEMPS = intArrayOf(R.id.h0_temp, R.id.h1_temp, R.id.h2_temp, R.id.h3_temp, R.id.h4_temp, R.id.h5_temp)
    val D_DAYS = intArrayOf(R.id.d0_day, R.id.d1_day, R.id.d2_day, R.id.d3_day, R.id.d4_day)
    val D_ICONS = intArrayOf(R.id.d0_icon, R.id.d1_icon, R.id.d2_icon, R.id.d3_icon, R.id.d4_icon)
    val D_MAXS = intArrayOf(R.id.d0_max, R.id.d1_max, R.id.d2_max, R.id.d3_max, R.id.d4_max)
    val D_MINS = intArrayOf(R.id.d0_min, R.id.d1_min, R.id.d2_min, R.id.d3_min, R.id.d4_min)
}

/** Разметка стиля часов с нужным начертанием шрифта. */
object ClockLayouts {
    fun digital(style: String, weight: String): Int = when ("${style}_$weight") {
        "classic_thin" -> R.layout.widget_clock_classic_thin
        "classic_light" -> R.layout.widget_clock_classic_light
        "classic_regular" -> R.layout.widget_clock_classic_regular
        "classic_bold" -> R.layout.widget_clock_classic_bold
        "big_thin" -> R.layout.widget_clock_big_thin
        "big_light" -> R.layout.widget_clock_big_light
        "big_regular" -> R.layout.widget_clock_big_regular
        "big_bold" -> R.layout.widget_clock_big_bold
        "stacked_thin" -> R.layout.widget_clock_stacked_thin
        "stacked_light" -> R.layout.widget_clock_stacked_light
        "stacked_regular" -> R.layout.widget_clock_stacked_regular
        "stacked_bold" -> R.layout.widget_clock_stacked_bold
        "side_thin" -> R.layout.widget_clock_side_thin
        "side_light" -> R.layout.widget_clock_side_light
        "side_regular" -> R.layout.widget_clock_side_regular
        "side_bold" -> R.layout.widget_clock_side_bold
        "seconds_thin" -> R.layout.widget_clock_seconds_thin
        "seconds_light" -> R.layout.widget_clock_seconds_light
        "seconds_regular" -> R.layout.widget_clock_seconds_regular
        "seconds_bold" -> R.layout.widget_clock_seconds_bold
        else -> R.layout.widget_clock_classic_light
    }

    fun analog(dial: String, seconds: Boolean): Int = when (dial) {
        "ticks" -> if (seconds) R.layout.widget_clock_analog_ticks else R.layout.widget_clock_analog_ticks_nosec
        "numbers" -> if (seconds) R.layout.widget_clock_analog_numbers else R.layout.widget_clock_analog_numbers_nosec
        "minimal" -> if (seconds) R.layout.widget_clock_analog_minimal else R.layout.widget_clock_analog_minimal_nosec
        else -> R.layout.widget_clock_analog_ticks
    }
}
