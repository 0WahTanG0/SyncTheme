package app.synctheme.mobile.tablet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.synctheme.R
import app.synctheme.mobile.ui.MainActivity

/**
 * Планшет ждёт тему с телефона и в фоне: служба с тихим уведомлением держит [TabletReceiver].
 * Работает, только пока на планшете включён приём («Ещё» → «Устройства»).
 */
class TabletSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Приём темы с телефона", NotificationManager.IMPORTANCE_MIN).apply {
                description = "SyncTheme на планшете ждёт тему с телефона"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("SyncTheme ждёт тему с телефона")
            .setContentText("Нажмите «Применить» на телефоне — тема появится и здесь")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(ID, notification)
            TabletReceiver.start(this)
        }.onFailure {
            Log.w("TabletSyncService", "Cannot start receiving", it)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        TabletReceiver.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "tablet_sync"
        private const val ID = 7

        /** Включает или выключает приём — как выбрано в настройках. */
        fun ensure(context: Context) {
            val intent = Intent(context, TabletSyncService::class.java)
            if (TabletStore.receiving(context)) runCatching { context.startForegroundService(intent) }
            else context.stopService(intent)
        }
    }
}

/** После перезагрузки и обновления приложения планшет снова ждёт тему. */
class TabletBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TabletSyncService.ensure(context)
        }
    }
}
