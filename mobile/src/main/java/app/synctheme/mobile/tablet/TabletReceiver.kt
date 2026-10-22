package app.synctheme.mobile.tablet

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
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/** Что видно на экране планшета: ждёт ли он тему, код связывания и что пришло последним. */
data class ReceiverState(
    val running: Boolean = false,
    val address: String? = null,
    /** Код связывания, пока открыто окно «Связать с телефоном». */
    val code: String? = null,
    val phone: String? = null,
    val last: String? = null,
)

/**
 * Планшет принимает тему с телефона: слушает порт в домашней сети и объявляет себя через mDNS.
 * Запускается службой [TabletSyncService] и живёт, пока включён приём.
 */
object TabletReceiver {
    private const val TAG = "TabletReceiver"
    private const val PAIRING_MS = 10 * 60_000L

    private val _state = MutableStateFlow(ReceiverState())
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var pairingUntil = 0L

    /** Порт, на котором планшет ждёт телефон (для тестов и ручного ввода адреса). */
    val port: Int get() = server?.localPort ?: 0

    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        val socket = ServerSocket(0)
        server = socket
        _state.value = _state.value.copy(running = true, address = address(socket.localPort), phone = TabletStore.phone(app)?.name)
        thread(name = "synctheme-tablet", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(name = "synctheme-tablet-client", isDaemon = true) { client.use { handle(app, it) } }
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
        _state.value = _state.value.copy(running = false, code = null)
    }

    /** Окно «Связать с телефоном»: новый код на 10 минут. */
    fun openPairing(): String {
        val code = TabletProtocol.code()
        pairingUntil = System.currentTimeMillis() + PAIRING_MS
        _state.value = _state.value.copy(code = code)
        return code
    }

    fun closePairing() {
        pairingUntil = 0
        _state.value = _state.value.copy(code = null)
    }

    private fun register(context: Context, port: Int) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "SyncTheme · ${DeviceSettings.name(context)}"
            serviceType = TabletProtocol.SERVICE
            setPort(port)
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
            val (type, payload) = TabletProtocol.read(input)
            when (type) {
                TabletProtocol.PAIR -> pair(context, payload, out)
                TabletProtocol.THEME -> {
                    val message = receive(context, payload)
                    TabletProtocol.write(out, TabletProtocol.DONE, message.toByteArray())
                }
                else -> throw IOException("Неизвестный запрос")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Request failed", e)
            runCatching { TabletProtocol.write(out, TabletProtocol.ERROR, (e.message ?: e.javaClass.simpleName).toByteArray()) }
        }
    }

    /** Телефон прислал код: если он верный — общий секрет для темы. */
    private fun pair(context: Context, payload: ByteArray, out: DataOutputStream) {
        val code = _state.value.code
        if (code == null || System.currentTimeMillis() > pairingUntil) {
            throw IOException("На планшете откройте «Связать с телефоном» и введите код оттуда")
        }
        val input = DataInputStream(ByteArrayInputStream(payload))
        val salt = ByteArray(16).also(input::readFully)
        val phoneName = input.readUTF()
        val proof = ByteArray(input.readInt()).also(input::readFully)
        val key = TabletProtocol.keyFromCode(code, salt)
        val ok = runCatching { TabletProtocol.open(key, proof).contentEquals(TabletProtocol.proof()) }.getOrDefault(false)
        if (!ok) throw IOException("Неверный код — проверьте цифры на планшете")
        val secret = TabletProtocol.bytes(32)
        TabletStore.setPhone(context, Peer(phoneName, secret))
        val reply = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                write(secret)
                writeUTF(DeviceSettings.name(context))
            }
        }.toByteArray()
        TabletProtocol.write(out, TabletProtocol.PAIR_OK, TabletProtocol.seal(key, reply))
        closePairing()
        _state.value = _state.value.copy(phone = phoneName, last = "Связан с телефоном «$phoneName»")
    }

    /** Тема с телефона: фото — в папку приложения, тема — на обои и виджеты планшета. */
    private fun receive(context: Context, sealed: ByteArray): String {
        val phone = TabletStore.phone(context) ?: throw IOException("Планшет не связан с телефоном — свяжите их в «Устройствах»")
        val body = runCatching { TabletProtocol.open(TabletProtocol.key(phone.secret), sealed) }
            .getOrElse { throw IOException("Планшет связан с другим телефоном — свяжите их заново") }
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
        val targets = ApplyTargets.load(context).copy(watch = false, tablet = false)
        val report = runBlocking { ThemeApplier(context).apply(theme, targets) }
        val message = buildString {
            append("Тема «${theme.name}» с телефона")
            if (report.done.isNotEmpty()) append(": ${report.done.joinToString()}")
            if (report.errors.isNotEmpty()) append(". Ошибки — ${report.errors.joinToString("; ")}")
        }
        _state.value = _state.value.copy(last = message)
        return message
    }

    /** Адрес планшета в домашней сети — на случай, если телефон не найдёт его сам. */
    private fun address(port: Int): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress?.let { "$it:$port" }
    }.getOrNull()
}
