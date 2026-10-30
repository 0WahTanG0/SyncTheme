package app.synctheme.mobile.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.synctheme.R
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Новая версия — без ручной проверки: дважды в день в фоне смотрим, не вышла ли она, и сообщаем
 * уведомлением (один раз на каждую версию). В приложении о ней же говорит плашка сверху.
 */
object UpdateCheck {
    private const val WORK = "update_check"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 7301

    fun ensure(context: Context) {
        // На телевизоре обновления ставятся с телефона.
        if (DeviceSettings.isTv(context)) return
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }

    /** Уведомление «Вышла новая версия»; по нажатию открывается приложение с плашкой «Обновить». */
    fun notify(context: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Обновления", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Сообщает, когда вышла новая версия SyncTheme"
                },
            )
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.notify(
                NOTIFICATION_ID,
                Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle("Вышла SyncTheme ${info.versionName}")
                    .setContentText("Нажмите, чтобы обновить — темы и настройки сохранятся")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updater = Updater(applicationContext)
        if (updater.repo.isBlank()) return Result.success()
        val info = runCatching { updater.check() }.getOrElse { return Result.retry() } ?: return Result.success()
        if (info.versionCode > updater.notifiedCode) {
            UpdateCheck.notify(applicationContext, info)
            updater.notifiedCode = info.versionCode
        }
        return Result.success()
    }
}
