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

    /**
     * Отдельные объекты на фото: по маске на каждый (того же размера, что фото). По ним обводка берёт
     * именно обведённый предмет, а не всё, что на переднем плане. В тестах подменяется.
     */
    @Volatile
    var subjects: suspend (Context, Bitmap) -> List<Bitmap> = ::mlKitSubjects

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
     * Граница объекта ищется на самом фото ([snap]): от середины обводки выделение растёт до ближайших
     * резких краёв, а фон растёт снаружи обводки — они встречаются ровно на краю объекта. Так находятся
     * и предметы, которые распознаватель не отделяет от соседних (ягоды, листья, рисунки). Распознаватель
     * подсказывает край там, где он отделяет объект от фона. Результат ограничен обводкой с запасом:
     * край объекта может чуть выходить за линию пальца. Краёв нет — объект по линии пальца с мягким краем.
     */
    suspend fun fromLasso(context: Context, photo: Bitmap, lasso: List<android.graphics.PointF>): Bitmap = withContext(Dispatchers.Default) {
        val w = photo.width
        val h = photo.height
        val path = android.graphics.Path().apply {
            lasso.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
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
            fill.strokeWidth = margin
            drawPath(path, fill)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        var snapped: Bitmap? = null
        val found = if (crop.width() > 8 && crop.height() > 8) {
            val part = Bitmap.createBitmap(photo, crop.left, crop.top, crop.width(), crop.height())
            // Подсказка распознавателя: отдельные объекты, что в основном внутри обводки, иначе передний план.
            val inCrop = Bitmap.createBitmap(region, crop.left, crop.top, crop.width(), crop.height())
            val picked = runCatching { pick(subjects(context, part), inCrop) }.getOrNull()
            val hint = picked ?: runCatching { segmenter(context, part) }.getOrNull()
            snapped = snap(part, inCrop, path, crop, hint)
            inCrop.recycle()
            part.recycle()
            hint
        } else null
        val inside = found?.let { coverage(it) } ?: 0f
        if (snapped != null) {
            canvas.drawBitmap(snapped, crop.left.toFloat(), crop.top.toFloat(), null)
            snapped.recycle()
        } else if (found != null && inside > 0.02f) {
            canvas.drawBitmap(found, null, android.graphics.RectF(crop), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            // Только то, что внутри обводки (с запасом): соседние объекты не цепляются.
            canvas.drawBitmap(region, 0f, 0f, android.graphics.Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            })
        } else {
            // Распознаватель ничего не нашёл — объект по линии пальца, с мягким краем.
            canvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                maskFilter = android.graphics.BlurMaskFilter(max(2f, margin * 0.15f), android.graphics.BlurMaskFilter.Blur.NORMAL)
            })
        }
        found?.recycle()
        region.recycle()
        result
    }

    /**
     * Граница обведённого объекта по самому фото — водораздел от меток: «объект» растёт от середины обводки
     * (обводка, сжатая к центру), «фон» — снаружи обводки с запасом. Оба растут сначала по ровным местам
     * и встречаются на самых резких краях между ними: контурах, перепадах цвета и яркости. Край маски
     * распознавателя ([hint]) добавляется к краям фото, если она отделяет объект от фона, а не залила всё.
     * null — резких краёв нет (однотонное место): тогда объект — сама обводка.
     */
    private fun snap(part: Bitmap, area: Bitmap, lasso: android.graphics.Path, crop: android.graphics.Rect, hint: Bitmap?): Bitmap? {
        val w = part.width
        val h = part.height
        val n = w * h
        if (w < 8 || h < 8) return null
        // Фото чуть размыто: мелкая фактура и блики — не края, края — контуры и смена цвета.
        val radius = max(1, max(w, h) / 160)
        val rgb = blur(IntArray(n).also { part.getPixels(it, 0, w, 0, 0, w, h) }, w, h, radius)
        val inArea = IntArray(n).also { area.getPixels(it, 0, w, 0, 0, w, h) }
        // Метки «объект»: обводка, сжатая к своему центру; и сама обводка — чтобы знать её площадь.
        val bounds = android.graphics.RectF().also { lasso.computeBounds(it, true) }
        fun mask(scale: Float): IntArray {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
            android.graphics.Canvas(bmp).apply {
                translate(-crop.left.toFloat(), -crop.top.toFloat())
                scale(scale, scale, bounds.centerX(), bounds.centerY())
                drawPath(lasso, android.graphics.Paint())
            }
            return IntArray(n).also { bmp.getPixels(it, 0, w, 0, 0, w, h); bmp.recycle() }
        }
        val core = mask(0.55f)
        val inLasso = mask(1f)
        var lassoArea = 0
        for (v in inLasso) if (v ushr 24 > 127) lassoArea++
        if (lassoArea < 50) return null

        // Резкость края в каждой точке: наибольший по каналам перепад (Собель), 0…255.
        val cost = IntArray(n)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            var best = 0
            for (shift in intArrayOf(16, 8, 0)) {
                fun c(dx: Int, dy: Int) = (rgb[i + dy * w + dx] shr shift) and 0xFF
                val gx = c(1, -1) + 2 * c(1, 0) + c(1, 1) - c(-1, -1) - 2 * c(-1, 0) - c(-1, 1)
                val gy = c(-1, 1) + 2 * c(0, 1) + c(1, 1) - c(-1, -1) - 2 * c(0, -1) - c(1, -1)
                best = max(best, kotlin.math.abs(gx) + kotlin.math.abs(gy))
            }
            cost[i] = (best / 4).coerceAtMost(255)
        }
        // Маска распознавателя помогает, только если отделяет объект: заливка всей обводки — не подсказка.
        if (hint != null && hint.width == w && hint.height == h) {
            val conf = IntArray(n).also { hint.getPixels(it, 0, w, 0, 0, w, h) }
            var filled = 0L
            var total = 0L
            for (i in 0 until n) if (inArea[i] ushr 24 > 127) {
                total++
                if (conf[i] ushr 24 > 127) filled++
            }
            if (total > 0 && filled < 0.85 * total) for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                val d = max(kotlin.math.abs((conf[i + 1] ushr 24) - (conf[i - 1] ushr 24)), kotlin.math.abs((conf[i + w] ushr 24) - (conf[i - w] ushr 24)))
                cost[i] = max(cost[i], d * 2 / 5)
            }
        }

        // Водораздел: из очереди по возрастанию резкости берётся точка и получает метку соседа, который её добавил.
        val label = ByteArray(n)
        val queue = Array(256) { IntQueue() }
        var level = 0
        fun push(i: Int, lab: Int) {
            val c = cost[i]
            queue[c].add(i * 2 + lab - 1)
            if (c < level) level = c
        }
        for (i in 0 until n) {
            when {
                core[i] ushr 24 > 127 -> label[i] = 1
                inArea[i] ushr 24 <= 127 -> label[i] = 2
            }
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (label[i].toInt() == 0) continue
            if ((x > 0 && label[i - 1].toInt() == 0) || (x < w - 1 && label[i + 1].toInt() == 0) ||
                (y > 0 && label[i - w].toInt() == 0) || (y < h - 1 && label[i + w].toInt() == 0)
            ) {
                if (x > 0 && label[i - 1].toInt() == 0) push(i - 1, label[i].toInt())
                if (x < w - 1 && label[i + 1].toInt() == 0) push(i + 1, label[i].toInt())
                if (y > 0 && label[i - w].toInt() == 0) push(i - w, label[i].toInt())
                if (y < h - 1 && label[i + w].toInt() == 0) push(i + w, label[i].toInt())
            }
        }
        while (level < 256) {
            val q = queue[level]
            if (q.isEmpty()) {
                level++
                continue
            }
            val e = q.poll()
            val i = e shr 1
            if (label[i].toInt() != 0) continue
            val lab = (e and 1) + 1
            label[i] = lab.toByte()
            val x = i % w
            val y = i / w
            if (x > 0 && label[i - 1].toInt() == 0) push(i - 1, lab)
            if (x < w - 1 && label[i + 1].toInt() == 0) push(i + 1, lab)
            if (y > 0 && label[i - w].toInt() == 0) push(i - w, lab)
            if (y < h - 1 && label[i + w].toInt() == 0) push(i + w, lab)
        }

        // Встретились ли метки на настоящем крае: средняя резкость по границе объекта.
        var edge = 0L
        var edgeN = 0
        var objArea = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (label[i].toInt() != 1) continue
            objArea++
            if (label[i - 1].toInt() == 2 || label[i + 1].toInt() == 2 || label[i - w].toInt() == 2 || label[i + w].toInt() == 2) {
                edge += cost[i]
                edgeN++
            }
        }
        if (edgeN == 0 || edge / edgeN < 14 || objArea < 0.12 * lassoArea) return null

        // Маска с мягким краем в один пиксель.
        val out = IntArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            var cnt = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) {
                    cnt++
                    if (label[yy * w + xx].toInt() == 1) sum++
                }
            }
            out[y * w + x] = ((255 * sum / cnt) shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Размытие по квадрату со стороной 2r+1, дважды (почти по Гауссу) — по каждому каналу. */
    private fun blur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        var a = src
        repeat(2) {
            val tmp = IntArray(w * h)
            for (pass in 0..1) {
                val from = if (pass == 0) a else tmp
                val to = if (pass == 0) tmp else IntArray(w * h)
                val len = if (pass == 0) w else h
                val lines = if (pass == 0) h else w
                for (line in 0 until lines) {
                    var sr = 0
                    var sg = 0
                    var sb = 0
                    fun at(k: Int): Int {
                        val kk = k.coerceIn(0, len - 1)
                        return if (pass == 0) from[line * w + kk] else from[kk * w + line]
                    }
                    for (k in -r..r) at(k).let { sr += (it shr 16) and 0xFF; sg += (it shr 8) and 0xFF; sb += it and 0xFF }
                    val d = 2 * r + 1
                    for (k in 0 until len) {
                        val idx = if (pass == 0) line * w + k else k * w + line
                        to[idx] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                        val add = at(k + r + 1)
                        val sub = at(k - r)
                        sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                        sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                        sb += (add and 0xFF) - (sub and 0xFF)
                    }
                }
                if (pass == 1) a = to
            }
        }
        return a
    }

    /** Очередь точек без упаковки чисел — водораздел проходит сотни тысяч точек. */
    private class IntQueue {
        private var data = IntArray(64)
        private var head = 0
        private var tail = 0

        fun isEmpty() = head == tail

        fun add(v: Int) {
            if (tail == data.size) {
                if (head > data.size / 2) {
                    data.copyInto(data, 0, head, tail)
                    tail -= head
                    head = 0
                } else data = data.copyOf(data.size * 2)
            }
            data[tail++] = v
        }

        fun poll(): Int = data[head++]
    }

    /**
     * Объекты, которые пользователь обвёл: большая часть объекта — внутри обводки [area] (с запасом).
     * Объект, обведённый лишь краешком, — чужой. Возвращает их общую маску или null, если таких нет.
     */
    private fun pick(found: List<Bitmap>, area: Bitmap): Bitmap? {
        val w = area.width
        val h = area.height
        val inside = IntArray(w * h).also { area.getPixels(it, 0, w, 0, 0, w, h) }
        var areaSum = 0L
        for (px in inside) if (px ushr 24 > 127) areaSum++
        val px = IntArray(w * h)
        val chosen = found.filter { m ->
            if (m.width != w || m.height != h) return@filter false
            m.getPixels(px, 0, w, 0, 0, w, h)
            var total = 0L
            var within = 0L
            for (i in px.indices) {
                val a = (px[i] ushr 24).toLong()
                total += a
                if (inside[i] ushr 24 > 127) within += a
            }
            total > 0 && within >= 0.6 * total && within >= 0.03 * 255 * areaSum
        }
        if (chosen.isEmpty()) {
            found.forEach { it.recycle() }
            return null
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        chosen.forEach { canvas.drawBitmap(it, 0f, 0f, null) }
        found.forEach { it.recycle() }
        return out
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

    private suspend fun mlKitSubjects(context: Context, bitmap: Bitmap): List<Bitmap> {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
                .build(),
        )
        try {
            ensureModel(context, segmenter)
            val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
            return result.subjects.mapNotNull { subject ->
                val confidence = subject.confidenceMask ?: return@mapNotNull null
                val pixels = IntArray(bitmap.width * bitmap.height)
                confidence.rewind()
                for (y in 0 until subject.height) for (x in 0 until subject.width) {
                    val a = ((confidence.get() - 0.3f) / 0.4f).coerceIn(0f, 1f)
                    val tx = subject.startX + x
                    val ty = subject.startY + y
                    if (tx < bitmap.width && ty < bitmap.height) pixels[ty * bitmap.width + tx] = ((a * 255).toInt() shl 24) or 0xFFFFFF
                }
                Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            }
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
