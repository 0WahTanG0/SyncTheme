package app.synctheme.mobile.install

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Найденная в сети служба (ADB часов или SyncTheme на устройстве); [attributes] — её TXT-запись,
 * [addresses] — все адреса устройства (у телевизора их бывает два: Wi‑Fi и кабель).
 */
data class AdbService(
    val name: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String> = emptyMap(),
    val addresses: List<String> = listOf(host),
)

/**
 * Ищет часы с включённой «Отладкой по Wi‑Fi» через mDNS (как это делает Android Studio):
 * _adb-tls-pairing._tcp — открыт экран «Сопряжение нового устройства»,
 * _adb-tls-connect._tcp — отладка включена и можно подключаться.
 */
object AdbDiscovery {
    const val PAIRING = "_adb-tls-pairing._tcp"
    const val CONNECT = "_adb-tls-connect._tcp"

    /** Google TV и Android TV объявляют себя для пульта в приложении Google TV — так находится адрес телевизора. */
    const val TV = "_androidtvremote2._tcp"

    fun services(context: Context, type: String): Flow<List<AdbService>> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val found = ConcurrentHashMap<String, AdbService>()
        val ownAddresses = localAddresses()
        // Ответы NsdManager — прямо в его потоке. Свой пул потоков здесь опасен: после отмены поиска
        // система ещё присылает «callback снят», и закрытый пул роняет приложение (RejectedExecutionException).
        val executor = Executor { it.run() }
        val callbacks = ConcurrentHashMap<String, Any>()

        fun publish() {
            trySend(found.values.sortedBy { it.host })
        }

        fun resolved(name: String, addresses: List<InetAddress>, port: Int, attributes: Map<String, ByteArray?>?) {
            val all = (addresses.filterIsInstance<Inet4Address>() + addresses).mapNotNull { it.hostAddress }.distinct()
            val host = all.firstOrNull() ?: return
            // Свой телефон (если на нём тоже включена отладка) не показываем.
            if (all.any { it in ownAddresses } || port <= 0) return
            val txt = attributes.orEmpty().mapNotNull { (k, v) -> v?.let { k to String(it, Charsets.UTF_8) } }.toMap()
            found[name] = AdbService(name, host, port, txt, all.filterNot { ':' in it }.ifEmpty { listOf(host) })
            publish()
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                publish()
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val callback = object : NsdManager.ServiceInfoCallback {
                        override fun onServiceUpdated(updated: NsdServiceInfo) =
                            resolved(updated.serviceName, updated.hostAddresses, updated.port, updated.attributes)
                        override fun onServiceLost() {
                            found.remove(info.serviceName)
                            publish()
                        }
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    }
                    if (callbacks.putIfAbsent(info.serviceName, callback) == null) {
                        runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching {
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                @Suppress("DEPRECATION")
                                resolved(info.serviceName, listOfNotNull(info.host), info.port, info.attributes)
                            }
                        })
                    }
                }
            }
        }

        publish()
        if (runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }.isFailure) {
            // Поиск в сети недоступен — остаётся ручной ввод адреса.
            close()
        }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it as NsdManager.ServiceInfoCallback) } }
            }
        }
    }

    private fun localAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }
            .toSet()
    }.getOrDefault(emptySet())
}
