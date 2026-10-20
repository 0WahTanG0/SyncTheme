package app.synctheme.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.synctheme.mobile.data.ThemeRepository

/** Базовый виджет: у каждого экземпляра свои настройки ([WidgetConfigStore]). */
abstract class ThemedWidget(val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.updateOne(context, manager, it, kind) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetConfigStore.delete(context, ids)
    }

    override fun onEnabled(context: Context) = WidgetScheduler.ensure(context)

    override fun onDisabled(context: Context) = WidgetScheduler.ensure(context)
}

class ClockWidget : ThemedWidget(WidgetKind.CLOCK)
class CalendarWidget : ThemedWidget(WidgetKind.CALENDAR)
class WeatherWidget : ThemedWidget(WidgetKind.WEATHER)
class BatteryWidget : ThemedWidget(WidgetKind.BATTERY)
class AppsWidget : ThemedWidget(WidgetKind.APPS)

/** Прежний виджет «Дата»: теперь он часть календаря и скрыт из списка виджетов, но уже добавленные работают. */
class DateWidget : ThemedWidget(WidgetKind.CALENDAR)

/** Будильник поменялся — обновить часы, где показан следующий будильник. */
class AlarmChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.update(context, WidgetKind.CLOCK)
    }
}

/** Размещённый на экране виджет. */
data class PlacedWidget(val id: Int, val kind: WidgetKind, val config: WidgetConfig)

object WidgetUpdater {
    private val providers = mapOf(
        WidgetKind.CLOCK to listOf(ClockWidget::class.java),
        WidgetKind.CALENDAR to listOf(CalendarWidget::class.java, DateWidget::class.java),
        WidgetKind.WEATHER to listOf(WeatherWidget::class.java),
        WidgetKind.BATTERY to listOf(BatteryWidget::class.java),
        WidgetKind.APPS to listOf(AppsWidget::class.java),
    )

    fun ids(context: Context, kind: WidgetKind): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return providers.getValue(kind).flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()
    }

    fun kindOf(context: Context, id: Int): WidgetKind? {
        val cls = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className ?: return null
        return providers.entries.firstOrNull { e -> e.value.any { it.name == cls } }?.key
    }

    fun config(context: Context, id: Int, kind: WidgetKind): WidgetConfig =
        WidgetConfigStore.get(context, id, kind, ThemeRepository.get(context).effective.value.widgetOpacity)

    fun placed(context: Context): List<PlacedWidget> =
        WidgetKind.entries.flatMap { kind -> ids(context, kind).map { PlacedWidget(it, kind, config(context, it, kind)) } }

    fun updateAll(context: Context) = WidgetKind.entries.forEach { update(context, it) }

    fun update(context: Context, kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        ids(context, kind).forEach { updateOne(context, manager, it, kind) }
    }

    fun updateOne(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind) {
        val theme = ThemeRepository.get(context).effective.value
        runCatching { manager.updateAppWidget(id, WidgetRenderer.build(context, kind, config(context, id, kind), theme)) }
    }

    /** Одинаковая прозрачность для всех размещённых виджетов. */
    fun setOpacityForAll(context: Context, opacity: Float) {
        placed(context).forEach { WidgetConfigStore.set(context, it.id, it.config.copy(opacity = opacity)) }
        updateAll(context)
    }

    /**
     * Просит лаунчер добавить виджет; после добавления сразу открывается его настройка
     * (система подставит EXTRA_APPWIDGET_ID в intent).
     */
    fun requestPin(context: Context, kind: WidgetKind): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val configure = PendingIntent.getActivity(
            context, kind.ordinal,
            Intent(context, WidgetConfigActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_KIND, kind.name),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return manager.requestPinAppWidget(ComponentName(context, providers.getValue(kind).first()), null, configure)
    }
}
