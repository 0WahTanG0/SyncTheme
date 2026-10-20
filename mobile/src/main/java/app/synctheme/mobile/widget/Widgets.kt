package app.synctheme.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.synctheme.R
import app.synctheme.core.ClockStyle
import app.synctheme.core.FontWeight
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.Colors
import java.util.concurrent.TimeUnit

/** Базовый виджет: строит RemoteViews из применённой темы. */
abstract class ThemedWidget : AppWidgetProvider() {
    abstract fun build(context: Context, theme: Theme): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context, ThemeRepository.get(context).applied.value)
        manager.updateAppWidget(ids, views)
    }

    protected fun RemoteViews.applySurface(theme: Theme) {
        setInt(R.id.widget_bg, "setColorFilter", Colors.widgetSurface(theme))
        setInt(R.id.widget_bg, "setImageAlpha", (theme.widgetOpacity.coerceIn(0f, 1f) * 255).toInt())
    }

    protected fun RemoteViews.onClick(context: Context, intent: Intent) {
        val pending = PendingIntent.getActivity(
            context, javaClass.name.hashCode(), intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setOnClickPendingIntent(R.id.widget_root, pending)
    }
}

class ClockWidget : ThemedWidget() {
    override fun build(context: Context, theme: Theme): RemoteViews {
        val layout = when (theme.fontWeight) {
            FontWeight.THIN -> R.layout.widget_clock_thin
            FontWeight.LIGHT -> R.layout.widget_clock_light
            FontWeight.REGULAR -> R.layout.widget_clock_regular
            FontWeight.BOLD -> R.layout.widget_clock_bold
        }
        return RemoteViews(context.packageName, layout).apply {
            applySurface(theme)
            val stacked = theme.clockStyle == ClockStyle.STACKED
            setViewVisibility(R.id.clock_time, if (stacked) android.view.View.GONE else android.view.View.VISIBLE)
            setViewVisibility(R.id.clock_hours, if (stacked) android.view.View.VISIBLE else android.view.View.GONE)
            setViewVisibility(R.id.clock_minutes, if (stacked) android.view.View.VISIBLE else android.view.View.GONE)
            setViewVisibility(R.id.clock_date, if (theme.showDate) android.view.View.VISIBLE else android.view.View.GONE)
            setTextColor(R.id.clock_time, theme.textColor)
            setTextColor(R.id.clock_hours, theme.textColor)
            setTextColor(R.id.clock_minutes, theme.accentColor)
            setTextColor(R.id.clock_date, theme.accentColor)
            onClick(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }
    }
}

class AnalogClockWidget : ThemedWidget() {
    override fun build(context: Context, theme: Theme): RemoteViews {
        val layout = if (theme.showSeconds) R.layout.widget_analog else R.layout.widget_analog_nosec
        return RemoteViews(context.packageName, layout).apply {
            applySurface(theme)
            val text = ColorStateList.valueOf(theme.textColor)
            setColorStateList(R.id.analog, "setDialTintList", ColorStateList.valueOf(theme.accentColor))
            setColorStateList(R.id.analog, "setHourHandTintList", text)
            setColorStateList(R.id.analog, "setMinuteHandTintList", text)
            if (theme.showSeconds) {
                setColorStateList(R.id.analog, "setSecondHandTintList", ColorStateList.valueOf(theme.accentColor))
            }
            onClick(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }
    }
}

class DateWidget : ThemedWidget() {
    override fun build(context: Context, theme: Theme): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_date).apply {
            applySurface(theme)
            setTextColor(R.id.date_weekday, theme.accentColor)
            setTextColor(R.id.date_day, theme.textColor)
            setTextColor(R.id.date_month, theme.textColor)
            val calendar = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                .appendPath(System.currentTimeMillis().toString()).build()
            onClick(context, Intent(Intent.ACTION_VIEW, calendar))
        }
}

class BatteryWidget : ThemedWidget() {
    override fun build(context: Context, theme: Theme): RemoteViews {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = bm.isCharging
        return RemoteViews(context.packageName, R.layout.widget_battery).apply {
            applySurface(theme)
            setProgressBar(R.id.battery_ring, 100, level, false)
            setColorStateList(R.id.battery_ring, "setProgressTintList", ColorStateList.valueOf(theme.accentColor))
            setColorStateList(
                R.id.battery_ring, "setProgressBackgroundTintList",
                ColorStateList.valueOf(Colors.withAlpha(theme.textColor, 0.25f)),
            )
            setTextViewText(R.id.battery_percent, "$level%")
            setTextViewText(R.id.battery_state, if (charging) "⚡ заряжается" else "")
            setTextColor(R.id.battery_percent, theme.textColor)
            setTextColor(R.id.battery_state, theme.accentColor)
            onClick(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        }
    }

    override fun onEnabled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BatteryRefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "battery_widget_refresh"
    }
}

/** Периодически обновляет виджет батареи (системного широковещательного события для этого нет). */
class BatteryRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        WidgetUpdater.update(applicationContext, BatteryWidget::class.java)
        return Result.success()
    }
}

object WidgetUpdater {
    val all = listOf(
        ClockWidget::class.java, AnalogClockWidget::class.java, DateWidget::class.java, BatteryWidget::class.java,
    )

    fun updateAll(context: Context) = all.forEach { update(context, it) }

    fun update(context: Context, cls: Class<out ThemedWidget>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val views = cls.getDeclaredConstructor().newInstance().build(context, ThemeRepository.get(context).applied.value)
        manager.updateAppWidget(ids, views)
    }

    fun installedCount(context: Context): Int {
        val manager = AppWidgetManager.getInstance(context)
        return all.sumOf { manager.getAppWidgetIds(ComponentName(context, it)).size }
    }

    /** Просит лаунчер добавить виджет на рабочий стол (One UI показывает диалог). */
    fun requestPin(context: Context, cls: Class<out ThemedWidget>): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, cls), null, null)
    }
}
