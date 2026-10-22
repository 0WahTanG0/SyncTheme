package app.synctheme.mobile.tablet

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Телефон и планшет говорят по Wi‑Fi (TCP в домашней сети, планшет виден через mDNS как
 * [SERVICE]). Каждое сообщение — рамка: «STHM», версия, тип, длина, данные.
 *
 * Связывание: планшет показывает 6-значный код; телефон шлёт [PAIR] — соль, своё имя и пробу,
 * зашифрованную ключом из кода. Код верный — планшет отвечает [PAIR_OK] с общим секретом
 * (тем же ключом). Дальше тема ([THEME]) шифруется секретом (AES-GCM): чужой телефон в той же сети
 * её не подменит и не прочитает.
 */
internal object TabletProtocol {
    const val SERVICE = "_synctheme._tcp"
    private val MAGIC = "STHM".toByteArray()
    private const val VERSION = 1

    const val PAIR = 1
    const val PAIR_OK = 2
    const val THEME = 3
    const val DONE = 4
    const val ERROR = 5

    /** Тема с фото — несколько мегабайт; больше не принимаем. */
    const val MAX = 96 * 1024 * 1024

    private val PROOF = "synctheme-pair".toByteArray()
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
        if (version != VERSION) throw IOException("Обновите SyncTheme на телефоне и планшете до одной версии")
        val type = input.readUnsignedByte()
        val size = input.readInt()
        if (size < 0 || size > MAX) throw IOException("Слишком большое сообщение")
        return type to ByteArray(size).also(input::readFully)
    }

    fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)

    /** Новый код связывания: шесть цифр. */
    fun code(): String = (random.nextInt(900_000) + 100_000).toString()

    /** Ключ из кода: медленный, чтобы код нельзя было быстро подобрать по перехваченным сообщениям. */
    fun keyFromCode(code: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(code.toCharArray(), salt, 60_000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
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
