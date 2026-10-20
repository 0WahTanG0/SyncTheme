package app.synctheme.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Обработка DataItem с телефона: скачать APK, установить, отчитаться. */
object ThemeSync {
    private const val TAG = "ThemeSync"
    private val mutex = Mutex()

    suspend fun handle(context: Context, map: DataMap, sourceNodeId: String?, force: Boolean = false) = mutex.withLock {
        val installer = WatchFaceInstaller(context)
        val timestamp = map.getLong(SyncPaths.KEY_TIMESTAMP)
        if (!force && timestamp <= installer.installedTimestamp) return@withLock
        val asset = map.getAsset(SyncPaths.KEY_APK) ?: return@withLock
        val token = map.getString(SyncPaths.KEY_TOKEN) ?: return@withLock
        val name = map.getString(SyncPaths.KEY_THEME_NAME).orEmpty()
        val activate = map.getBoolean(SyncPaths.KEY_ACTIVATE, true)

        val apk = File(context.filesDir, "watchface.apk")
        Wearable.getDataClient(context).getFdForAsset(asset).await().inputStream.use { input ->
            apk.outputStream().use { input.copyTo(it) }
        }
        val status = installer.install(apk, token, name, timestamp, activate)
        sendStatus(context, sourceNodeId, status)
    }

    /** На случай, если сервис пропустил событие (часы были выключены и т.п.). */
    suspend fun checkPending(context: Context, force: Boolean = false) {
        val buffer = Wearable.getDataClient(context).dataItems.await()
        val newest = try {
            buffer.filter { it.uri.path == SyncPaths.PATH_WATCHFACE }
                .map { DataMapItem.fromDataItem(it).dataMap to it.uri.host }
                .maxByOrNull { (map, _) -> map.getLong(SyncPaths.KEY_TIMESTAMP) }
        } finally {
            buffer.release()
        }
        newest?.let { (map, node) -> handle(context, map, node, force) }
    }

    suspend fun sendStatus(context: Context, nodeId: String?, status: InstallStatus) {
        val payload = JSONObject()
            .put("state", status.state.name)
            .put("theme", status.themeName)
            .put("message", status.message)
            .put("active", status.isActive)
            .put("ts", status.timestamp)
            .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
            .toString().toByteArray()
        runCatching {
            val client = Wearable.getMessageClient(context)
            val nodes = nodeId?.let { listOf(it) }
                ?: Wearable.getNodeClient(context).connectedNodes.await().map { it.id }
            nodes.forEach { client.sendMessage(it, SyncPaths.PATH_STATUS, payload).await() }
        }.onFailure { Log.w(TAG, "Status not delivered", it) }
    }
}
