package app.synctheme.mobile.render

import android.app.WallpaperManager
import android.content.Context
import android.view.WindowManager
import app.synctheme.core.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperApplier {
    suspend fun apply(context: Context, theme: Theme, home: Boolean, lock: Boolean) = withContext(Dispatchers.Default) {
        if (!home && !lock) return@withContext
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        // Рисуем в портретной ориентации в реальном разрешении экрана (на S25+ до 1440x3120).
        val w = minOf(bounds.width(), bounds.height())
        val h = maxOf(bounds.width(), bounds.height())
        val manager = WallpaperManager.getInstance(context)
        val sameOnLock = theme.lockBackground == null && theme.lockDim == null
        if (home && lock && sameOnLock) {
            val bitmap = ThemeRenderer.background(context, theme, w, h)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
            return@withContext
        }
        if (home) {
            val bitmap = ThemeRenderer.background(context, theme, w, h)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            bitmap.recycle()
        }
        if (lock) {
            val bitmap = ThemeRenderer.lockBackground(context, theme, w, h)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
        }
    }
}
