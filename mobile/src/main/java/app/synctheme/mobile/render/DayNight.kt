package app.synctheme.mobile.render

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.synctheme.core.Theme
import app.synctheme.core.ThemeMode
import app.synctheme.core.resolved
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.wallpaper.ClockWallpaper
import app.synctheme.mobile.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Светлая и тёмная версии темы на этом устройстве: какая видна сейчас и смена при переключении
 * оформления телефона. Живые обои и цвета виджетов (Android 12+) меняются сами; обои картинкой
 * и виджеты-картинки перерисовываются здесь — при возврате в приложение, по смене настройки тёмной
 * темы и раз в полчаса (тёмная тема по расписанию настройку не меняет).
 */
object DayNight {
    private const val CHANGES = "night_changes"
    private const val PERIODIC = "night_check"

    /** На телефоне сейчас тёмное оформление. */
    fun isNight(context: Context): Boolean =
        (context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Тема в том виде, в каком она сейчас на экране. */
    fun of(context: Context, theme: Theme): Theme = theme.resolved(isNight(context))

    /** Циферблат всегда тёмный: «Как в системе» на часах — тёмная версия (и меньше расход батареи). */
    fun forWatch(theme: Theme): Theme = theme.resolved(night = true)

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Обои картинкой только что поставлены: запоминаем, какие из них наши и для какого оформления.
     * Если потом обои сменят в другом приложении, их номер станет другим — и мы их не тронем.
     */
    fun rememberWallpapers(context: Context, home: Boolean, lock: Boolean) {
        val manager = WallpaperManager.getInstance(context)
        fun id(which: Int) = runCatching { manager.getWallpaperId(which) }.getOrDefault(0)
        val edit = prefs(context).edit().putBoolean(KEY_NIGHT, isNight(context))
        if (home) edit.putInt(KEY_HOME, id(WallpaperManager.FLAG_SYSTEM))
        if (lock) edit.putInt(KEY_LOCK, id(WallpaperManager.FLAG_LOCK))
        edit.apply()
    }

    /** Наши обои картинкой, которые всё ещё стоят: рабочий стол и экран блокировки. */
    private fun ourWallpapers(context: Context): Pair<Boolean, Boolean> {
        val p = prefs(context)
        val manager = WallpaperManager.getInstance(context)
        fun id(which: Int) = runCatching { manager.getWallpaperId(which) }.getOrDefault(0)
        val home = p.getInt(KEY_HOME, 0).let { it > 0 && it == id(WallpaperManager.FLAG_SYSTEM) } && !ClockWallpaper.isActive(context)
        // −1 — у блокировки нет своих обоев, там те же, что на рабочем столе.
        val lock = p.getInt(KEY_LOCK, NONE).let { it != NONE && it == id(WallpaperManager.FLAG_LOCK) && (it > 0 || home) } &&
            !ClockWallpaper.isActiveOnLock(context)
        return home to lock
    }

    /** Оформление телефона сменилось, а тема — «Как в системе»: перерисовать обои картинкой и виджеты. */
    suspend fun refresh(context: Context) {
        val night = isNight(context)
        val p = prefs(context)
        val last = if (p.contains(KEY_NIGHT)) p.getBoolean(KEY_NIGHT, night) else null
        if (last == night) return
        p.edit().putBoolean(KEY_NIGHT, night).apply()
        val theme = ThemeRepository.get(context).applied.value
        if (last == null || theme.mode != ThemeMode.AUTO) return
        runCatching { WidgetUpdater.updateAll(context) }
        val (home, lock) = ourWallpapers(context)
        if (home || lock) runCatching { WallpaperApplier.apply(context, theme, home, lock) }
    }

    /** Следить за оформлением телефона, только пока применена тема «Как в системе». */
    fun ensure(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (ThemeRepository.get(context).applied.value.mode != ThemeMode.AUTO) {
            wm.cancelUniqueWork(CHANGES)
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        watchSetting(context, ExistingWorkPolicy.KEEP)
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<NightWorker>(30, TimeUnit.MINUTES).build())
    }

    /** Одноразовая задача: срабатывает, когда меняют настройку тёмной темы, и ставит себя снова. */
    internal fun watchSetting(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(Settings.Secure.getUriFor("ui_night_mode"), false)
            .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHANGES, policy, OneTimeWorkRequestBuilder<NightChangeWorker>().setConstraints(constraints).build(),
        )
    }

    private const val KEY_NIGHT = "night_last"
    private const val KEY_HOME = "night_wp_home"
    private const val KEY_LOCK = "night_wp_lock"
    private const val NONE = Int.MIN_VALUE
}

class NightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DayNight.refresh(applicationContext)
        return Result.success()
    }
}

class NightChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DayNight.refresh(applicationContext)
        // Задача сейчас выполняется, поэтому новую ставим в очередь за ней.
        if (ThemeRepository.get(applicationContext).applied.value.mode == ThemeMode.AUTO) {
            DayNight.watchSetting(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
