package app.synctheme.mobile.render

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.synctheme.core.Theme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Картинки темы для устройств, куда SyncTheme не может поставить её сам: iPhone, iPad, Mac,
 * Apple Watch (циферблат «Фото») и часы других систем с фото-циферблатом.
 */
enum class ExportKind(val title: String, val note: String) {
    IPHONE("iPhone", "обои экрана и блокировки"),
    APPLE_WATCH("Apple Watch", "фон для циферблата «Фото»"),
    IPAD("iPad", "обои"),
    MAC("Mac", "обои рабочего стола"),
    ROUND_WATCH("Круглые часы", "Huawei, Amazfit, Xiaomi, Garmin"),
    SQUARE_WATCH("Квадратные часы", "Amazfit GTS, Huawei Watch Fit, браслеты"),
}

object ExportKit {
    private class Image(val name: String, val width: Int, val height: Int, val lock: Boolean = false, val watch: Boolean = false)

    private fun images(kind: ExportKind): List<Image> = when (kind) {
        // 19.5:9 — как у всех iPhone с Face ID; iOS сам подгонит под свой экран.
        ExportKind.IPHONE -> listOf(Image("iPhone", 1290, 2796), Image("iPhone-lock", 1290, 2796, lock = true))
        // 410×502 — Apple Watch Ultra; на 41–46 мм циферблат «Фото» обрежет края.
        ExportKind.APPLE_WATCH -> listOf(Image("AppleWatch", 410, 502, watch = true))
        ExportKind.IPAD -> listOf(Image("iPad", 2048, 2732))
        ExportKind.MAC -> listOf(Image("Mac", 3024, 1964))
        ExportKind.ROUND_WATCH -> listOf(Image("Watch-round", 480, 480, watch = true))
        ExportKind.SQUARE_WATCH -> listOf(Image("Watch-square", 390, 450, watch = true))
    }

    /** Рисует картинки в cache/shared (оттуда ими делятся через FileProvider). */
    suspend fun render(context: Context, theme: Theme, kinds: Set<ExportKind>): List<File> = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("SyncTheme-") }?.forEach { it.delete() }
        kinds.sortedBy { it.ordinal }.flatMap(::images).map { img ->
            val bmp = when {
                img.watch -> ThemeRenderer.background(context, theme.watchBackground(), theme.watchSettings().dim, img.width, img.height)
                img.lock -> ThemeRenderer.lockBackground(context, theme, img.width, img.height)
                else -> ThemeRenderer.background(context, theme, img.width, img.height)
            }
            File(dir, "SyncTheme-${img.name}.jpg").also { f ->
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                bmp.recycle()
            }
        }
    }

    fun uris(context: Context, files: List<File>): ArrayList<Uri> =
        ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })

    /** «Поделиться»: почта, мессенджеры, облако — чтобы открыть картинки на iPhone, Mac или в приложении часов. */
    fun shareIntent(context: Context, files: List<File>): Intent {
        val uris = uris(context, files)
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = "image/jpeg"
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        return Intent.createChooser(send, "Картинки темы").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Сохраняет в «Галерею» (Pictures/SyncTheme) — оттуда их заберут Google Фото или облако. */
    suspend fun saveToGallery(context: Context, files: List<File>) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        for (f in files) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SyncTheme")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: continue
            resolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }
}
