package app.synctheme.mobile.watch

import android.content.Context
import android.graphics.Bitmap
import app.synctheme.core.SyncProtocol
import app.synctheme.core.Theme
import app.synctheme.core.WatchFaceApk
import app.synctheme.core.WatchFaceXml
import app.synctheme.mobile.render.ThemeRenderer
import com.google.android.wearable.watchface.validator.client.DwfValidatorFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Собирает APK циферблата по теме прямо на телефоне и получает токен
 * официальным валидатором Watch Face Push (без него часы не примут циферблат).
 */
class WatchFaceBuilder(private val context: Context) {
    /** Готовый циферблат; hasDepth — в него вошёл слой глубины. */
    class Result(val apk: ByteArray, val token: String, val hasDepth: Boolean)

    class ValidationException(message: String) : Exception(message)

    suspend fun build(theme: Theme): Result = withContext(Dispatchers.Default) {
        val settings = theme.watchSettings()
        val bg = ThemeRenderer.watchBackground(context, theme)
        val bgJpeg = bg.encode(Bitmap.CompressFormat.JPEG, 90)
        val fg = if (settings.depth) ThemeRenderer.watchForeground(context, theme) else null
        val fgPng = fg?.encode(Bitmap.CompressFormat.PNG, 100)
        // Время передвинуто вручную или встаёт напротив объекта фото — в превью и в циферблате одинаково.
        val shift = ThemeRenderer.watchTimeShift(context, theme)
        val preview = ThemeRenderer.watchPreview(context, settings, bg, PREVIEW_SIZE, fg, shift).encode(Bitmap.CompressFormat.PNG, 100)
        bg.recycle()
        fg?.recycle()

        val assets = context.assets
        val template = assets.open("watchface_template.apk").use { it.readBytes() }
        val signer = WatchFaceApk.Signer.fromDer(
            assets.open("watchface_key.pk8").use { it.readBytes() },
            assets.open("watchface_cert.der").use { it.readBytes() },
        )
        val workDir = File(context.cacheDir, "watchface").apply { mkdirs() }
        val xml = WatchFaceXml.generate(settings, foreground = fgPng != null, shift = shift)
        val apk = WatchFaceApk.build(template, xml, bgJpeg, preview, signer, workDir, foreground = fgPng)

        val apkFile = File(workDir, "watchface.apk").apply { writeBytes(apk) }
        val result = DwfValidatorFactory.create().validate(apkFile, SyncProtocol.APP_PACKAGE)
        if (result.failures().isNotEmpty()) {
            throw ValidationException(result.failures().joinToString("\n") { "${it.name()}: ${it.failureMessage()}" })
        }
        Result(apk, result.validationToken(), fgPng != null)
    }

    private fun Bitmap.encode(format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(format, quality, it) }.toByteArray()

    companion object {
        private const val PREVIEW_SIZE = 320
    }
}
