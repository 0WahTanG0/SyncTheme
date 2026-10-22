package app.synctheme.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** В присланном нет картинки, или её не удалось получить. */
class NotAnImageException(message: String) : Exception(message)

/**
 * Картинка, присланная в SyncTheme через «Поделиться»: сама картинка (браузер, галерея, мессенджер)
 * или ссылка на страницу с ней (Pinterest, сайт) — тогда берётся главная картинка страницы, та, что
 * сайт показывает в превью ссылки (og:image). Картинка скачивается во внутреннюю папку приложения,
 * в галерею телефона ничего не сохраняется.
 */
object SharedImage {
    private const val MAX_IMAGE = 30L * 1024 * 1024
    private const val MAX_PAGE = 3 * 1024 * 1024
    private const val AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    /** Прислали ли что-то, из чего получится картинка. */
    fun accepts(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND && (stream(intent) != null || url(intent) != null)

    /** Сама картинка (content://…). */
    fun stream(intent: Intent): Uri? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

    /** Первая ссылка в тексте («Посмотрите этот пин: https://pin.it/…»). */
    fun url(intent: Intent): String? = intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::firstUrl)

    fun firstUrl(text: String): String? =
        Regex("""https?://[^\s<>"']+""").find(text)?.value?.trimEnd('.', ',', ')', ';', '!', '?')

    /** Откуда картинка — для сообщения пользователю. */
    fun source(intent: Intent): String = url(intent)?.let { link ->
        runCatching { URL(link).host.removePrefix("www.") }.getOrNull()?.let { host ->
            when {
                "pin" in host -> "Pinterest"
                else -> host
            }
        }
    } ?: "другого приложения"

    /**
     * Скачивает картинку по ссылке во временный файл приложения. Ссылка на страницу — берётся её
     * главная картинка; у Pinterest — в исходном размере, если он доступен.
     */
    suspend fun download(context: Context, link: String): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "shared_image")
        val first = open(link)
        try {
            if (first.isImage) return@withContext first.saveTo(target)
            val html = first.readPage()
            val image = pageImage(html, first.url) ?: throw NotAnImageException("На странице не нашлось картинки")
            first.close()
            for (candidate in listOfNotNull(original(image), image)) {
                val r = runCatching { open(candidate) }.getOrNull() ?: continue
                try {
                    if (r.isImage) return@withContext r.saveTo(target)
                } finally {
                    r.close()
                }
            }
            throw NotAnImageException("Не удалось скачать картинку со страницы")
        } finally {
            first.close()
        }
    }

    /** Главная картинка страницы: og:image, twitter:image или link rel="image_src" (адрес — уже полный). */
    fun pageImage(html: String, base: String): String? {
        val metas = Regex("""<(meta|link)\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).map { tag ->
            Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""").findAll(tag.value)
                .associate { it.groupValues[1].lowercase() to unescape(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        }.toList()
        fun meta(key: String) = metas.firstOrNull { (it["property"] ?: it["name"])?.lowercase() == key }?.get("content")
        val found = listOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")
            .firstNotNullOfOrNull { meta(it)?.takeIf(String::isNotBlank) }
            ?: metas.firstOrNull { it["rel"]?.lowercase() == "image_src" }?.get("href")
        return found?.let { runCatching { URL(URL(base), it).toString() }.getOrNull() }
    }

    /** У Pinterest превью страницы — уменьшенная копия (/736x/); исходник лежит в /originals/. */
    fun original(image: String): String? =
        Regex("""(https?://i\.pinimg\.com/)\d+x\d*/""").find(image)?.let { image.replaceFirst(it.value, it.groupValues[1] + "originals/") }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x2F;", "/").replace("&#47;", "/").replace("&lt;", "<").replace("&gt;", ">")

    private class Response(val connection: HttpURLConnection, val url: String) {
        val type: String = connection.contentType.orEmpty().lowercase()
        val isImage get() = type.startsWith("image/")

        fun saveTo(file: File): File {
            connection.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_IMAGE) throw NotAnImageException("Картинка слишком большая")
                        out.write(buf, 0, n)
                    }
                }
            }
            return file
        }

        fun readPage(): String {
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(32 * 1024)
                while (out.size() < MAX_PAGE) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        }

        fun close() = runCatching { connection.disconnect() }
    }

    /** GET с переходами по перенаправлениям (в том числе http → https, как у коротких ссылок). */
    private fun open(link: String): Response {
        var url = link
        repeat(6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Accept", "text/html,image/avif,image/webp,image/*,*/*;q=0.8")
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: throw IOException("Перенаправление без адреса")
                c.disconnect()
                url = URL(URL(url), next).toString()
                return@repeat
            }
            if (code !in 200..299) {
                c.disconnect()
                throw NotAnImageException("Сайт ответил ошибкой $code")
            }
            return Response(c, url)
        }
        throw IOException("Слишком много перенаправлений")
    }
}
