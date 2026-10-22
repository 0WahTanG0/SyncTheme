package app.synctheme.mobile.widget

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.synctheme.mobile.data.WeatherRepository
import java.util.concurrent.TimeUnit

/** Фоновые обновления: погода (30 мин), календарь (час и при изменениях), батарея (15 мин), небо часов (30 мин). */
object WidgetScheduler {
    private const val WEATHER = "weather_refresh"
    private const val WEATHER_NOW = "weather_refresh_now"
    private const val CALENDAR = "calendar_refresh"
    private const val CALENDAR_CHANGES = "calendar_changes"
    private const val BATTERY = "battery_widget_refresh"
    private const val CLOCK_SKY = "clock_sky_refresh"

    fun ensure(context: Context) {
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        if (WidgetUpdater.ids(context, WidgetKind.WEATHER).isNotEmpty()) {
            wm.enqueueUniquePeriodicWork(
                WEATHER, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        } else {
            wm.cancelUniqueWork(WEATHER)
        }

        if (WidgetUpdater.ids(context, WidgetKind.CALENDAR).isNotEmpty()) {
            wm.enqueueUniquePeriodicWork(
                CALENDAR, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CalendarWorker>(1, TimeUnit.HOURS).build(),
            )
            watchCalendar(context, ExistingWorkPolicy.KEEP)
        } else {
            wm.cancelUniqueWork(CALENDAR)
            wm.cancelUniqueWork(CALENDAR_CHANGES)
        }

        if (WidgetUpdater.ids(context, WidgetKind.BATTERY).isNotEmpty()) {
            wm.enqueueUniquePeriodicWork(
                BATTERY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork(BATTERY)
        }

        // Небо на часах меняется с временем суток — им достаточно обновляться раз в полчаса.
        if (WidgetUpdater.placed(context).any { it.kind == WidgetKind.CLOCK && it.config.sky }) {
            wm.enqueueUniquePeriodicWork(
                CLOCK_SKY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ClockSkyWorker>(30, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork(CLOCK_SKY)
        }
    }

    /** Обновить погоду прямо сейчас (после выбора города или добавления виджета). */
    fun refreshWeatherNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WEATHER_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Одноразовая задача, которая срабатывает при изменении событий календаря и перезапускает себя. */
    internal fun watchCalendar(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CALENDAR_CHANGES, policy,
            OneTimeWorkRequestBuilder<CalendarChangeWorker>().setConstraints(constraints).build(),
        )
    }
}

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = WeatherRepository(applicationContext)
        if (repo.place == null) return Result.success()
        return runCatching { repo.refresh() }.fold(
            onSuccess = {
                WidgetUpdater.update(applicationContext, WidgetKind.WEATHER)
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }
}

class CalendarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.update(applicationContext, WidgetKind.CALENDAR)
        return Result.success()
    }
}

class CalendarChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.update(applicationContext, WidgetKind.CALENDAR)
        // Задача сейчас выполняется, поэтому новую ставим в очередь за ней.
        WidgetScheduler.watchCalendar(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

class BatteryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.update(applicationContext, WidgetKind.BATTERY)
        return Result.success()
    }
}

class ClockSkyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.update(applicationContext, WidgetKind.CLOCK)
        return Result.success()
    }
}
