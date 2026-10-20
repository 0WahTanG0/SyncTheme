package app.synctheme.mobile.data

import android.content.Context
import app.synctheme.core.Theme
import app.synctheme.mobile.render.WallpaperApplier
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.watch.WatchStatusStore
import app.synctheme.mobile.watch.WatchSync
import app.synctheme.mobile.widget.WidgetUpdater

/**
 * Ставит тему на устройства: обои, виджеты, циферблат. Ставится «действующая» тема —
 * с поправкой «Хамелеона», если он включён. Ошибка в одном шаге не мешает остальным.
 */
class ThemeApplier(private val context: Context) {
    class Report(val done: List<String>, val errors: List<String>, val watchSent: Boolean)

    suspend fun apply(theme: Theme, targets: ApplyTargets, progress: (String) -> Unit = {}): Report {
        val repo = ThemeRepository.get(context)
        repo.setApplied(theme)
        val t = repo.effective.value
        val done = mutableListOf<String>()
        val errors = mutableListOf<String>()
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
        if (targets.widgets) step("виджеты", "Обновляю виджеты…") { WidgetUpdater.updateAll(context) }
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
        return Report(done, errors, sent)
    }
}
