package app.synctheme.buildtools

import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockStyle
import app.synctheme.core.DialStyle
import app.synctheme.core.Presets
import app.synctheme.core.SyncProtocol
import app.synctheme.core.Theme
import app.synctheme.core.TimeShift
import app.synctheme.core.WatchFaceApk
import app.synctheme.core.WatchFaceXml
import com.google.android.wearable.watchface.validator.client.DwfValidatorFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверяет, что всё, что генерирует телефон, принимается валидатором Watch Face Push. */
class WatchFaceValidationTest {
    private val template = File(System.getProperty("templateApk")).readBytes()
    private val keystore = File(System.getProperty("keystoreDir"))
    private val outDir = File(System.getProperty("outDir")).apply { mkdirs() }
    private val signer = WatchFaceApk.Signer.fromDer(
        keystore.resolve("watchface_key.pk8").readBytes(),
        keystore.resolve("watchface_cert.der").readBytes(),
    )
    private val validator = DwfValidatorFactory.create()

    @Test
    fun templateIsValid() {
        val apk = Files.createTempFile("template", ".apk").toFile().apply { writeBytes(template) }
        assertValid("template", apk)
    }

    @Test
    fun allPresetsAndStylesAreValid() {
        val themes = buildList {
            addAll(Presets.all)
            for (style in ClockStyle.entries) {
                add(Presets.default.copy(id = "min-$style", clockStyle = style, showSeconds = false, showDate = false, watchComplications = false))
                add(Presets.default.copy(id = "max-$style", clockStyle = style, showSeconds = true, showDate = true, watchComplications = true))
            }
            // Слой глубины — во всех стилях; светлая тема — для слоя Always-On со светлыми цветами.
            for (style in ClockStyle.entries) {
                add(Presets.default.copy(id = "depth-$style", clockStyle = style, watchDepth = true))
                add(Presets.all.first { it.id == "sakura" }.copy(id = "light-$style", clockStyle = style, watchDepth = true))
            }
            // Время, передвинутое по циферблату (вручную или напротив объекта фото).
            for (style in ClockStyle.entries) {
                add(Presets.default.copy(id = "shift-$style", clockStyle = style, watchDepth = true, watchTimeShift = TimeShift(-60f, 42f, 0.8f)))
            }
            for (dial in DialStyle.entries) {
                add(Presets.default.copy(id = "dial-$dial", clockStyle = ClockStyle.ANALOG, dialStyle = dial))
                add(Presets.default.copy(id = "dial-min-$dial", clockStyle = ClockStyle.ANALOG, dialStyle = dial, showDate = false, watchComplications = false, showSeconds = false))
            }
            // Отдельные настройки часов: свой фон, цвета и стиль.
            add(
                Presets.all[1].copy(
                    id = "watch-override",
                    watch = Presets.all[1].followPhone().copy(
                        background = Background(BackgroundType.SOLID, listOf(0xFF101010.toInt())),
                        clockStyle = ClockStyle.ANALOG, dialStyle = DialStyle.NUMBERS, accentColor = 0xFF00E5FF.toInt(),
                    ),
                )
            )
        }
        val failures = themes.mapNotNull { theme ->
            val apk = build(theme)
            val result = validator.validate(apk, SyncProtocol.APP_PACKAGE)
            if (result.failures().isEmpty() && result.validationToken().isNotEmpty()) {
                println("OK  ${theme.id} (${theme.clockStyle}) ${apk.length() / 1024} KB")
                null
            } else {
                "${theme.id}: " + result.failures().joinToString { "${it.name()}: ${it.failureMessage()}" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun build(theme: Theme): File {
        val bg = DefaultAssets.background(theme, WatchFaceXml.SIZE)
        val bgFile = File.createTempFile("wf_bg", ".jpg").also { DefaultAssets.writeJpeg(bg, it) }
        val previewFile = File.createTempFile("wf_preview", ".jpg").also { DefaultAssets.writeJpeg(DefaultAssets.opaque(DefaultAssets.preview(theme, bg)), it, 0.85f) }
        val preview = ByteArrayOutputStream().also { it.write(previewFile.readBytes()) }
        val depth = theme.watchSettings().depth
        val bytes = WatchFaceApk.build(
            template, WatchFaceXml.generate(theme), bgFile.readBytes(), preview.toByteArray(),
            signer, File(outDir, "tmp"), foreground = if (depth) foreground() else null,
        )
        return File(outDir, "${theme.id}.apk").apply { writeBytes(bytes) }
    }

    /** Слой глубины для проверки: полупрозрачный круг на прозрачном фоне. */
    private fun foreground(): ByteArray {
        val img = java.awt.image.BufferedImage(WatchFaceXml.SIZE, WatchFaceXml.SIZE, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(255, 200, 120, 220)
        g.fillOval(120, 160, 220, 290)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private fun assertValid(name: String, apk: File) {
        val result = validator.validate(apk, SyncProtocol.APP_PACKAGE)
        val msg = result.failures().joinToString("\n") { "${it.name()}: ${it.failureMessage()}" }
        assertTrue("$name invalid:\n$msg", result.failures().isEmpty())
    }
}
