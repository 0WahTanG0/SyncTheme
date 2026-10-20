package app.synctheme.wear

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import app.synctheme.core.Presets
import app.synctheme.core.WatchSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Классический циферблат для часов без Watch Face Push (Wear OS 3–5): тема с телефона
 * хранится в файлах, а рисует её [SyncThemeFaceService] тем же кодом, что и превью на телефоне.
 */
object ClassicFace {
    /** Watch Face Push есть с Wear OS 6 (API 36) — там классический циферблат не нужен. */
    val needed: Boolean get() = Build.VERSION.SDK_INT < 36

    class Face(val settings: WatchSettings, val background: Bitmap?, val foreground: Bitmap?, val version: Long)

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    @Volatile private var cached: Face? = null

    private fun dir(context: Context) = File(context.filesDir, "classic").apply { mkdirs() }

    /** Сохраняет тему с телефона; циферблат перерисуется со следующим кадром. */
    @Synchronized
    fun save(context: Context, settingsJson: String, background: ByteArray, foreground: ByteArray?) {
        val d = dir(context)
        File(d, "bg.jpg").writeBytes(background)
        File(d, "fg.png").let { if (foreground != null) it.writeBytes(foreground) else it.delete() }
        File(d, "settings.json").writeText(settingsJson)
        cached = null
        _version.value = System.currentTimeMillis()
    }

    /** Текущая тема циферблата (из файлов, с кэшем); без темы — оформление по умолчанию. */
    @Synchronized
    fun current(context: Context): Face {
        cached?.let { return it }
        val d = dir(context)
        val settings = runCatching { WatchSettings.fromJson(File(d, "settings.json").readText()) }
            .getOrDefault(Presets.default.watchSettings())
        val face = Face(
            settings,
            BitmapFactory.decodeFile(File(d, "bg.jpg").path),
            if (settings.depth) BitmapFactory.decodeFile(File(d, "fg.png").path) else null,
            _version.value,
        )
        cached = face
        return face
    }

    fun component(context: Context) = ComponentName(context, SyncThemeFaceService::class.java)

    fun isActive(context: Context): Boolean =
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context) }.getOrDefault(false)

    /** Системный экран выбора циферблата с нашим циферблатом. */
    fun chooseIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
