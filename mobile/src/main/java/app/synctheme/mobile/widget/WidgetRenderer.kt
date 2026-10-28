package app.synctheme.mobile.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.synctheme.R
import app.synctheme.core.DigitFont
import app.synctheme.core.Theme
import app.synctheme.core.ThemeMode
import app.synctheme.core.resolved
import app.synctheme.mobile.data.CalendarData
import app.synctheme.mobile.data.CalendarEvent
import app.synctheme.mobile.data.WeatherCodes
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.render.Colors
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.util.Compat
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

    private const val INK = 0xFF2B2622.toInt()
    private const val PAPER = 0xFFF6F1E6.toInt()
    private const val BOARD = 0xFF161619.toInt()

    /**
     * Цвета по ролям элементов разметки. У каждого стиля свои: «Стекло» и «Неон» — в цветах темы,
     * «Плакат» — фон цвета акцента и контрастный к нему текст, «Бумага» — чернила на кремовом листе,
     * «Табло» — светящиеся цифры цвета акцента на графите. Свои цвета виджета ([WidgetConfig]) — важнее.
     */
    private class Palette(theme: Theme, config: WidgetConfig, family: String, accentBackground: Boolean) {
        val main: Int
        val accent: Int
        val label: Int
        val bg: Int
        val onAccent: Int

        init {
            val text = config.textColor ?: theme.textColor
            val themeAccent = config.accentColor ?: theme.accentColor
            when (family) {
                "poster" -> {
                    val ink = if (Colors.isLight(themeAccent)) 0xFF111111.toInt() else WHITE
                    bg = themeAccent
                    main = config.textColor ?: ink
                    accent = main
                    label = main
                    onAccent = themeAccent
                }
                "paper" -> {
                    // Акцент на бумаге — как чернила: глубокий, не кричащий.
                    val deep = if (ColorUtils.calculateLuminance(themeAccent) > 0.2) ColorUtils.blendARGB(themeAccent, 0xFF000000.toInt(), 0.35f) else themeAccent
                    bg = PAPER
                    main = config.textColor ?: INK
                    accent = deep
                    label = deep
                    onAccent = PAPER
                }
                "board" -> {
                    // Огоньки табло: цвет акцента, высветленный, чтобы гореть на графите.
                    val led = if (ColorUtils.calculateLuminance(themeAccent) < 0.3) ColorUtils.blendARGB(themeAccent, WHITE, 0.45f) else themeAccent
                    bg = BOARD
                    main = config.textColor ?: led
                    accent = led
                    label = led
                    onAccent = BOARD
                }
                "neon" -> {
                    // Неон горит только на тёмном: цифры светлые и у светлых тем.
                    bg = 0xFF0B0B12.toInt()
                    main = config.textColor ?: if (Colors.isLight(text)) text else 0xFFF4F4FA.toInt()
                    accent = themeAccent
                    label = ColorUtils.blendARGB(themeAccent, WHITE, 0.35f)
                    onAccent = if (Colors.isLight(themeAccent)) 0xFF141414.toInt() else WHITE
                }
                else -> {
                    bg = Colors.widgetSurface(theme)
                    main = text
                    accent = themeAccent
                    label = themeAccent
                    onAccent = if (Colors.isLight(themeAccent)) 0xFF141414.toInt() else WHITE
                }
            }
        }

        /** Подписи «Табло» — белые приглушённые, на остальных — цвет текста приглушённый. */
        val sub = if (family == "board") Colors.withAlpha(WHITE, 0.62f) else Colors.withAlpha(main, 0.72f)
        val faint = if (family == "board") Colors.withAlpha(WHITE, 0.28f) else Colors.withAlpha(main, 0.3f)
        /** Текст на фигуре: если сама фигура почти прозрачна, он стоит прямо на обоях. */
        val on = if (accentBackground && config.opacity < 0.5f) main else onAccent
        val onSub = Colors.withAlpha(on, 0.75f)
        /** Плакат, неон, лист бумаги и табло совсем прозрачными не становятся — без фона стиль теряется. */
        val alpha = config.opacity.coerceIn(0f, 1f).let { o -> SOLID[family]?.let { min -> min + (1 - min) * o } ?: o }.times(255).roundToInt()
    }

    /**
     * Цвета виджета для светлого ([day]) и тёмного ([night]) оформления телефона. У темы «Как в системе»
     * на Android 12+ в виджете сразу оба набора — лаунчер переключает их сам, в ту же секунду, что и телефон.
     * Иначе набор один ([night] = null). [now] — набор, который виден сейчас: им рисуются картинки.
     */
    private class Paints(val day: Palette, val night: Palette?, val now: Palette) {
        val alpha get() = now.alpha

        fun color(rv: RemoteViews, id: Int, method: String, pick: (Palette) -> Int) {
            val n = night
            if (n != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) rv.setColorInt(id, method, pick(day), pick(n))
            else rv.setInt(id, method, pick(day))
        }

        fun text(rv: RemoteViews, id: Int, pick: (Palette) -> Int) = color(rv, id, "setTextColor", pick)

        fun tint(rv: RemoteViews, id: Int, pick: (Palette) -> Int) = color(rv, id, "setColorFilter", pick)

        fun list(rv: RemoteViews, id: Int, method: String, pick: (Palette) -> Int) {
            val n = night
            if (n != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                rv.setColorStateList(id, method, ColorStateList.valueOf(pick(day)), ColorStateList.valueOf(pick(n)))
            } else rv.setColorStateList(id, method, ColorStateList.valueOf(pick(day)))
        }
    }

    private fun paints(context: Context, theme: Theme, config: WidgetConfig, family: String, accentBackground: Boolean): Paints {
        fun of(t: Theme) = Palette(t, config, family, accentBackground)
        if (theme.mode == ThemeMode.AUTO && Compat.modernWidgets) {
            val day = of(theme.resolved(night = false))
            val night = of(theme.resolved(night = true))
            return Paints(day, night, if (DayNight.isNight(context)) night else day)
        }
        val now = of(DayNight.of(context, theme))
        return Paints(now, null, now)
    }

    /** Данные, общие для всех размеров одного виджета: считаются один раз. */
    private class Data(val context: Context, val config: WidgetConfig, val weather: WeatherData?, val widgetId: Int) {
        val alarm: String? by lazy { nextAlarm(context) }
        val events: List<CalendarEvent> by lazy { CalendarData.upcoming(context, 3) }
        val month by lazy { CalendarData.month() }
        val battery: Battery by lazy { Battery.read(context) }
        /** Картинки цифр часов: одна на всех размерах виджета с тем же текстом и высотой. */
        val digits = HashMap<String, Bitmap>()
        val apps: List<Pair<String, Bitmap?>> by lazy {
            val circle = config.style != "glass"
            config.apps.ifEmpty { AppCatalog.defaults(context) }.take(APPS_ON_WIDGET).map { it to AppCatalog.icon(context, it, 120, circle) }
        }
    }

    /** Стили со своим фоном (плакат, неон на тёмном, лист бумаги, табло): наименьшая непрозрачность подложки. */
    private val SOLID = mapOf("poster" to 0.6f, "paper" to 0.88f, "board" to 0.8f, "neon" to 0.7f)

    /** Стиль оформления виджета (для цветов): у всех видов одни и те же пять стилей. */
    fun family(kind: WidgetKind, style: String): String = kind.style(style).id

    fun layoutKey(kind: WidgetKind, config: WidgetConfig, size: WidgetSize): String {
        val style = kind.style(config.style).id
        val base = "${kind.prefix}_${style}_${size.key}"
        // У часов своя разметка на каждый шрифт цифр.
        return if (kind == WidgetKind.CLOCK) base + "_" + config.copy(style = style).digitFont().name.lowercase() else base
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
        widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val data = Data(context, config, weather, widgetId)
        if (!Compat.modernWidgets) {
            // До Android 12 лаунчер не выбирает разметку по размеру: одна — под текущий размер виджета.
            val s = sizes.firstOrNull() ?: defaultPreviewSize(kind).sizeF
            return sized(context, kind, config, theme, WidgetSize.classify(s.width, s.height), data, s)
        }
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
    ): RemoteViews = sized(context, kind, config, theme, size, Data(context, config, weather, AppWidgetManager.INVALID_APPWIDGET_ID), dp)

    private fun sized(
        context: Context, kind: WidgetKind, config: WidgetConfig, theme: Theme, size: WidgetSize, data: Data, dp: SizeF,
    ): RemoteViews {
        if (kind == WidgetKind.APPS && config.isFolder) return FolderWidget.views(context, config, DayNight.of(context, theme), dp, data.widgetId)
        val layout = WidgetLayouts[layoutKey(kind, config, size)]
            ?: WidgetLayouts[layoutKey(kind, WidgetConfig(style = kind.defaultStyle), size)]!!
        val style = kind.style(config.style).id
        val rv = RemoteViews(context.packageName, layout.res)
        val v = Views(rv, layout)
        // Небо — по погоде или по времени суток; на нём текст светлый, акцент — осветлённый цвет темы (если цвета не свои).
        val scene = when {
            kind == WidgetKind.WEATHER && config.scene -> data.weather?.now?.let { WeatherCodes.scene(it.code, it.isDay) }
            kind == WidgetKind.CLOCK && config.sky -> skyNow()
            else -> null
        }?.takeIf { SkyScenes.radii.any { r -> v.has("sc_sky$r") } }
        val colors = if (scene == null) config else config.copy(
            textColor = config.textColor ?: WHITE,
            accentColor = config.accentColor ?: ColorUtils.blendARGB(theme.accentColor, WHITE, 0.45f),
        )
        val p = paints(context, theme, colors, family(kind, style), layout.accentBackground)
        v.paint(p, config.align)
        if (scene != null) v.sky(scene, config.opacity)
        when (kind) {
            WidgetKind.CLOCK -> {
                v.clock(data)
                val font = config.copy(style = style).digitFont()
                if (ClockDigits.enabled(context, font)) {
                    v.digits(context, font, family(kind, style), config.align, dp, data, p)
                }
            }
            WidgetKind.CALENDAR -> v.calendar(context, family(kind, style), data, p)
            WidgetKind.WEATHER -> v.weather(data, p)
            WidgetKind.BATTERY -> v.battery(context, data.battery)
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
        fun textColor(slot: String, p: Paints, pick: (Palette) -> Int) = each(slot) { p.text(rv, it, pick) }
        fun tint(slot: String, color: Int) = each(slot) { rv.setInt(it, "setColorFilter", color) }
        fun tint(slot: String, p: Paints, pick: (Palette) -> Int) = each(slot) { p.tint(rv, it, pick) }

        inline fun each(slot: String, action: (Int) -> Unit) {
            for (id in WidgetSlots[slot]) if (layout.has(id)) action(id)
        }

        fun has(slot: String) = WidgetSlots[slot].any(layout::has)

        fun paint(p: Paints, align: WidgetAlign) {
            val gravity = when (align) {
                WidgetAlign.START -> Gravity.START or Gravity.CENTER_VERTICAL
                WidgetAlign.CENTER -> Gravity.CENTER
                WidgetAlign.END -> Gravity.END or Gravity.CENTER_VERTICAL
            }
            for (i in layout.ids.indices) {
                val id = layout.ids[i]
                when (layout.roles[2 * i]) {
                    'm' -> p.text(rv, id) { it.main }
                    's' -> p.text(rv, id) { it.sub }
                    'f' -> p.text(rv, id) { it.faint }
                    'a' -> p.text(rv, id) { it.label }
                    'o' -> p.text(rv, id) { it.on }
                    'n' -> p.text(rv, id) { it.onSub }
                    'M' -> p.tint(rv, id) { it.main }
                    'S' -> p.tint(rv, id) { it.sub }
                    'F' -> p.tint(rv, id) { it.faint }
                    'A' -> p.tint(rv, id) { it.label }
                    'O' -> p.tint(rv, id) { it.on }
                    'N' -> p.tint(rv, id) { it.onSub }
                    'b' -> {
                        p.tint(rv, id) { it.bg }
                        rv.setInt(id, "setImageAlpha", p.alpha)
                    }
                    'B' -> {
                        p.tint(rv, id) { it.accent }
                        rv.setInt(id, "setImageAlpha", p.alpha)
                    }
                    'g' -> rv.setInt(id, "setImageAlpha", p.alpha)
                    'h', 'x' -> p.tint(rv, id) { it.accent }
                    // Цвета через ColorStateList виджеты понимают с Android 12; раньше — цвета по умолчанию.
                    'P' -> {
                        p.text(rv, id) { it.onAccent }
                        if (Compat.modernWidgets) p.list(rv, id, "setBackgroundTintList") { it.accent }
                    }
                    'p' -> if (Compat.modernWidgets) {
                        p.list(rv, id, "setProgressTintList") { it.accent }
                        p.list(rv, id, "setProgressBackgroundTintList") { it.faint }
                    }
                }
                // Выравнивание: текст и контейнеры, помеченные в разметке (менять его виджетам можно с Android 12).
                if (layout.roles[2 * i + 1] != '.' && Compat.modernWidgets) rv.setInt(id, "setGravity", gravity)
            }
        }

        // ------------------------------------------------------------ часы

        fun clock(data: Data) {
            val alarm = data.alarm
            visible("row_alarm", alarm != null)
            if (alarm != null) text("alarm", alarm)
        }

        /**
         * Цифры выбранным шрифтом: картинка встаёт на место TextClock (в разметке рядом — с теми же
         * размерами), сам TextClock прячется. У выравниваемых цифр три картинки — видна та, что по выравниванию.
         */
        fun digits(context: Context, font: DigitFont, family: String, align: WidgetAlign, dp: SizeF, data: Data, p: Paints) {
            val glow = family == "neon"
            // На плашках «Табло» — только сами цифры, без полей шрифта: так они заполняют плашку.
            val tight = family == "board"
            val side = when (align) {
                WidgetAlign.START -> 's'
                WidgetAlign.CENTER -> 'c'
                WidgetAlign.END -> 'e'
            }
            val density = context.resources.displayMetrics.density
            // Чёткость: по высоте виджета, но без огромных картинок — их передают лаунчеру целиком.
            val px = (dp.height * density * 0.8f).toInt().coerceIn(64, 320)
            for (i in layout.ids.indices) {
                val t = TimeImages[layout.ids[i]] ?: continue
                val show = t.align == '.' || t.align == side
                rv.setViewVisibility(t.image, if (show) View.VISIBLE else View.GONE)
                if (!show) continue
                rv.setViewVisibility(t.clock, View.GONE)
                val text = ClockDigits.text(context, t.format)
                val bmp = data.digits.getOrPut("$text|$px|$tight") { ClockDigits.render(context, font, text, glow, px, tight) }
                rv.setImageViewBitmap(t.image, bmp)
                val role = layout.ids.indexOf(t.clock).takeIf { it >= 0 }?.let { layout.roles[2 * it] }
                p.tint(rv, t.image) {
                    when (role) {
                        'a' -> it.label
                        's' -> it.sub
                        'f' -> it.faint
                        'o' -> it.on
                        'n' -> it.onSub
                        else -> it.main
                    }
                }
            }
        }

        // ------------------------------------------------------------ календарь

        fun calendar(context: Context, family: String, data: Data, p: Paints) {
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
                    val own = e.color
                    if (own != 0) tint("ed_$i", own) else tint("ed_$i", p) { it.accent }
                }
            }
            // В маленьких размерах строки событий нет — там первое событие или подсказка.
            if (events.isEmpty()) text("ev0_title", hint.orEmpty())
            visible("ev_empty", hint != null)
            if (hint != null) text("ev_empty", hint)

            if (!layout.has(R.id.m_cd0)) return
            val grid = data.month
            grid.weekdays.forEachIndexed { i, name -> text("wd$i", name.take(2)) }
            // Сегодня: у «Неона» — кольцо, у «Бумаги» — черта под числом; число — цветом подписи, у остальных — на фигуре.
            val ownToday = family == "neon" || family == "paper"
            grid.cells.forEachIndexed { i, cell ->
                text("cd$i", cell.day.toString())
                textColor("cd$i", p) {
                    when {
                        cell.today -> if (ownToday) it.label else it.onAccent
                        cell.inMonth -> it.main
                        else -> it.faint
                    }
                }
                visible("tb_$i", cell.today)
                if (cell.today) tint("tb_$i", p) { it.accent }
            }
        }

        // ------------------------------------------------------------ погода

        /** Небо вместо подложки стиля. */
        fun sky(scene: String, opacity: Float) {
            for (r in SkyScenes.radii) each("sc_sky$r") {
                rv.setImageViewResource(it, SkyScenes.of(scene, r))
                rv.setViewVisibility(it, View.VISIBLE)
                // Небо видно и при прозрачной подложке; ползунок делает его плотнее.
                rv.setInt(it, "setImageAlpha", (255 * (0.55f + 0.45f * opacity.coerceIn(0f, 1f))).roundToInt())
            }
            if (layout.has(R.id.bg_s)) rv.setViewVisibility(R.id.bg_s, View.GONE)
            // У «Стекла» от стекла остаются блик и рамка поверх неба.
            if (layout.has(R.id.bg_g)) rv.setInt(R.id.bg_g, "setImageAlpha", 70)
        }

        fun weather(data: Data, p: Paints) {
            val mono = !data.config.colorIcons
            fun icon(slot: String, code: Int, isDay: Boolean) {
                each(slot) {
                    rv.setImageViewResource(it, WeatherCodes.icon(code, isDay))
                    // 0 — цвета иконки без изменений; иначе вся иконка в цвете текста.
                    p.tint(rv, it) { c -> if (mono) c.main else 0 }
                }
                // На акцентной фигуре цветная иконка теряется — она всегда одного цвета с текстом на фигуре.
                if (slot == "wx_now") each("wxo_now") {
                    rv.setImageViewResource(it, WeatherCodes.icon(code, isDay))
                    p.tint(rv, it) { c -> c.on }
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
            val week = w.days.take(5)
            val low = week.minOfOrNull { it.min } ?: 0.0
            val high = week.maxOfOrNull { it.max } ?: 0.0
            for (i in 0 until 5) {
                val d = w.days.getOrNull(i)
                visible("cell_d$i", d != null)
                if (d != null) {
                    val label = if (i == 0) "Сегодня" else LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    text("d${i}_day", label)
                    icon("wx_d$i", d.code, true)
                    text("d${i}_min", deg(d.min))
                    text("d${i}_max", deg(d.max))
                    each("rb_d$i") { rv.setImageViewBitmap(it, RangeBar.render(d.min, d.max, low, high, w.now.temp.takeIf { i == 0 }, p.now.main)) }
                }
            }
        }

        // ------------------------------------------------------------ батарея

        fun battery(context: Context, b: Battery) {
            text("pct", "${b.level}%")
            for (slot in listOf("pb_ring", "pb_bar")) each(slot) { rv.setProgressBar(it, 100, b.level, false) }
            // Цвет по состоянию: заряжается — зелёный, мало заряда — янтарный и красный, иначе — акцент темы.
            b.color?.let { c ->
                if (Compat.modernWidgets) {
                    for (slot in listOf("pb_ring", "pb_bar")) each(slot) { rv.setColorStateList(it, "setProgressTintList", ColorStateList.valueOf(c)) }
                }
                tint("i_bolt", c)
            }
            text("device", Battery.deviceName(context))
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

    /** Текущий час — для неба часов; тесты подставляют свой. */
    internal var hourNow: () -> Int = { java.time.LocalTime.now().hour }

    /** Небо часов по времени суток. */
    fun skyNow(hour: Int = hourNow()): String = when (hour) {
        in 5..7 -> "dawn"
        in 8..16 -> "day"
        in 17..19 -> "sunset"
        in 20..21 -> "evening"
        else -> "night"
    }

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
        /** Цвет заряда по состоянию; null — акцент темы. */
        val color: Int?
            get() = when {
                charging -> 0xFF34C759.toInt()
                level <= 15 -> 0xFFFF453A.toInt()
                level <= 30 -> 0xFFFFB020.toInt()
                else -> null
            }

        companion object {
            /** Имя телефона, как оно задано в настройках («Galaxy S25+»), иначе модель. */
            fun deviceName(context: Context): String = app.synctheme.mobile.data.DeviceSettings.name(context)

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
                    BatteryManager.BATTERY_HEALTH_GOOD -> ""
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
