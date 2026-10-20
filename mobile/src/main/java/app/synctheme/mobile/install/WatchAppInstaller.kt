package app.synctheme.mobile.install

import android.content.Context
import android.content.pm.PackageManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Сведения о подключённых часах. */
data class WatchDevice(val model: String, val sdk: Int, val isWatch: Boolean, val installedVersion: Long?) {
    /** Watch Face Push появился в Wear OS 6 (Android 16, API 36). */
    val supportsWatchFacePush get() = sdk >= 36
}

class InstallException(message: String) : Exception(message)

/**
 * Ставит приложение для часов, вшитое в приложение телефона (assets/watch_app.apk),
 * по протоколу ADB через «Отладку по Wi‑Fi».
 */
class WatchAppInstaller(private val context: Context) {
    private val adb get() = PhoneAdbManager.get(context)

    /** Версия приложения для часов, которая лежит внутри приложения телефона. */
    val bundledVersion: Long by lazy {
        val file = File(context.cacheDir, "watch_app_probe.apk")
        context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        file.delete()
        info?.longVersionCode ?: 0L
    }

    suspend fun pair(host: String, port: Int, code: String) = withContext(Dispatchers.IO) {
        adb.pair(host, port, code.trim())
    }

    suspend fun connect(host: String, port: Int): WatchDevice = withContext(Dispatchers.IO) {
        runCatching { adb.disconnect() }
        if (!adb.connect(host, port)) throw InstallException("Часы не приняли подключение")
        val sdk = shell("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0
        val brand = shell("getprop ro.product.brand").trim()
        val model = shell("getprop ro.product.model").trim()
        val isWatch = "watch" in shell("getprop ro.build.characteristics")
        val versionLine = shell("dumpsys package $PACKAGE | grep versionCode").trim()
        val installed = Regex("versionCode=(\\d+)").find(versionLine)?.groupValues?.get(1)?.toLongOrNull()
        WatchDevice(listOf(brand, model).filter { it.isNotBlank() }.joinToString(" "), sdk, isWatch, installed)
    }

    /** Передаёт APK потоком в `cmd package install` (так же делает `adb install`). */
    suspend fun install(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val apk = context.assets.open(ASSET).use { it.readBytes() }
        var result = installOnce(apk, onProgress)
        if ("INSTALL_FAILED_UPDATE_INCOMPATIBLE" in result) {
            // На часах SyncTheme с другой подписью (например, собранный отдельно) — удаляем и ставим заново.
            shell("pm uninstall $PACKAGE")
            result = installOnce(apk, onProgress)
        }
        if ("Success" !in result) throw InstallException(explain(result))
    }

    private fun installOnce(apk: ByteArray, onProgress: (Float) -> Unit): String {
        val stream = adb.openStream("exec:cmd package install -r -d -S ${apk.size}")
        val out = stream.openOutputStream()
        // По одному пакету ADB за вызов: библиотека ждёт подтверждения перед каждой записью.
        val chunk = minOf(adb.adbConnection?.maxData ?: 4096, 64 * 1024)
        var sent = 0
        while (sent < apk.size) {
            val n = minOf(chunk, apk.size - sent)
            out.write(apk, sent, n)
            sent += n
            onProgress(sent.toFloat() / apk.size)
        }
        out.flush()
        return readAll(stream)
    }

    /** Выдаёт разрешение «менять циферблат» и открывает приложение на часах. */
    suspend fun finishSetup() = withContext(Dispatchers.IO) {
        shell("pm grant $PACKAGE com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE")
        shell("am start -n $PACKAGE/.wear.MainActivity")
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { runCatching { adb.disconnect() } }

    private fun shell(command: String): String = readAll(adb.openStream("shell:$command"))

    private fun readAll(stream: AdbStream): String {
        val buffer = ByteArrayOutputStream()
        val input = stream.openInputStream()
        val bytes = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                buffer.write(bytes, 0, n)
            }
        } catch (_: IOException) {
            // Часы закрыли поток — это конец вывода.
        } finally {
            runCatching { stream.close() }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun explain(output: String): String = when {
        "MISSING_SHARED_LIBRARY" in output || "OLDER_SDK" in output ->
            "На часах нет Wear OS 6. Обновите ПО часов (Galaxy Wearable → Обновление ПО часов) и повторите."
        "INSUFFICIENT_STORAGE" in output -> "На часах не хватает памяти."
        "VERSION_DOWNGRADE" in output -> "На часах более новая версия SyncTheme. Удалите её и повторите."
        else -> "Часы отказали в установке: ${output.trim().ifEmpty { "нет ответа" }}"
    }

    companion object {
        const val ASSET = "watch_app.apk"
        const val PACKAGE = "app.synctheme"
    }
}
