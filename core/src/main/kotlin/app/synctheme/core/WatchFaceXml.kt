package app.synctheme.core

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Генерирует watchface.xml в формате Watch Face Format (v1) по настройкам часов.
 *
 * Фон (уже затемнённый и обрезанный под круг) телефон кладёт в ресурс [BG_RESOURCE],
 * стрелки — белые PNG в шаблоне, перекрашиваются через tintColor.
 */
object WatchFaceXml {
    const val SIZE = 450
    const val BG_RESOURCE = "wf_bg"
    const val HAND_HOUR = "hand_hour"
    const val HAND_MINUTE = "hand_minute"
    const val HAND_SECOND = "hand_second"
    /** Толстые скруглённые стрелки стиля «Облако». */
    const val HAND_HOUR_WIDE = "hand_hour_wide"
    const val HAND_MINUTE_WIDE = "hand_minute_wide"

    /** Шрифты в res/font шаблона циферблата (те же, что в приложении на телефоне). */
    const val FONT_BOLD = "oswald_700"
    const val FONT_NEON = "unbounded_700"
    const val FONT_ROUND = "nunito_900"
    const val FONT_GLASS = "manrope_800"
    const val FONT_LABEL = "onest_600"

    private const val C = SIZE / 2

    /** Внешний радиус делений и кольца секунд: почти у самого края экрана. */
    private const val EDGE = 222f

    fun generate(theme: Theme): String = generate(theme.watchSettings())

    fun generate(w: WatchSettings): String {
        val x = Xml()
        val text = color(w.textColor)
        val accent = color(w.accentColor)
        val weight = weight(w.fontWeight)
        val clockType = if (w.clockStyle == ClockStyle.ANALOG || w.clockStyle == ClockStyle.BLOB) "ANALOG" else "DIGITAL"

        x.line("""<?xml version="1.0" encoding="utf-8"?>""")
        x.open("""WatchFace width="$SIZE" height="$SIZE" clipShape="CIRCLE"""")
        x.line("""<Metadata key="CLOCK_TYPE" value="$clockType"/>""")
        x.line("""<Metadata key="PREVIEW_TIME" value="10:08:32"/>""")
        x.open("""Scene backgroundColor="#FF000000"""")

        // Фон: в режиме Always-On гасим, чтобы не жечь экран.
        x.open("""PartImage x="0" y="0" width="$SIZE" height="$SIZE" name="background"""")
        x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
        x.line("""<Image resource="$BG_RESOURCE"/>""")
        x.close("PartImage")

        when (w.clockStyle) {
            ClockStyle.DIGITAL -> digital(x, w, text, accent, weight)
            ClockStyle.STACKED -> stacked(x, w, text, accent, weight)
            ClockStyle.ANALOG -> analog(x, w, text, accent, weight)
            ClockStyle.BOLD -> bold(x, w, text, accent)
            ClockStyle.ORBIT -> orbit(x, w, text, accent)
            ClockStyle.NEON -> neon(x, w, text, accent)
            ClockStyle.BLOB -> blob(x, w, text, accent)
            ClockStyle.GLASS -> glass(x, w, text, accent)
            ClockStyle.MINIMAL -> minimal(x, w, text, accent)
        }

        x.close("Scene")
        x.close("WatchFace")
        return x.toString()
    }

    private fun digital(x: Xml, w: WatchSettings, text: String, accent: String, weight: String) {
        if (w.showSeconds) secondsRing(x, w.accentColor)
        if (w.showDate) {
            dateText(x, 75, 92, 300, 44, 30f, accent, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        }
        x.open("""DigitalClock x="15" y="140" width="420" height="150"""")
        x.open("""TimeText x="0" y="0" width="420" height="150" format="hh:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER"""")
        x.line("""<Font family="SYNC_TO_DEVICE" size="132" weight="$weight" color="$text"/>""")
        x.close("TimeText")
        x.close("DigitalClock")
        if (w.complications) {
            complication(x, 0, 117, 300, 96, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 237, 300, 96, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    private fun stacked(x: Xml, w: WatchSettings, text: String, accent: String, weight: String) {
        if (w.showSeconds) secondsRing(x, w.accentColor)
        x.open("""DigitalClock x="100" y="58" width="250" height="170"""")
        x.open("""TimeText x="0" y="0" width="250" height="170" format="hh" hourFormat="SYNC_TO_DEVICE" align="CENTER"""")
        x.line("""<Font family="SYNC_TO_DEVICE" size="168" weight="$weight" color="$text"/>""")
        x.close("TimeText")
        x.close("DigitalClock")
        x.open("""DigitalClock x="100" y="212" width="250" height="170"""")
        x.open("""TimeText x="0" y="0" width="250" height="170" format="mm" align="CENTER"""")
        x.line("""<Font family="SYNC_TO_DEVICE" size="168" weight="$weight" color="$accent"/>""")
        x.close("TimeText")
        x.close("DigitalClock")
        if (w.showDate) {
            dateText(x, 125, 382, 200, 36, 26f, text, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
        }
        if (w.complications) {
            complication(x, 0, 18, 177, 90, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 342, 177, 90, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    private fun analog(x: Xml, w: WatchSettings, text: String, accent: String, weight: String) {
        x.open("""PartDraw x="0" y="0" width="$SIZE" height="$SIZE" name="ticks"""")
        x.line("""<Variant mode="AMBIENT" target="alpha" value="150"/>""")
        for (i in 0 until 60) {
            val main = i % 15 == 0
            val hour = i % 5 == 0
            val (len, thickness, col) = when (w.dialStyle) {
                // Деления: 60 минутных + 12 часовых, акцентом — 12/3/6/9.
                DialStyle.TICKS -> when {
                    main -> Triple(30f, 8f, accent)
                    hour -> Triple(20f, 5f, color(withAlpha(w.textColor, 0xE6)))
                    else -> Triple(7f, 2f, color(withAlpha(w.textColor, 0x80)))
                }
                // Цифры по кругу + тонкие минутные деления у края.
                DialStyle.NUMBERS -> if (hour) Triple(8f, 3f, color(withAlpha(w.textColor, 0xB3)))
                    else Triple(5f, 1.5f, color(withAlpha(w.textColor, 0x66)))
                // Только четыре метки на 12/3/6/9.
                DialStyle.MINIMAL -> if (main) Triple(26f, 7f, accent) else continue
            }
            val a = Math.toRadians(i * 6.0)
            val sx = C + EDGE * sin(a)
            val sy = C - EDGE * cos(a)
            val ex = C + (EDGE - len) * sin(a)
            val ey = C - (EDGE - len) * cos(a)
            x.open("""Line startX="${f(sx)}" startY="${f(sy)}" endX="${f(ex)}" endY="${f(ey)}"""")
            x.line("""<Stroke color="$col" thickness="${f(thickness)}" cap="ROUND"/>""")
            x.close("Line")
        }
        x.close("PartDraw")

        if (w.dialStyle == DialStyle.NUMBERS) {
            for (h in 1..12) {
                val a = Math.toRadians(h * 30.0)
                val r = 186.0
                val cx = (C + r * sin(a)).toInt()
                val cy = (C - r * cos(a)).toInt()
                val col = if (h % 3 == 0) accent else text
                x.open("""PartText x="${cx - 30}" y="${cy - 24}" width="60" height="48" name="n$h"""")
                x.line("""<Variant mode="AMBIENT" target="alpha" value="150"/>""")
                x.open("""Text align="CENTER"""")
                x.line("""<Font family="SYNC_TO_DEVICE" size="38" weight="$weight" color="$col">$h</Font>""")
                x.close("Text")
                x.close("PartText")
            }
            if (w.showDate) {
                dateText(x, 165, 112, 120, 40, 26f, accent, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
            }
            if (w.complications) {
                complication(x, 0, 116, 262, 84, w, "WATCH_BATTERY", "RANGED_VALUE")
                complication(x, 1, 250, 262, 84, w, "STEP_COUNT", "SHORT_TEXT")
            }
        } else {
            if (w.showDate) {
                dateText(x, 268, 205, 120, 40, 26f, accent, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
            }
            if (w.complications) {
                complication(x, 0, 62, 177, 96, w, "WATCH_BATTERY", "RANGED_VALUE")
                complication(x, 1, 177, 292, 96, w, "STEP_COUNT", "SHORT_TEXT")
            }
        }

        val shadow = "#59000000"
        x.open("""AnalogClock x="0" y="0" width="$SIZE" height="$SIZE"""")
        // Тени стрелок для читаемости на светлых фото.
        hands(x, dx = 3, tintHour = shadow, tintMinute = shadow, tintSecond = shadow, seconds = w.showSeconds, ambientHidden = true)
        hands(x, dx = 0, tintHour = text, tintMinute = text, tintSecond = accent, seconds = w.showSeconds, ambientHidden = false)
        x.close("AnalogClock")

        x.open("""PartDraw x="211" y="211" width="28" height="28" name="cap"""")
        x.open("""Ellipse x="0" y="0" width="28" height="28"""")
        x.line("""<Fill color="$accent"/>""")
        x.close("Ellipse")
        x.open("""Ellipse x="9" y="9" width="10" height="10"""")
        x.line("""<Fill color="#FF000000"/>""")
        x.close("Ellipse")
        x.close("PartDraw")
    }

    // ---------------------------------------------------------------- «живые» стили

    /** «Крупно»: часы и минуты узким шрифтом во весь экран, дата и батарея — в просвете между ними. */
    private fun bold(x: Xml, w: WatchSettings, text: String, accent: String) {
        if (w.showSeconds) secondsRing(x, w.accentColor)
        timeText(x, 50, 28, 350, 200, "hh", FONT_BOLD, 200f, text, hourFormat = true)
        timeText(x, 50, 222, 350, 200, "mm", FONT_BOLD, 200f, accent)
        if (w.showDate) label(x, 326, 205, 104, 40, 24f, accent, FONT_LABEL, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
        if (w.complications) complication(x, 0, 24, 186, 78, w, "WATCH_BATTERY", "RANGED_VALUE")
    }

    /** «Орбита»: три кольца — часы, минуты, секунды — с бегущими точками; время в центре. */
    private fun orbit(x: Xml, w: WatchSettings, text: String, accent: String) {
        val track = color(withAlpha(w.textColor, 0x26))
        val hours = "([HOUR_0_11] + [MINUTE] / 60) * 30"
        val minutes = "([MINUTE] + [SECOND] / 60) * 6"
        part(x, "orbitHours") {
            arc(x, 166f, 0f, 360f, track, 12f)
            arc(x, 166f, 0f, 0f, color(withAlpha(w.accentColor, 0xCC)), 12f, end = hours)
        }
        part(x, "orbitMinutes") {
            arc(x, 190f, 0f, 360f, track, 8f)
            arc(x, 190f, 0f, 0f, text, 8f, end = minutes)
            dot(x, 190f, minutes, accent, 18f)
        }
        if (w.showSeconds) {
            part(x, "orbitSeconds", ambientAlpha = 0) {
                arc(x, 212f, 0f, 360f, track, 4f)
                arc(x, 212f, 0f, 0f, accent, 4f, end = "[SECOND] * 6")
                dot(x, 212f, "[SECOND] * 6", text, 12f)
            }
        }
        if (w.showDate) label(x, 105, 118, 240, 40, 26f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        timeText(x, 45, 160, 360, 124, "hh:mm", FONT_GLASS, 96f, text, hourFormat = true)
        if (w.complications) {
            complication(x, 0, 152, 290, 66, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 290, 66, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /** «Неон»: светящееся кольцо по краю, секунды — «комета» с хвостом, время с цветным ореолом. */
    private fun neon(x: Xml, w0: WatchSettings, text0: String, accent: String) {
        // Фон у неона всегда притемнён — тёмные цифры светлой темы на нём не видны.
        val w = if (isLight(w0.textColor)) w0 else w0.copy(textColor = 0xFFFFFFFF.toInt())
        val text = if (isLight(w0.textColor)) text0 else color(w.textColor)
        part(x, "neonRing") {
            for ((thickness, alpha) in listOf(22f to 0x10, 14f to 0x22, 8f to 0x50)) {
                arc(x, 210f, 0f, 360f, color(withAlpha(w.accentColor, alpha)), thickness)
            }
            arc(x, 210f, 0f, 360f, accent, 3.5f)
        }
        part(x, "neonMarks", ambientAlpha = 120) {
            for (h in 0 until 12) {
                val a = Math.toRadians(h * 30.0)
                val r = if (h % 3 == 0) 7 else 3
                val cx = (C + 186 * sin(a)).toInt()
                val cy = (C - 186 * cos(a)).toInt()
                x.open("""Ellipse x="${cx - r}" y="${cy - r}" width="${2 * r}" height="${2 * r}"""")
                x.line("""<Fill color="${if (h % 3 == 0) text else color(withAlpha(w.textColor, 0x99))}"/>""")
                x.close("Ellipse")
            }
        }
        if (w.showSeconds) {
            part(x, "neonComet", ambientAlpha = 0) {
                val head = "[SECOND] * 6"
                for ((len, alpha) in listOf(48 to 0x26, 28 to 0x55, 12 to 0xAA)) {
                    arc(x, 210f, 0f, 0f, color(withAlpha(0xFFFFFFFF.toInt(), alpha)), 6f, start = "$head - $len", end = head)
                }
                dot(x, 210f, head, "#FFFFFFFF", 11f)
            }
        }
        // Ореол: две полупрозрачные копии времени цветом акцента со сдвигом.
        val halo = color(withAlpha(w.accentColor, 0x66))
        timeText(x, 22, 161, 410, 128, "hh:mm", FONT_NEON, 80f, halo, hourFormat = true, ambientAlpha = 0)
        timeText(x, 28, 167, 410, 128, "hh:mm", FONT_NEON, 80f, halo, hourFormat = true, ambientAlpha = 0)
        timeText(x, 25, 164, 400, 128, "hh:mm", FONT_NEON, 80f, text, hourFormat = true)
        if (w.showDate) label(x, 95, 116, 260, 40, 24f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        if (w.complications) {
            complication(x, 0, 150, 300, 68, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 300, 68, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /**
     * «Облако»: «печенье» акцентного цвета нарисовано в фоне (телефон рисует его вместе с обоями),
     * сверху — толстые скруглённые стрелки цвета текста на акценте.
     */
    private fun blob(x: Xml, w: WatchSettings, text: String, accent: String) {
        val on = color(onAccent(w.accentColor))
        if (w.showDate) {
            x.open("""PartText x="300" y="203" width="80" height="44" name="day"""")
            x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
            x.open("""Text align="CENTER"""")
            x.line("""<Font family="$FONT_ROUND" size="34" color="$on"><Template><![CDATA[%d]]><Parameter expression="[DAY]"/></Template></Font>""")
            x.close("Text")
            x.close("PartText")
        }
        if (w.complications) complication(x, 0, 186, 300, 78, w, "WATCH_BATTERY", "RANGED_VALUE")
        x.open("""AnalogClock x="0" y="0" width="$SIZE" height="$SIZE"""")
        // На «печенье» — цвет текста на акценте; в режиме Always-On фон гаснет — там белые стрелки.
        wideHands(x, on, alpha = 255, ambient = 0)
        wideHands(x, "#FFFFFFFF", alpha = 0, ambient = 255)
        if (w.showSeconds) {
            x.open("""SecondHand resource="$HAND_SECOND" x="${C - 3}" y="40" width="6" height="225" pivotX="0.5" pivotY="${f(185.0 / 225, 4)}" tintColor="$on"""")
            x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
            x.close("SecondHand")
        }
        x.close("AnalogClock")
        x.open("""PartDraw x="207" y="207" width="36" height="36" name="cap"""")
        x.open("""Ellipse x="0" y="0" width="36" height="36"""")
        x.line("""<Fill color="$on"/>""")
        x.close("Ellipse")
        x.open("""Ellipse x="11" y="11" width="14" height="14"""")
        x.line("""<Fill color="$accent"/>""")
        x.close("Ellipse")
        x.close("PartDraw")
    }

    private fun wideHands(x: Xml, tint: String, alpha: Int, ambient: Int) {
        fun hand(tag: String, res: String, w: Int, h: Int, top: Int) {
            val pivotY = (C - top).toFloat() / h
            x.open("""$tag resource="$res" x="${C - w / 2}" y="$top" width="$w" height="$h" pivotX="0.5" pivotY="${f(pivotY, 4)}" tintColor="$tint" alpha="$alpha"""")
            x.line("""<Variant mode="AMBIENT" target="alpha" value="$ambient"/>""")
            x.close(tag)
        }
        hand("HourHand", HAND_HOUR_WIDE, 28, 140, 107)
        hand("MinuteHand", HAND_MINUTE_WIDE, 20, 196, 47)
    }

    /** «Стекло»: матовый диск в фоне, секунды бегут по его краю, время и дата — на стекле. */
    private fun glass(x: Xml, w: WatchSettings, text: String, accent: String) {
        if (w.showSeconds) {
            part(x, "glassSeconds", ambientAlpha = 0) {
                arc(x, 172f, 0f, 0f, accent, 5f, end = "[SECOND] * 6")
                dot(x, 172f, "[SECOND] * 6", accent, 13f)
            }
        }
        if (w.showDate) label(x, 105, 120, 240, 40, 25f, color(withAlpha(w.textColor, 0xCC)), FONT_LABEL, "%s, %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        timeText(x, 55, 160, 340, 124, "hh:mm", FONT_GLASS, 98f, text, hourFormat = true)
        if (w.complications) {
            complication(x, 0, 152, 288, 68, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 230, 288, 68, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /** «Минимал»: тонкие цифры, деления по краю и акцентная точка, которая обходит круг за час. */
    private fun minimal(x: Xml, w: WatchSettings, text: String, accent: String) {
        part(x, "minimalTicks", ambientAlpha = 110) {
            for (i in 0 until 60) {
                val hour = i % 5 == 0
                val len = if (hour) 16f else 7f
                val a = Math.toRadians(i * 6.0)
                x.open("""Line startX="${f(C + EDGE * sin(a))}" startY="${f(C - EDGE * cos(a))}" endX="${f(C + (EDGE - len) * sin(a))}" endY="${f(C - (EDGE - len) * cos(a))}"""")
                x.line("""<Stroke color="${color(withAlpha(w.textColor, if (hour) 0xB3 else 0x40))}" thickness="${if (hour) 3 else 1.5}" cap="ROUND"/>""")
                x.close("Line")
            }
        }
        part(x, "minuteDot") { dot(x, 196f, "([MINUTE] + [SECOND] / 60) * 6", accent, 16f) }
        if (w.showSeconds) part(x, "secondDot", ambientAlpha = 0) { dot(x, 180f, "[SECOND] * 6", color(withAlpha(w.textColor, 0x99)), 7f) }
        timeText(x, 35, 150, 380, 140, "hh:mm", "SYNC_TO_DEVICE", 116f, text, weight = "THIN", hourFormat = true)
        if (w.showDate) label(x, 105, 290, 240, 36, 22f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        if (w.complications) {
            complication(x, 0, 156, 330, 62, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 330, 62, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    // ---------------------------------------------------------------- общие элементы

    private fun timeText(
        x: Xml, px: Int, py: Int, w: Int, h: Int, format: String, family: String, size: Float, color: String,
        weight: String = "NORMAL", hourFormat: Boolean = false, ambientAlpha: Int? = null,
    ) {
        x.open("""DigitalClock x="$px" y="$py" width="$w" height="$h"""")
        val hf = if (hourFormat) """ hourFormat="SYNC_TO_DEVICE"""" else ""
        x.open("""TimeText x="0" y="0" width="$w" height="$h" format="$format"$hf align="CENTER"""")
        if (ambientAlpha != null) x.line("""<Variant mode="AMBIENT" target="alpha" value="$ambientAlpha"/>""")
        x.line("""<Font family="$family" size="${f(size)}" weight="$weight" color="$color"/>""")
        x.close("TimeText")
        x.close("DigitalClock")
    }

    private fun label(
        x: Xml, px: Int, py: Int, w: Int, h: Int, size: Float, color: String, family: String,
        template: String, vararg params: String,
    ) {
        x.open("""PartText x="$px" y="$py" width="$w" height="$h" name="label"""")
        x.open("""Text align="CENTER" ellipsis="TRUE"""")
        val parameters = params.joinToString("") { """<Parameter expression="$it"/>""" }
        x.line("""<Font family="$family" size="${f(size)}" color="$color"><Template><![CDATA[$template]]>$parameters</Template></Font>""")
        x.close("Text")
        x.close("PartText")
    }

    /** Слой рисования во весь экран; ambientAlpha — прозрачность в режиме Always-On. */
    private inline fun part(x: Xml, name: String, ambientAlpha: Int? = null, body: () -> Unit) {
        x.open("""PartDraw x="0" y="0" width="$SIZE" height="$SIZE" name="$name"""")
        if (ambientAlpha != null) x.line("""<Variant mode="AMBIENT" target="alpha" value="$ambientAlpha"/>""")
        body()
        x.close("PartDraw")
    }

    /** Дуга по кругу радиуса r; start/end — выражения, если дуга движется. 0° — это 12 часов. */
    private fun arc(
        x: Xml, r: Float, startAngle: Float, endAngle: Float, color: String, thickness: Float,
        start: String? = null, end: String? = null,
    ) {
        val d = f(r * 2)
        x.open("""Arc centerX="$C" centerY="$C" width="$d" height="$d" startAngle="${f(startAngle)}" endAngle="${f(endAngle)}"""")
        x.line("""<Stroke color="$color" thickness="${f(thickness)}" cap="ROUND"/>""")
        if (start != null) x.line("""<Transform target="startAngle" value="$start"/>""")
        if (end != null) x.line("""<Transform target="endAngle" value="$end"/>""")
        x.close("Arc")
    }

    /** Точка на окружности под углом angle (выражение): короткая дуга со скруглёнными концами. */
    private fun dot(x: Xml, r: Float, angle: String, color: String, size: Float) =
        arc(x, r, 0f, 0.5f, color, size, start = "$angle - 0.25", end = "$angle + 0.25")

    fun onAccent(accent: Int): Int = if (isLight(accent)) 0xFF141414.toInt() else 0xFFFFFFFF.toInt()

    /** Стрелки: изображения в шаблоне, точка вращения совпадает с центром циферблата. */
    private fun hands(
        x: Xml, dx: Int, tintHour: String, tintMinute: String, tintSecond: String,
        seconds: Boolean, ambientHidden: Boolean,
    ) {
        fun hand(tag: String, res: String, w: Int, h: Int, top: Int, tint: String, hideInAmbient: Boolean) {
            val pivotY = (C - top).toFloat() / h
            x.open("""$tag resource="$res" x="${C - w / 2 + dx}" y="${top + dx}" width="$w" height="$h" pivotX="0.5" pivotY="${f(pivotY, 4)}" tintColor="$tint"""")
            if (hideInAmbient) x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
            x.close(tag)
        }
        hand("HourHand", HAND_HOUR, 18, 150, 95, tintHour, ambientHidden)
        hand("MinuteHand", HAND_MINUTE, 12, 210, 35, tintMinute, ambientHidden)
        if (seconds) hand("SecondHand", HAND_SECOND, 6, 240, 25, tintSecond, true)
    }

    /** Тонкое кольцо по краю, которое заполняется за минуту. */
    private fun secondsRing(x: Xml, accentColor: Int) {
        x.open("""PartDraw x="0" y="0" width="$SIZE" height="$SIZE" name="secondsRing"""")
        x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
        val d = f(EDGE * 2 - 3)
        x.open("""Arc centerX="$C" centerY="$C" width="$d" height="$d" startAngle="0" endAngle="360"""")
        x.line("""<Stroke color="${color(withAlpha(accentColor, 0x40))}" thickness="4"/>""")
        x.close("Arc")
        x.open("""Arc centerX="$C" centerY="$C" width="$d" height="$d" startAngle="0" endAngle="0"""")
        x.line("""<Stroke color="${color(accentColor)}" thickness="6" cap="ROUND"/>""")
        x.line("""<Transform target="endAngle" value="[SECOND] * 6"/>""")
        x.close("Arc")
        x.close("PartDraw")
    }

    private fun dateText(
        x: Xml, px: Int, py: Int, w: Int, h: Int, size: Float, color: String,
        template: String, vararg params: String,
    ) {
        x.open("""PartText x="$px" y="$py" width="$w" height="$h" name="date"""")
        x.open("""Text align="CENTER" ellipsis="TRUE"""")
        // Font и Template — смешанный контент, поэтому без переносов строк внутри.
        val parameters = params.joinToString("") { """<Parameter expression="$it"/>""" }
        x.line("""<Font family="SYNC_TO_DEVICE" size="${f(size)}" weight="MEDIUM" color="$color"><Template><![CDATA[$template]]>$parameters</Template></Font>""")
        x.close("Text")
        x.close("PartText")
    }

    // В WFF v1 x/y/width/height у элементов — целые числа, у фигур (Arc, Line) — дробные.

    /** Мини-виджет (complication) в стиле темы; пользователь может сменить источник на часах. */
    private fun complication(
        x: Xml, id: Int, px: Int, py: Int, s: Int, w: WatchSettings,
        provider: String, providerType: String,
    ) {
        val text = color(w.textColor)
        val accent = color(w.accentColor)
        val track = color(withAlpha(w.textColor, 0x40))
        // Подложка под мини-виджет: тёмная для светлых цифр, светлая — для тёмных.
        val disc = if (isLight(w.textColor)) "#66000000" else "#80FFFFFF"
        val half = s / 2f
        x.open(
            """ComplicationSlot x="$px" y="$py" width="$s" height="$s" slotId="$id" """ +
                """supportedTypes="RANGED_VALUE SHORT_TEXT MONOCHROMATIC_IMAGE SMALL_IMAGE EMPTY" isCustomizable="TRUE""""
        )
        x.line("""<DefaultProviderPolicy defaultSystemProvider="$provider" defaultSystemProviderType="$providerType"/>""")
        x.line("""<BoundingOval x="0" y="0" width="$s" height="$s"/>""")

        fun discPart() {
            x.open("""PartDraw x="0" y="0" width="$s" height="$s"""")
            x.open("""Ellipse x="0" y="0" width="$s" height="$s"""")
            x.line("""<Fill color="$disc"/>""")
            x.close("Ellipse")
            x.close("PartDraw")
        }

        fun iconAndText(textSize: Float) {
            val icon = (s * 0.3f).toInt()
            x.open("Condition")
            x.open("Expressions")
            x.line("""<Expression name="hasIcon"><![CDATA[[COMPLICATION.MONOCHROMATIC_IMAGE] != null]]></Expression>""")
            x.close("Expressions")
            x.open("""Compare expression="hasIcon"""")
            x.open("""PartImage x="${(s - icon) / 2}" y="${(s * 0.17f).toInt()}" width="$icon" height="$icon" tintColor="$accent"""")
            x.line("""<Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/>""")
            x.close("PartImage")
            complicationText(x, 6, (s * 0.5f).toInt(), s - 12, (s * 0.3f).toInt(), textSize, text)
            x.close("Compare")
            x.open("Default")
            complicationText(x, 6, (s * 0.33f).toInt(), s - 12, (s * 0.34f).toInt(), textSize, text)
            x.close("Default")
            x.close("Condition")
        }

        // RANGED_VALUE: дуга прогресса (например, заряд батареи).
        x.open("""Complication type="RANGED_VALUE"""")
        discPart()
        x.open("""PartDraw x="0" y="0" width="$s" height="$s"""")
        x.open("""Arc centerX="${f(half)}" centerY="${f(half)}" width="${s - 10}" height="${s - 10}" startAngle="-140" endAngle="140"""")
        x.line("""<Stroke color="$track" thickness="6" cap="ROUND"/>""")
        x.close("Arc")
        x.open("""Arc centerX="${f(half)}" centerY="${f(half)}" width="${s - 10}" height="${s - 10}" startAngle="-140" endAngle="140"""")
        x.line("""<Stroke color="$accent" thickness="6" cap="ROUND"/>""")
        x.line(
            """<Transform target="endAngle" value="-140 + 280 * clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)"/>"""
        )
        x.close("Arc")
        x.close("PartDraw")
        iconAndText(s * 0.23f)
        x.close("Complication")

        x.open("""Complication type="SHORT_TEXT"""")
        discPart()
        iconAndText(s * 0.23f)
        x.close("Complication")

        x.open("""Complication type="MONOCHROMATIC_IMAGE"""")
        discPart()
        val big = (s * 0.5f).toInt()
        x.open("""PartImage x="${(s - big) / 2}" y="${(s - big) / 2}" width="$big" height="$big" tintColor="$accent"""")
        x.line("""<Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/>""")
        x.close("PartImage")
        x.close("Complication")

        x.open("""Complication type="SMALL_IMAGE"""")
        discPart()
        val img = (s * 0.62f).toInt()
        x.open("""PartImage x="${(s - img) / 2}" y="${(s - img) / 2}" width="$img" height="$img"""")
        x.line("""<Image resource="[COMPLICATION.SMALL_IMAGE]"/>""")
        x.close("PartImage")
        x.close("Complication")

        x.line("""<Complication type="EMPTY"/>""")
        x.close("ComplicationSlot")
    }

    private fun complicationText(x: Xml, px: Int, py: Int, w: Int, h: Int, size: Float, color: String) {
        x.open("""PartText x="$px" y="$py" width="$w" height="$h"""")
        x.open("""Text align="CENTER" ellipsis="TRUE"""")
        x.line("""<Font family="SYNC_TO_DEVICE" size="${f(size)}" weight="SEMI_BOLD" color="$color"><Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template></Font>""")
        x.close("Text")
        x.close("PartText")
    }

    private fun weight(w: FontWeight) = when (w) {
        FontWeight.THIN -> "THIN"
        FontWeight.LIGHT -> "LIGHT"
        FontWeight.REGULAR -> "NORMAL"
        FontWeight.BOLD -> "BOLD"
    }

    fun color(argb: Int): String = String.format(Locale.ROOT, "#%08X", argb)

    fun isLight(argb: Int): Boolean {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.5
    }

    fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)

    private fun f(v: Double, digits: Int = 1) = String.format(Locale.ROOT, "%.${digits}f", v)
    private fun f(v: Float, digits: Int = 1) = f(v.toDouble(), digits)

    /** Минимальный построитель XML с отступами. */
    private class Xml {
        private val sb = StringBuilder()
        private var depth = 0

        fun line(s: String) {
            repeat(depth) { sb.append("  ") }
            sb.append(s).append('\n')
        }

        fun open(tagWithAttrs: String) {
            line("<$tagWithAttrs>")
            depth++
        }

        fun close(tag: String) {
            depth--
            line("</$tag>")
        }

        override fun toString() = sb.toString()
    }
}
