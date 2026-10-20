package app.synctheme.mobile.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.synctheme.mobile.data.ThemeRepository
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Главный объект на фото не найден или распознавание недоступно. */
class DepthException(message: String) : Exception(message)

/**
 * Маска главного объекта фото для эффекта глубины: белый цвет с прозрачностью по уверенности
 * распознавания. Считается один раз (ML Kit, на телефоне, без интернета после загрузки модели)
 * и лежит рядом с фото: photo_x.jpg → photo_x.mask.png.
 */
object SubjectMask {
    /** Сторона, до которой уменьшается фото перед распознаванием. */
    private const val MAX_SIDE = 1280

    /**
     * Распознаватель: фото → маска того же размера (альфа — объект). В тестах подменяется,
     * на телефоне — ML Kit Subject Segmentation.
     */
    @Volatile
    var segmenter: suspend (Context, Bitmap) -> Bitmap = ::mlKit

    private val cache = LinkedHashMap<String, Bitmap>()

    fun file(context: Context, photo: String): File =
        ThemeRepository.get(context).photoFile(photo.substringBeforeLast('.') + ".mask.png")

    fun exists(context: Context, photo: String) = file(context, photo).exists()

    @Synchronized
    fun load(context: Context, photo: String): Bitmap? {
        cache[photo]?.let { return it }
        val f = file(context, photo)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        cache[photo] = bmp
        while (cache.size > 3) cache.remove(cache.keys.first())
        return bmp
    }

    /** Считает маску, если её ещё нет. */
    suspend fun ensure(context: Context, photo: String) {
        if (exists(context, photo)) return
        val source = ThemeRepository.get(context).photoFile(photo)
        val mask = withContext(Dispatchers.Default) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, opts)
            var sample = 1
            while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw DepthException("Не удалось открыть фото")
            val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                    .also { decoded.recycle() }
            } else decoded
            segmenter(context, small).also { small.recycle() }
        }
        if (coverage(mask) < 0.01f) throw DepthException("На фото не нашлось главного объекта — попробуйте снимок, где он крупнее")
        withContext(Dispatchers.IO) {
            file(context, photo).outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        synchronized(this) { cache[photo] = mask }
    }

    /** Доля «объекта» на маске (по альфе). */
    private fun coverage(mask: Bitmap): Float {
        val step = 4
        var sum = 0L
        var n = 0
        for (y in 0 until mask.height step step) for (x in 0 until mask.width step step) {
            sum += mask.getPixel(x, y) ushr 24
            n++
        }
        return if (n == 0) 0f else sum / (255f * n)
    }

    private suspend fun mlKit(context: Context, bitmap: Bitmap): Bitmap {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
        )
        try {
            ensureModel(context, segmenter)
            val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
            val confidence = result.foregroundConfidenceMask ?: throw DepthException("Объект на фото не найден")
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            confidence.rewind()
            for (i in pixels.indices) {
                // Мягкий край: уверенность 0.3…0.7 превращается в плавный переход прозрачности.
                val a = ((confidence.get() - 0.3f) / 0.4f).coerceIn(0f, 1f)
                pixels[i] = ((a * 255).toInt() shl 24) or 0xFFFFFF
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } finally {
            segmenter.close()
        }
    }

    /** Модель распознавания приходит через сервисы Google Play — при первом запуске её нужно скачать. */
    private suspend fun ensureModel(context: Context, api: com.google.android.gms.common.api.OptionalModuleApi) {
        val client = ModuleInstall.getClient(context)
        val available = runCatching { client.areModulesAvailable(api).await().areModulesAvailable() }.getOrDefault(false)
        if (available) return
        val done = CompletableDeferred<Int>()
        val listener = InstallStatusListener { update ->
            val state = update.installState
            if (state == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED ||
                state == ModuleInstallStatusUpdate.InstallState.STATE_FAILED ||
                state == ModuleInstallStatusUpdate.InstallState.STATE_CANCELED
            ) done.complete(state)
        }
        val request = ModuleInstallRequest.newBuilder().addApi(api).setListener(listener).build()
        val response = runCatching { client.installModules(request).await() }
            .getOrElse { throw DepthException("Распознавание недоступно: нужны сервисы Google Play (${it.message})") }
        if (response.areModulesAlreadyInstalled()) return
        val state = withTimeoutOrNull(180_000) { done.await() }
        client.unregisterListener(listener)
        if (state != ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED) {
            throw DepthException("Не удалось скачать модель распознавания — проверьте интернет и повторите")
        }
    }
}
