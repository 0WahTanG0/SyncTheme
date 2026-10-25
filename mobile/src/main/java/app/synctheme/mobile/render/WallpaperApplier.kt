package app.synctheme.mobile.render

import android.app.WallpaperManager
import android.content.Context
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.util.Compat
import app.synctheme.core.Theme
import app.synctheme.mobile.wallpaper.ClockWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperApplier {
    suspend fun apply(context: Context, theme: Theme, home: Boolean, lock: Boolean) = withContext(Dispatchers.Default) {
        if (!home && !lock) return@withContext
        // Телефон — портрет в реальном разрешении экрана (на S25+ до 1440x3120). Планшет поворачивают:
        // квадрат по длинной стороне, и система вырезает из него и портрет, и альбомную ориентацию.
        val (w, h) = Compat.screen(context).let { (w, h) -> if (DeviceSettings.isTablet(context)) h to h else w to h }
        val manager = WallpaperManager.getInstance(context)
        // Живые обои с часами перерисуются сами — картинку поверх них не ставим.
        val liveHome = theme.wallClock != null && ClockWallpaper.isActive(context)
        val setHome = home && !liveHome
        val sameOnLock = theme.lockBackground == null && theme.lockDim == null
        if (setHome && lock && sameOnLock && !(theme.wallClock?.onLock == true && ClockWallpaper.isActiveOnLock(context))) {
            val bitmap = ThemeRenderer.background(context, theme, w, h)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
            return@withContext
        }
        if (setHome) {
            val bitmap = ThemeRenderer.background(context, theme, w, h)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            bitmap.recycle()
        }
        // Свои часы на экране блокировки рисуют живые обои — картинка их бы закрыла.
        val liveLock = theme.wallClock?.onLock == true && ClockWallpaper.isActiveOnLock(context)
        if (lock && !liveLock) {
            val bitmap = ThemeRenderer.lockBackground(context, theme, w, h)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
        }
    }
}
