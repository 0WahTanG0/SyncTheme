package app.synctheme.mobile.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeApplier
import app.synctheme.mobile.data.ThemeRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Телефон просит подключиться: его имя и код сверки (на телефоне — такой же). */
data class LinkRequest(val phone: String, val code: String)

/** Что видно на экране устройства: ждёт ли оно тему, с каким телефоном связано, что пришло последним. */
data class ReceiverState(
    val running: Boolean = false,
    val address: String? = null,
    val phone: String? = null,
    val last: String? = null,
    /** Телефон ждёт ответа «Подключить / Отклонить». */
    val request: LinkRequest? = null,
)

/**
 * Устройство (планшет, телевизор, другой телефон) принимает тему с телефона: слушает порт в домашней
 * сети и объявляет себя через mDNS. Запускается службой [LinkService] и живёт, пока включён приём.
 */
object LinkReceiver {
    private const val TAG = "LinkReceiver"

    /** Сколько ждать «Подключить» на устройстве. */
    private const val CONFIRM_MS = 120_000L

    private val _state = MutableStateFlow(ReceiverState())
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var decision: CompletableDeferred<Boolean>? = null

    /** Порт, на котором устройство ждёт телефон (для тестов и ручного ввода адреса). */
    val port: Int get() = server?.localPort ?: 0

    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        val socket = bind()
        server = socket
        _state.value = _state.value.copy(running = true, address = address(socket.localPort), phone = LinkStore.phone(app)?.name)
        thread(name = "synctheme-link", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(name = "synctheme-link-client", isDaemon = true) { client.use { handle(app, it) } }
            }
        }
        register(app, socket.localPort)
    }

    @Synchronized
    fun stop(context: Context) {
        registration?.let { runCatching { context.getSystemService(NsdManager::class.java).unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        server = null
        answer(false)
        _state.value = _state.value.copy(running = false, request = null)
    }

    /** Ответ на запрос подключения: «Подключить» или «Отклонить». */
    fun answer(approve: Boolean) {
        decision?.complete(approve)
    }

    /** Первый свободный из постоянных портов; если все заняты — любой. */
    private fun bind(): ServerSocket = LinkProtocol.PORTS.firstNotNullOfOrNull { port ->
        runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
    } ?: ServerSocket(0)

    private fun register(context: Context, port: Int) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "SyncTheme · ${DeviceSettings.name(context)}"
            serviceType = LinkProtocol.SERVICE
            setPort(port)
            // По TXT-записи телефон показывает тип устройства и узнаёт его после перезапусков.
            runCatching {
                setAttribute(LinkProtocol.ATTR_KIND, DeviceSettings.kind(context).name)
                setAttribute(LinkProtocol.ATTR_ID, LinkStore.id(context))
            }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { Log.w(TAG, "mDNS unavailable", it) }
    }

    private fun handle(context: Context, socket: Socket) {
        socket.soTimeout = 30_000
        val input = DataInputStream(socket.getInputStream().buffered())
        val out = DataOutputStream(socket.getOutputStream().buffered())
        try {
            val (type, payload) = LinkProtocol.read(input)
            when (type) {
                LinkProtocol.HELLO -> link(context, payload, out)
                LinkProtocol.THEME -> {
                    val message = receive(context, payload)
                    LinkProtocol.write(out, LinkProtocol.DONE, message.toByteArray())
                }
                else -> throw IOException("Неизвестный запрос")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Request failed", e)
            runCatching { LinkProtocol.write(out, LinkProtocol.ERROR, (e.message ?: e.javaClass.simpleName).toByteArray()) }
        }
    }

    /**
     * Телефон просит подключиться: обмен ключами, код сверки на обоих экранах, и только после
     * «Подключить» здесь — общий ключ сохраняется и телефон получает подтверждение.
     */
    private fun link(context: Context, payload: ByteArray, out: DataOutputStream) {
        val hello = DataInputStream(ByteArrayInputStream(payload))
        val phoneName = hello.readUTF()
        val phoneId = hello.readUTF()
        val phoneKey = ByteArray(hello.readInt()).also(hello::readFully)
        val name = DeviceSettings.name(context)
        val pending = CompletableDeferred<Boolean>()
        synchronized(this) {
            if (decision?.isActive == true) throw IOException("«$name» сейчас подключает другой телефон — попробуйте через минуту")
            decision = pending
        }
        try {
            val keys = LinkProtocol.keyPair()
            val own = keys.public.encoded
            val shared = LinkProtocol.shared(keys.private, phoneKey)
            LinkProtocol.write(out, LinkProtocol.KEY, ByteArrayOutputStream().also { b ->
                DataOutputStream(b).apply {
                    writeInt(own.size)
                    write(own)
                    writeUTF(name)
                    writeUTF(DeviceSettings.kind(context).name)
                    writeUTF(LinkStore.id(context))
                }
            }.toByteArray())
            val code = LinkProtocol.code(shared, phoneKey, own)
            _state.value = _state.value.copy(request = LinkRequest(phoneName, code))
            LinkService.showRequest(context, phoneName, code)
            val approved = runBlocking { withTimeoutOrNull(CONFIRM_MS) { pending.await() } }
            if (approved != true) {
                throw IOException(if (approved == false) "На «$name» отказались подключать телефон" else "На «$name» не подтвердили подключение — нажмите там «Подключить»")
            }
            val secret = LinkProtocol.linkSecret(shared, phoneKey, own)
            LinkStore.setPhone(context, Peer(phoneId, phoneName, DeviceKind.PHONE, secret))
            LinkProtocol.write(out, LinkProtocol.LINKED, LinkProtocol.seal(LinkProtocol.key(secret), LinkProtocol.proof()))
            _state.value = _state.value.copy(phone = phoneName, last = "Подключён к телефону «$phoneName»")
        } finally {
            pending.cancel()
            synchronized(this) { if (decision === pending) decision = null }
            _state.value = _state.value.copy(request = null)
            LinkService.hideRequest(context)
        }
    }

    /** Тема с телефона: фото — в папку приложения, тема — на обои и виджеты (на телевизоре — в заставку). */
    private fun receive(context: Context, sealed: ByteArray): String {
        val device = DeviceSettings.name(context)
        val phone = LinkStore.phone(context) ?: throw IOException("«$device» не подключён к телефону — подключите его в «Устройствах»")
        val body = runCatching { LinkProtocol.open(LinkProtocol.key(phone.secret), sealed) }
            .getOrElse { throw IOException("«$device» подключён к другому телефону — подключите его заново") }
        val input = DataInputStream(ByteArrayInputStream(body))
        val json = String(ByteArray(input.readInt()).also(input::readFully), Charsets.UTF_8)
        val repo = ThemeRepository.get(context)
        repeat(input.readInt()) {
            val name = input.readUTF()
            val data = ByteArray(input.readInt()).also(input::readFully)
            // Только имена файлов из папки фото — никаких путей.
            if (!name.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*"))) throw IOException("Недопустимое имя файла")
            repo.photoFile(name).writeBytes(data)
        }
        val theme = Theme.fromJson(json)
        repo.setDraft(theme)
        val message = if (DeviceSettings.isTv(context)) {
            // У телевизора нет обоев и виджетов — тема идёт в заставку SyncTheme.
            repo.setApplied(theme)
            "Тема «${theme.name}» — в заставке телевизора"
        } else {
            val targets = ApplyTargets.load(context).copy(watch = false, tablet = false)
            val report = runBlocking { ThemeApplier(context).apply(theme, targets) }
            // Ничего не получилось — телефон должен узнать об этом как об ошибке, а не «готово».
            if (report.done.isEmpty() && report.errors.isNotEmpty()) {
                val text = "«$device» не смог поставить тему: ${report.errors.joinToString("; ")}"
                _state.value = _state.value.copy(last = text)
                throw IOException(text)
            }
            buildString {
                append("Тема «${theme.name}» с телефона")
                if (report.done.isNotEmpty()) append(": ${report.done.joinToString()}")
                if (report.errors.isNotEmpty()) append(". Ошибки — ${report.errors.joinToString("; ")}")
            }
        }
        _state.value = _state.value.copy(last = message)
        LinkService.showLast(context, message)
        return message
    }

    /** Адрес устройства в домашней сети — на случай, если телефон не найдёт его сам. */
    private fun address(port: Int): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress?.let { "$it:$port" }
    }.getOrNull()
}
