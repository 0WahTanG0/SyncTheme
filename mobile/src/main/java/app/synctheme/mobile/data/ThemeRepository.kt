package app.synctheme.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import app.synctheme.core.Presets
import app.synctheme.core.Theme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Хранит черновик темы (то, что редактируется), применённую тему (её видят виджеты и живые обои)
 * и сохранённые пользователем темы.
 */
class ThemeRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("themes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(Theme.serializer())

    private val _draft = MutableStateFlow(read("draft") ?: Presets.default)
    val draft: StateFlow<Theme> = _draft.asStateFlow()

    private val _applied = MutableStateFlow(read("applied") ?: Presets.default)
    val applied: StateFlow<Theme> = _applied.asStateFlow()

    private val _saved = MutableStateFlow(
        prefs.getString("saved", null)?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            ?: emptyList()
    )
    val saved: StateFlow<List<Theme>> = _saved.asStateFlow()

    val photosDir: File get() = File(appContext.filesDir, "photos").apply { mkdirs() }

    fun photoFile(name: String): File = File(photosDir, name)

    fun setDraft(theme: Theme) {
        _draft.value = theme
        prefs.edit().putString("draft", theme.toJson()).apply()
    }

    fun setApplied(theme: Theme) {
        _applied.value = theme
        prefs.edit().putString("applied", theme.toJson()).apply()
    }

    /** Сохраняет тему в «Мои темы» (по id; новая тема получает новый id). */
    fun save(theme: Theme): Theme {
        val stored = if (_saved.value.any { it.id == theme.id }) theme
        else theme.copy(id = "my-" + UUID.randomUUID().toString().take(8))
        _saved.value = listOf(stored) + _saved.value.filterNot { it.id == stored.id }
        persistSaved()
        return stored
    }

    fun delete(theme: Theme) {
        _saved.value = _saved.value.filterNot { it.id == theme.id }
        persistSaved()
        cleanupPhotos()
    }

    /** Копирует фото из галереи в каталог приложения (уменьшая до разумного размера). */
    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > MAX_PHOTO_SIDE) {
                val scale = MAX_PHOTO_SIDE.toFloat() / longSide
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val name = "photo_" + UUID.randomUUID().toString().take(8) + ".jpg"
        photoFile(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        cleanupPhotos(keep = name)
        name
    }

    private fun cleanupPhotos(keep: String? = null) {
        val photos = (listOf(_draft.value, _applied.value) + _saved.value)
            .flatMap { t -> listOfNotNull(t.background.photoFile, t.lockBackground?.photoFile, t.watch?.background?.photoFile) }
            .toSet() + listOfNotNull(keep)
        // Рядом с фото лежит маска эффекта глубины (photo_x.mask.png) — её тоже оставляем.
        val used = photos + photos.map { it.substringBeforeLast('.') + ".mask.png" }
        photosDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    private fun persistSaved() {
        prefs.edit().putString("saved", json.encodeToString(listSerializer, _saved.value)).apply()
    }

    private fun read(key: String): Theme? =
        prefs.getString(key, null)?.let { runCatching { Theme.fromJson(it) }.getOrNull() }

    companion object {
        private const val MAX_PHOTO_SIDE = 3200

        @Volatile private var instance: ThemeRepository? = null

        fun get(context: Context): ThemeRepository =
            instance ?: synchronized(this) { instance ?: ThemeRepository(context).also { instance = it } }
    }
}
