package app.synctheme.mobile.link

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
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.tv.TvActivity
import app.synctheme.mobile.ui.MainActivity

/**
 * Устройство ждёт тему с телефона и в фоне: служба с тихим уведомлением держит [LinkReceiver].
 * Работает, только пока на нём включён приём («Настройки» → «Устройства»; на телевизоре — всегда).
 */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Каждый startForegroundService требует startForeground — вызываем при каждом запуске.
        runCatching {
            val notification = notification(this, LinkReceiver.state.value.last)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(ID, notification)
            LinkReceiver.start(this)
        }.onFailure {
            Log.w("LinkService", "Cannot start receiving", it)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LinkReceiver.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "tablet_sync"
        private const val ID = 7
        private const val REQUESTS = "link_requests"
        private const val REQUEST_ID = 8
        const val ACTION_APPROVE = "app.synctheme.link.APPROVE"
        const val ACTION_DECLINE = "app.synctheme.link.DECLINE"

        /** Главный экран приложения на этом устройстве: на телевизоре — свой. */
        private fun openApp(context: Context): PendingIntent {
            val screen = if (DeviceSettings.isTv(context)) Intent(context, TvActivity::class.java) else Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(context, 0, screen, PendingIntent.FLAG_IMMUTABLE)
        }

        /** Телефон просит подключиться — заметное уведомление с кнопками, если приложение сейчас закрыто. */
        fun showRequest(context: Context, phone: String, code: String) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(REQUESTS, "Подключение телефона", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Телефон просит подключить это устройство к SyncTheme"
                    },
                )
                fun action(action: String, title: String) = Notification.Action.Builder(
                    null, title,
                    PendingIntent.getBroadcast(context, action.hashCode(), Intent(context, LinkActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE),
                ).build()
                manager.notify(
                    REQUEST_ID,
                    Notification.Builder(context, REQUESTS)
                        .setSmallIcon(R.drawable.ic_launcher_monochrome)
                        .setContentTitle("Подключить телефон «$phone»?")
                        .setContentText("Код на телефоне должен быть таким же: ${code.chunked(3).joinToString(" ")}")
                        .setContentIntent(openApp(context))
                        .addAction(action(ACTION_APPROVE, "Подключить"))
                        .addAction(action(ACTION_DECLINE, "Отклонить"))
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }

        fun hideRequest(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java).cancel(REQUEST_ID) }
        }

        private fun notification(context: Context, last: String?): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Приём темы с телефона", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "SyncTheme ждёт тему с телефона"
                    setShowBadge(false)
                },
            )
            val open = openApp(context)
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("SyncTheme ждёт тему с телефона")
                .setContentText(last ?: "Нажмите «Применить» на телефоне — тема появится и здесь")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }

        /** В уведомлении — какая тема пришла последней. */
        fun showLast(context: Context, text: String) {
            if (!LinkStore.receiving(context)) return
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, notification(context, text)) }
        }

        /** Включает или выключает приём — как выбрано в настройках. */
        fun ensure(context: Context) {
            val intent = Intent(context, LinkService::class.java)
            if (LinkStore.receiving(context)) runCatching { context.startForegroundService(intent) }
            else context.stopService(intent)
        }
    }
}

/** «Подключить» / «Отклонить» из уведомления о запросе телефона. */
class LinkActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LinkService.ACTION_APPROVE -> LinkReceiver.answer(true)
            LinkService.ACTION_DECLINE -> LinkReceiver.answer(false)
        }
    }
}

/** После перезагрузки и обновления приложения устройство снова ждёт тему. */
class LinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LinkService.ensure(context)
        }
    }
}
