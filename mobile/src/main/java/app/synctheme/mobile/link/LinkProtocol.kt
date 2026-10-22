package app.synctheme.mobile.link

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Телефон и его устройства (планшет, телевизор, другой телефон) говорят по Wi‑Fi: TCP в домашней сети,
 * устройство видно через mDNS как [SERVICE] (в TXT-записи — его тип и id). Каждое сообщение — рамка:
 * «STHM», версия, тип, длина, данные.
 *
 * Подключение — без ввода кода: телефон шлёт [HELLO] со своим открытым ключом (ECDH P-256),
 * устройство отвечает [KEY] своим. Из общего секрета обе стороны считают одинаковый 6-значный код
 * сверки и показывают его; на устройстве нажимают «Подключить» — оно отвечает [LINKED] (зашифровано
 * общим ключом). Подменить ключи посередине незаметно нельзя: коды на экранах бы не совпали.
 * Дальше тема ([THEME]) шифруется этим ключом (AES-GCM).
 */
internal object LinkProtocol {
    const val SERVICE = "_synctheme._tcp"

    /**
     * Постоянные порты устройства: после перезапуска приёма оно слушает тот же порт, и телефон
     * находит его по последнему адресу, даже если поиск в сети (mDNS) роутер не пропускает.
     */
    val PORTS = 47231..47240
    private val MAGIC = "STHM".toByteArray()
    private const val VERSION = 2

    const val HELLO = 1
    const val KEY = 2
    const val LINKED = 3
    const val THEME = 4
    const val DONE = 5
    const val ERROR = 6

    /** Тема с фото — несколько мегабайт; больше не принимаем. */
    const val MAX = 96 * 1024 * 1024

    /** Атрибуты TXT-записи mDNS: тип устройства и его постоянный id. */
    const val ATTR_KIND = "kind"
    const val ATTR_ID = "id"

    private val PROOF = "synctheme-linked".toByteArray()
    private val random = SecureRandom()

    fun write(out: DataOutputStream, type: Int, payload: ByteArray) {
        out.write(MAGIC)
        out.writeByte(VERSION)
        out.writeByte(type)
        out.writeInt(payload.size)
        out.write(payload)
        out.flush()
    }

    fun read(input: DataInputStream): Pair<Int, ByteArray> {
        val magic = ByteArray(4).also(input::readFully)
        if (!magic.contentEquals(MAGIC)) throw IOException("Это не SyncTheme")
        val version = input.readUnsignedByte()
        if (version != VERSION) throw IOException("Обновите SyncTheme на обоих устройствах до одной версии")
        val type = input.readUnsignedByte()
        val size = input.readInt()
        if (size < 0 || size > MAX) throw IOException("Слишком большое сообщение")
        return type to ByteArray(size).also(input::readFully)
    }

    fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)

    /** Пара ключей для подключения (ECDH на кривой P-256 — есть во всех Android). */
    fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** Общий секрет из своего закрытого и чужого открытого ключа. */
    fun shared(own: PrivateKey, other: ByteArray): ByteArray {
        val public = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(other))
        return KeyAgreement.getInstance("ECDH").apply {
            init(own)
            doPhase(public, true)
        }.generateSecret()
    }

    private fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { parts.forEach(::update) }.digest()

    /** Ключ связи (хранится на обеих сторонах): из общего секрета и обоих открытых ключей. */
    fun linkSecret(shared: ByteArray, phoneKey: ByteArray, deviceKey: ByteArray): ByteArray =
        sha256("synctheme-link".toByteArray(), shared, phoneKey, deviceKey)

    /** Код сверки: шесть цифр, одинаковые на телефоне и устройстве, если ключи никто не подменил. */
    fun code(shared: ByteArray, phoneKey: ByteArray, deviceKey: ByteArray): String {
        val h = sha256("synctheme-code".toByteArray(), shared, phoneKey, deviceKey)
        val n = h.take(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        return "%06d".format(n % 1_000_000)
    }

    fun key(secret: ByteArray): SecretKey = SecretKeySpec(secret, "AES")

    /** Шифрует: 12 байт nonce + шифротекст с тегом. */
    fun seal(key: SecretKey, data: ByteArray): ByteArray {
        val iv = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + cipher.doFinal(data)
    }

    /** Расшифровывает; чужой ключ или подмена — исключение. */
    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        if (sealed.size < 28) throw IOException("Повреждённое сообщение")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        }
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }

    fun proof() = PROOF
}
