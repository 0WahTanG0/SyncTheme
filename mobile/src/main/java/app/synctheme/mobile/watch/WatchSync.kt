package app.synctheme.mobile.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.synctheme.core.SyncProtocol
import app.synctheme.core.Theme
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await

/** Состояние связи с часами. */
data class WatchConnection(
    val checked: Boolean = false,
    /** Подключённые устройства Wear OS (часы). */
    val connectedNames: List<String> = emptyList(),
    /** Часы, на которых установлено приложение SyncTheme. */
    val appNodeIds: List<String> = emptyList(),
    val appNodeName: String? = null,
    val error: String? = null,
) {
    val hasWatch get() = connectedNames.isNotEmpty()
    val hasApp get() = appNodeIds.isNotEmpty()
}

class WatchSync(private val context: Context) {
    suspend fun connection(): WatchConnection = runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val capable = Wearable.getCapabilityClient(context)
            .getCapability(SyncProtocol.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes
        // Запасной признак: часы уже присылали отчёт — значит, приложение на них есть.
        val reported = WatchStatusStore.knownAppNodes(context)
        val withApp = (capable + nodes.filter { it.id in reported }).distinctBy { it.id }
        WatchConnection(
            checked = true,
            connectedNames = nodes.map { it.displayName },
            appNodeIds = withApp.map { it.id },
            appNodeName = withApp.firstOrNull()?.displayName,
        )
    }.getOrElse { WatchConnection(checked = true, error = it.message ?: it.toString()) }

    /**
     * Публикует циферблат как DataItem: Play Services доставит его на часы,
     * даже если они сейчас не на связи, — как только подключатся.
     */
    suspend fun push(theme: Theme, built: WatchFaceBuilder.Result) {
        val request = PutDataMapRequest.create(SyncProtocol.PATH_WATCHFACE).apply {
            dataMap.putAsset(SyncProtocol.KEY_APK, Asset.createFromBytes(built.apk))
            dataMap.putString(SyncProtocol.KEY_TOKEN, built.token)
            dataMap.putString(SyncProtocol.KEY_THEME_NAME, theme.name)
            dataMap.putLong(SyncProtocol.KEY_TIMESTAMP, System.currentTimeMillis())
            dataMap.putBoolean(SyncProtocol.KEY_ACTIVATE, true)

        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    /** Открывает приложение SyncTheme на часах (для выдачи разрешения). */
    suspend fun openOnWatch(nodeId: String?) {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(SyncProtocol.WATCH_OPEN_URI))
        RemoteActivityHelper(context).startRemoteActivity(intent, nodeId).await()
    }
}
