package app.synctheme.mobile.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme
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

    /** Растёт, когда маску выделили заново (в том числе вручную): обои и превью перерисовываются. */
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Маска, выделенная вручную в редакторе: сохраняется рядом с фото вместо найденной автоматически. */
    suspend fun save(context: Context, photo: String, mask: Bitmap) {
        withContext(Dispatchers.IO) {
            file(context, photo).outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        synchronized(this) { cache[photo] = mask.copy(Bitmap.Config.ARGB_8888, false) }
        version.value++
    }

    /** Фото, уменьшенное так же, как для распознавания: по нему и строится маска (в том числе вручную). */
    suspend fun preparedPhoto(context: Context, photo: String): Bitmap = withContext(Dispatchers.Default) {
        val source = ThemeRepository.get(context).photoFile(photo)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, opts)
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw DepthException("Не удалось открыть фото")
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                .also { decoded.recycle() }
        } else decoded
    }

    /**
     * Умная обводка: пользователь примерно обвёл объект пальцем ([lasso] — точки в координатах [photo]).
     * Распознаватель ищет объект только в обведённом месте (крупнее — значит точнее), а результат
     * ограничивается обводкой с запасом: край объекта может чуть выходить за линию пальца.
     * Если распознаватель там ничего не увидел — объектом считается сама обводка с мягким краем.
     */
    suspend fun fromLasso(context: Context, photo: Bitmap, lasso: List<android.graphics.PointF>): Bitmap = withContext(Dispatchers.Default) {
        val w = photo.width
        val h = photo.height
        // Линия пальца неровная — сглаживаем её: углы между точками скругляются кривыми через середины отрезков.
        val path = android.graphics.Path().apply {
            val n = lasso.size
            fun mid(i: Int) = android.graphics.PointF((lasso[i % n].x + lasso[(i + 1) % n].x) / 2, (lasso[i % n].y + lasso[(i + 1) % n].y) / 2)
            mid(0).let { moveTo(it.x, it.y) }
            for (i in 1..n) mid(i).let { m -> quadTo(lasso[i % n].x, lasso[i % n].y, m.x, m.y) }
            close()
        }
        val bounds = android.graphics.RectF().also { path.computeBounds(it, true) }
        val margin = max(bounds.width(), bounds.height()) * 0.12f + 8f
        val crop = android.graphics.Rect(
            (bounds.left - margin).toInt().coerceAtLeast(0), (bounds.top - margin).toInt().coerceAtLeast(0),
            (bounds.right + margin).toInt().coerceAtMost(w), (bounds.bottom + margin).toInt().coerceAtMost(h),
        )
        // Обводка с запасом: залитый контур, расширенный толстой линией по краю.
        val region = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        android.graphics.Canvas(region).apply {
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            drawPath(path, fill)
            fill.style = android.graphics.Paint.Style.STROKE
            fill.strokeJoin = android.graphics.Paint.Join.ROUND
            fill.strokeWidth = margin * 0.6f
            drawPath(path, fill)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val found = if (crop.width() > 8 && crop.height() > 8) {
            val part = Bitmap.createBitmap(photo, crop.left, crop.top, crop.width(), crop.height())
            runCatching { segmenter(context, part) }.getOrNull().also { part.recycle() }
        } else null
        val inside = found?.let { coverage(it) } ?: 0f
        if (found != null && inside > 0.02f) {
            canvas.drawBitmap(found, null, android.graphics.RectF(crop), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            // Только то, что внутри обводки (с запасом): соседние объекты не цепляются.
            canvas.drawBitmap(region, 0f, 0f, android.graphics.Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            })
        }
        // Распознаватель взял почти всю обводку целиком (фото из одинаковых предметов: ягоды, листья, камни) —
        // он не отделил объект от соседних, и выделение стало бы прямоугольником куска фото. Тогда — по линии пальца.
        val isolated = found != null && inside > 0.02f && coverage(result, crop) < 0.9f * coverage(region, crop)
        if (!isolated) {
            result.eraseColor(android.graphics.Color.TRANSPARENT)
            // Объект — по сглаженной линии пальца, с мягким краем.
            canvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                maskFilter = android.graphics.BlurMaskFilter(max(2f, margin * 0.15f), android.graphics.BlurMaskFilter.Blur.NORMAL)
            })
        }
        found?.recycle()
        region.recycle()
        result
    }

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
        if (coverage(mask) < 0.01f) throw DepthException("Объект на фото не нашёлся сам — обведите его пальцем: «Выбрать объект»")
        withContext(Dispatchers.IO) {
            file(context, photo).outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        synchronized(this) { cache[photo] = mask }
        version.value++
    }

    /** Доля «объекта» на маске (по альфе). */
    /** Доля непрозрачного в маске (или в её части [area]). */
    private fun coverage(mask: Bitmap, area: android.graphics.Rect = android.graphics.Rect(0, 0, mask.width, mask.height)): Float {
        val step = 4
        var sum = 0L
        var n = 0
        for (y in area.top until area.bottom step step) for (x in area.left until area.right step step) {
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

/** Маски для всех фото темы, где включён эффект глубины. */
object DepthMasks {
    private fun photo(bg: Background) = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO }

    /** Фото, для которых нужна маска: часы на обоях, экран блокировки, циферблат. */
    fun photosFor(theme: Theme): List<String> = buildList {
        if (theme.wallClock?.depth == true) photo(theme.background)?.let(::add)
        val wall = theme.wallClock
        if (wall?.depth == true && wall.onLock) photo(theme.lockScreenBackground())?.let(::add)
        if (theme.watchSettings().depth) photo(theme.watchBackground())?.let(::add)
    }.distinct()

    /**
     * Считает недостающие маски. Ошибка распознавания не мешает поставить тему — просто без слоя
     * глубины; её текст возвращается, чтобы сказать о ней пользователю.
     */
    suspend fun ensureFor(context: Context, theme: Theme): List<String> =
        photosFor(theme).mapNotNull { name -> runCatching { SubjectMask.ensure(context, name) }.exceptionOrNull()?.message }
}
