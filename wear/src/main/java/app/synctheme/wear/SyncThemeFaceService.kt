package app.synctheme.wear

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Rect
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import app.synctheme.faces.FaceFonts
import app.synctheme.faces.FaceRenderer
import app.synctheme.faces.FaceState
import java.time.ZonedDateTime

/**
 * Классический циферблат SyncTheme для Wear OS 3–5. На Wear OS 6 выключен (values-v36/bools.xml):
 * там циферблат ставится через Watch Face Push.
 */
class SyncThemeFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace = WatchFace(
        WatchFaceType.DIGITAL,
        FaceCanvasRenderer(surfaceHolder, watchState, currentUserStyleRepository),
    )

    private inner class FaceCanvasRenderer(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        styles: CurrentUserStyleRepository,
    ) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
        surfaceHolder, styles, watchState, CanvasType.HARDWARE, FRAME_MS, false,
    ) {
        private val fonts = FaceFonts(this@SyncThemeFaceService)

        override suspend fun createSharedAssets(): SharedAssets = object : SharedAssets {
            override fun onDestroy() = Unit
        }

        override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
            val ctx = this@SyncThemeFaceService
            val face = ClassicFace.current(ctx)
            val state = FaceState.now(zonedDateTime.toInstant().toEpochMilli(), DateFormat.is24HourFormat(ctx)).copy(
                battery = battery(),
                ambient = renderParameters.drawMode == DrawMode.AMBIENT,
            )
            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / FaceRenderer.SIZE, bounds.height() / FaceRenderer.SIZE)
            FaceRenderer.draw(canvas, face.settings, face.background, face.foreground, state, fonts)
            canvas.restore()
        }

        override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
            renderParameters.highlightLayer?.let { canvas.drawColor(it.backgroundTint) }
        }

        /** Заряд из «липкого» системного события — без разрешений и подписок. */
        private fun battery(): Int? {
            val intent: Intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            return if (level >= 0 && scale > 0) level * 100 / scale else null
        }
    }

    private companion object {
        /** Раз в секунду: секунды и «живые» элементы движутся, батарея почти не тратится. */
        const val FRAME_MS = 1000L
    }
}
