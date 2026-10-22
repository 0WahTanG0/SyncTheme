package app.synctheme.mobile.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.ThemeRenderer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Экран темы для телевизора (заставка и «Показать заставку»): обои темы во весь экран, крупные часы
 * и дата. Время обновляется раз в минуту; часы понемногу смещаются, чтобы экран не выгорал.
 * Новая тема с телефона появляется сразу, не дожидаясь следующей минуты.
 */
class ThemeScreenView(context: Context) : View(context) {
    private val repo = ThemeRepository.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private var worker: ExecutorService? = null
    private var scope: CoroutineScope? = null
    private var theme: Theme? = null
    private var layers: Pair<Bitmap, Bitmap?>? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000 - now % 60_000 + 50)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        worker = Executors.newSingleThreadExecutor()
        handler.post(tick)
        scope = MainScope().also { s -> s.launch { repo.applied.collect { refresh() } } }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        scope?.cancel()
        scope = null
        worker?.shutdownNow()
        worker = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        theme?.let { render(it) }
    }

    /** Тема сменилась — перерисовать обои; иначе только время. */
    private fun refresh() {
        val t = repo.applied.value
        if (t != theme || layers == null) {
            theme = t
            render(t)
        }
        invalidate()
    }

    private fun render(t: Theme) {
        val w = width
        val h = height
        val pool = worker
        if (w == 0 || h == 0 || pool == null || pool.isShutdown) return
        pool.execute {
            val next = runCatching { ThemeRenderer.tvLayers(context, t, w, h) }.getOrNull() ?: return@execute
            handler.post {
                val old = layers
                layers = next
                invalidate()
                old?.first?.recycle()
                old?.second?.recycle()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val l = layers
        val t = theme
        if (l == null || t == null) {
            canvas.drawColor(Color.BLACK)
            return
        }
        canvas.drawBitmap(l.first, 0f, 0f, null)
        // Сдвиг против выгорания: по минутам, медленно и в пределах нескольких процентов экрана.
        val minute = System.currentTimeMillis() / 60_000
        val dx = (width * 0.05f * sin(minute / 9.0)).toFloat()
        val dy = (height * 0.04f * sin(minute / 13.0)).toFloat()
        ThemeRenderer.drawTvClock(context, canvas, t, width, height, System.currentTimeMillis(), dx, dy, l.second)
    }
}
