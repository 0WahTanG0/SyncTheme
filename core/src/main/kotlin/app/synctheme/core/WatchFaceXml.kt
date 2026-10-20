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

    private const val C = SIZE / 2

    /** Внешний радиус делений и кольца секунд: почти у самого края экрана. */
    private const val EDGE = 222f

    fun generate(theme: Theme): String = generate(theme.watchSettings())

    fun generate(w: WatchSettings): String {
        val x = Xml()
        val text = color(w.textColor)
        val accent = color(w.accentColor)
        val weight = weight(w.fontWeight)
        val clockType = if (w.clockStyle == ClockStyle.ANALOG) "ANALOG" else "DIGITAL"

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
