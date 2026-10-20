package app.synctheme.mobile.watch

import android.content.Context
import android.net.Uri
import app.synctheme.core.SyncProtocol
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Насколько время часов и телефона расходится с точным.
 * watchMs — часы относительно телефона (плюс — часы спешат), phoneMs — телефон относительно интернета.
 */
data class TimeCheckResult(
    val watchMs: Long? = null,
    /** Погрешность замера часов (половина задержки Bluetooth туда-обратно). */
    val watchErrorMs: Long = 0,
    val phoneMs: Long? = null,
    /** Часы не ответили: на них старая версия SyncTheme или они не на связи. */
    val watchSilent: Boolean = false,
    val checkedAt: Long = System.currentTimeMillis(),
) {
    /** Часы относительно точного времени (если удалось узнать и то и другое). */
    val watchVsTrueMs get() = if (watchMs != null && phoneMs != null) watchMs + phoneMs else null
}

object TimeCheck {
    /** Расхождение, которое уже заметно глазом на секундах. */
    const val NOTICEABLE_MS = 1500L

    suspend fun run(context: Context, nodeId: String?): TimeCheckResult {
        val phone = runCatching { internetOffset() }.getOrNull()
        val watch = nodeId?.let { runCatching { watchOffset(context, it) }.getOrNull() }
        return TimeCheckResult(
            watchMs = watch?.first, watchErrorMs = watch?.second ?: 0,
            phoneMs = phone, watchSilent = nodeId != null && watch == null,
        )
    }

    /**
     * Как NTP: t0 — отправили ping, tw — время часов в ответе, t1 — получили ответ.
     * Расхождение = tw − (t0 + t1) / 2. Из нескольких замеров берём самый быстрый.
     */
    private suspend fun watchOffset(context: Context, nodeId: String): Pair<Long, Long>? {
        val client = Wearable.getMessageClient(context)
        val samples = mutableListOf<Pair<Long, Long>>()
        repeat(4) {
            val reply = CompletableDeferred<Pair<Long, Long>>()
            val listener = MessageClient.OnMessageReceivedListener { event ->
                if (event.path == SyncProtocol.PATH_PONG) {
                    String(event.data).toLongOrNull()?.let { reply.complete(it to System.currentTimeMillis()) }
                }
            }
            client.addListener(listener, Uri.parse("wear://*${SyncProtocol.PATH_PONG}"), MessageClient.FILTER_LITERAL).await()
            try {
                val t0 = System.currentTimeMillis()
                client.sendMessage(nodeId, SyncProtocol.PATH_PING, ByteArray(0)).await()
                val (tw, t1) = withTimeoutOrNull(4000) { reply.await() } ?: return@repeat
                samples += (tw - (t0 + t1) / 2) to (t1 - t0) / 2
            } finally {
                client.removeListener(listener)
            }
        }
        return samples.minByOrNull { it.second }
    }

    /** Телефон относительно точного времени: заголовок Date ответа сервера (точность около полсекунды). */
    private suspend fun internetOffset(): Long? = withContext(Dispatchers.IO) {
        val samples = (0 until 3).mapNotNull {
            val conn = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.useCaches = false
                val t0 = System.currentTimeMillis()
                conn.connect()
                conn.responseCode
                val t1 = System.currentTimeMillis()
                val date = conn.getHeaderFieldDate("Date", 0L).takeIf { it > 0 } ?: return@mapNotNull null
                // Сервер округляет до секунды вниз — в среднем это полсекунды.
                (date + 500 - (t0 + t1) / 2) to (t1 - t0)
            } finally {
                conn.disconnect()
            }
        }
        samples.minByOrNull { it.second }?.first
    }

    /** «на 5,2 с» для расхождения в миллисекундах. */
    fun seconds(ms: Long): String = String.format(java.util.Locale("ru"), "%.1f с", abs(ms) / 1000.0)
}
