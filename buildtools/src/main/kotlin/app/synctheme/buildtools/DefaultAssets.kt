package app.synctheme.buildtools

import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockStyle
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import app.synctheme.core.WatchFaceXml
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.cos
import kotlin.math.sin

/**
 * Генерирует ресурсы шаблона циферблата (тема по умолчанию):
 * watchface.xml, фон, превью и белые стрелки.
 *
 * Аргументы: <каталог res>
 */
fun main(args: Array<String>) {
    val res = File(args.single())
    val theme = Presets.default
    File(res, "raw").apply { mkdirs() }.resolve("watchface.xml").writeText(WatchFaceXml.generate(theme))
    val drawable = File(res, "drawable-nodpi").apply { mkdirs() }
    val bg = DefaultAssets.background(theme, WatchFaceXml.SIZE)
    DefaultAssets.writeJpeg(bg, drawable.resolve("wf_bg.jpg"))
    ImageIO.write(DefaultAssets.preview(theme, bg), "png", drawable.resolve("preview.png"))
    // Заглушка слоя глубины: телефон подменяет её вырезанным с фото объектом.
    ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", drawable.resolve("${WatchFaceXml.FG_RESOURCE}.png"))
    ImageIO.write(DefaultAssets.hand(18, 150), "png", drawable.resolve("${WatchFaceXml.HAND_HOUR}.png"))
    ImageIO.write(DefaultAssets.hand(12, 210), "png", drawable.resolve("${WatchFaceXml.HAND_MINUTE}.png"))
    ImageIO.write(DefaultAssets.hand(6, 240), "png", drawable.resolve("${WatchFaceXml.HAND_SECOND}.png"))
    ImageIO.write(DefaultAssets.hand(28, 140), "png", drawable.resolve("${WatchFaceXml.HAND_HOUR_WIDE}.png"))
    ImageIO.write(DefaultAssets.hand(20, 196), "png", drawable.resolve("${WatchFaceXml.HAND_MINUTE_WIDE}.png"))
}

object DefaultAssets {
    fun background(theme: Theme, size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.smooth()
        paintBackground(g, theme.watchBackground(), size)
        val dim = theme.watchSettings().dim
        if (dim > 0f) {
            g.color = Color(0f, 0f, 0f, dim.coerceIn(0f, 0.8f))
            g.fillRect(0, 0, size, size)
        }
        g.dispose()
        return img
    }

    private fun paintBackground(g: Graphics2D, bg: Background, size: Int) {
        val colors = bg.colors.map { Color(it, true) }.ifEmpty { listOf(Color.BLACK) }
        val s = size.toFloat()
        when (bg.type) {
            BackgroundType.SOLID, BackgroundType.PHOTO -> {
                g.color = colors.first(); g.fillRect(0, 0, size, size)
            }
            BackgroundType.LINEAR -> {
                val a = Math.toRadians(bg.angle.toDouble())
                val dx = (sin(a) * s / 2).toFloat()
                val dy = (-cos(a) * s / 2).toFloat()
                g.paint = if (colors.size == 1) GradientPaint(0f, 0f, colors[0], s, s, colors[0])
                else java.awt.LinearGradientPaint(
                    Point2D.Float(s / 2 - dx, s / 2 - dy), Point2D.Float(s / 2 + dx, s / 2 + dy),
                    fractions(colors.size), colors.toTypedArray(),
                )
                g.fillRect(0, 0, size, size)
            }
            BackgroundType.RADIAL -> {
                g.paint = RadialGradientPaint(
                    Point2D.Float(s / 2, s * 0.35f), s * 0.8f, fractions(colors.size), colors.toTypedArray(),
                    MultipleGradientPaint.CycleMethod.NO_CYCLE,
                )
                g.fillRect(0, 0, size, size)
            }
            BackgroundType.AURORA -> {
                g.color = colors.first(); g.fillRect(0, 0, size, size)
                AURORA_SPOTS.forEachIndexed { i, (cx, cy, r) ->
                    val c = colors[1 + i % (colors.size - 1).coerceAtLeast(1)].takeIf { colors.size > 1 } ?: colors[0]
                    g.paint = RadialGradientPaint(
                        Point2D.Float(cx * s, cy * s), r * s, floatArrayOf(0f, 1f),
                        arrayOf(Color(c.red, c.green, c.blue, 200), Color(c.red, c.green, c.blue, 0)),
                    )
                    g.fillRect(0, 0, size, size)
                }
            }
        }
    }

    /** Те же «пятна», что рисует телефон (см. ThemeRenderer в :mobile). */
    private val AURORA_SPOTS = listOf(
        Triple(0.15f, 0.2f, 0.75f),
        Triple(0.9f, 0.45f, 0.7f),
        Triple(0.35f, 0.95f, 0.8f),
    )

    private fun fractions(n: Int) = FloatArray(n) { if (n == 1) 0f else it / (n - 1f) }

    fun preview(theme: Theme, bg: BufferedImage): BufferedImage {
        val size = bg.width
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.smooth()
        g.clip = Ellipse2D.Float(0f, 0f, size.toFloat(), size.toFloat())
        g.drawImage(bg, 0, 0, null)
        val w = theme.watchSettings()
        g.color = Color(w.textColor, true)
        if (w.clockStyle == ClockStyle.ANALOG || w.clockStyle == ClockStyle.BLOB) {
            g.stroke = BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(225, 225, 225 - 70, 225 - 80)
            g.stroke = BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(225, 225, 225 + 150, 225 - 70)
        } else {
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 120)
            val text = "10:08"
            val w = g.fontMetrics.stringWidth(text)
            g.drawString(text, (size - w) / 2, 265)
        }
        g.dispose()
        return img
    }

    fun hand(w: Int, h: Int): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.smooth()
        g.composite = AlphaComposite.Src
        g.color = Color.WHITE
        g.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), w.toFloat(), w.toFloat()))
        g.dispose()
        return img
    }

    fun writeJpeg(img: BufferedImage, file: File, quality: Float = 0.92f) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        file.outputStream().use { os ->
            ImageIO.createImageOutputStream(os).use { ios ->
                writer.output = ios
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                }
                writer.write(null, IIOImage(img, null, null), param)
            }
        }
        writer.dispose()
    }

    private fun Graphics2D.smooth() {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }
}
