package app.synctheme.mobile.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import app.synctheme.R
import app.synctheme.core.FontWeight
import app.synctheme.core.Theme
import app.synctheme.mobile.data.CalendarData
import app.synctheme.mobile.data.WeatherCodes
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.render.Colors
import app.synctheme.mobile.ui.MainActivity
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Собирает RemoteViews виджета по его виду, стилю и настройкам. */
object WidgetRenderer {

    /** Цвета виджета: свои или из темы. */
    private class Palette(theme: Theme, config: WidgetConfig) {
        val text = config.textColor ?: theme.textColor
        val accent = config.accentColor ?: theme.accentColor
        val muted = Colors.withAlpha(text, 0.6f)
        val faint = Colors.withAlpha(text, 0.3f)
        val surface = Colors.widgetSurface(theme)
        /** Текст поверх акцентного цвета. */
        val onAccent = if (Colors.isLight(accent)) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
    }

    fun build(
        context: Context,
        kind: WidgetKind,
        config: WidgetConfig,
        theme: Theme,
        weather: WeatherData? = WeatherRepository(context).cached,
    ): RemoteViews {
        val p = Palette(theme, config)
        val views = when (kind) {
            WidgetKind.CLOCK -> clock(context, config, theme, p)
            WidgetKind.DATE -> date(context, config, p)
            WidgetKind.CALENDAR -> calendar(context, config, p)
            WidgetKind.WEATHER -> weather(context, config, p, weather)
            WidgetKind.BATTERY -> battery(context, config, p)
        }
        views.setImageViewResource(R.id.widget_bg, config.shape.drawable)
        views.setInt(R.id.widget_bg, "setColorFilter", p.surface)
        views.setInt(R.id.widget_bg, "setImageAlpha", (config.opacity.coerceIn(0f, 1f) * 255).roundToInt())
        views.setOnClickPendingIntent(R.id.widget_root, clickIntent(context, kind))
        return views
    }

    // ---------------------------------------------------------------- часы

    private fun clock(context: Context, config: WidgetConfig, theme: Theme, p: Palette): RemoteViews {
        val style = config.style
        if (style.startsWith("analog_")) {
            val dial = style.removePrefix("analog_")
            return RemoteViews(context.packageName, ClockLayouts.analog(dial, config.showSeconds)).apply {
                // Цифры на циферблате — цветом текста, деления — акцентом.
                val dialColor = if (dial == "numbers") p.text else p.accent
                setColorStateList(R.id.analog, "setDialTintList", ColorStateList.valueOf(dialColor))
                setColorStateList(R.id.analog, "setHourHandTintList", ColorStateList.valueOf(p.text))
                setColorStateList(R.id.analog, "setMinuteHandTintList", ColorStateList.valueOf(p.text))
                if (config.showSeconds) {
                    setColorStateList(R.id.analog, "setSecondHandTintList", ColorStateList.valueOf(p.accent))
                }
            }
        }
        val weight = when (config.fontWeight ?: theme.fontWeight) {
            FontWeight.THIN -> "thin"
            FontWeight.LIGHT -> "light"
            FontWeight.REGULAR -> "regular"
            FontWeight.BOLD -> "bold"
        }
        val gravity = gravity(config.align)
        return RemoteViews(context.packageName, ClockLayouts.digital(style, weight)).apply {
            when (style) {
                "stacked" -> {
                    setTextColor(R.id.clock_hours, p.text)
                    setTextColor(R.id.clock_minutes, p.accent)
                    setInt(R.id.clock_hours, "setGravity", gravity)
                    setInt(R.id.clock_minutes, "setGravity", gravity)
                    dateLine(config, p, gravity)
                    setInt(R.id.content, "setGravity", gravity)
                }
                "side" -> {
                    setTextColor(R.id.clock_time, p.text)
                    setInt(R.id.clock_divider, "setColorFilter", p.accent)
                    setTextColor(R.id.clock_weekday, p.accent)
                    setTextColor(R.id.clock_date, p.text)
                }
                "big" -> {
                    setTextColor(R.id.clock_time, p.text)
                    setInt(R.id.clock_time, "setGravity", gravity)
                }
                else -> { // classic, seconds
                    setTextColor(R.id.clock_time, p.text)
                    setInt(R.id.clock_time, "setGravity", gravity)
                    dateLine(config, p, gravity)
                    setInt(R.id.content, "setGravity", gravity)
                }
            }
        }
    }

    private fun RemoteViews.dateLine(config: WidgetConfig, p: Palette, gravity: Int) {
        setViewVisibility(R.id.clock_date, if (config.showDate) View.VISIBLE else View.GONE)
        setTextColor(R.id.clock_date, p.accent)
        setInt(R.id.clock_date, "setGravity", gravity)
    }

    // ---------------------------------------------------------------- дата

    private fun date(context: Context, config: WidgetConfig, p: Palette): RemoteViews {
        val layout = when (config.style) {
            "line" -> R.layout.widget_date_line
            "tear" -> R.layout.widget_date_tear
            "compact" -> R.layout.widget_date_compact
            else -> R.layout.widget_date_big_day
        }
        return RemoteViews(context.packageName, layout).apply {
            when (config.style) {
                "line" -> {
                    setTextColor(R.id.date_weekday, p.text)
                    setTextColor(R.id.date_month, p.accent)
                    val g = gravity(config.align)
                    setInt(R.id.content, "setGravity", g)
                    setInt(R.id.date_weekday, "setGravity", g)
                    setInt(R.id.date_month, "setGravity", g)
                }
                "tear" -> {
                    setInt(R.id.date_header_bg, "setColorFilter", p.accent)
                    setTextColor(R.id.date_month, p.onAccent)
                    setTextColor(R.id.date_day, p.text)
                    setTextColor(R.id.date_weekday, p.accent)
                }
                "compact" -> {
                    setTextColor(R.id.date_weekday, p.accent)
                    setTextColor(R.id.date_day, p.text)
                }
                else -> {
                    setTextColor(R.id.date_weekday, p.accent)
                    setTextColor(R.id.date_day, p.text)
                    setTextColor(R.id.date_month, p.muted)
                    val g = gravity(config.align)
                    setInt(R.id.content, "setGravity", g)
                    setInt(R.id.date_weekday, "setGravity", g)
                    setInt(R.id.date_day, "setGravity", g)
                    setInt(R.id.date_month, "setGravity", g)
                }
            }
        }
    }

    // ---------------------------------------------------------------- календарь

    private fun calendar(context: Context, config: WidgetConfig, p: Palette): RemoteViews {
        val layout = when (config.style) {
            "agenda" -> R.layout.widget_calendar_agenda
            "month_agenda" -> R.layout.widget_calendar_month_agenda
            else -> R.layout.widget_calendar_month
        }
        return RemoteViews(context.packageName, layout).apply {
            setTextColor(R.id.cal_title, p.accent)
            if (config.style != "agenda") monthGrid(p)
            when (config.style) {
                "agenda" -> {
                    val today = LocalDate.now()
                    val title = today.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                        .replaceFirstChar { it.titlecase() } + ", " + SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date())
                    setTextViewText(R.id.cal_title, title)
                    events(context, 5, p)
                }
                "month_agenda" -> events(context, 3, p)
            }
        }
    }

    private fun RemoteViews.monthGrid(p: Palette) {
        val grid = CalendarData.month()
        setTextViewText(R.id.cal_title, grid.title)
        grid.weekdays.forEachIndexed { i, name ->
            setTextViewText(WidgetIds.CAL_WEEKDAYS[i], name.take(2))
            setTextColor(WidgetIds.CAL_WEEKDAYS[i], p.muted)
        }
        grid.cells.forEachIndexed { i, cell ->
            val id = WidgetIds.CAL_DAYS[i]
            setTextViewText(id, cell.day.toString())
            setTextColor(id, if (cell.today) p.onAccent else if (cell.inMonth) p.text else p.faint)
            setViewVisibility(WidgetIds.CAL_DAY_BGS[i], if (cell.today) View.VISIBLE else View.GONE)
            if (cell.today) setInt(WidgetIds.CAL_DAY_BGS[i], "setColorFilter", p.accent)
        }
    }

    private fun RemoteViews.events(context: Context, rows: Int, p: Palette) {
        val events = CalendarData.upcoming(context, rows)
        for (i in 0 until rows) {
            val e = events.getOrNull(i)
            setViewVisibility(WidgetIds.EV_ROWS[i], if (e != null) View.VISIBLE else View.GONE)
            if (e == null) continue
            setInt(WidgetIds.EV_DOTS[i], "setColorFilter", e.color.takeIf { it != 0 } ?: p.accent)
            setTextViewText(WidgetIds.EV_TIMES[i], e.time)
            setTextColor(WidgetIds.EV_TIMES[i], p.muted)
            setTextViewText(WidgetIds.EV_TITLES[i], e.title)
            setTextColor(WidgetIds.EV_TITLES[i], p.text)
        }
        val empty = when {
            !CalendarData.hasPermission(context) -> "Разрешите доступ к календарю в SyncTheme"
            events.isEmpty() -> "Ближайших событий нет"
            else -> null
        }
        setViewVisibility(R.id.ev_empty, if (empty != null) View.VISIBLE else View.GONE)
        if (empty != null) {
            setTextViewText(R.id.ev_empty, empty)
            setTextColor(R.id.ev_empty, p.muted)
        }
    }

    // ---------------------------------------------------------------- погода

    private fun weather(context: Context, config: WidgetConfig, p: Palette, data: WeatherData?): RemoteViews {
        val layout = when (config.style) {
            "details" -> R.layout.widget_weather_details
            "hourly" -> R.layout.widget_weather_hourly
            "daily" -> R.layout.widget_weather_daily
            "full" -> R.layout.widget_weather_full
            else -> R.layout.widget_weather_compact
        }
        val style = config.style
        return RemoteViews(context.packageName, layout).apply {
            fun icon(id: Int, code: Int, isDay: Boolean) {
                setImageViewResource(id, WeatherCodes.icon(code, isDay))
                // 0 — цвета иконки без изменений; иначе вся иконка в цвете текста.
                setInt(id, "setColorFilter", if (config.colorIcons) 0 else p.text)
            }
            val hasCurrentBlock = style == "compact" || style == "details" || style == "full"
            setTextColor(R.id.wx_temp, p.text)
            setTextColor(R.id.wx_city, if (hasCurrentBlock) p.muted else p.text)
            if (hasCurrentBlock) setTextColor(R.id.wx_desc, p.accent)
            if (data == null) {
                setTextViewText(R.id.wx_temp, "--°")
                setTextViewText(R.id.wx_city, "Выберите город в SyncTheme")
                if (hasCurrentBlock) setTextViewText(R.id.wx_desc, "Нет данных")
                icon(R.id.wx_icon, 2, true)
                return@apply
            }
            val now = data.now
            setTextViewText(R.id.wx_temp, deg(now.temp))
            setTextViewText(R.id.wx_city, data.place)
            icon(R.id.wx_icon, now.code, now.isDay)
            if (hasCurrentBlock) setTextViewText(R.id.wx_desc, WeatherCodes.description(now.code))
            val today = data.days.firstOrNull()
            if (style == "details") {
                setTextViewText(R.id.wx_feels, "Ощущается ${deg(now.feels)}")
                setTextViewText(R.id.wx_wind, "Ветер ${now.wind.roundToInt()} м/с")
                setTextViewText(R.id.wx_humidity, "Влажн. ${now.humidity}%")
                for (id in listOf(R.id.wx_feels, R.id.wx_wind, R.id.wx_humidity)) setTextColor(id, p.text)
                setTextViewText(R.id.wx_updated, "Обновлено " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.updatedAt)))
                setTextColor(R.id.wx_updated, p.muted)
            }
            if ((style == "details" || style == "full") && today != null) {
                setTextViewText(R.id.wx_range, "Днём ${deg(today.max)} · ночью ${deg(today.min)}")
                setTextColor(R.id.wx_range, p.text)
            }
            if (style == "hourly" || style == "full") {
                for (i in WidgetIds.H_TIMES.indices) {
                    val h = data.hours.getOrNull(i) ?: continue
                    setTextViewText(WidgetIds.H_TIMES[i], h.time)
                    setTextColor(WidgetIds.H_TIMES[i], p.muted)
                    icon(WidgetIds.H_ICONS[i], h.code, h.isDay)
                    setTextViewText(WidgetIds.H_TEMPS[i], deg(h.temp))
                    setTextColor(WidgetIds.H_TEMPS[i], p.text)
                }
            }
            if (style == "daily" || style == "full") {
                val locale = Locale.getDefault()
                for (i in WidgetIds.D_DAYS.indices) {
                    val d = data.days.getOrNull(i) ?: continue
                    val label = if (i == 0) "Сегодня" else LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    setTextViewText(WidgetIds.D_DAYS[i], label)
                    setTextColor(WidgetIds.D_DAYS[i], if (i == 0) p.accent else p.muted)
                    icon(WidgetIds.D_ICONS[i], d.code, true)
                    setTextViewText(WidgetIds.D_MAXS[i], deg(d.max))
                    setTextColor(WidgetIds.D_MAXS[i], p.text)
                    setTextViewText(WidgetIds.D_MINS[i], deg(d.min))
                    setTextColor(WidgetIds.D_MINS[i], p.text)
                }
            }
        }
    }

    private fun deg(t: Double) = "${t.roundToInt()}°"

    // ---------------------------------------------------------------- батарея

    private fun battery(context: Context, config: WidgetConfig, p: Palette): RemoteViews {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = bm.isCharging
        val layout = when (config.style) {
            "bar" -> R.layout.widget_battery_bar
            "big" -> R.layout.widget_battery_big
            else -> R.layout.widget_battery_ring
        }
        return RemoteViews(context.packageName, layout).apply {
            when (config.style) {
                "bar" -> progress(R.id.battery_bar, level, p)
                "big" -> Unit
                else -> progress(R.id.battery_ring, level, p)
            }
            setTextViewText(R.id.battery_percent, "$level%")
            setTextViewText(R.id.battery_state, if (charging) "⚡ заряжается" else if (config.style == "ring") "" else "телефон")
            setTextColor(R.id.battery_percent, p.text)
            setTextColor(R.id.battery_state, p.accent)
        }
    }

    private fun RemoteViews.progress(id: Int, level: Int, p: Palette) {
        setProgressBar(id, 100, level, false)
        setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(p.accent))
        setColorStateList(id, "setProgressBackgroundTintList", ColorStateList.valueOf(Colors.withAlpha(p.text, 0.25f)))
    }

    // ---------------------------------------------------------------- общее

    private fun gravity(align: WidgetAlign) = when (align) {
        WidgetAlign.START -> Gravity.START or Gravity.CENTER_VERTICAL
        WidgetAlign.CENTER -> Gravity.CENTER
        WidgetAlign.END -> Gravity.END or Gravity.CENTER_VERTICAL
    }

    private fun clickIntent(context: Context, kind: WidgetKind): PendingIntent {
        val intent = when (kind) {
            WidgetKind.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            WidgetKind.DATE, WidgetKind.CALENDAR -> Intent(
                Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build(),
            )
            WidgetKind.WEATHER -> Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_WIDGETS)
            WidgetKind.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, kind.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
