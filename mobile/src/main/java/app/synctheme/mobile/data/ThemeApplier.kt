package app.synctheme.mobile.data

import android.content.Context
import app.synctheme.core.Theme
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.render.DepthMasks
import app.synctheme.mobile.render.WallpaperApplier
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.watch.WatchStatusStore
import app.synctheme.mobile.watch.WatchSync
import app.synctheme.mobile.link.LinkClient
import app.synctheme.mobile.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Ставит тему на устройства: обои, виджеты, циферблат часов и планшет. Ошибка в одном шаге не мешает остальным.
 * Для эффекта глубины сначала находит главный объект на фото, если его ещё не искали.
 */
class ThemeApplier(private val context: Context) {
    class Report(val done: List<String>, val errors: List<String>, val watchSent: Boolean)

    suspend fun apply(theme: Theme, targets: ApplyTargets, progress: (String) -> Unit = {}): Report = coroutineScope {
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
        // «Как в системе» — дальше обои и виджеты сами переходят между светлой и тёмной версией.
        DayNight.ensure(context)
        suspend fun step(label: String, busy: String, block: suspend () -> Unit) {
            progress(busy)
            try {
                block()
                done += label
            } catch (e: Exception) {
                errors += "$label: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        // Часы — первыми и параллельно с остальным: сборка циферблата и передача по Bluetooth — самое долгое.
        // Обычно циферблат уже собран заранее (пока тему настраивали), и он сразу уходит на часы.
        val watch = if (!targets.watch) null else async {
            runCatching {
                val face = DayNight.forWatch(t)
                val built = WatchFaceBuilder(context).build(face)
                WatchSync(context).push(face, built)
                WatchStatusStore.markPending(t.name)
            }
        }
        if (targets.homeWallpaper || targets.lockWallpaper) {
            step("обои", "Ставлю обои…") { WallpaperApplier.apply(context, t, targets.homeWallpaper, targets.lockWallpaper) }
        }
        if (targets.widgets) step("виджеты", "Обновляю виджеты…") { withContext(Dispatchers.Default) { WidgetUpdater.updateAll(context) } }
        if (targets.tablet) {
            // Все устройства по Wi‑Fi (планшет, телевизор, телефон) — одновременно.
            progress("Отправляю на устройства…")
            LinkClient.pushAll(context, t).forEach { (peer, result) ->
                result.onSuccess { reply ->
                    if ("Ошибки" in reply) errors += "${peer.name}: ${reply.substringAfter("Ошибки — ")}" else done += peer.name
                }.onFailure { errors += "${peer.name}: ${it.message ?: it.javaClass.simpleName}" }
            }
        }
        var sent = false
        if (watch != null) {
            if (watch.isActive) progress("Отправляю на часы…")
            watch.await()
                .onSuccess { done += "часы"; sent = true }
                .onFailure { errors += "часы: ${it.message ?: it.javaClass.simpleName}" }
        }
        Report(done, errors, sent)
    }
}
