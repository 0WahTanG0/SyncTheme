package app.synctheme.mobile.tablet

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Планшет не нашёлся в сети или не ответил — тему можно отправить позже. */
class TabletUnreachable(message: String) : IOException(message)

/** Телефон: находит планшет в сети, связывается с ним по коду и отправляет ему тему. */
object TabletClient {
    /**
     * Планшеты с включённым приёмом в той же сети Wi‑Fi — каждый один раз: после перезапусков в сети
     * остаются старые записи того же планшета («… (2)», «… (3)»), они сводятся в одну.
     */
    fun discover(context: Context): Flow<List<AdbService>> = AdbDiscovery.services(context, TabletProtocol.SERVICE).map(::distinctDevices)

    /** Имя без « (2)», которое Android добавляет при совпадении имён в сети. */
    fun baseName(service: String): String = service.replace(Regex(" \\(\\d+\\)$"), "")

    /** По одной записи на устройство: та, что на постоянном порту приёма (новая), иначе — любая. */
    fun distinctDevices(list: List<AdbService>): List<AdbService> =
        list.groupBy { baseName(it.name) }.values.map { same ->
            (same.firstOrNull { it.port in TabletProtocol.PORTS } ?: same.last()).let { it.copy(name = baseName(it.name)) }
        }

    /** Связывание по коду с экрана планшета. */
    suspend fun pair(context: Context, host: String, port: Int, code: String, service: String? = null): Peer = withContext(Dispatchers.IO) {
        val salt = TabletProtocol.bytes(16)
        val key = TabletProtocol.keyFromCode(code.trim(), salt)
        val payload = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                write(salt)
                writeUTF(DeviceSettings.name(context))
                val proof = TabletProtocol.seal(key, TabletProtocol.proof())
                writeInt(proof.size)
                write(proof)
            }
        }.toByteArray()
        // Адрес из списка мог устареть (старая запись в сети) — тогда постоянные порты того же планшета.
        val socket = connect(context, host, port, 5_000)
            ?: TabletProtocol.PORTS.firstNotNullOfOrNull { if (it == port) null else connect(context, host, it, 1_500) }
            ?: throw TabletUnreachable(unreachable(context, "$host:$port"))
        val reply = exchange(socket, TabletProtocol.PAIR, payload, TabletProtocol.PAIR_OK)
        val input = DataInputStream(ByteArrayInputStream(TabletProtocol.open(key, reply)))
        val secret = ByteArray(32).also(input::readFully)
        val name = input.readUTF()
        Peer(name, secret, service, host, port).also {
            TabletStore.setTablet(context, it)
            TabletRetry.cancel(context)
        }
    }

    /**
     * Отправляет тему (с фото и масками глубины) на связанный планшет; ответ планшета — что он сделал.
     * Планшет не ответил — [TabletUnreachable], и телефон повторит сам ([TabletRetry]), если [retry].
     */
    suspend fun push(context: Context, theme: Theme, retry: Boolean = true): String = withContext(Dispatchers.IO) {
        val peer = TabletStore.tablet(context) ?: throw IOException("Планшет не подключён")
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
            val reply = exchange(socket, TabletProtocol.THEME, TabletProtocol.seal(TabletProtocol.key(peer.secret), body), TabletProtocol.DONE)
            if (host != peer.host || socket.port != peer.port) TabletStore.setTablet(context, peer.copy(host = host, port = socket.port))
            TabletRetry.cancel(context)
            val message = String(reply, Charsets.UTF_8)
            // Планшет поставил тему, но не всё: показываем его ответ целиком.
            val partial = "Ошибки" in message
            TabletStore.setPush(context, TabletPush(!partial, if (partial) message else "Тема «${theme.name}» на планшете", System.currentTimeMillis()))
            message
        } catch (e: TabletUnreachable) {
            if (!retry) throw e
            TabletRetry.schedule(context)
            val text = "Планшет не ответил — тема уйдёт на него, когда он появится в сети"
            TabletStore.setPush(context, TabletPush(false, text, System.currentTimeMillis(), pending = true))
            throw TabletUnreachable(text)
        } catch (e: IOException) {
            TabletStore.setPush(context, TabletPush(false, e.message ?: "Ошибка связи с планшетом", System.currentTimeMillis()))
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
     * Где планшет сейчас: сначала последний адрес, затем поиск в сети по имени (адрес мог смениться),
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
        if (found != null) attempt(found.host, found.port, 5_000)?.let { return it }
        for (port in TabletProtocol.PORTS) attempt(peer.host, port, 1_500)?.let { return it }
        throw TabletUnreachable(unreachable(context, peer.host ?: found?.host.orEmpty()))
    }

    /** Планшет в списке найденных: по имени службы без « (2)», которое Android добавляет при совпадении имён. */
    private fun AdbService.matches(peer: Peer): Boolean =
        baseName(name) == baseName(peer.service ?: "SyncTheme · ${peer.name}")

    /**
     * Соединение с планшетом — напрямую через Wi‑Fi, в которой он находится: с включённым VPN обычное
     * соединение уходит в VPN и до планшета в домашней сети не доходит (так же подключаемся и к часам).
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

    private fun exchange(socket: Socket, type: Int, payload: ByteArray, expect: Int): ByteArray = socket.use {
        // Планшет отвечает, когда поставит обои и обновит виджеты, — это до минуты.
        it.soTimeout = 90_000
        val out = DataOutputStream(it.getOutputStream().buffered())
        TabletProtocol.write(out, type, payload)
        val (replyType, reply) = TabletProtocol.read(DataInputStream(it.getInputStream().buffered()))
        if (replyType == TabletProtocol.ERROR) throw IOException(String(reply, Charsets.UTF_8))
        if (replyType != expect) throw IOException("Неожиданный ответ планшета")
        reply
    }

    private fun unreachable(context: Context, address: String): String = buildString {
        append("Планшет не отвечает ($address) — он в той же сети Wi‑Fi и на нём включено «Принимать тему с телефона»?")
        if (AdbLink.vpnActive(context)) append(" На телефоне включён VPN: если не помогает, выключите его на минуту или разрешите в нём локальную сеть.")
    }
}

/**
 * Повтор отправки, если планшет был недоступен (выключен экран, другая сеть, перезагрузка):
 * в течение нескольких часов телефон пробует снова и отправляет последнюю применённую тему.
 */
object TabletRetry {
    private const val WORK = "tablet_retry"

    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TabletRetryWorker>()
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

class TabletRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (TabletStore.tablet(app) == null) return Result.success()
        return try {
            TabletClient.push(app, ThemeRepository.get(app).applied.value, retry = false)
            Result.success()
        } catch (e: TabletUnreachable) {
            // Паузы растут вдвое (1, 2, 4… минут): восемь попыток — это около четырёх часов.
            if (runAttemptCount < 8) return Result.retry()
            TabletStore.setPush(app, TabletPush(false, "Планшет так и не ответил — нажмите «Применить», когда он будет в сети", System.currentTimeMillis()))
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}
