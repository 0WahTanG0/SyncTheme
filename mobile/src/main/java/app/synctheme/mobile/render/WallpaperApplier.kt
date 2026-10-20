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
        val bitmap = ThemeRenderer.background(context, theme, w, h)
        var flags = 0
        if (home) flags = flags or WallpaperManager.FLAG_SYSTEM
        if (lock) flags = flags or WallpaperManager.FLAG_LOCK
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
        bitmap.recycle()
    }
}
