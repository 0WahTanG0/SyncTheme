package app.synctheme.mobile.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.ThemeRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Живые обои SyncTheme: включены ли они и как их поставить. */
object ClockWallpaper {
    fun isActive(context: Context): Boolean =
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName

    /** Системный экран «Установить живые обои» с нашими обоями. */
    fun setIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, ClockWallpaperService::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Фон и слой глубины для текущей темы и размера — их не нужно рисовать каждую минуту. */
private class Layers(val key: Any, val background: Bitmap, val foreground: Bitmap?)

/**
 * Обои с часами: фон темы, время шрифтом цифр и — с эффектом глубины — главный объект фото поверх цифр.
 * Перерисовываются раз в минуту, пока видны, и сразу при смене темы (в том числе «Хамелеоном»).
 */
class ClockWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val repo = ThemeRepository.get(this@ClockWallpaperService)
        private var width = 0
        private var height = 0
        private var visible = false

        private var layers: Layers? = null
        private var preparing: Job? = null

        private val tick = object : Runnable {
            override fun run() {
                draw()
                scheduleTick()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            scope.launch { repo.effective.collect { prepare() } }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            prepare()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                draw()
                scheduleTick()
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            scope.cancel()
            layers?.let {
                it.background.recycle()
                it.foreground?.recycle()
            }
            super.onDestroy()
        }

        private fun scheduleTick() {
            handler.removeCallbacks(tick)
            if (!visible) return
            val now = System.currentTimeMillis()
            handler.postDelayed(tick, 60_000 - now % 60_000 + 20)
        }

        /** Только экран блокировки (Android 14+ сообщает, где стоят обои). */
        private val lockOnly: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                wallpaperFlags == WallpaperManager.FLAG_LOCK

        private fun prepare() {
            if (width == 0 || height == 0) return
            val theme = repo.effective.value
            val lock = lockOnly
            val key = listOf(theme, width, height, lock)
            if (layers?.key == key) return draw()
            preparing?.cancel()
            preparing = scope.launch {
                val ctx = this@ClockWallpaperService
                val (bg, fg) = withContext(Dispatchers.Default) {
                    val background = if (lock) ThemeRenderer.lockBackground(ctx, theme, width, height)
                    else ThemeRenderer.background(ctx, theme, width, height)
                    val foreground = if (!lock && theme.wallClock?.depth == true) {
                        ThemeRenderer.foreground(ctx, theme.background, theme.dim, width, height)
                    } else null
                    background to foreground
                }
                layers?.let {
                    it.background.recycle()
                    it.foreground?.recycle()
                }
                layers = Layers(key, bg, fg)
                draw()
            }
        }

        private fun draw() {
            val l = layers ?: return
            val holder = surfaceHolder
            val canvas: Canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas() ?: return
            try {
                canvas.drawBitmap(l.background, null, android.graphics.Rect(0, 0, canvas.width, canvas.height), null)
                val theme: Theme = repo.effective.value
                if (!lockOnly) ThemeRenderer.drawWallClock(this@ClockWallpaperService, canvas, theme, canvas.width, canvas.height, System.currentTimeMillis())
                l.foreground?.let { canvas.drawBitmap(it, null, android.graphics.Rect(0, 0, canvas.width, canvas.height), null) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
