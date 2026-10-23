package app.synctheme.mobile

import app.synctheme.mobile.install.AdbLink
import app.synctheme.mobile.install.AdbService
import app.synctheme.mobile.install.InstallTarget
import app.synctheme.mobile.install.InstallerUi
import app.synctheme.mobile.install.WatchUnreachableException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Своё ADB-подключение против «часов» в тесте: TLS с ключом из сопряжения, команды, передача APK
 * с подтверждением каждой части и таймаут, если часы молчат.
 */
class AdbLinkTest {
    private val phone = keyPair()
    private val phoneCert = cert(phone, "SyncTheme")

    @Test
    fun runsCommandsAndStreamsApk() {
        val apk = ByteArray(300_000) { (it * 31).toByte() }
        var received = ByteArray(0)
        val watch = FakeWatch(trusted = phoneCert) { dest, io ->
            when {
                dest.startsWith("shell:getprop") -> io.reply("36\nsamsung\nSM-L310\nnosdcard,watch\npackage:app.synctheme versionCode:5\n")
                dest.startsWith("exec:cmd package install") -> {
                    received = io.readStdin(dest.substringAfter("-S ").toInt())
                    io.reply("Success\n")
                }
                else -> io.reject()
            }
        }
        val link = AdbLink.open("127.0.0.1", watch.port, null, phone.private, phoneCert, log = {})
        link.use {
            assertEquals("SM-L310", it.model)
            val info = it.run("shell:getprop ro.build.version.sdk; getprop ro.product.brand", 5_000)
            assertTrue(info.startsWith("36\nsamsung"))
            var progress = 0f
            val result = it.run("exec:cmd package install -r -d -S ${apk.size}", 10_000, apk) { p -> progress = p }
            assertEquals("Success\n", result)
            assertEquals(1f, progress)
        }
        assertArrayEquals(apk, received)
    }

    /** Часы приняли подключение, но молчат на команду: ошибка через таймаут, а не вечное ожидание. */
    @Test
    fun silentWatchTimesOut() {
        val watch = FakeWatch(trusted = phoneCert) { _, _ -> Thread.sleep(5_000) }
        val link = AdbLink.open("127.0.0.1", watch.port, null, phone.private, phoneCert, log = {})
        val start = System.currentTimeMillis()
        try {
            link.run("shell:getprop ro.build.version.sdk", 1_000)
            fail("ожидался таймаут")
        } catch (e: WatchUnreachableException) {
            assertTrue(e.message!!.contains("getprop"))
        } finally {
            link.close()
        }
        assertTrue(System.currentTimeMillis() - start < 3_000)
    }

    /** Порт закрыт (отладку выключили или её порт сменился) — так и написано, без кода ошибки. */
    @Test
    fun closedPortIsExplained() {
        val port = ServerSocket(0).use { it.localPort }
        try {
            AdbLink.open("127.0.0.1", port, null, phone.private, phoneCert, log = {}).close()
            fail("порт закрыт — ожидалась ошибка")
        } catch (e: WatchUnreachableException) {
            assertTrue(e.message, e.message!!.contains("127.0.0.1:$port") && e.message!!.contains("порт закрыт"))
        }
    }

    /** Телевизор объявляет два адреса (Wi‑Fi и кабель) — установщик пробует оба, начиная с выбранного. */
    @Test
    fun deviceAddressesIncludeEveryAnnouncedOne() {
        val ui = InstallerUi(
            target = InstallTarget.TV,
            connect = listOf(AdbService("adb-tv", "192.168.31.187", 45103, addresses = listOf("192.168.31.187", "192.168.31.239"))),
            tvs = listOf(AdbService("Google TV", "192.168.31.187", 6466)),
        )
        assertEquals(listOf("192.168.31.187", "192.168.31.239"), ui.addressesOf("192.168.31.187"))
        assertEquals(listOf("192.168.31.187"), ui.hosts)
    }

    /** Ключ телефона часам неизвестен (сопряжение забыто) — просим сопряжение заново. */
    @Test
    fun unknownKeyNeedsPairing() {
        val other = keyPair()
        val watch = FakeWatch(trusted = cert(other, "other")) { _, io -> io.reply("") }
        try {
            AdbLink.open("127.0.0.1", watch.port, null, phone.private, phoneCert, log = {}).close()
            fail("ожидался запрос сопряжения")
        } catch (_: AdbPairingRequiredException) {
        }
    }

    /**
     * Телевизор без «Отладки по Wi‑Fi» («Отладка по сети», порт 5555): ключ телефона ему незнаком —
     * телефон отправляет свой открытый ключ, на экране «Разрешить отладку?». В следующий раз
     * хватает подписи, и спрашивать не нужно.
     */
    @Test
    fun tvConfirmsKeyOnScreenOnce() {
        val tv = FakeTv(phone)
        var asked = 0
        AdbLink.open("127.0.0.1", tv.port, null, phone.private, phoneCert, {}) { asked++ }.use {
            assertEquals("Tuvio TV", it.model)
        }
        assertEquals("спросили один раз", 1, asked)
        assertTrue("телевизор запомнил ключ", tv.trusted)
        AdbLink.open("127.0.0.1", tv.port, null, phone.private, phoneCert, {}) { asked++ }.close()
        assertEquals("второй раз — без вопроса", 1, asked)
    }

    /** Телевизор «слушает» ключ adb: подпись токена — PKCS#1 v1.5 поверх SHA-1, ключ — в формате adbkey.pub. */
    private class FakeTv(private val phone: KeyPair) {
        private val server = ServerSocket(0)
        val port = server.localPort
        @Volatile var trusted = false

        init {
            thread(isDaemon = true) {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching { socket.use(::serve) }
                }
            }
        }

        private fun serve(socket: Socket) {
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
                val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(command).putInt(arg0).putInt(arg1).putInt(data.size).putInt(0).putInt(command.inv())
                output.write(header.array() + data)
                output.flush()
            }
            fun read(): Packet {
                val b = ByteBuffer.wrap(ByteArray(24).also { input.readFully(it) }).order(ByteOrder.LITTLE_ENDIAN)
                val command = b.int
                val arg0 = b.int
                val arg1 = b.int
                return Packet(command, arg0, arg1, ByteArray(b.int).also { input.readFully(it) })
            }
            check(read().command == CNXN)
            val token = ByteArray(20).also { SecureRandom().nextBytes(it) }
            send(AUTH, 1, 0, token)
            val signature = read()
            check(signature.command == AUTH && signature.arg0 == 2)
            val signed = trusted && java.security.Signature.getInstance("NONEwithRSA").run {
                initVerify(phone.public)
                update(SHA1_INFO + token)
                verify(signature.payload)
            }
            if (!signed) {
                send(AUTH, 1, 0, ByteArray(20).also { SecureRandom().nextBytes(it) })
                val key = read()
                check(key.command == AUTH && key.arg0 == 3)
                val text = String(key.payload, Charsets.UTF_8).trimEnd('\u0000')
                val raw = ByteBuffer.wrap(java.util.Base64.getDecoder().decode(text.substringBefore(' '))).order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(64, raw.int)
                val n0inv = raw.int
                fun le() = BigInteger(1, ByteArray(256).also { raw.get(it) }.reversedArray())
                val n = le()
                val rr = le()
                val pub = phone.public as java.security.interfaces.RSAPublicKey
                assertEquals(pub.modulus, n)
                assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), rr)
                assertEquals(-1, n0inv * n.toInt())
                assertEquals(pub.publicExponent.toInt(), raw.int)
                assertTrue(text.contains(" SyncTheme@"))
                // Пользователь нажал «OK» пультом.
                Thread.sleep(200)
                trusted = true
            }
            send(CNXN, 0x01000001, 256 * 1024, "device::ro.product.name=tv;ro.product.model=Tuvio TV;features=shell_v2\u0000".toByteArray())
        }
    }

    // ------------------------------------------------------------ «часы»

    private class Stream(val local: Int, val remote: Int, val watch: FakeWatch) {
        fun reply(text: String) {
            watch.send(OKAY, local, remote)
            if (text.isNotEmpty()) watch.send(WRTE, local, remote, text.toByteArray())
            watch.send(CLSE, local, remote)
        }

        fun reject() = watch.send(CLSE, 0, remote)

        /** Читает stdin команды, подтверждая каждую часть, как adbd. */
        fun readStdin(size: Int): ByteArray {
            watch.send(OKAY, local, remote)
            val out = java.io.ByteArrayOutputStream()
            while (out.size() < size) {
                val p = watch.read()
                check(p.command == WRTE)
                out.write(p.payload)
                watch.send(OKAY, local, remote)
            }
            return out.toByteArray()
        }
    }

    private class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    private class FakeWatch(trusted: X509Certificate, val handle: (String, Stream) -> Unit) {
        private val server = ServerSocket(0)
        val port = server.localPort
        private lateinit var input: InputStream
        private lateinit var output: OutputStream

        init {
            thread(isDaemon = true) {
                runCatching {
                    val plain = server.accept()
                    input = plain.getInputStream()
                    output = plain.getOutputStream()
                    check(read().command == CNXN)
                    send(STLS, 0x01000000, 0)
                    check(read().command == STLS)
                    val tls = serverContext(trusted).socketFactory.createSocket(plain, null, plain.port, true) as SSLSocket
                    tls.useClientMode = false
                    tls.needClientAuth = true
                    tls.startHandshake()
                    input = tls.inputStream
                    output = tls.outputStream
                    send(CNXN, 0x01000001, 256 * 1024, "device::ro.product.name=fresh;ro.product.model=SM-L310;features=shell_v2\u0000".toByteArray())
                    var id = 100
                    while (true) {
                        val p = read()
                        if (p.command != OPEN) continue
                        handle(String(p.payload).trimEnd('\u0000'), Stream(++id, p.arg0, this))
                    }
                }
            }
        }

        private fun serverContext(trusted: X509Certificate): SSLContext {
            val keys = keyPair()
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null)
                setKeyEntry("watch", keys.private, CharArray(0), arrayOf(cert(keys, "watch")))
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
            // Как adbd: пускаем только ключ, запомненный при сопряжении.
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) {
                    if (chain.firstOrNull()?.publicKey != trusted.publicKey) throw CertificateException("unknown key")
                }
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLSv1.3").apply { init(kmf.keyManagers, arrayOf(tm), SecureRandom()) }
        }

        @Synchronized
        fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(data.size).putInt(data.sumOf { it.toInt() and 0xFF }).putInt(command.inv())
            output.write(header.array() + data)
            output.flush()
        }

        fun read(): Packet {
            val din = DataInputStream(input)
            val header = ByteArray(24).also { din.readFully(it) }
            val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = b.int
            val arg0 = b.int
            val arg1 = b.int
            val payload = ByteArray(b.int).also { din.readFully(it) }
            return Packet(command, arg0, arg1, payload)
        }
    }

    private companion object {
        const val CNXN = 0x4e584e43
        const val STLS = 0x534c5453
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val WRTE = 0x45545257
        const val CLSE = 0x45534c43
        const val AUTH = 0x48545541
        val SHA1_INFO = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        fun cert(keys: KeyPair, cn: String): X509Certificate {
            val name = X500Name("CN=$cn")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 86_400_000L), name, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private))
            return JcaX509CertificateConverter().getCertificate(holder)
        }
    }
}
