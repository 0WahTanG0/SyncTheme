package app.synctheme.mobile.watch

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Последний отчёт часов об установке циферблата. */
data class WatchReport(
    val state: String = "",
    val theme: String = "",
    val message: String = "",
    val active: Boolean = false,
    val receivedAt: Long = 0L,
    /** Версия приложения на часах (0 — неизвестна). */
    val appVersion: Long = 0L,
) {
    val text: String
        get() = when (state) {
            "INSTALLING" -> "Часы устанавливают «$theme»…"
            "INSTALLED" -> if (active) "«$theme» на циферблате часов ✓" else "«$theme» установлен на часах"
            "NEEDS_PERMISSION" -> "Откройте SyncTheme на часах и разрешите менять циферблат"
            "NEEDS_MANUAL_SELECT" -> "Зажмите циферблат на часах и выберите «SyncTheme»"
            "UNSUPPORTED" -> "Часам нужен Wear OS 6 (обновите ПО часов)"
            "ERROR" -> "Ошибка на часах: $message"
            else -> ""
        }
}

object WatchStatusStore {
    private val flow = MutableStateFlow(WatchReport())
    private var loaded = false

    fun observe(context: Context): StateFlow<WatchReport> {
        if (!loaded) {
            loaded = true
            val p = context.getSharedPreferences("watch_status", Context.MODE_PRIVATE)
            p.getString("json", null)?.let { flow.value = parse(it, p.getLong("at", 0L)) }
        }
        return flow.asStateFlow()
    }

    fun onReport(context: Context, json: String) {
        val now = System.currentTimeMillis()
        flow.value = parse(json, now)
        context.getSharedPreferences("watch_status", Context.MODE_PRIVATE).edit()
            .putString("json", json).putLong("at", now).apply()
    }

    fun markPending(theme: String) {
        flow.value = flow.value.copy(state = "SENT", theme = theme, message = "", receivedAt = System.currentTimeMillis())
    }

    private fun parse(json: String, at: Long): WatchReport = runCatching {
        val o = JSONObject(json)
        WatchReport(
            o.optString("state"), o.optString("theme"), o.optString("message"), o.optBoolean("active"), at,
            o.optLong("appVersion"),
        )
    }.getOrDefault(WatchReport())
}

class WatchStatusService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WatchStatusStore.onReport(applicationContext, String(event.data))
    }
}
