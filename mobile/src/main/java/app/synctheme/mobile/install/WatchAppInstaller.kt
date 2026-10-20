package app.synctheme.mobile.install

import android.content.Context
import android.content.pm.PackageManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Сведения о подключённых часах. */
data class WatchDevice(val model: String, val sdk: Int, val isWatch: Boolean, val installedVersion: Long?) {
    /** Watch Face Push появился в Wear OS 6 (Android 16, API 36). */
    val supportsWatchFacePush get() = sdk >= 36
}

class InstallException(message: String) : Exception(message)

/** Часы не отвечают по сети (уснули, выключили Wi‑Fi, другой порт). */
class WatchUnreachableException(message: String) : Exception(message)

/**
 * Ставит приложение для часов, вшитое в приложение телефона (assets/watch_app.apk),
 * по протоколу ADB через «Отладку по Wi‑Fi».
 *
 * Библиотека libadb местами ждёт ответа без ограничения по времени, поэтому каждый шаг
 * обёрнут в таймаут, а доступность порта проверяется заранее.
 */
class WatchAppInstaller(private val context: Context, private val log: (String) -> Unit = {}) {
    private val adb get() = PhoneAdbManager.get(context)

    /** Версия приложения для часов, которая лежит внутри приложения телефона. */
    val bundledVersion: Long by lazy {
        val file = File(context.cacheDir, "watch_app_probe.apk")
        context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        file.delete()
        info?.longVersionCode ?: 0L
    }

    suspend fun pair(host: String, port: Int, code: String) {
        // Порт сопряжения заранее не проверяем: лишнее подключение часы считают неудачной попыткой.
        log("Сопряжение с $host:$port")
        val ok = step(PAIR_TIMEOUT) { adb.pair(host, port, code.trim()) }
            ?: throw WatchUnreachableException("Часы не ответили на сопряжение за ${PAIR_TIMEOUT / 1000} с")
        log("Сопряжение: ${if (ok) "успешно" else "отказ"}")
    }

    suspend fun connect(host: String, port: Int): WatchDevice {
        log("Подключение к $host:$port")
        checkReachable(host, port)
        disconnect()
        val connected = step(CONNECT_TIMEOUT) { adb.connect(host, port) }
            ?: throw WatchUnreachableException("Часы не завершили подключение за ${CONNECT_TIMEOUT / 1000} с")
        if (!connected) throw WatchUnreachableException("Часы не подтвердили подключение")
        log("Подключено, читаю сведения о часах")
        val sdk = shell("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0
        val brand = shell("getprop ro.product.brand").trim()
        val model = shell("getprop ro.product.model").trim()
        val isWatch = "watch" in shell("getprop ro.build.characteristics")
        val versionLine = shell("dumpsys package $PACKAGE | grep versionCode").trim()
        val installed = Regex("versionCode=(\\d+)").find(versionLine)?.groupValues?.get(1)?.toLongOrNull()
        log("Часы: $brand $model, API $sdk, watch=$isWatch, SyncTheme=${installed ?: "нет"}")
        return WatchDevice(listOf(brand, model).filter { it.isNotBlank() }.joinToString(" "), sdk, isWatch, installed)
    }

    /** Передаёт APK потоком в `cmd package install` (так же делает `adb install`). */
    suspend fun install(onProgress: (Float) -> Unit) {
        val apk = withContext(Dispatchers.IO) { context.assets.open(ASSET).use { it.readBytes() } }
        log("Передаю приложение (${apk.size / 1024} КБ)")
        var result = installOnce(apk, onProgress)
        if ("INSTALL_FAILED_UPDATE_INCOMPATIBLE" in result) {
            log("На часах SyncTheme с другой подписью — удаляю и ставлю заново")
            shell("pm uninstall $PACKAGE")
            result = installOnce(apk, onProgress)
        }
        log("Ответ установщика: ${result.trim()}")
        if ("Success" !in result) throw InstallException(explain(result))
    }

    private suspend fun installOnce(apk: ByteArray, onProgress: (Float) -> Unit): String =
        step(INSTALL_TIMEOUT) {
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
            readAll(stream)
        } ?: throw WatchUnreachableException("Передача на часы прервалась (нет ответа ${INSTALL_TIMEOUT / 1000} с)")

    /** Выдаёт разрешение «менять циферблат» и открывает приложение на часах. */
    suspend fun finishSetup() {
        log("Разрешение на смену циферблата: " +
            shell("pm grant $PACKAGE com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE").trim().ifEmpty { "выдано" })
        shell("am start -n $PACKAGE/.wear.MainActivity")
        log("SyncTheme открыт на часах")
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) { runCatching { adb.disconnect() } }
    }

    private suspend fun shell(command: String): String =
        step(SHELL_TIMEOUT) { readAll(adb.openStream("shell:$command")) }
            ?: throw WatchUnreachableException("Часы не ответили на команду (${command.substringBefore(' ')})")

    /** Блокирующий вызов библиотеки с таймаутом; ожидания внутри libadb прерываемы. */
    private suspend fun <T> step(timeoutMs: Long, block: () -> T): T? =
        withTimeoutOrNull(timeoutMs) { runInterruptible(Dispatchers.IO) { block() } }

    /** Быстрая проверка TCP-порта: без неё libadb может минутами ждать уснувшие часы. */
    private suspend fun checkReachable(host: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), REACH_TIMEOUT) }
        } catch (e: IOException) {
            log("Порт $host:$port недоступен: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            throw WatchUnreachableException("Часы не отвечают по Wi‑Fi ($host:$port)")
        }
    }

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
        private const val REACH_TIMEOUT = 5_000
        private const val PAIR_TIMEOUT = 30_000L
        private const val CONNECT_TIMEOUT = 25_000L
        private const val SHELL_TIMEOUT = 20_000L
        private const val INSTALL_TIMEOUT = 180_000L
    }
}
