package app.synctheme.mobile.link

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.synctheme.core.Theme
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.install.AdbDiscovery
import app.synctheme.mobile.install.AdbLink
import app.synctheme.mobile.install.AdbService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Устройство не нашлось в сети или не ответило — тему можно отправить позже. */
class LinkUnreachable(message: String) : IOException(message)

/** Устройство, найденное в сети: тип и id — из его TXT-записи. */
data class FoundDevice(val service: AdbService, val name: String, val kind: DeviceKind, val id: String?)

/** Телефон: находит устройства в сети, подключает их и отправляет им тему. */
object LinkClient {
    /**
     * Устройства с включённым приёмом в той же сети Wi‑Fi — каждое один раз: после перезапусков в сети
     * остаются старые записи того же устройства («… (2)», «… (3)»), они сводятся в одну.
     */
    fun discover(context: Context): Flow<List<FoundDevice>> = AdbDiscovery.services(context, LinkProtocol.SERVICE).map(::distinctDevices)

    /** Имя без « (2)», которое Android добавляет при совпадении имён в сети. */
    fun baseName(service: String): String = service.replace(Regex(" \\(\\d+\\)$"), "")

    /** По одной записи на устройство (по id, иначе по имени): та, что на постоянном порту (живая), иначе — любая. */
    fun distinctDevices(list: List<AdbService>): List<FoundDevice> =
        list.groupBy { it.attributes[LinkProtocol.ATTR_ID] ?: baseName(it.name) }.values.map { same ->
            val s = same.firstOrNull { it.port in LinkProtocol.PORTS } ?: same.last()
            FoundDevice(
                s.copy(name = baseName(s.name)), baseName(s.name).removePrefix("SyncTheme · "),
                DeviceKind.of(s.attributes[LinkProtocol.ATTR_KIND]), s.attributes[LinkProtocol.ATTR_ID],
            )
        }

    /**
     * Подключение без ввода кода: обмен ключами, затем устройство показывает код сверки и ждёт «Подключить».
     * [onCode] — тот же код на телефоне (и имя устройства), пока ждём подтверждения.
     */
    suspend fun link(context: Context, host: String, port: Int, service: String?, onCode: (code: String, device: String) -> Unit): Peer =
        withContext(Dispatchers.IO) {
            // Адрес из списка мог устареть (старая запись в сети) — тогда постоянные порты того же устройства.
            val socket = connect(context, host, port, 5_000)
                ?: LinkProtocol.PORTS.firstNotNullOfOrNull { if (it == port) null else connect(context, host, it, 1_500) }
                ?: throw LinkUnreachable(unreachable(context, "$host:$port"))
            socket.use {
                it.soTimeout = 15_000
                val out = DataOutputStream(it.getOutputStream().buffered())
                val input = DataInputStream(it.getInputStream().buffered())
                val keys = LinkProtocol.keyPair()
                val own = keys.public.encoded
                LinkProtocol.write(out, LinkProtocol.HELLO, ByteArrayOutputStream().also { b ->
                    DataOutputStream(b).apply {
                        writeUTF(DeviceSettings.name(context))
                        writeUTF(LinkStore.id(context))
                        writeInt(own.size)
                        write(own)
                    }
                }.toByteArray())
                val key = DataInputStream(ByteArrayInputStream(expect(input, LinkProtocol.KEY)))
                val deviceKey = ByteArray(key.readInt()).also(key::readFully)
                val name = key.readUTF()
                val kind = DeviceKind.of(key.readUTF())
                val id = key.readUTF()
                val shared = LinkProtocol.shared(keys.private, deviceKey)
                val secret = LinkProtocol.linkSecret(shared, own, deviceKey)
                onCode(LinkProtocol.code(shared, own, deviceKey), name)
                // Ждём, пока на устройстве нажмут «Подключить».
                it.soTimeout = 130_000
                val proof = runCatching { LinkProtocol.open(LinkProtocol.key(secret), expect(input, LinkProtocol.LINKED)) }
                    .getOrElse { e -> throw if (e is IOException) e else IOException("Устройство ответило не тем ключом — подключите заново") }
                if (!proof.contentEquals(LinkProtocol.proof())) throw IOException("Устройство ответило не тем ключом — подключите заново")
                Peer(id, name, kind, secret, service, host, it.port).also { peer ->
                    LinkStore.savePeer(context, peer)
                    LinkStore.setPush(context, peer.id, null)
                }
            }
        }

    /** Тема на все включённые устройства сразу; результат — по каждому. */
    suspend fun pushAll(context: Context, theme: Theme): List<Pair<Peer, Result<String>>> = coroutineScope {
        LinkStore.peers(context).filter { it.enabled }
            .map { peer -> async { peer to runCatching { push(context, theme, peer) } } }
            .awaitAll()
    }

    /**
     * Отправляет тему (с фото и масками глубины) на устройство; ответ — что оно сделало.
     * Не ответило — [LinkUnreachable], и телефон повторит сам ([LinkRetry]), если [retry].
     */
    suspend fun push(context: Context, theme: Theme, peer: Peer, retry: Boolean = true): String = withContext(Dispatchers.IO) {
        try {
            val socket = locate(context, peer)
            val host = socket.inetAddress.hostAddress
            val body = ByteArrayOutputStream().also { b ->
                DataOutputStream(b).apply {
                    val json = theme.toJson().toByteArray(Charsets.UTF_8)
                    writeInt(json.size)
                    write(json)
                    val files = files(context, theme)
                    writeInt(files.size)
                    for (f in files) {
                        writeUTF(f.name)
                        val bytes = f.readBytes()
                        writeInt(bytes.size)
                        write(bytes)
                    }
                }
            }.toByteArray()
            val reply = exchange(socket, LinkProtocol.THEME, LinkProtocol.seal(LinkProtocol.key(peer.secret), body), LinkProtocol.DONE)
            if (host != peer.host || socket.port != peer.port) LinkStore.updatePeer(context, peer.copy(host = host, port = socket.port))
            val message = String(reply, Charsets.UTF_8)
            // Устройство поставило тему, но не всё: показываем его ответ целиком.
            val partial = "Ошибки" in message
            LinkStore.setPush(context, peer.id, LinkPush(!partial, if (partial) message else "Тема «${theme.name}» на месте", System.currentTimeMillis()))
            message
        } catch (e: LinkUnreachable) {
            if (!retry) throw e
            LinkRetry.schedule(context)
            val text = "Не ответил — тема уйдёт, когда он появится в сети"
            LinkStore.setPush(context, peer.id, LinkPush(false, text, System.currentTimeMillis(), pending = true))
            throw LinkUnreachable(text)
        } catch (e: IOException) {
            LinkStore.setPush(context, peer.id, LinkPush(false, e.message ?: "Ошибка связи", System.currentTimeMillis()))
            throw e
        }
    }

    /** Фото обоев (рабочий стол и блокировка) и маски глубины к ним. */
    private fun files(context: Context, theme: Theme) = ThemeRepository.get(context).let { repo ->
        listOfNotNull(theme.background.photoFile, theme.lockBackground?.photoFile).distinct()
            .flatMap { listOf(it, it.substringBeforeLast('.') + ".mask.png") }
            .map(repo::photoFile).filter { it.exists() }
    }

    /**
     * Где устройство сейчас: сначала последний адрес, затем поиск в сети (адрес мог смениться),
     * затем постоянные порты приёма на последнем адресе.
     */
    private suspend fun locate(context: Context, peer: Peer): Socket {
        val tried = HashSet<Pair<String, Int>>()
        fun attempt(host: String?, port: Int, timeout: Int): Socket? =
            if (host == null || port <= 0 || !tried.add(host to port)) null else connect(context, host, port, timeout)

        attempt(peer.host, peer.port, 2_500)?.let { return it }
        // Поиск может и не запуститься (нет mDNS) — тогда поток просто закончится без находки.
        val found = withTimeoutOrNull(8_000) {
            discover(context).firstOrNull { list -> list.any { it.matches(peer) } }
        }?.firstOrNull { it.matches(peer) }
        if (found != null) attempt(found.service.host, found.service.port, 5_000)?.let { return it }
        for (port in LinkProtocol.PORTS) attempt(peer.host, port, 1_500)?.let { return it }
        throw LinkUnreachable(unreachable(context, peer.host ?: found?.service?.host.orEmpty()))
    }

    /** Это устройство в списке найденных: по id, а у старых версий — по имени службы без « (2)». */
    private fun FoundDevice.matches(peer: Peer): Boolean =
        id?.let { it == peer.id } ?: (baseName(service.name) == baseName(peer.service ?: "SyncTheme · ${peer.name}"))

    /**
     * Соединение с устройством — напрямую через Wi‑Fi, в которой оно находится: с включённым VPN обычное
     * соединение уходит в VPN и до устройства в домашней сети не доходит (так же подключаемся и к часам).
     */
    private fun connect(context: Context, host: String, port: Int, timeout: Int): Socket? {
        val socket = Socket()
        AdbLink.wifiFor(context, host)?.let { wifi -> runCatching { wifi.bindSocket(socket) } }
        return try {
            socket.connect(InetSocketAddress(host, port), timeout)
            socket
        } catch (e: IOException) {
            runCatching { socket.close() }
            null
        }
    }

    private fun expect(input: DataInputStream, type: Int): ByteArray {
        val (replyType, reply) = LinkProtocol.read(input)
        if (replyType == LinkProtocol.ERROR) throw IOException(String(reply, Charsets.UTF_8))
        if (replyType != type) throw IOException("Неожиданный ответ устройства")
        return reply
    }

    private fun exchange(socket: Socket, type: Int, payload: ByteArray, expect: Int): ByteArray = socket.use {
        // Устройство отвечает, когда поставит обои и обновит виджеты, — это до минуты.
        it.soTimeout = 90_000
        val out = DataOutputStream(it.getOutputStream().buffered())
        LinkProtocol.write(out, type, payload)
        expect(DataInputStream(it.getInputStream().buffered()), expect)
    }

    private fun unreachable(context: Context, address: String): String = buildString {
        append("Устройство не отвечает ($address) — оно в той же сети Wi‑Fi и на нём включено «Принимать тему с телефона»?")
        if (AdbLink.vpnActive(context)) append(" На телефоне включён VPN: если не помогает, выключите его на минуту или разрешите в нём локальную сеть.")
    }
}

/**
 * Повтор отправки на устройства, которые были недоступны (выключены, в другой сети, перезагружались):
 * в течение нескольких часов телефон пробует снова и отправляет последнюю применённую тему.
 */
object LinkRetry {
    private const val WORK = "link_retry"

    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<LinkRetryWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }
}

class LinkRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val status = LinkStore.push(app).value
        val waiting = LinkStore.peers(app).filter { it.enabled && status[it.id]?.pending == true }
        if (waiting.isEmpty()) return Result.success()
        val theme = ThemeRepository.get(app).applied.value
        var away = false
        for (peer in waiting) {
            try {
                LinkClient.push(app, theme, peer, retry = false)
            } catch (e: LinkUnreachable) {
                away = true
            } catch (e: Exception) {
                // Другая ошибка (отклонено, другая версия) — повтор не поможет, её покажет строка устройства.
            }
        }
        if (!away) return Result.success()
        // Паузы растут вдвое (1, 2, 4… минут): восемь попыток — это около четырёх часов.
        if (runAttemptCount < 8) return Result.retry()
        LinkStore.peers(app).filter { LinkStore.push(app).value[it.id]?.pending == true }.forEach {
            LinkStore.setPush(app, it.id, LinkPush(false, "Так и не ответил — нажмите «Применить», когда он будет в сети", System.currentTimeMillis()))
        }
        return Result.success()
    }
}
