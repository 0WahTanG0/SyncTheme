package app.synctheme.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import androidx.core.os.BundleCompat
import app.synctheme.mobile.util.Compat
import app.synctheme.mobile.data.ThemeRepository

/** Базовый виджет: у каждого экземпляра свои настройки ([WidgetConfigStore]). */
abstract class ThemedWidget(val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.updateOne(context, manager, it, kind) }
        if (kind == WidgetKind.CLOCK) ClockTicker.schedule(context)
    }

    /** Виджет растянули или сжали: лаунчер прислал новые размеры — подбираем разметку под них. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        WidgetUpdater.updateOne(context, manager, id, kind)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetConfigStore.delete(context, ids)
        if (kind == WidgetKind.CLOCK) ClockTicker.schedule(context)
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
        // Приёмник открыт для системы — реагируем только на её сообщение о будильнике.
        if (intent.action != android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return
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

    /** null — на устройстве нет виджетов (телевизоры с Google TV / Android TV). */
    private fun manager(context: Context): AppWidgetManager? = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()

    fun ids(context: Context, kind: WidgetKind): IntArray {
        val manager = manager(context) ?: return IntArray(0)
        return providers.getValue(kind).flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()
    }

    fun kindOf(context: Context, id: Int): WidgetKind? {
        val cls = manager(context)?.getAppWidgetInfo(id)?.provider?.className ?: return null
        return providers.entries.firstOrNull { e -> e.value.any { it.name == cls } }?.key
    }

    fun config(context: Context, id: Int, kind: WidgetKind): WidgetConfig =
        WidgetConfigStore.get(context, id, kind, ThemeRepository.get(context).applied.value.widgetOpacity)

    fun placed(context: Context): List<PlacedWidget> =
        WidgetKind.entries.flatMap { kind -> ids(context, kind).map { PlacedWidget(it, kind, config(context, it, kind)) } }

    fun updateAll(context: Context) = WidgetKind.entries.forEach { update(context, it) }

    fun update(context: Context, kind: WidgetKind) {
        val manager = manager(context) ?: return
        ids(context, kind).forEach { updateOne(context, manager, it, kind) }
        if (kind == WidgetKind.CLOCK) ClockTicker.schedule(context)
    }

    fun updateOne(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind) {
        val theme = ThemeRepository.get(context).applied.value
        runCatching {
            manager.updateAppWidget(id, WidgetRenderer.build(context, kind, config(context, id, kind), theme, sizes = sizes(manager, id), widgetId = id))
        }
    }

    /** Точные размеры виджета от лаунчера (Android 12+), dp. */
    private fun sizes(manager: AppWidgetManager, id: Int): List<SizeF> = runCatching {
        val options = manager.getAppWidgetOptions(id)
        if (Compat.modernWidgets) {
            BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                .orEmpty().filter { it.width > 0 && it.height > 0 }
        } else {
            // До Android 12 лаунчер сообщает только границы: в портрете — ширина min, высота max.
            val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (w > 0 && h > 0) listOf(SizeF(w.toFloat(), h.toFloat())) else emptyList()
        }
    }.getOrDefault(emptyList())

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
        val manager = manager(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val configure = PendingIntent.getActivity(
            context, kind.ordinal,
            Intent(context, WidgetConfigActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_KIND, kind.name),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return manager.requestPinAppWidget(ComponentName(context, providers.getValue(kind).first()), null, configure)
    }
}
