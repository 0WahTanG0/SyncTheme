package app.synctheme.mobile.watch

import android.content.Context
import android.graphics.Bitmap
import app.synctheme.core.SyncProtocol
import app.synctheme.core.Theme
import app.synctheme.core.WatchFaceApk
import app.synctheme.core.WatchFaceXml
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.SubjectMask
import app.synctheme.mobile.render.ThemeRenderer
import com.google.android.wearable.watchface.validator.client.DwfValidatorFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Собирает APK циферблата по теме прямо на телефоне и получает токен
 * официальным валидатором Watch Face Push (без него часы не примут циферблат).
 *
 * Готовый циферблат запоминается: пока тему настраивают, он собирается заранее ([prebuild]),
 * и по «Применить» сразу уходит на часы.
 */
class WatchFaceBuilder(private val context: Context) {
    /** Готовый циферблат; hasDepth — в него вошёл слой глубины. */
    class Result(val apk: ByteArray, val token: String, val hasDepth: Boolean)

    class ValidationException(message: String) : Exception(message)

    /** Циферблат для темы: готовый из памяти или собранный сейчас. */
    suspend fun build(theme: Theme): Result = withContext(Dispatchers.Default) {
        val key = key(theme)
        // Две сборки одной темы одновременно не нужны: вторая дождётся первой и возьмёт её результат.
        // Блокировка берётся и отпускается в фоновом потоке — она не ждёт главный поток.
        cached?.takeIf { it.first == key }?.second ?: lock.withLock {
            cached?.takeIf { it.first == key }?.second ?: assemble(theme).also { cached = key to it }
        }
    }

    /** Собрать заранее (в фоне, пока тему настраивают); ошибки здесь не важны — повторятся по «Применить». */
    suspend fun prebuild(theme: Theme) {
        runCatching { build(theme) }
    }

    private suspend fun assemble(theme: Theme): Result = withContext(Dispatchers.Default) {
        val settings = theme.watchSettings()
        val bg = ThemeRenderer.watchBackground(context, theme)
        val bgJpeg = bg.encode(Bitmap.CompressFormat.JPEG, 88)
        val fg = if (settings.depth) ThemeRenderer.watchForeground(context, theme) else null
        val fgPng = fg?.encode(Bitmap.CompressFormat.PNG, 100)
        // Время передвинуто вручную или встаёт напротив объекта фото — в превью и в циферблате одинаково.
        val shift = ThemeRenderer.watchTimeShift(context, theme)
        val preview = ThemeRenderer.watchPreview(context, settings, bg, PREVIEW_SIZE, fg, shift).opaque().encode(Bitmap.CompressFormat.JPEG, 85)
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

    /**
     * Что влияет на циферблат: настройки часов, их фон и само фото (и найден ли на нём объект для глубины).
     * Тема телефона без часов (виджеты, блокировка) готовый циферблат не сбрасывает.
     */
    private fun key(theme: Theme): String {
        val bg = theme.watchBackground()
        val photo = bg.photoFile?.let { ThemeRepository.get(context).photoFile(it) }
        return listOf(
            theme.watchSettings().toString(), bg.toString(),
            photo?.let { "${it.length()}:${it.lastModified()}" }, bg.photoFile?.let { SubjectMask.exists(context, it) },
        ).joinToString("|")
    }

    private fun Bitmap.encode(format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(format, quality, it) }.toByteArray()

    /** JPEG без прозрачности: углы круглого превью — чёрные, как экран часов. */
    private fun Bitmap.opaque(): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply {
            drawColor(android.graphics.Color.BLACK)
            drawBitmap(this@opaque, 0f, 0f, null)
        }
        recycle()
        return out
    }

    companion object {
        private const val PREVIEW_SIZE = 320
        private val lock = Mutex()
        @Volatile private var cached: Pair<String, Result>? = null
    }
}
