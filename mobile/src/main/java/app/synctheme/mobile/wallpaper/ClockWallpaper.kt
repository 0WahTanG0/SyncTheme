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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.SubjectMask
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.render.WallClockSpot
import app.synctheme.mobile.widget.AppCatalog
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

    /** Стоят ли живые обои SyncTheme и на экране блокировки (свои у блокировки или общие с рабочим столом). */
    fun isActiveOnLock(context: Context): Boolean = runCatching {
        val manager = WallpaperManager.getInstance(context)
        val shared = manager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0 && isActive(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            shared || manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.packageName == context.packageName
        } else shared
    }.getOrDefault(false)

    /** Системный экран «Установить живые обои» с нашими обоями. */
    fun setIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, ClockWallpaperService::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Фон, слой глубины и место часов для текущей темы и размера — их не нужно считать каждую минуту.
 * width × height — размер, для которого всё посчитано.
 */
private class Layers(val key: Any, val background: Bitmap, val foreground: Bitmap?, val spot: WallClockSpot, val width: Int, val height: Int)

/**
 * Обои с часами: фон темы, время шрифтом цифр и — с эффектом глубины — главный объект фото поверх цифр.
 * Перерисовываются раз в минуту, пока видны, и сразу при смене темы. Нажатие на часы открывает
 * выбранное приложение. Касания рабочего стола система дублирует обоям (у окна лаунчера
 * FLAG_SHOW_WALLPAPER), поэтому обои сами узнают короткое нажатие на часы; «нажатие» от лаунчера
 * (COMMAND_TAP, его шлют не все лаунчеры — One UI Home нет) тоже принимается. Запускать
 * приложения видимым живым обоям Android разрешает.
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
        private val tap by lazy { TapDetector(TAP_MOVE_DP * resources.displayMetrics.density) }
        private var lastLaunch = 0L
        private var touchesEnabled = false
        /** До этого момента часы нарисованы «нажатыми» — отклик на нажатие. */
        private var pressedUntil = 0L

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

        private val lockListener by lazy { KeyguardManager.KeyguardLockedStateListener { draw() } }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Касания включаются, только когда нажатие на часы что-то открывает (см. prepare).
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                // Перерисовка и при выключении: к включению экрана кадр уже правильный, а не прежний.
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(events, filter)
            // Телефон заблокировался или разблокировался (в том числе с задержкой после выключения экрана).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { keyguard?.addKeyguardLockedStateListener(mainExecutor, lockListener) }
            }
            scope.launch { repo.applied.collect { prepare() } }
        }

        override fun onTouchEvent(event: MotionEvent) {
            tap.onEvent(event.actionMasked, event.x, event.y, event.eventTime)?.let { onTap(it.x, it.y) }
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) onTap(x.toFloat(), y.toFloat())
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        /** Касание пустого места рабочего стола: если на часах (с запасом под палец) — открываем приложение. */
        private fun onTap(x: Float, y: Float) {
            val l = layers ?: return
            val theme = repo.applied.value
            val wall = theme.wallClock ?: return
            if (!wall.tapToOpen || isPreview || lockOnly || keyguard?.isKeyguardLocked == true) return
            // Место часов посчитано для размера слоёв, касание — в точках поверхности.
            val sx = x * l.width / width.coerceAtLeast(1)
            val sy = y * l.height / height.coerceAtLeast(1)
            if (!ThemeRenderer.wallClockHit(theme, l.spot, sx, sy, TAP_SLOP_DP * resources.displayMetrics.density)) return
            // Одно нажатие может прийти дважды: касанием и командой лаунчера.
            val now = SystemClock.uptimeMillis()
            if (now - lastLaunch < 800) return
            lastLaunch = now
            val intent = AppCatalog.wallClockIntent(this@ClockWallpaperService, wall.openApp) ?: return
            pressedUntil = now + PRESS_MS
            draw()
            handler.postDelayed({ draw() }, PRESS_MS + 10)
            runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "Tap launch failed", it) }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { keyguard?.removeKeyguardLockedStateListener(lockListener) }
            }
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
            val touches = !lock && theme.wallClock?.tapToOpen == true
            if (touches != touchesEnabled) {
                touchesEnabled = touches
                setTouchEventsEnabled(touches)
            }
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
                        // Только экран блокировки — часы и объект по его фону.
                        val clockTheme = if (lock) lockTheme(theme) else theme
                        val foreground = if (!lock || theme.wallClock?.onLock == true) foreground(ctx, clockTheme, w, h) else null
                        // Место часов — после маски объекта: с глубиной часы встают напротив него.
                        Layers(key, background, foreground, ThemeRenderer.wallClockSpot(ctx, clockTheme, w, h), w, h)
                    }.onFailure { Log.e(TAG, "Wallpaper layers failed", it) }.getOrNull()
                } ?: return@launch
                layers?.let {
                    it.background.recycle()
                    it.foreground?.recycle()
                }
                layers = prepared
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
                // На экране блокировки — свои часы, только если их туда попросили; иначе там часы самого телефона.
                val stored = repo.applied.value
                val locked = lockOnly || keyguard?.isKeyguardLocked == true
                if (!locked || stored.wallClock?.onLock == true) {
                    val theme = if (lockOnly) lockTheme(stored) else stored
                    val now = System.currentTimeMillis()
                    // Часы считались для размера слоёв; если холст другого размера — масштабируем.
                    canvas.save()
                    canvas.scale(canvas.width / l.width.toFloat(), canvas.height / l.height.toFloat())
                    val pressed = SystemClock.uptimeMillis() < pressedUntil
                    ThemeRenderer.drawWallClock(this@ClockWallpaperService, canvas, theme, l.spot, now, alpha = if (pressed) 150 else 255)
                    canvas.restore()
                    // Объект с фото целиком закрывает часы и подписи — они за ним.
                    l.foreground?.let { fg -> canvas.drawBitmap(fg, null, dst, null) }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    /** Фон и затемнение экрана блокировки — по ним место часов и объект, когда обои стоят только там. */
    private fun lockTheme(theme: Theme): Theme = theme.copy(background = theme.lockScreenBackground(), dim = theme.lockScreenDim())

    private companion object {
        const val TAG = "ClockWallpaper"
        const val TAP_SLOP_DP = 20
        /** Насколько палец может сдвинуться, чтобы это ещё было нажатие. */
        const val TAP_MOVE_DP = 12
        const val PRESS_MS = 160L
    }
}
