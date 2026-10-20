package app.synctheme.buildtools

import app.synctheme.core.ClockStyle
import app.synctheme.core.Presets
import app.synctheme.core.SyncProtocol
import app.synctheme.core.Theme
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
        val preview = ByteArrayOutputStream().also { ImageIO.write(DefaultAssets.preview(theme, bg), "png", it) }
        val bytes = WatchFaceApk.build(
            template, WatchFaceXml.generate(theme), bgFile.readBytes(), preview.toByteArray(),
            signer, File(outDir, "tmp"),
        )
        return File(outDir, "${theme.id}.apk").apply { writeBytes(bytes) }
    }

    private fun assertValid(name: String, apk: File) {
        val result = validator.validate(apk, SyncProtocol.APP_PACKAGE)
        val msg = result.failures().joinToString("\n") { "${it.name()}: ${it.failureMessage()}" }
        assertTrue("$name invalid:\n$msg", result.failures().isEmpty())
    }
}
