package app.synctheme.mobile.data

import android.content.Context
import app.synctheme.core.Theme
import app.synctheme.mobile.render.DepthMasks
import app.synctheme.mobile.render.WallpaperApplier
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.watch.WatchStatusStore
import app.synctheme.mobile.watch.WatchSync
import app.synctheme.mobile.tablet.TabletClient
import app.synctheme.mobile.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ставит тему на устройства: обои, виджеты, циферблат часов и планшет. Ошибка в одном шаге не мешает остальным.
 * Для эффекта глубины сначала находит главный объект на фото, если его ещё не искали.
 */
class ThemeApplier(private val context: Context) {
    class Report(val done: List<String>, val errors: List<String>, val watchSent: Boolean)

    suspend fun apply(theme: Theme, targets: ApplyTargets, progress: (String) -> Unit = {}): Report {
        val repo = ThemeRepository.get(context)
        val t = theme
        val done = mutableListOf<String>()
        val errors = mutableListOf<String>()
        // Фото могли сменить после включения глубины — маска для нового фото считается здесь.
        if (DepthMasks.photosFor(t).isNotEmpty()) {
            progress("Ищу главный объект на фото…")
            DepthMasks.ensureFor(context, t).forEach { errors += "глубина: $it" }
        }
        repo.setApplied(t)
        suspend fun step(label: String, busy: String, block: suspend () -> Unit) {
            progress(busy)
            try {
                block()
                done += label
            } catch (e: Exception) {
                errors += "$label: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        if (targets.homeWallpaper || targets.lockWallpaper) {
            step("обои", "Ставлю обои…") { WallpaperApplier.apply(context, t, targets.homeWallpaper, targets.lockWallpaper) }
        }
        if (targets.widgets) step("виджеты", "Обновляю виджеты…") { withContext(Dispatchers.Default) { WidgetUpdater.updateAll(context) } }
        var sent = false
        if (targets.watch) {
            step("часы", "Собираю циферблат для часов…") {
                val built = WatchFaceBuilder(context).build(t)
                progress("Отправляю на часы…")
                WatchSync(context).push(t, built)
                WatchStatusStore.markPending(t.name)
                sent = true
            }
        }
        if (targets.tablet) {
            step("планшет", "Отправляю на планшет…") {
                val reply = TabletClient.push(context, t)
                if ("Ошибки" in reply) throw java.io.IOException(reply.substringAfter("Ошибки — "))
            }
        }
        return Report(done, errors, sent)
    }
}
