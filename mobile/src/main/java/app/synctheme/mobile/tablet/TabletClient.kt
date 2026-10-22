package app.synctheme.mobile.tablet

import android.content.Context
import app.synctheme.core.Theme
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.install.AdbDiscovery
import app.synctheme.mobile.install.AdbService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Телефон: находит планшет в сети, связывается с ним по коду и отправляет ему тему. */
object TabletClient {
    /** Планшеты с включённым приёмом в той же сети Wi‑Fi. */
    fun discover(context: Context): Flow<List<AdbService>> = AdbDiscovery.services(context, TabletProtocol.SERVICE)

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
        val reply = exchange(host, port, TabletProtocol.PAIR, payload, TabletProtocol.PAIR_OK)
        val input = DataInputStream(ByteArrayInputStream(TabletProtocol.open(key, reply)))
        val secret = ByteArray(32).also(input::readFully)
        val name = input.readUTF()
        Peer(name, secret, service, host, port).also { TabletStore.setTablet(context, it) }
    }

    /** Отправляет тему (с фото и масками глубины) на связанный планшет; ответ планшета — что он сделал. */
    suspend fun push(context: Context, theme: Theme): String = withContext(Dispatchers.IO) {
        val peer = TabletStore.tablet(context) ?: throw IOException("Планшет не подключён")
        val (host, port) = locate(context, peer)
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
        val reply = exchange(host, port, TabletProtocol.THEME, TabletProtocol.seal(TabletProtocol.key(peer.secret), body), TabletProtocol.DONE)
        if (host != peer.host || port != peer.port) TabletStore.setTablet(context, peer.copy(host = host, port = port))
        String(reply, Charsets.UTF_8)
    }

    /** Фото обоев (рабочий стол и блокировка) и маски глубины к ним. */
    private fun files(context: Context, theme: Theme) = ThemeRepository.get(context).let { repo ->
        listOfNotNull(theme.background.photoFile, theme.lockBackground?.photoFile).distinct()
            .flatMap { listOf(it, it.substringBeforeLast('.') + ".mask.png") }
            .map(repo::photoFile).filter { it.exists() }
    }

    /** Где планшет сейчас: ищем его в сети по имени, иначе — последний известный адрес. */
    private suspend fun locate(context: Context, peer: Peer): Pair<String, Int> {
        val found = peer.service?.let { service ->
            withTimeoutOrNull(4_000) { discover(context).first { list -> list.any { it.name == service } } }
                ?.first { it.name == service }
        }
        if (found != null) return found.host to found.port
        val host = peer.host ?: throw IOException("Планшет не найден в сети — откройте SyncTheme на планшете")
        return host to peer.port
    }

    private fun exchange(host: String, port: Int, type: Int, payload: ByteArray, expect: Int): ByteArray {
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(host, port), 5_000)
            } catch (e: IOException) {
                throw IOException("Планшет не отвечает — он в той же сети Wi‑Fi и SyncTheme на нём принимает тему?")
            }
            // Планшет отвечает, когда поставит обои и обновит виджеты, — это до минуты.
            socket.soTimeout = 90_000
            val out = DataOutputStream(socket.getOutputStream().buffered())
            TabletProtocol.write(out, type, payload)
            val (replyType, reply) = TabletProtocol.read(DataInputStream(socket.getInputStream().buffered()))
            if (replyType == TabletProtocol.ERROR) throw IOException(String(reply, Charsets.UTF_8))
            if (replyType != expect) throw IOException("Неожиданный ответ планшета")
            return reply
        }
    }
}
