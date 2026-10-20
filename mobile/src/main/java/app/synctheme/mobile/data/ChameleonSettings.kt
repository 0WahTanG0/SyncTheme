package app.synctheme.mobile.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.synctheme.core.ChameleonMode
import app.synctheme.core.DayPart
import app.synctheme.core.Mood
import app.synctheme.core.Sky
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Настройка «Хамелеона» и его фоновая работа. */
object ChameleonSettings {
    private const val WORK = "chameleon"
    private const val WORK_NOW = "chameleon-now"

    fun mode(context: Context): ChameleonMode = runCatching {
        ChameleonMode.valueOf(prefs(context).getString("chameleon", null)!!)
    }.getOrDefault(ChameleonMode.OFF)

    fun setMode(context: Context, mode: ChameleonMode) {
        prefs(context).edit().putString("chameleon", mode.name).apply()
        schedule(context, mode)
        // Сразу пересчитать настроение и поставить тему.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ChameleonWorker>().build(),
        )
    }

    /** Раз в полчаса проверяет время суток и погоду; при старте приложения — восстанавливает расписание. */
    fun schedule(context: Context, mode: ChameleonMode = mode(context)) {
        val wm = WorkManager.getInstance(context)
        if (mode == ChameleonMode.OFF) {
            wm.cancelUniqueWork(WORK)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ChameleonWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    /** Настроение сейчас: часть суток по часам телефона, небо — по последней погоде. */
    suspend fun currentMood(context: Context, mode: ChameleonMode, now: Calendar = Calendar.getInstance()): Mood? {
        if (mode == ChameleonMode.OFF) return null
        val part = if (mode.byTime) DayPart.of(now.get(Calendar.HOUR_OF_DAY)) else null
        val sky = if (mode.byWeather) {
            val weather = WeatherRepository(context)
            val cached = weather.cached
            val fresh = if (cached == null || System.currentTimeMillis() - cached.updatedAt > 45 * 60_000L) {
                runCatching { weather.refresh() }.getOrNull() ?: cached
            } else cached
            fresh?.let { Sky.of(it.now.code) }
        } else null
        return Mood(part, sky)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

/** Пересчитывает настроение «Хамелеона» и, если оно сменилось, ставит обновлённую тему на устройства. */
class ChameleonWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = ThemeRepository.get(ctx)
        val mode = ChameleonSettings.mode(ctx)
        val mood = ChameleonSettings.currentMood(ctx, mode)
        if (mood == repo.mood.value) return Result.success()
        repo.setMood(mood)
        ThemeApplier(ctx).apply(repo.applied.value, ApplyTargets.load(ctx))
        return Result.success()
    }
}
