package app.synctheme.mobile.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.BatteryManager
import android.text.format.DateFormat
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.synctheme.R
import app.synctheme.core.Theme
import app.synctheme.mobile.data.CalendarData
import app.synctheme.mobile.data.CalendarEvent
import app.synctheme.mobile.data.WeatherCodes
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.render.Colors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Собирает RemoteViews виджета. Для рабочего стола — сразу все размеры ([WidgetSize]):
 * лаунчер сам показывает подходящий, поэтому при растягивании информации становится больше.
 */
object WidgetRenderer {
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Цвета по ролям элементов разметки. */
    private class Palette(theme: Theme, config: WidgetConfig, family: String, accentBackground: Boolean) {
        val main = config.textColor ?: theme.textColor
        val accent = config.accentColor ?: theme.accentColor
        val sub = Colors.withAlpha(main, 0.72f)
        val faint = Colors.withAlpha(main, 0.3f)
        val label = if (family == "neon") ColorUtils.blendARGB(accent, WHITE, 0.35f) else accent
        val onAccent = if (Colors.isLight(accent)) 0xFF141414.toInt() else WHITE
        /** Текст на фигуре: если сама фигура почти прозрачна, он стоит прямо на обоях. */
        val on = if (accentBackground && config.opacity < 0.5f) main else onAccent
        val onSub = Colors.withAlpha(on, 0.75f)
        private val surface = Colors.widgetSurface(theme)
        val bg = when (family) {
            "blob" -> ColorUtils.blendARGB(surface, accent, 0.3f) or 0xFF000000.toInt()
            "neon" -> 0xFF0B0B12.toInt()
            else -> surface
        }
        val alpha = (config.opacity.coerceIn(0f, 1f) * 255).roundToInt()
    }

    /** Данные, общие для всех размеров одного виджета: считаются один раз. */
    private class Data(val context: Context, val config: WidgetConfig, val weather: WeatherData?) {
        val alarm: String? by lazy { nextAlarm(context) }
        val events: List<CalendarEvent> by lazy { CalendarData.upcoming(context, 3) }
        val month by lazy { CalendarData.month() }
        val battery: Battery by lazy { Battery.read(context) }
        val apps: List<Pair<String, Bitmap?>> by lazy {
            val circle = config.style != "glass"
            config.apps.ifEmpty { AppCatalog.defaults(context) }.take(8).map { it to AppCatalog.icon(context, it, 120, circle) }
        }
    }

    /** Стиль оформления, к которому относится стиль виджета (для цветов). */
    fun family(kind: WidgetKind, style: String): String = when {
        kind == WidgetKind.CLOCK && style.startsWith("analog_") -> when (style) {
            "analog_blob" -> "blob"
            "analog_neon" -> "neon"
            "analog_minimal" -> "minimal"
            else -> "glass"
        }
        kind == WidgetKind.APPS -> when (style) {
            "glass", "neon", "minimal" -> style
            else -> "blob"
        }
        else -> style
    }

    fun layoutKey(kind: WidgetKind, config: WidgetConfig, size: WidgetSize): String {
        val style = kind.style(config.style).id
        val base = "${kind.prefix}_${style}_${size.key}"
        return when {
            style.startsWith("analog_") -> if (config.showSeconds) base else base + "_ns"
            // У цифровых часов своя разметка на каждый шрифт цифр.
            kind == WidgetKind.CLOCK -> base + "_" + config.copy(style = style).digitFont().name.lowercase()
            else -> base
        }
    }

    /**
     * @param sizes размеры виджета, которые сообщил лаунчер (обычно для портрета и альбома):
     * для каждого — своя разметка по [WidgetSize.classify]. Пока их нет — все разметки
     * со своими «идеальными» размерами, лаунчер выберет ближайшую.
     */
    fun build(
        context: Context,
        kind: WidgetKind,
        config: WidgetConfig,
        theme: Theme,
        weather: WeatherData? = WeatherRepository(context).cached,
        sizes: List<SizeF> = emptyList(),
    ): RemoteViews {
        val data = Data(context, config, weather)
        if (sizes.isNotEmpty()) {
            val byClass = HashMap<WidgetSize, RemoteViews>()
            return RemoteViews(
                sizes.take(16).associateWith { s ->
                    val cls = WidgetSize.classify(s.width, s.height)
                    // Столбикам кегль подбирается по точному размеру, остальным хватает одной разметки на форму.
                    if (cls.vertical) sized(context, kind, config, theme, cls, data, s)
                    else byClass.getOrPut(cls) { sized(context, kind, config, theme, cls, data, s) }
                },
            )
        }
        return RemoteViews(WidgetSize.entries.associate { it.sizeF to sized(context, kind, config, theme, it, data, it.sizeF) })
    }

    /** Один размер — для превью в настройке; dp — размер превью (по умолчанию — из [WidgetSize]). */
    fun buildSize(
        context: Context,
        kind: WidgetKind,
        config: WidgetConfig,
        theme: Theme,
        size: WidgetSize,
        weather: WeatherData? = WeatherRepository(context).cached,
        dp: SizeF = SizeF(size.previewW.toFloat(), size.previewH.toFloat()),
    ): RemoteViews = sized(context, kind, config, theme, size, Data(context, config, weather), dp)

    private fun sized(
        context: Context, kind: WidgetKind, config: WidgetConfig, theme: Theme, size: WidgetSize, data: Data, dp: SizeF,
    ): RemoteViews {
        val layout = WidgetLayouts[layoutKey(kind, config, size)]
            ?: WidgetLayouts[layoutKey(kind, WidgetConfig(style = kind.defaultStyle), size)]!!
        val style = kind.style(config.style).id
        val p = Palette(theme, config, family(kind, style), layout.accentBackground)
        val rv = RemoteViews(context.packageName, layout.res)
        val v = Views(rv, layout)
        v.paint(p, config.align)
        when (kind) {
            WidgetKind.CLOCK -> v.clock(style, data, p)
            WidgetKind.CALENDAR -> v.calendar(context, family(kind, style), data, p)
            WidgetKind.WEATHER -> v.weather(data, p)
            WidgetKind.BATTERY -> v.battery(data.battery)
            WidgetKind.APPS -> v.apps(context, data)
        }
        if (size.vertical) {
            val content = when (kind) {
                WidgetKind.CLOCK -> VerticalContent(alarm = data.alarm)
                WidgetKind.CALENDAR -> VerticalContent(
                    rows = data.events.size,
                    hint = when {
                        !CalendarData.hasPermission(context) -> "Нет доступа к календарю"
                        data.events.isEmpty() -> "Событий нет"
                        else -> null
                    },
                )
                WidgetKind.WEATHER -> VerticalContent(
                    main = data.weather?.let { deg(it.now.temp) } ?: "--°", rows = data.weather?.hours?.size ?: 0,
                    desc = data.weather?.let { WeatherCodes.description(it.now.code) },
                )
                WidgetKind.BATTERY -> data.battery.let { VerticalContent(main = "${it.level}%", charging = it.charging, remain = it.remain != null) }
                WidgetKind.APPS -> VerticalContent(rows = data.apps.size, title = config.title.isNotBlank())
            }
            VerticalFit.apply(context, rv, layout, kind, family(kind, style), config.copy(style = style).digitFont(), size, dp, content)
        }
        if (kind != WidgetKind.APPS) {
            AppCatalog.clickIntent(context, kind, config)?.let { rv.setOnClickPendingIntent(R.id.widget_root, it) }
        }
        return rv
    }

    /** RemoteViews одной разметки: действия только для элементов, которые в ней есть. */
    private class Views(val rv: RemoteViews, val layout: WidgetLayout) {
        fun text(slot: String, value: CharSequence) = each(slot) { rv.setTextViewText(it, value) }
        fun visible(slot: String, show: Boolean) = each(slot) { rv.setViewVisibility(it, if (show) View.VISIBLE else View.GONE) }
        fun textColor(slot: String, color: Int) = each(slot) { rv.setTextColor(it, color) }
        fun tint(slot: String, color: Int) = each(slot) { rv.setInt(it, "setColorFilter", color) }

        inline fun each(slot: String, action: (Int) -> Unit) {
            for (id in WidgetSlots[slot]) if (layout.has(id)) action(id)
        }

        fun paint(p: Palette, align: WidgetAlign) {
            val gravity = when (align) {
                WidgetAlign.START -> Gravity.START or Gravity.CENTER_VERTICAL
                WidgetAlign.CENTER -> Gravity.CENTER
                WidgetAlign.END -> Gravity.END or Gravity.CENTER_VERTICAL
            }
            for (i in layout.ids.indices) {
                val id = layout.ids[i]
                when (layout.roles[2 * i]) {
                    'm' -> rv.setTextColor(id, p.main)
                    's' -> rv.setTextColor(id, p.sub)
                    'f' -> rv.setTextColor(id, p.faint)
                    'a' -> rv.setTextColor(id, p.label)
                    'o' -> rv.setTextColor(id, p.on)
                    'n' -> rv.setTextColor(id, p.onSub)
                    'M' -> rv.setInt(id, "setColorFilter", p.main)
                    'S' -> rv.setInt(id, "setColorFilter", p.sub)
                    'F' -> rv.setInt(id, "setColorFilter", p.faint)
                    'A' -> rv.setInt(id, "setColorFilter", p.label)
                    'O' -> rv.setInt(id, "setColorFilter", p.on)
                    'N' -> rv.setInt(id, "setColorFilter", p.onSub)
                    'b' -> {
                        rv.setInt(id, "setColorFilter", p.bg)
                        rv.setInt(id, "setImageAlpha", p.alpha)
                    }
                    'B' -> {
                        rv.setInt(id, "setColorFilter", p.accent)
                        rv.setInt(id, "setImageAlpha", p.alpha)
                    }
                    'g' -> rv.setInt(id, "setImageAlpha", p.alpha)
                    'h', 'x' -> rv.setInt(id, "setColorFilter", p.accent)
                    'P' -> {
                        rv.setTextColor(id, p.onAccent)
                        rv.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(p.accent))
                    }
                    'p' -> {
                        rv.setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(p.accent))
                        rv.setColorStateList(id, "setProgressBackgroundTintList", ColorStateList.valueOf(p.faint))
                    }
                }
                // Выравнивание: текст и контейнеры, помеченные в разметке.
                if (layout.roles[2 * i + 1] != '.') rv.setInt(id, "setGravity", gravity)
            }
        }

        // ------------------------------------------------------------ часы

        fun clock(style: String, data: Data, p: Palette) {
            val alarm = data.alarm
            visible("row_alarm", alarm != null)
            if (alarm != null) text("alarm", alarm)
            if (!style.startsWith("analog_") || !layout.has(R.id.analog)) return
            val (dial, hands, second) = when (style) {
                "analog_blob" -> Triple(p.accent, p.onAccent, p.onAccent)
                "analog_numbers" -> Triple(p.main, p.main, p.accent)
                else -> Triple(p.accent, p.main, p.accent)
            }
            rv.setColorStateList(R.id.analog, "setDialTintList", ColorStateList.valueOf(dial))
            rv.setColorStateList(R.id.analog, "setHourHandTintList", ColorStateList.valueOf(hands))
            rv.setColorStateList(R.id.analog, "setMinuteHandTintList", ColorStateList.valueOf(hands))
            if (data.config.showSeconds) rv.setColorStateList(R.id.analog, "setSecondHandTintList", ColorStateList.valueOf(second))
        }

        // ------------------------------------------------------------ календарь

        fun calendar(context: Context, family: String, data: Data, p: Palette) {
            val events = data.events
            val hint = when {
                !CalendarData.hasPermission(context) -> "Разрешите доступ к календарю в SyncTheme"
                events.isEmpty() -> "Событий нет"
                else -> null
            }
            for (i in 0 until 3) {
                val e = events.getOrNull(i)
                visible("row_ev$i", e != null)
                if (e != null) {
                    text("ev${i}_title", e.title)
                    text("ev${i}_time", e.time)
                    tint("ed_$i", e.color.takeIf { it != 0 } ?: p.accent)
                }
            }
            // В маленьких размерах строки событий нет — там первое событие или подсказка.
            if (events.isEmpty()) text("ev0_title", hint.orEmpty())
            visible("ev_empty", hint != null)
            if (hint != null) text("ev_empty", hint)

            if (!layout.has(R.id.m_cd0)) return
            val grid = data.month
            grid.weekdays.forEachIndexed { i, name -> text("wd$i", name.take(2)) }
            val todayText = if (family == "neon" || family == "minimal") p.label else p.onAccent
            grid.cells.forEachIndexed { i, cell ->
                text("cd$i", cell.day.toString())
                textColor("cd$i", if (cell.today) todayText else if (cell.inMonth) p.main else p.faint)
                visible("tb_$i", cell.today)
                if (cell.today) tint("tb_$i", p.accent)
            }
        }

        // ------------------------------------------------------------ погода

        fun weather(data: Data, p: Palette) {
            val mono = !data.config.colorIcons
            fun icon(slot: String, code: Int, isDay: Boolean) {
                each(slot) {
                    rv.setImageViewResource(it, WeatherCodes.icon(code, isDay))
                    // 0 — цвета иконки без изменений; иначе вся иконка в цвете текста.
                    rv.setInt(it, "setColorFilter", if (mono) p.main else 0)
                }
                // На акцентной фигуре цветная иконка теряется — она всегда одного цвета с текстом на фигуре.
                if (slot == "wx_now") each("wxo_now") {
                    rv.setImageViewResource(it, WeatherCodes.icon(code, isDay))
                    rv.setInt(it, "setColorFilter", p.on)
                }
            }
            val w = data.weather
            if (w == null) {
                text("temp", "--°")
                text("city", "Выберите город")
                text("desc", "в SyncTheme")
                text("range", "")
                icon("wx_now", 2, true)
                for (i in 0 until 6) {
                    visible("cell_h$i", false)
                    visible("cell_d$i", false)
                }
                return
            }
            text("temp", deg(w.now.temp))
            text("city", w.place)
            text("desc", WeatherCodes.description(w.now.code))
            icon("wx_now", w.now.code, w.now.isDay)
            w.days.firstOrNull()?.let { text("range", "↑${deg(it.max)}  ↓${deg(it.min)}") }
            for (i in 0 until 6) {
                val h = w.hours.getOrNull(i)
                visible("cell_h$i", h != null)
                if (h != null) {
                    text("h${i}_time", h.time)
                    icon("wx_h$i", h.code, h.isDay)
                    text("h${i}_temp", deg(h.temp))
                }
            }
            for (i in 0 until 5) {
                val d = w.days.getOrNull(i)
                visible("cell_d$i", d != null)
                if (d != null) {
                    val label = if (i == 0) "Сегодня" else LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    text("d${i}_day", label)
                    icon("wx_d$i", d.code, true)
                    text("d${i}_min", deg(d.min))
                    text("d${i}_max", deg(d.max))
                }
            }
        }

        // ------------------------------------------------------------ батарея

        fun battery(b: Battery) {
            text("pct", "${b.level}%")
            for (slot in listOf("pb_ring", "pb_bar")) each(slot) { rv.setProgressBar(it, 100, b.level, false) }
            text("state", b.state)
            visible("i_bolt", b.charging)
            visible("remain", b.remain != null)
            b.remain?.let { text("remain", it) }
            text("temp", b.temperature)
            text("health", b.health)
        }

        // ------------------------------------------------------------ приложения

        fun apps(context: Context, data: Data) {
            val apps = data.apps
            for (i in 0 until 8) {
                val app = apps.getOrNull(i)
                visible("cell_ap$i", app != null)
                if (app == null) continue
                val (component, icon) = app
                each("ap_$i") { id ->
                    if (icon != null) rv.setImageViewBitmap(id, icon) else rv.setImageViewResource(id, R.drawable.wapp_placeholder)
                }
                text("ap${i}_label", AppCatalog.label(context, component) ?: component.substringBefore('/'))
                AppCatalog.launchIntent(context, component)?.let { intent ->
                    val pi = PendingIntent.getActivity(
                        context, component.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    each("cell_ap$i") { rv.setOnClickPendingIntent(it, pi) }
                }
            }
            visible("row_apps1", apps.size > 4)
            val title = data.config.title.trim()
            visible("title", title.isNotEmpty())
            if (title.isNotEmpty()) text("title", title)
        }
    }

    private fun deg(t: Double) = "${t.roundToInt()}°"

    private fun nextAlarm(context: Context): String? {
        val info = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock ?: return null
        val at = Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault()).toLocalDate()
        val time = DateFormat.getTimeFormat(context).format(Date(info.triggerTime))
        val today = LocalDate.now()
        return when (at) {
            today -> time
            today.plusDays(1) -> "завтра $time"
            else -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + time
        }
    }

    /** Состояние батареи телефона. */
    class Battery(
        val level: Int,
        val charging: Boolean,
        val state: String,
        val remain: String?,
        val temperature: String,
        val health: String,
    ) {
        companion object {
            fun read(context: Context): Battery {
                val bm = context.getSystemService(BatteryManager::class.java)
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
                val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = bm.isCharging || status == BatteryManager.BATTERY_STATUS_CHARGING
                val full = status == BatteryManager.BATTERY_STATUS_FULL || (charging && level >= 100)
                val state = when {
                    full -> "Заряжено"
                    charging -> "Заряжается"
                    else -> "От батареи"
                }
                val left = if (charging) bm.computeChargeTimeRemaining() else -1L
                val remain = when {
                    charging && left > 0 -> "Полная через " + duration(left)
                    !charging && level <= 20 -> "Пора зарядить"
                    else -> null
                }
                val temp = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
                val temperature = if (temp == Int.MIN_VALUE) "" else "${(temp / 10.0).roundToInt()}°C"
                val health = when (sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Состояние: норма"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Слишком холодно"
                    BatteryManager.BATTERY_HEALTH_DEAD, BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Неисправность"
                    else -> ""
                }
                return Battery(level, charging, state, remain, temperature, health)
            }

            private fun duration(ms: Long): String {
                val min = (ms / 60_000).toInt().coerceAtLeast(1)
                return if (min < 60) "$min мин" else "${min / 60} ч ${min % 60} мин"
            }
        }
    }
}
