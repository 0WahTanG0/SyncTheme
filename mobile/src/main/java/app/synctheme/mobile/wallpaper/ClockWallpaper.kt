package app.synctheme.mobile.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.SubjectMask
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
 * Перерисовываются раз в минуту, пока видны, и сразу при смене темы.
 *
 * Надёжность: кадр рисуется всякий раз, когда система просит (onSurfaceRedrawNeeded, включение
 * экрана, разблокировка); маска объекта, если её ещё нет, считается прямо здесь; при ошибке
 * обои остаются с фоном и часами, а не пустыми.
 */
class ClockWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val repo = ThemeRepository.get(this@ClockWallpaperService)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private var width = 0
        private var height = 0
        private var visible = false
        private var layers: Layers? = null
        private var preparing: Job? = null
        private var preparingKey: Any? = null

        private val tick = object : Runnable {
            override fun run() {
                draw()
                scheduleTick()
            }
        }

        /** Экран включили, разблокировали или сменили время — сразу новый кадр. */
        private val events = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                draw()
                scheduleTick()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(events, filter)
            scope.launch { repo.applied.collect { prepare() } }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            prepare()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                prepare()
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
            runCatching { unregisterReceiver(events) }
            scope.cancel()
            layers?.let {
                it.background.recycle()
                it.foreground?.recycle()
            }
            layers = null
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

        /** Готовит фон и слой глубины; пока готовит, на экране остаётся предыдущий кадр. */
        private fun prepare() {
            if (width == 0 || height == 0) return
            val theme = repo.applied.value
            val lock = lockOnly
            val key = listOf(theme, width, height, lock)
            if (layers?.key == key) return draw()
            if (preparingKey == key && preparing?.isActive == true) return
            preparing?.cancel()
            preparingKey = key
            preparing = scope.launch {
                val ctx = this@ClockWallpaperService
                val w = width
                val h = height
                val prepared = withContext(Dispatchers.Default) {
                    runCatching {
                        val background = if (lock) ThemeRenderer.lockBackground(ctx, theme, w, h)
                        else ThemeRenderer.background(ctx, theme, w, h)
                        background to (if (!lock) foreground(ctx, theme, w, h) else null)
                    }.onFailure { Log.e(TAG, "Wallpaper layers failed", it) }.getOrNull()
                } ?: return@launch
                layers?.let {
                    it.background.recycle()
                    it.foreground?.recycle()
                }
                layers = Layers(key, prepared.first, prepared.second)
                draw()
            }
        }

        /** Слой глубины; если маски для фото ещё нет (фото сменили), считаем её здесь же. */
        private suspend fun foreground(ctx: Context, theme: Theme, w: Int, h: Int): Bitmap? {
            if (theme.wallClock?.depth != true) return null
            val photo = theme.background.photoFile ?: return null
            if (!SubjectMask.exists(ctx, photo)) {
                runCatching { SubjectMask.ensure(ctx, photo) }.onFailure { Log.w(TAG, "No subject mask", it) }
            }
            return ThemeRenderer.foreground(ctx, theme.background, theme.dim, w, h)
        }

        private fun draw() {
            val l = layers ?: return
            val holder = surfaceHolder
            val canvas: Canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val dst = Rect(0, 0, canvas.width, canvas.height)
                canvas.drawBitmap(l.background, null, dst, null)
                // На экране блокировки часы рисует сам телефон — свои не дублируем.
                val locked = lockOnly || keyguard?.isKeyguardLocked == true
                if (!locked) {
                    ThemeRenderer.drawWallClock(this@ClockWallpaperService, canvas, repo.applied.value, canvas.width, canvas.height, System.currentTimeMillis())
                    l.foreground?.let { fg ->
                        canvas.drawBitmap(fg, null, dst, null)
                        // Время поверх объекта — полупрозрачное: объект впереди, но время читается.
                        ThemeRenderer.drawWallClock(
                            this@ClockWallpaperService, canvas, repo.applied.value, canvas.width, canvas.height,
                            System.currentTimeMillis(), alpha = app.synctheme.core.WatchFaceXml.DEPTH_TIME_ALPHA,
                        )
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private companion object {
        const val TAG = "ClockWallpaper"
    }
}
