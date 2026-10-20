package app.synctheme.mobile.install

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlin.math.min
import org.conscrypt.Conscrypt

/**
 * ADB-подключение к часам по «Отладке по Wi‑Fi» (TLS): один поток, одна команда за раз и таймаут на
 * каждое чтение. В libadb ожидание ответа на команду не ограничено, а ответ, пришедший раньше, чем
 * его начали ждать, теряется — тогда установка висит на «читаю сведения о часах». Здесь такого нет:
 * пакеты читаются по порядку в том же потоке, который отправил команду. Сопряжение по коду (SPAKE2)
 * по-прежнему делает libadb.
 */
internal class AdbLink private constructor(private val plain: Socket, private val log: (String) -> Unit) : Closeable {
    private var socket: Socket = plain
    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private var maxData = MAX_DATA
    private var lastId = 0

    /** Строка, которой часы представились: модель и возможности adbd. */
    var banner = ""
        private set

    /** Модель часов из приветствия (ro.product.model). */
    val model get() = Regex("ro\\.product\\.model=([^;]*)").find(banner)?.groupValues?.get(1).orEmpty()

    private class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    private fun handshake(host: String, port: Int, key: PrivateKey, cert: X509Certificate) {
        input = plain.getInputStream()
        output = plain.getOutputStream()
        send(A_CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
        var p = receive(HANDSHAKE_MS)
        if (p.command == A_STLS) {
            send(A_STLS, STLS_VERSION, 0)
            val tls = sslContext(key, cert).socketFactory.createSocket(plain, host, port, true) as SSLSocket
            tls.useClientMode = true
            tls.soTimeout = HANDSHAKE_MS
            try {
                tls.startHandshake()
                socket = tls
                input = tls.inputStream
                output = tls.outputStream
                // В TLS 1.3 часы проверяют ключ телефона уже после рукопожатия: отказ приходит при первом чтении.
                p = receive(HANDSHAKE_MS)
            } catch (e: SSLException) {
                // Часы не приняли ключ телефона: сопряжение забыто или его не было.
                throw AdbPairingRequiredException("Часы не узнали телефон (${e.message})")
            }
            log("TLS: ${tls.session.protocol}")
        }
        when (p.command) {
            A_CNXN -> Unit
            A_AUTH -> throw AdbPairingRequiredException("Часы просят подтвердить ключ — нужно сопряжение по коду")
            else -> throw IOException("Неожиданный ответ часов: ${name(p.command)}")
        }
        if (p.arg1 > 0) maxData = min(p.arg1, MAX_DATA)
        banner = String(p.payload, Charsets.UTF_8).trimEnd('\u0000')
    }

    /**
     * Выполняет службу ADB («shell:…», «exec:…») и возвращает её вывод. [stdin] отправляется частями
     * по одной за подтверждение часов, как требует протокол.
     */
    fun run(destination: String, timeoutMs: Long, stdin: ByteArray? = null, onProgress: (Float) -> Unit = {}): String {
        val local = ++lastId
        val what = destination.substringAfter(':').substringBefore(' ')
        send(A_OPEN, local, 0, "$destination\u0000".toByteArray())
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        val out = ByteArrayOutputStream()
        var remote = 0
        var ready = false
        var sent = 0
        while (true) {
            val left = (deadline - System.nanoTime()) / 1_000_000
            val p = try {
                if (left <= 0) throw SocketTimeoutException()
                receive(left.toInt())
            } catch (e: SocketTimeoutException) {
                throw WatchUnreachableException(
                    if (remote == 0) "Часы не ответили на команду ($what)" else "Часы перестали отвечать ($what)",
                )
            } catch (e: IOException) {
                throw WatchUnreachableException("Связь с часами прервалась ($what): ${e.message ?: e.javaClass.simpleName}")
            }
            if (p.arg1 != local) {
                log("Пропускаю пакет ${name(p.command)} для потока ${p.arg1}")
                continue
            }
            when (p.command) {
                A_OKAY -> {
                    remote = p.arg0
                    ready = true
                }
                A_WRTE -> {
                    out.write(p.payload)
                    send(A_OKAY, local, p.arg0)
                }
                A_CLSE -> {
                    if (remote == 0) throw InstallException("Часы отклонили команду ($what)")
                    return out.toString(Charsets.UTF_8.name())
                }
                else -> log("Неожиданный пакет ${name(p.command)}")
            }
            if (ready && stdin != null && sent < stdin.size) {
                val n = min(min(maxData, CHUNK), stdin.size - sent)
                send(A_WRTE, local, remote, stdin, sent, n)
                sent += n
                ready = false
                onProgress(sent.toFloat() / stdin.size)
            }
        }
    }

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray = EMPTY, offset: Int = 0, length: Int = data.size) {
        var sum = 0
        for (i in offset until offset + length) sum += data[i].toInt() and 0xFF
        val header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(command.inv())
        output.write(header.array())
        if (length > 0) output.write(data, offset, length)
        output.flush()
    }

    private fun receive(timeoutMs: Int): Packet {
        socket.soTimeout = timeoutMs.coerceAtLeast(1)
        val header = ByteArray(HEADER).also(::readFully)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = b.int
        val arg0 = b.int
        val arg1 = b.int
        val length = b.int
        b.int // контрольная сумма: с версии протокола 0x01000001 не проверяется
        if (b.int != command.inv()) throw IOException("повреждённый пакет")
        if (length < 0 || length > MAX_DATA) throw IOException("пакет слишком большой ($length)")
        return Packet(command, arg0, arg1, ByteArray(length).also(::readFully))
    }

    private fun readFully(buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) throw EOFException("часы закрыли соединение")
            read += n
        }
    }

    override fun close() {
        runCatching { socket.close() }
        runCatching { plain.close() }
    }

    companion object {
        private const val A_CNXN = 0x4e584e43
        private const val A_STLS = 0x534c5453
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
        private const val VERSION = 0x01000001
        private const val STLS_VERSION = 0x01000000
        private const val MAX_DATA = 1024 * 1024
        private const val CHUNK = 64 * 1024
        private const val HEADER = 24
        private const val CONNECT_MS = 5_000
        private const val HANDSHAKE_MS = 15_000
        private val EMPTY = ByteArray(0)

        private fun name(command: Int) =
            String(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array(), Charsets.US_ASCII)

        /** Подключается к часам и проходит TLS-рукопожатие с ключом, который часы запомнили при сопряжении. */
        fun open(host: String, port: Int, network: Network?, key: PrivateKey, cert: X509Certificate, log: (String) -> Unit): AdbLink {
            val socket = Socket()
            if (network != null) {
                runCatching { network.bindSocket(socket) }
                    .onSuccess { log("Подключаюсь напрямую через Wi‑Fi") }
                    .onFailure { log("Не удалось обойти VPN: ${it.message ?: it.javaClass.simpleName}") }
            }
            socket.tcpNoDelay = true
            try {
                socket.connect(InetSocketAddress(host, port), CONNECT_MS)
            } catch (e: IOException) {
                runCatching { socket.close() }
                throw WatchUnreachableException("Часы не отвечают по Wi‑Fi ($host:$port): ${e.javaClass.simpleName}")
            }
            val link = AdbLink(socket, log)
            try {
                link.handshake(host, port, key, cert)
            } catch (e: SocketTimeoutException) {
                link.close()
                throw WatchUnreachableException("Часы не завершили подключение за ${HANDSHAKE_MS / 1000} с")
            } catch (e: Throwable) {
                link.close()
                throw e
            }
            return link
        }

        /**
         * Сеть Wi‑Fi, в которой находятся часы. Сокет привязывается к ней, чтобы соединение шло напрямую,
         * а не через VPN или мобильный интернет. null — такой сети нет (например, часы в точке доступа телефона).
         */
        fun wifiFor(context: Context, host: String): Network? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val target = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet4Address ?: return null
            @Suppress("DEPRECATION")
            return cm.allNetworks.firstOrNull { n ->
                val caps = cm.getNetworkCapabilities(n)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    cm.getLinkProperties(n)?.linkAddresses.orEmpty().any { la ->
                        val a = la.address
                        a is Inet4Address && sameSubnet(a, target, la.prefixLength)
                    }
            }
        }

        fun vpnActive(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
            return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

        private fun sameSubnet(a: Inet4Address, b: Inet4Address, prefix: Int): Boolean {
            fun bits(x: Inet4Address) = ByteBuffer.wrap(x.address).int
            val mask = if (prefix <= 0) 0 else -1 shl (32 - prefix.coerceAtMost(32))
            return bits(a) and mask == bits(b) and mask
        }

        private fun sslContext(key: PrivateKey, cert: X509Certificate): SSLContext {
            val provider = runCatching { Conscrypt.newProvider() }.getOrNull()
            val context = if (provider != null) SSLContext.getInstance("TLSv1.3", provider) else SSLContext.getInstance("TLSv1.3")
            context.init(arrayOf(KeyManager(key, cert)), arrayOf(AnyServer), SecureRandom())
            return context
        }
    }

    /** Клиентский ключ — тот, что часы запомнили при сопряжении; он один, поэтому выбирать нечего. */
    private class KeyManager(private val key: PrivateKey, private val cert: X509Certificate) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?) = arrayOf(ALIAS)
        override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?) = arrayOf(cert)
        override fun getPrivateKey(alias: String?) = key

        private companion object {
            const val ALIAS = "adb"
        }
    }

    /**
     * Сертификат часов самоподписанный и проверить его не по чему — так же поступает и сам adb.
     * Подлинность обеспечивает сопряжение: часы пускают только телефон, чей ключ запомнили.
     */
    @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object AnyServer : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
