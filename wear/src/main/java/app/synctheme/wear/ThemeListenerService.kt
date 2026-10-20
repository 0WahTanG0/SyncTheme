package app.synctheme.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** Play Services будит этот сервис, когда телефон публикует новый циферблат. */
class ThemeListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        // Копируем данные: буфер освобождается сразу после выхода из метода.
        val latest = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == SyncPaths.PATH_WATCHFACE }
            .map { DataMapItem.fromDataItem(it.dataItem).dataMap to it.dataItem.uri.host }
            .lastOrNull() ?: return
        // Метод вызывается в фоновом потоке, поэтому блокировать его можно.
        runBlocking { ThemeSync.handle(applicationContext, latest.first, latest.second) }
    }

    /** Проверка времени с телефона: отвечаем сразу, без лишней работы, чтобы задержка была минимальной. */
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != SyncPaths.PATH_PING) return
        val now = System.currentTimeMillis().toString().toByteArray()
        Wearable.getMessageClient(applicationContext).sendMessage(event.sourceNodeId, SyncPaths.PATH_PONG, now)
    }
}
