package app.synctheme.mobile.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import app.synctheme.R
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Описание новой версии из update.json релиза. */
data class UpdateInfo(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Обновление приложения «поверх» — без удаления и с сохранением настроек.
 *
 * Новые версии публикуются как релизы GitHub: в каждом лежат SyncTheme-phone.apk и update.json.
 * Ставится APK только если он подписан тем же ключом, что и установленное приложение.
 */
class Updater(private val context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** Репозиторий вида owner/name; по умолчанию — из сборки. */
    var repo: String
        get() = prefs.getString("repo", null) ?: context.getString(R.string.update_repo)
        set(value) = prefs.edit().putString("repo", value.trim().removePrefix("https://github.com/").trim('/')).apply()

    val currentVersionCode: Long
        get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    val currentVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    var lastCheck: Long
        get() = prefs.getLong("last_check", 0L)
        private set(value) = prefs.edit().putLong("last_check", value).apply()

    /** null — обновлений нет. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val repo = repo.ifBlank { throw IOException("Источник обновлений не настроен") }
        val text = open("https://github.com/$repo/releases/latest/download/update.json").use { it.inputStream.readBytes() }
        val json = JSONObject(String(text))
        lastCheck = System.currentTimeMillis()
        val info = UpdateInfo(
            versionCode = json.getLong("versionCode"),
            versionName = json.optString("versionName"),
            apkUrl = json.optString("apk").ifBlank { "https://github.com/$repo/releases/latest/download/SyncTheme-phone.apk" },
            notes = json.optString("notes"),
        )
        info.takeIf { it.versionCode > currentVersionCode }
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        open(info.apkUrl).use { conn ->
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total != null) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    /** Копирует выбранный пользователем APK во временный файл. */
    suspend fun copyFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    }

    /** Проверяет, что это SyncTheme, подписанный тем же ключом, и что версия не старее. */
    fun verify(apk: File) {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
            ?: throw IOException("Файл повреждён или это не APK")
        if (archive.packageName != context.packageName) throw IOException("Это APK другого приложения (${archive.packageName})")
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (signers(archive) != signers(installed)) {
            throw IOException("APK подписан другим ключом — его нельзя поставить как обновление")
        }
        if (archive.longVersionCode < currentVersionCode) {
            throw IOException("В файле более старая версия (${archive.versionName})")
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Экран, где пользователь один раз разрешает SyncTheme устанавливать обновления. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Ставит APK поверх текущей версии через системный установщик. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // После первого обновления SyncTheme становится «установщиком» самого себя — дальше без лишних вопросов.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("SyncTheme.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun signers(info: android.content.pm.PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "SyncTheme")
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IOException(
                if (conn.responseCode == 404) "Релиз не найден (github.com/$repo)" else "Сервер ответил ${conn.responseCode}"
            )
        }
        return conn
    }
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try {
        block(this)
    } finally {
        disconnect()
    }
