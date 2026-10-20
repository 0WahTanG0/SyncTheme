package app.synctheme.core

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Генерирует watchface.xml в формате Watch Face Format (v1) по настройкам часов.
 *
 * Фон (уже затемнённый и обрезанный под круг) телефон кладёт в ресурс [BG_RESOURCE],
 * стрелки — белые PNG в шаблоне, перекрашиваются через tintColor.
 *
 * Каждый стиль рисуется слоями ([Layer]): обычный циферблат, «только время» поверх объекта
 * с фото (эффект глубины) и отдельный контрастный слой для Always-On — так время читается всегда.
 */
object WatchFaceXml {
    const val SIZE = 450
    const val BG_RESOURCE = "wf_bg"
    /** Вырезанный с фото главный объект (PNG с прозрачностью) — слой поверх цифр для эффекта глубины. */
    const val FG_RESOURCE = "wf_fg"
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

    /**
     * Прозрачность времени, нарисованного поверх объекта с фото (0..255): объект остаётся впереди,
     * но цифры и стрелки сквозь него видно — время понятно, даже если объект закрыл их целиком.
     */
    const val DEPTH_TIME_ALPHA = 0x73

    private const val C = SIZE / 2

    /** Внешний радиус делений и кольца секунд: почти у самого края экрана. */
    private const val EDGE = 222f

    /** Слой стиля. */
    enum class Layer {
        /** Обычный режим: всё оформление. */
        FULL,
        /** Только то, по чему читается время (цифры, стрелки, кольца-шкалы) — поверх объекта с фото. */
        TIME,
        /** Always-On: время, дата и тонкие метки светлыми цветами на чёрном. */
        AMBIENT,
    }

    /**
     * Настройки для слоя: без секунд и мини-виджетов поверх объекта и в Always-On, а в Always-On
     * ещё и светлые цвета — тёмные цифры светлой темы на чёрном экране не видны.
     */
    fun forLayer(w: WatchSettings, layer: Layer): WatchSettings = when (layer) {
        Layer.FULL -> w
        Layer.TIME -> w.copy(showSeconds = false, complications = false, showDate = false)
        Layer.AMBIENT -> w.copy(
            showSeconds = false, complications = false,
            textColor = ambientText(w.textColor), accentColor = ambientAccent(w.accentColor),
        )
    }

    /** Цвет цифр в Always-On: светлые цвета темы остаются, тёмные становятся белыми. */
    fun ambientText(c: Int): Int = if (luminance(c) >= 0.55) c or (0xFF shl 24) else 0xFFFFFFFF.toInt()

    /** Акцент в Always-On: тот же оттенок, осветлённый до читаемого на чёрном. */
    fun ambientAccent(c: Int): Int {
        var t = 0.0
        var out = c or (0xFF shl 24)
        while (luminance(out) < 0.45 && t < 1.0) {
            t += 0.1
            out = mixWhite(c, t)
        }
        return out
    }

    private fun mixWhite(c: Int, t: Double): Int {
        fun ch(shift: Int) = ((c shr shift and 0xFF) + (255 - (c shr shift and 0xFF)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun generate(theme: Theme, foreground: Boolean = theme.watchSettings().depth): String =
        generate(theme.watchSettings(), foreground)

    /**
     * @param foreground в APK есть слой [FG_RESOURCE] — рисуем его поверх времени (эффект глубины).
     */
    fun generate(w: WatchSettings, foreground: Boolean = w.depth): String {
        val x = Xml()
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

        // Мини-виджеты копятся отдельно и выводятся поверх слоя глубины.
        x.deferComplications = true
        group(x, "face", alpha = 255, ambientAlpha = 0) { style(x, w, Layer.FULL) }
        x.deferComplications = false

        if (foreground) {
            x.open("""PartImage x="0" y="0" width="$SIZE" height="$SIZE" name="depth"""")
            x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
            x.line("""<Image resource="$FG_RESOURCE"/>""")
            x.close("PartImage")
            group(x, "timeOverDepth", alpha = DEPTH_TIME_ALPHA, ambientAlpha = 0) { style(x, forLayer(w, Layer.TIME), Layer.TIME) }
        }
        x.flushDeferred()
        group(x, "alwaysOn", alpha = 0, ambientAlpha = 255) { style(x, forLayer(w, Layer.AMBIENT), Layer.AMBIENT) }

        x.close("Scene")
        x.close("WatchFace")
        return x.toString()
    }

    private fun style(x: Xml, w: WatchSettings, layer: Layer) {
        val text = color(w.textColor)
        val accent = color(w.accentColor)
        val weight = weight(w.fontWeight)
        when (w.clockStyle) {
            ClockStyle.DIGITAL -> digital(x, w, text, accent, weight)
            ClockStyle.STACKED -> stacked(x, w, text, accent, weight)
            ClockStyle.ANALOG -> analog(x, w, text, accent, weight, layer)
            ClockStyle.BOLD -> bold(x, w, text, accent)
            ClockStyle.ORBIT -> orbit(x, w, text, accent, layer)
            ClockStyle.NEON -> neon(x, w, text, accent, layer)
            ClockStyle.BLOB -> blob(x, w, text, accent, layer)
            ClockStyle.GLASS -> glass(x, w, text, accent)
            ClockStyle.MINIMAL -> minimal(x, w, text, accent, layer)
            ClockStyle.DRUM -> drum(x, w, text, accent, layer)
            ClockStyle.COLUMNS -> columns(x, w, text, accent, layer)
            ClockStyle.PLANETS -> planets(x, w, text, accent, layer)
            ClockStyle.MATRIX -> matrix(x, w, text, accent, layer)
            ClockStyle.LIQUID -> liquid(x, w, text, accent, layer)
            ClockStyle.DIAGONAL -> diagonal(x, w, text, accent)
        }
    }

    /** Группа во весь экран с прозрачностью alpha и ambientAlpha в режиме Always-On. */
    private inline fun group(x: Xml, name: String, alpha: Int, ambientAlpha: Int, body: () -> Unit) {
        x.open("""Group x="0" y="0" width="$SIZE" height="$SIZE" alpha="$alpha" name="$name"""")
        x.line("""<Variant mode="AMBIENT" target="alpha" value="$ambientAlpha"/>""")
        body()
        x.close("Group")
    }

    // ---------------------------------------------------------------- классические стили

    private fun digital(x: Xml, w: WatchSettings, text: String, accent: String, weight: String) {
        if (w.showSeconds) secondsRing(x, w.accentColor)
        if (w.showDate) {
            dateText(x, 75, 92, 300, 44, 30f, accent, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        }
        val font = w.digits()
        if (font != null) {
            digits(x, C, 215, 380, 110, "hh:mm", font, text)
        } else {
            timeText(x, 15, 140, 420, 150, "hh:mm", "SYNC_TO_DEVICE", 132f, text, weight, hourFormat = true)
        }
        if (w.complications) {
            complication(x, 0, 117, 300, 96, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 237, 300, 96, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    private fun stacked(x: Xml, w: WatchSettings, text: String, accent: String, weight: String) {
        if (w.showSeconds) secondsRing(x, w.accentColor)
        val font = w.digits()
        if (font != null) {
            digits(x, C, 143, 240, 130, "hh", font, text)
            digits(x, C, 297, 240, 130, "mm", font, accent, hourFormat = null)
        } else {
            timeText(x, 100, 58, 250, 170, "hh", "SYNC_TO_DEVICE", 168f, text, weight, hourFormat = true)
            timeText(x, 100, 212, 250, 170, "mm", "SYNC_TO_DEVICE", 168f, accent, weight)
        }
        if (w.showDate) {
            dateText(x, 125, 382, 200, 36, 26f, text, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
        }
        if (w.complications) {
            complication(x, 0, 18, 177, 90, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 342, 177, 90, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    private fun analog(x: Xml, w: WatchSettings, text: String, accent: String, weight: String, layer: Layer) {
        // Деления нужны, чтобы читать стрелки, — и в Always-On (тоньше); поверх объекта с фото их нет.
        if (layer != Layer.TIME) {
            val dim = layer == Layer.AMBIENT
            x.open("""PartDraw x="0" y="0" width="$SIZE" height="$SIZE" name="ticks"""")
            for (i in 0 until 60) {
                val main = i % 15 == 0
                val hour = i % 5 == 0
                if (dim && !hour) continue
                val (len, thickness, col) = when (w.dialStyle) {
                    // Деления: 60 минутных + 12 часовых, акцентом — 12/3/6/9.
                    DialStyle.TICKS -> when {
                        main -> Triple(30f, if (dim) 4f else 8f, accent)
                        hour -> Triple(20f, if (dim) 3f else 5f, color(withAlpha(w.textColor, if (dim) 0xB3 else 0xE6)))
                        else -> Triple(7f, 2f, color(withAlpha(w.textColor, 0x80)))
                    }
                    // Цифры по кругу + тонкие минутные деления у края.
                    DialStyle.NUMBERS -> if (hour) Triple(8f, 3f, color(withAlpha(w.textColor, 0xB3)))
                        else Triple(5f, 1.5f, color(withAlpha(w.textColor, 0x66)))
                    // Только четыре метки на 12/3/6/9.
                    DialStyle.MINIMAL -> if (main) Triple(26f, if (dim) 4f else 7f, accent) else continue
                }
                val a = Math.toRadians(i * 6.0)
                x.open("""Line startX="${f(C + EDGE * sin(a))}" startY="${f(C - EDGE * cos(a))}" endX="${f(C + (EDGE - len) * sin(a))}" endY="${f(C - (EDGE - len) * cos(a))}"""")
                x.line("""<Stroke color="$col" thickness="${f(thickness)}" cap="ROUND"/>""")
                x.close("Line")
            }
            x.close("PartDraw")
        }

        if (w.dialStyle == DialStyle.NUMBERS) {
            if (layer != Layer.TIME) {
                for (h in 1..12) {
                    val a = Math.toRadians(h * 30.0)
                    val cx = (C + 186.0 * sin(a)).toInt()
                    val cy = (C - 186.0 * cos(a)).toInt()
                    val col = if (h % 3 == 0) accent else text
                    x.open("""PartText x="${cx - 30}" y="${cy - 24}" width="60" height="48" name="n$h"""")
                    x.open("""Text align="CENTER"""")
                    x.line("""<Font family="SYNC_TO_DEVICE" size="38" weight="$weight" color="$col">$h</Font>""")
                    x.close("Text")
                    x.close("PartText")
                }
            }
            if (w.showDate) dateText(x, 165, 112, 120, 40, 26f, accent, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
            if (w.complications) {
                complication(x, 0, 116, 262, 84, w, "WATCH_BATTERY", "RANGED_VALUE")
                complication(x, 1, 250, 262, 84, w, "STEP_COUNT", "SHORT_TEXT")
            }
        } else {
            if (w.showDate) dateText(x, 268, 205, 120, 40, 26f, accent, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
            if (w.complications) {
                complication(x, 0, 62, 177, 96, w, "WATCH_BATTERY", "RANGED_VALUE")
                complication(x, 1, 177, 292, 96, w, "STEP_COUNT", "SHORT_TEXT")
            }
        }

        x.open("""AnalogClock x="0" y="0" width="$SIZE" height="$SIZE"""")
        // Тени стрелок для читаемости на светлых фото.
        if (layer == Layer.FULL) {
            val shadow = "#59000000"
            hands(x, dx = 3, tintHour = shadow, tintMinute = shadow, tintSecond = shadow, seconds = w.showSeconds)
        }
        hands(x, dx = 0, tintHour = text, tintMinute = text, tintSecond = accent, seconds = w.showSeconds)
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
        val font = w.digits() ?: DigitFont.NARROW
        digits(x, C, 136, 300, 170, "hh", font, text)
        digits(x, C, 318, 300, 170, "mm", font, accent, hourFormat = null)
        if (w.showDate) label(x, 326, 205, 104, 40, 24f, accent, FONT_LABEL, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
        if (w.complications) complication(x, 0, 24, 186, 78, w, "WATCH_BATTERY", "RANGED_VALUE")
    }

    /** «Орбита»: три кольца — часы, минуты, секунды — с бегущими точками; время в центре. */
    private fun orbit(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val track = color(withAlpha(w.textColor, 0x26))
        val hours = "([HOUR_0_11] + [MINUTE] / 60) * 30"
        val minutes = "([MINUTE] + [SECOND] / 60) * 6"
        // Кольца часов и минут — тоже шкала времени: в Always-On они тонкие, поверх объекта их нет.
        if (layer != Layer.TIME) {
            val thin = layer == Layer.AMBIENT
            part(x, "orbitHours") {
                if (!thin) arc(x, 166f, 0f, 360f, track, 12f)
                arc(x, 166f, 0f, 0f, color(withAlpha(w.accentColor, if (thin) 0x99 else 0xCC)), if (thin) 4f else 12f, end = hours)
            }
            part(x, "orbitMinutes") {
                if (!thin) arc(x, 190f, 0f, 360f, track, 8f)
                arc(x, 190f, 0f, 0f, if (thin) color(withAlpha(w.textColor, 0x99)) else text, if (thin) 3f else 8f, end = minutes)
                if (!thin) dot(x, 190f, minutes, accent, 18f)
            }
        }
        if (w.showSeconds) {
            part(x, "orbitSeconds") {
                arc(x, 212f, 0f, 360f, track, 4f)
                arc(x, 212f, 0f, 0f, accent, 4f, end = "[SECOND] * 6")
                dot(x, 212f, "[SECOND] * 6", text, 12f)
            }
        }
        if (w.showDate) label(x, 105, 118, 240, 40, 26f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        digits(x, C, 222, 280, 76, "hh:mm", w.digits() ?: DigitFont.SPACE, text)
        if (w.complications) {
            complication(x, 0, 152, 290, 66, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 290, 66, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /** «Неон»: светящееся кольцо по краю, секунды — «комета» с хвостом, время с цветным ореолом. */
    private fun neon(x: Xml, w0: WatchSettings, text0: String, accent: String, layer: Layer) {
        // Фон у неона всегда притемнён — тёмные цифры светлой темы на нём не видны.
        val w = if (isLight(w0.textColor)) w0 else w0.copy(textColor = 0xFFFFFFFF.toInt())
        val text = if (isLight(w0.textColor)) text0 else color(w.textColor)
        if (layer == Layer.FULL) {
            part(x, "neonGlow") {
                for ((thickness, alpha) in listOf(22f to 0x10, 14f to 0x22, 8f to 0x50)) {
                    arc(x, 210f, 0f, 360f, color(withAlpha(w.accentColor, alpha)), thickness)
                }
            }
        }
        if (layer != Layer.TIME) {
            val ambient = layer == Layer.AMBIENT
            part(x, "neonRing") {
                arc(x, 210f, 0f, 360f, if (ambient) color(withAlpha(w.accentColor, 0x80)) else accent, if (ambient) 2f else 3.5f)
            }
            part(x, "neonMarks") {
                for (h in 0 until 12) {
                    if (ambient && h % 3 != 0) continue
                    val a = Math.toRadians(h * 30.0)
                    val r = if (h % 3 == 0) 7 else 3
                    val cx = (C + 186 * sin(a)).toInt()
                    val cy = (C - 186 * cos(a)).toInt()
                    x.open("""Ellipse x="${cx - r}" y="${cy - r}" width="${2 * r}" height="${2 * r}"""")
                    x.line("""<Fill color="${if (h % 3 == 0) text else color(withAlpha(w.textColor, 0x99))}"/>""")
                    x.close("Ellipse")
                }
            }
        }
        if (w.showSeconds) {
            part(x, "neonComet") {
                val head = "[SECOND] * 6"
                for ((len, alpha) in listOf(48 to 0x26, 28 to 0x55, 12 to 0xAA)) {
                    arc(x, 210f, 0f, 0f, color(withAlpha(0xFFFFFFFF.toInt(), alpha)), 6f, start = "$head - $len", end = head)
                }
                dot(x, 210f, head, "#FFFFFFFF", 11f)
            }
        }
        val font = w.digits() ?: DigitFont.TUBE
        if (layer == Layer.FULL) {
            // Ореол: две полупрозрачные копии времени цветом акцента со сдвигом.
            val halo = color(withAlpha(w.accentColor, 0x66))
            digits(x, C - 3, 225, 300, 84, "hh:mm", font, halo)
            digits(x, C + 3, 231, 300, 84, "hh:mm", font, halo)
        }
        digits(x, C, 228, 300, 84, "hh:mm", font, text)
        if (w.showDate) label(x, 95, 116, 260, 40, 24f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        if (w.complications) {
            complication(x, 0, 150, 300, 68, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 300, 68, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /**
     * «Облако»: «печенье» акцентного цвета нарисовано в фоне (телефон рисует его вместе с обоями),
     * сверху — толстые скруглённые стрелки цвета текста на акценте. В Always-On «печенья» нет —
     * там стрелки светлые, а часы подсказывают 12 точек.
     */
    private fun blob(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val ambient = layer == Layer.AMBIENT
        val on = if (ambient) text else color(onAccent(w.accentColor))
        if (ambient) {
            part(x, "blobMarks") {
                for (h in 0 until 12) {
                    val a = Math.toRadians(h * 30.0)
                    val r = if (h % 3 == 0) 6 else 3
                    val cx = (C + 160 * sin(a)).toInt()
                    val cy = (C - 160 * cos(a)).toInt()
                    x.open("""Ellipse x="${cx - r}" y="${cy - r}" width="${2 * r}" height="${2 * r}"""")
                    x.line("""<Fill color="${color(withAlpha(w.accentColor, 0xB3))}"/>""")
                    x.close("Ellipse")
                }
            }
        }
        if (w.showDate) {
            x.open("""PartText x="300" y="203" width="80" height="44" name="day"""")
            x.open("""Text align="CENTER"""")
            x.line("""<Font family="$FONT_ROUND" size="34" color="$on"><Template><![CDATA[%d]]><Parameter expression="[DAY]"/></Template></Font>""")
            x.close("Text")
            x.close("PartText")
        }
        if (w.complications) complication(x, 0, 186, 300, 78, w, "WATCH_BATTERY", "RANGED_VALUE")
        x.open("""AnalogClock x="0" y="0" width="$SIZE" height="$SIZE"""")
        wideHands(x, on)
        if (w.showSeconds) {
            x.open("""SecondHand resource="$HAND_SECOND" x="${C - 3}" y="40" width="6" height="225" pivotX="0.5" pivotY="${f(185.0 / 225, 4)}" tintColor="$on"""")
            x.close("SecondHand")
        }
        x.close("AnalogClock")
        x.open("""PartDraw x="207" y="207" width="36" height="36" name="cap"""")
        x.open("""Ellipse x="0" y="0" width="36" height="36"""")
        x.line("""<Fill color="$on"/>""")
        x.close("Ellipse")
        x.open("""Ellipse x="11" y="11" width="14" height="14"""")
        x.line("""<Fill color="${if (ambient) "#FF000000" else accent}"/>""")
        x.close("Ellipse")
        x.close("PartDraw")
    }

    private fun wideHands(x: Xml, tint: String) {
        fun hand(tag: String, res: String, w: Int, h: Int, top: Int) {
            val pivotY = (C - top).toFloat() / h
            x.open("""$tag resource="$res" x="${C - w / 2}" y="$top" width="$w" height="$h" pivotX="0.5" pivotY="${f(pivotY, 4)}" tintColor="$tint"""")
            x.close(tag)
        }
        hand("HourHand", HAND_HOUR_WIDE, 28, 140, 107)
        hand("MinuteHand", HAND_MINUTE_WIDE, 20, 196, 47)
    }

    /** «Стекло»: матовый диск в фоне, секунды бегут по его краю, время и дата — на стекле. */
    private fun glass(x: Xml, w: WatchSettings, text: String, accent: String) {
        if (w.showSeconds) {
            part(x, "glassSeconds") {
                arc(x, 172f, 0f, 0f, accent, 5f, end = "[SECOND] * 6")
                dot(x, 172f, "[SECOND] * 6", accent, 13f)
            }
        }
        if (w.showDate) label(x, 105, 120, 240, 40, 25f, color(withAlpha(w.textColor, 0xCC)), FONT_LABEL, "%s, %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        digits(x, C, 222, 280, 84, "hh:mm", w.digits() ?: DigitFont.ITALIC, text)
        if (w.complications) {
            complication(x, 0, 152, 288, 68, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 230, 288, 68, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /** «Минимал»: тонкие цифры, деления по краю и акцентная точка, которая обходит круг за час. */
    private fun minimal(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        if (layer != Layer.TIME) {
            val dim = layer == Layer.AMBIENT
            part(x, "minimalTicks") {
                for (i in 0 until 60) {
                    val hour = i % 5 == 0
                    if (dim && !hour) continue
                    val len = if (hour) 16f else 7f
                    val a = Math.toRadians(i * 6.0)
                    x.open("""Line startX="${f(C + EDGE * sin(a))}" startY="${f(C - EDGE * cos(a))}" endX="${f(C + (EDGE - len) * sin(a))}" endY="${f(C - (EDGE - len) * cos(a))}"""")
                    x.line("""<Stroke color="${color(withAlpha(w.textColor, if (hour) 0xB3 else 0x40))}" thickness="${if (hour) (if (dim) 2 else 3) else 1.5}" cap="ROUND"/>""")
                    x.close("Line")
                }
            }
            part(x, "minuteDot") { dot(x, 196f, "([MINUTE] + [SECOND] / 60) * 6", accent, if (dim) 10f else 16f) }
        }
        if (w.showSeconds) part(x, "secondDot") { dot(x, 180f, "[SECOND] * 6", color(withAlpha(w.textColor, 0x99)), 7f) }
        digits(x, C, 220, 320, 90, "hh:mm", w.digits() ?: DigitFont.THIN, text)
        if (w.showDate) label(x, 105, 290, 240, 36, 22f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        if (w.complications) {
            complication(x, 0, 156, 330, 62, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 330, 62, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /**
     * «Барабан»: два кольца с цифрами вращаются, текущие час и минута проходят через окошко справа.
     * Подписи повёрнуты вдоль радиуса — в окошке они стоят ровно.
     */
    private fun drum(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val font = w.digits() ?: DigitFont.MONO
        // Окошко показывает, где читать время, — оно есть во всех слоях.
        part(x, "window") {
            x.open("""RoundRectangle x="296" y="191" width="150" height="68" cornerRadiusX="34" cornerRadiusY="34"""")
            if (layer == Layer.FULL) x.line("""<Fill color="${color(withAlpha(w.accentColor, 0x33))}"/>""")
            x.line("""<Stroke color="$accent" thickness="${if (layer == Layer.FULL) 3 else 2}"/>""")
            x.close("RoundRectangle")
        }
        group(x, "minuteRing", "90 - ([MINUTE] + [SECOND] / 60) * 6") {
            if (layer == Layer.FULL) {
                part(x, "minuteTicks") {
                    for (m in 0 until 60) {
                        if (m % 5 == 0) continue
                        val a = Math.toRadians(m * 6.0)
                        x.open("""Line startX="${f(C + 219 * sin(a))}" startY="${f(C - 219 * cos(a))}" endX="${f(C + 212 * sin(a))}" endY="${f(C - 212 * cos(a))}"""")
                        x.line("""<Stroke color="${color(withAlpha(w.textColor, 0x55))}" thickness="2" cap="ROUND"/>""")
                        x.close("Line")
                    }
                }
            }
            for (m in 0 until 60 step 5) {
                ringLabel(x, m * 6.0, 190.0, "%02d".format(m), font, 40, 28, text)
            }
        }
        group(x, "hourRing", "90 - ([HOUR_0_11] + [MINUTE] / 60) * 30") {
            for (h in 0 until 12) {
                ringLabel(x, h * 30.0, 124.0, "${if (h == 0) 12 else h}", font, 60, 40, if (h % 3 == 0) accent else text)
            }
        }
        if (w.showSeconds) {
            part(x, "drumSeconds") {
                arc(x, 62f, 0f, 360f, color(withAlpha(w.textColor, 0x26)), 4f)
                arc(x, 62f, 0f, 0f, accent, 4f, end = "[SECOND] * 6")
                dot(x, 62f, "[SECOND] * 6", text, 10f)
            }
        }
        if (w.showDate) {
            label(x, 165, 196, 120, 30, 22f, accent, FONT_LABEL, "%s", "[DAY_OF_WEEK_S]")
            label(x, 165, 222, 120, 36, 30f, text, FONT_LABEL, "%d", "[DAY]")
        }
    }

    /** Подпись на кольце «Барабана»: центр на окружности под углом angle, повёрнута вдоль радиуса. */
    private fun ringLabel(x: Xml, angle: Double, r: Double, value: String, font: DigitFont, w: Int, h: Int, color: String) {
        val a = Math.toRadians(angle)
        val cx = (C + r * sin(a)).toInt()
        val cy = (C - r * cos(a)).toInt()
        val size = fit(font, "0".repeat(value.length), w - 6, h - 8)
        x.open("""PartText x="${cx - w / 2}" y="${cy - h / 2}" width="$w" height="$h" angle="${f(angle - 90)}" name="l$value"""")
        x.open("""Text align="CENTER"""")
        x.line("""<Font family="${family(font)}" size="${f(size)}" weight="${fontWeight(font)}" color="$color">$value</Font>""")
        x.close("Text")
        x.close("PartText")
    }

    /** «Колонки»: прошлый, текущий и следующий час и минута столбиками, секунды — шкала между ними. */
    private fun columns(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val font = w.digits() ?: DigitFont.SERIF
        digits(x, 140, 225, 150, 116, "hh", font, text, hourFormat = "24")
        digits(x, 310, 225, 150, 116, "mm", font, accent, hourFormat = null)
        if (layer == Layer.FULL) {
            val faded = color(withAlpha(w.textColor, 0x59))
            for ((cy, expr) in listOf(92 to "([HOUR_0_23] + 23) % 24", 358 to "([HOUR_0_23] + 1) % 24")) {
                number(x, 140, cy, 90, 46, "%02d", expr, font, faded)
            }
            for ((cy, expr) in listOf(92 to "([MINUTE] + 59) % 60", 358 to "([MINUTE] + 1) % 60")) {
                number(x, 310, cy, 90, 46, "%02d", expr, font, faded)
            }
        }
        if (w.showSeconds) {
            part(x, "secondsBar") {
                x.open("""RoundRectangle x="221" y="150" width="8" height="150" cornerRadiusX="4" cornerRadiusY="4"""")
                x.line("""<Fill color="${color(withAlpha(w.textColor, 0x26))}"/>""")
                x.close("RoundRectangle")
                x.open("""RoundRectangle x="221" y="150" width="8" height="8" cornerRadiusX="4" cornerRadiusY="4"""")
                x.line("""<Fill color="$accent"/>""")
                x.line("""<Transform target="height" value="8 + [SECOND] * 2.4"/>""")
                x.close("RoundRectangle")
            }
        }
        if (w.showDate) label(x, 125, 392, 200, 34, 22f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
    }

    /** «Планеты»: часы и минуты — шары на своих орбитах, секунды — спутник у края. */
    private fun planets(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val font = w.digits() ?: DigitFont.WIDE
        if (layer != Layer.TIME) {
            part(x, "orbits") {
                val orbit = color(withAlpha(w.textColor, if (layer == Layer.AMBIENT) 0x40 else 0x33))
                arc(x, 105f, 0f, 360f, orbit, 2f)
                arc(x, 178f, 0f, 360f, orbit, 2f)
                x.open("""Ellipse x="${C - 9}" y="${C - 9}" width="18" height="18"""")
                x.line("""<Fill color="$accent"/>""")
                x.close("Ellipse")
            }
        }
        // В Always-On шары — тёмные с цветным контуром: время видно, экран почти не светится.
        val ambient = layer == Layer.AMBIENT
        planet(x, "hourPlanet", 105, 44, "([HOUR_0_11] + [MINUTE] / 60) * 30", accent,
            if (ambient) accent else color(onAccent(w.accentColor)), "%d", "[HOUR_0_23]", font, ray = layer == Layer.FULL, outline = ambient)
        planet(x, "minutePlanet", 178, 36, "([MINUTE] + [SECOND] / 60) * 6", text,
            if (ambient) text else color(onAccent(w.textColor)), "%02d", "[MINUTE]", font, ray = layer == Layer.FULL, outline = ambient)
        if (w.showSeconds) {
            part(x, "satellite") {
                arc(x, 214f, 0f, 0f, color(withAlpha(w.accentColor, 0x66)), 4f, start = "[SECOND] * 6 - 30", end = "[SECOND] * 6")
                dot(x, 214f, "[SECOND] * 6", accent, 12f)
            }
        }
        if (w.showDate) label(x, 150, 252, 150, 32, 22f, color(withAlpha(w.textColor, 0xCC)), FONT_LABEL, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
    }

    private fun planet(
        x: Xml, name: String, r: Int, radius: Int, angle: String, fill: String, textColor: String,
        template: String, param: String, font: DigitFont, ray: Boolean, outline: Boolean,
    ) {
        group(x, name, angle) {
            if (ray) {
                part(x, "${name}Ray") {
                    x.open("""Line startX="$C" startY="${C - 12}" endX="$C" endY="${C - r + radius}"""")
                    x.line("""<Stroke color="${color(withAlpha(0xFFFFFFFF.toInt(), 0x40))}" thickness="2" cap="ROUND"/>""")
                    x.close("Line")
                }
            }
            // Шар с числом поворачивается обратно, чтобы цифры всегда стояли ровно.
            val d = radius * 2
            x.open("""Group x="${C - radius}" y="${C - r - radius}" width="$d" height="$d" pivotX="0.5" pivotY="0.5" name="${name}Body"""")
            x.line("""<Transform target="angle" value="-($angle)"/>""")
            x.open("""PartDraw x="0" y="0" width="$d" height="$d"""")
            x.open("""Ellipse x="2" y="2" width="${d - 4}" height="${d - 4}"""")
            if (outline) {
                x.line("""<Fill color="#FF000000"/>""")
                x.line("""<Stroke color="$fill" thickness="3"/>""")
            } else {
                x.line("""<Fill color="$fill"/>""")
            }
            x.close("Ellipse")
            x.close("PartDraw")
            val size = fit(font, "00", (d * 0.72).toInt(), (d * 0.42).toInt())
            numberBox(x, 0, 0, d, d, template, param, font, size, textColor)
            x.close("Group")
        }
    }

    /** «Матрица»: точечные цифры и 60 огоньков секунд — прошедшие горят цветом акцента. */
    private fun matrix(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val font = w.digits() ?: DigitFont.DOTS
        if (layer == Layer.FULL) {
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val r = if (i % 5 == 0) 5 else 3
                val cx = (C + 207 * sin(a)).toInt()
                val cy = (C - 207 * cos(a)).toInt()
                x.open("""PartDraw x="${cx - r}" y="${cy - r}" width="${2 * r}" height="${2 * r}" name="s$i"""")
                if (w.showSeconds) x.line("""<Transform target="alpha" value="[SECOND] &gt;= $i ? 255 : 60"/>""")
                x.open("""Ellipse x="0" y="0" width="${2 * r}" height="${2 * r}"""")
                x.line("""<Fill color="${if (w.showSeconds) accent else color(withAlpha(w.textColor, 0x66))}"/>""")
                x.close("Ellipse")
                x.close("PartDraw")
            }
        } else if (layer == Layer.AMBIENT) {
            // В Always-On — только 12 тусклых точек часов.
            part(x, "hourDots") {
                for (h in 0 until 12) {
                    val a = Math.toRadians(h * 30.0)
                    val cx = (C + 207 * sin(a)).toInt()
                    val cy = (C - 207 * cos(a)).toInt()
                    x.open("""Ellipse x="${cx - 3}" y="${cy - 3}" width="6" height="6"""")
                    x.line("""<Fill color="${color(withAlpha(w.accentColor, 0x99))}"/>""")
                    x.close("Ellipse")
                }
            }
        }
        digits(x, C, 212, 330, 112, "hh:mm", font, text)
        if (w.showDate) label(x, 95, 290, 260, 36, 24f, accent, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        if (w.complications) {
            complication(x, 0, 156, 334, 62, w, "WATCH_BATTERY", "RANGED_VALUE")
            complication(x, 1, 232, 334, 62, w, "STEP_COUNT", "SHORT_TEXT")
        }
    }

    /** «Заливка»: за минуту циферблат наполняется «жидкостью» цвета акцента с волной и пузырьками. */
    private fun liquid(x: Xml, w: WatchSettings, text: String, accent: String, layer: Layer) {
        val font = w.digits() ?: DigitFont.BUBBLE
        if (layer == Layer.FULL) {
            val fluid = color(withAlpha(w.accentColor, 0x8C))
            val level = "([SECOND] + 1) * 7.5"
            part(x, "liquid") {
                x.open("""Rectangle x="0" y="450" width="$SIZE" height="0"""")
                x.line("""<Fill color="$fluid"/>""")
                x.line("""<Transform target="y" value="450 - $level"/>""")
                x.line("""<Transform target="height" value="$level"/>""")
                x.close("Rectangle")
                // Волна: широкий эллипс над уровнем, качается из стороны в сторону.
                x.open("""Ellipse x="-60" y="430" width="570" height="40"""")
                x.line("""<Fill color="$fluid"/>""")
                x.line("""<Transform target="y" value="430 - $level"/>""")
                x.open("""Transform target="x" value="[SECOND] % 2 == 0 ? -90 : -30"""")
                x.line("""<Animation interpolation="EASE_IN_OUT" duration="1"/>""")
                x.close("Transform")
                x.close("Ellipse")
                val bubble = color(withAlpha(0xFFFFFFFF.toInt(), 0x66))
                for ((k, bx) in listOf(0 to 120, 1 to 210, 2 to 300, 3 to 165)) {
                    val r = 6 + k * 2
                    x.open("""Ellipse x="$bx" y="440" width="${2 * r}" height="${2 * r}"""")
                    x.line("""<Fill color="$bubble"/>""")
                    x.line("""<Transform target="y" value="clamp(440 - (([SECOND] * 7 + ${k * 17}) % 60) * 6, 456 - $level, 440)"/>""")
                    x.close("Ellipse")
                }
            }
            digits(x, C + 3, 213, 330, 110, "hh:mm", font, "#40000000")
        } else if (layer == Layer.AMBIENT) {
            // В Always-On от «жидкости» остаётся тонкая линия уровня.
            part(x, "level") {
                x.open("""Line startX="40" startY="330" endX="410" endY="330"""")
                x.line("""<Stroke color="${color(withAlpha(w.accentColor, 0x80))}" thickness="2" cap="ROUND"/>""")
                x.close("Line")
            }
        }
        digits(x, C, 210, 330, 110, "hh:mm", font, text)
        if (w.showDate) label(x, 105, 108, 240, 40, 26f, text, FONT_LABEL, "%s %d %s", "[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]")
        if (w.complications) complication(x, 0, 186, 296, 78, w, "WATCH_BATTERY", "RANGED_VALUE")
    }

    /** «Диагональ»: часы слева сверху, минуты справа снизу, секунда бежит по диагонали между ними. */
    private fun diagonal(x: Xml, w: WatchSettings, text: String, accent: String) {
        val font = w.digits() ?: DigitFont.BLOCK
        if (w.showSeconds) {
            part(x, "diagonal") {
                x.open("""Line startX="108" startY="342" endX="342" endY="108"""")
                x.line("""<Stroke color="${color(withAlpha(w.textColor, 0x33))}" thickness="2" cap="ROUND"/>""")
                x.close("Line")
                x.open("""Ellipse x="100" y="334" width="16" height="16"""")
                x.line("""<Fill color="$accent"/>""")
                x.line("""<Transform target="x" value="100 + [SECOND] * 234 / 59"/>""")
                x.line("""<Transform target="y" value="334 - [SECOND] * 234 / 59"/>""")
                x.close("Ellipse")
            }
        }
        digits(x, 165, 165, 210, 140, "hh", font, text)
        digits(x, 288, 292, 210, 140, "mm", font, accent, hourFormat = null)
        if (w.showDate) label(x, 290, 118, 130, 34, 22f, accent, FONT_LABEL, "%s %d", "[DAY_OF_WEEK_S]", "[DAY]")
        if (w.complications) complication(x, 0, 62, 262, 76, w, "WATCH_BATTERY", "RANGED_VALUE")
    }

    // ---------------------------------------------------------------- шрифт цифр

    /** Кегль, при котором текст из цифр помещается в maxW × maxH (по высоте цифр, не строки). */
    fun fit(font: DigitFont, sample: String, maxW: Int, maxH: Int): Float =
        minOf(maxH / font.digitHeight, maxW / font.width(sample))

    /** Где поставить центр строки, чтобы центр самих цифр оказался в cy (строка центрируется в рамке). */
    fun lineCenter(font: DigitFont, cy: Float, size: Float): Float =
        cy - ((font.ascent - font.descent) / 2 - (font.top + font.bottom) / 2) * size

    private fun family(font: DigitFont) = font.res ?: "SYNC_TO_DEVICE"

    private fun fontWeight(font: DigitFont) = if (font.res == null) "THIN" else "NORMAL"

    /**
     * Время (format: «hh:mm», «hh» или «mm») шрифтом font, вписанное в рамку maxW × maxH
     * с центром цифр в (cx, cy). hourFormat — как у TimeText («SYNC_TO_DEVICE», «24» …), null — без него.
     */
    private fun digits(
        x: Xml, cx: Int, cy: Int, maxW: Int, maxH: Int, format: String, font: DigitFont, color: String,
        hourFormat: String? = "SYNC_TO_DEVICE",
    ) {
        val size = fit(font, format.replace(Regex("[hm]"), "0"), maxW, maxH)
        val h = (font.digitHeight * size * 1.3f).toInt() + 8
        val w = maxW + 40
        val top = (lineCenter(font, cy.toFloat(), size) - h / 2f).toInt()
        x.open("""DigitalClock x="${cx - w / 2}" y="$top" width="$w" height="$h"""")
        val hf = hourFormat?.let { """ hourFormat="$it"""" } ?: ""
        x.open("""TimeText x="0" y="0" width="$w" height="$h" format="$format"$hf align="CENTER"""")
        x.line("""<Font family="${family(font)}" size="${f(size)}" weight="${fontWeight(font)}" color="$color"/>""")
        x.close("TimeText")
        x.close("DigitalClock")
    }

    /** Число из выражения (например, следующий час) шрифтом цифр, центр цифр — в (cx, cy). */
    private fun number(x: Xml, cx: Int, cy: Int, maxW: Int, maxH: Int, template: String, expr: String, font: DigitFont, color: String) {
        val size = fit(font, "00", maxW, maxH)
        val h = (font.digitHeight * size * 1.3f).toInt() + 8
        val w = maxW + 30
        numberBox(x, cx - w / 2, cy - h / 2, w, h, template, expr, font, size, color)
    }

    private fun numberBox(x: Xml, px: Int, py: Int, w: Int, h: Int, template: String, expr: String, font: DigitFont, size: Float, color: String) {
        // Центр цифр в центре рамки: сдвигаем рамку на разницу между центром строки и цифр.
        val shift = (lineCenter(font, 0f, size)).toInt()
        x.open("""PartText x="$px" y="${py + shift}" width="$w" height="$h"""")
        x.open("""Text align="CENTER"""")
        x.line("""<Font family="${family(font)}" size="${f(size)}" weight="${fontWeight(font)}" color="$color"><Template><![CDATA[$template]]><Parameter expression="$expr"/></Template></Font>""")
        x.close("Text")
        x.close("PartText")
    }

    /** Группа во весь экран, которая вращается вокруг центра на угол angle (выражение). */
    private inline fun group(x: Xml, name: String, angle: String, body: () -> Unit) {
        x.open("""Group x="0" y="0" width="$SIZE" height="$SIZE" pivotX="0.5" pivotY="0.5" name="$name"""")
        x.line("""<Transform target="angle" value="$angle"/>""")
        body()
        x.close("Group")
    }

    // ---------------------------------------------------------------- общие элементы

    private fun timeText(
        x: Xml, px: Int, py: Int, w: Int, h: Int, format: String, family: String, size: Float, color: String,
        weight: String = "NORMAL", hourFormat: Boolean = false,
    ) {
        x.open("""DigitalClock x="$px" y="$py" width="$w" height="$h"""")
        val hf = if (hourFormat) """ hourFormat="SYNC_TO_DEVICE"""" else ""
        x.open("""TimeText x="0" y="0" width="$w" height="$h" format="$format"$hf align="CENTER"""")
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

    /** Слой рисования во весь экран. */
    private inline fun part(x: Xml, name: String, body: () -> Unit) {
        x.open("""PartDraw x="0" y="0" width="$SIZE" height="$SIZE" name="$name"""")
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
    private fun hands(x: Xml, dx: Int, tintHour: String, tintMinute: String, tintSecond: String, seconds: Boolean) {
        fun hand(tag: String, res: String, w: Int, h: Int, top: Int, tint: String) {
            val pivotY = (C - top).toFloat() / h
            x.open("""$tag resource="$res" x="${C - w / 2 + dx}" y="${top + dx}" width="$w" height="$h" pivotX="0.5" pivotY="${f(pivotY, 4)}" tintColor="$tint"""")
            x.close(tag)
        }
        hand("HourHand", HAND_HOUR, 18, 150, 95, tintHour)
        hand("MinuteHand", HAND_MINUTE, 12, 210, 35, tintMinute)
        if (seconds) hand("SecondHand", HAND_SECOND, 6, 240, 25, tintSecond)
    }

    /** Тонкое кольцо по краю, которое заполняется за минуту. */
    private fun secondsRing(x: Xml, accentColor: Int) {
        x.open("""PartDraw x="0" y="0" width="$SIZE" height="$SIZE" name="secondsRing"""")
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
        x.deferredIfNeeded {
        x.open(
            """ComplicationSlot x="$px" y="$py" width="$s" height="$s" slotId="$id" """ +
                """supportedTypes="RANGED_VALUE SHORT_TEXT MONOCHROMATIC_IMAGE SMALL_IMAGE EMPTY" isCustomizable="TRUE""""
        )
        x.line("""<DefaultProviderPolicy defaultSystemProvider="$provider" defaultSystemProviderType="$providerType"/>""")
        x.line("""<BoundingOval x="0" y="0" width="$s" height="$s"/>""")

        // В Always-On мини-виджет гаснет целиком: остаются только время и дата.
        fun hidden(body: () -> Unit) {
            x.open("""Group x="0" y="0" width="$s" height="$s" name="c$id"""")
            x.line("""<Variant mode="AMBIENT" target="alpha" value="0"/>""")
            body()
            x.close("Group")
        }

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
        hidden {
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
        }
        x.close("Complication")

        x.open("""Complication type="SHORT_TEXT"""")
        hidden {
            discPart()
            iconAndText(s * 0.23f)
        }
        x.close("Complication")

        x.open("""Complication type="MONOCHROMATIC_IMAGE"""")
        hidden {
            discPart()
            val big = (s * 0.5f).toInt()
            x.open("""PartImage x="${(s - big) / 2}" y="${(s - big) / 2}" width="$big" height="$big" tintColor="$accent"""")
            x.line("""<Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/>""")
            x.close("PartImage")
        }
        x.close("Complication")

        x.open("""Complication type="SMALL_IMAGE"""")
        hidden {
            discPart()
            val img = (s * 0.62f).toInt()
            x.open("""PartImage x="${(s - img) / 2}" y="${(s - img) / 2}" width="$img" height="$img"""")
            x.line("""<Image resource="[COMPLICATION.SMALL_IMAGE]"/>""")
            x.close("PartImage")
        }
        x.close("Complication")

        x.line("""<Complication type="EMPTY"/>""")
        x.close("ComplicationSlot")
        }
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

    private fun luminance(argb: Int): Double {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun isLight(argb: Int): Boolean = luminance(argb) > 0.5

    fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)

    private fun f(v: Double, digits: Int = 1) = String.format(Locale.ROOT, "%.${digits}f", v)
    private fun f(v: Float, digits: Int = 1) = f(v.toDouble(), digits)

    /** Минимальный построитель XML с отступами. */
    private class Xml {
        private var sb = StringBuilder()
        private var depth = 0
        private val deferred = StringBuilder()

        /** Мини-виджеты копятся отдельно и выводятся в [flushDeferred] (поверх слоя глубины). */
        var deferComplications = false

        /** Глубина вложенности, на которой окажутся мини-виджеты (уровень Scene). */
        private var deferredDepth = 0

        inline fun deferredIfNeeded(body: () -> Unit) {
            if (!deferComplications) return body()
            val main = sb
            val mainDepth = depth
            sb = deferred
            depth = deferredDepth
            try {
                body()
            } finally {
                sb = main
                depth = mainDepth
            }
        }

        fun flushDeferred() {
            sb.append(deferred)
            deferred.setLength(0)
        }

        fun line(s: String) {
            repeat(depth) { sb.append("  ") }
            sb.append(s).append('\n')
        }

        fun open(tagWithAttrs: String) {
            line("<$tagWithAttrs>")
            depth++
            if (tagWithAttrs.startsWith("Scene")) deferredDepth = depth
        }

        fun close(tag: String) {
            depth--
            line("</$tag>")
        }

        override fun toString() = sb.toString()
    }
}
