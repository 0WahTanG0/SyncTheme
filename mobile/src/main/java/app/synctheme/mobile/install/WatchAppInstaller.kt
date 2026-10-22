package app.synctheme.mobile.install

import app.synctheme.mobile.util.Compat
import android.content.Context
import android.content.pm.PackageManager
import io.github.muntashirakon.adb.PairingConnectionCtx
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * по протоколу ADB через «Отладку по Wi‑Fi». Сопряжение по коду делает libadb, а подключение
 * и команды — [AdbLink]: у него таймаут на каждое чтение, поэтому установка не зависает.
 */
class WatchAppInstaller(private val context: Context, private val log: (String) -> Unit = {}) {
    private val adb get() = PhoneAdbManager.get(context)

    @Volatile
    private var link: AdbLink? = null

    /** Версия приложения для часов, которая лежит внутри приложения телефона. */
    val bundledVersion: Long by lazy {
        val file = File(context.cacheDir, "watch_app_probe.apk")
        context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
        val info = Compat.archiveInfo(context.packageManager, file.path, 0)
        file.delete()
        info?.longVersionCode ?: 0L
    }

    suspend fun pair(host: String, port: Int, code: String) {
        // Порт сопряжения заранее не проверяем: лишнее подключение часы считают неудачной попыткой.
        log("Сопряжение с $host:$port")
        val manager = adb
        detached(PAIR_TIMEOUT) {
            PairingConnectionCtx(host, port, code.trim().toByteArray(), manager.key, manager.cert, manager.deviceName).use { it.start() }
        } ?: throw WatchUnreachableException("Часы не ответили на сопряжение за ${PAIR_TIMEOUT / 1000} с")
        log("Сопряжение: успешно")
    }

    suspend fun connect(host: String, port: Int): WatchDevice {
        disconnect()
        log("Подключение к $host:$port")
        val l = withContext(Dispatchers.IO) {
            val wifi = AdbLink.wifiFor(context, host)
            if (AdbLink.vpnActive(context)) log("На телефоне включён VPN")
            AdbLink.open(host, port, wifi, adb.key, adb.cert, log)
        }
        link = l
        log("Подключено: ${l.model.ifBlank { "часы" }}, читаю сведения")
        // Одной командой: каждый лишний запрос — ещё одно ожидание ответа по Wi‑Fi.
        val lines = shell(
            "getprop ro.build.version.sdk; getprop ro.product.brand; getprop ro.product.model; " +
                "getprop ro.build.characteristics; cmd package list packages --show-versioncode $PACKAGE",
            INFO_TIMEOUT,
        ).lines().map { it.trim() }
        val sdk = lines.getOrNull(0)?.toIntOrNull() ?: 0
        val brand = lines.getOrNull(1).orEmpty()
        val model = lines.getOrNull(2).orEmpty().ifBlank { l.model }
        val isWatch = "watch" in lines.getOrNull(3).orEmpty()
        val installed = lines.firstNotNullOfOrNull { line ->
            Regex("^package:${Regex.escape(PACKAGE)} versionCode:(\\d+)$").find(line)?.groupValues?.get(1)?.toLongOrNull()
        }
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

    private suspend fun installOnce(apk: ByteArray, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        connected().run("exec:cmd package install -r -d -S ${apk.size}", INSTALL_TIMEOUT, apk, onProgress)
    }

    /** Выдаёт разрешение «менять циферблат» и открывает приложение на часах. */
    suspend fun finishSetup() {
        log("Разрешение на смену циферблата: " +
            shell("pm grant $PACKAGE com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE").trim().ifEmpty { "выдано" })
        shell("am start -n $PACKAGE/.wear.MainActivity")
        log("SyncTheme открыт на часах")
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { close() }

    /** Закрывает подключение сразу: зависшее чтение прервётся. */
    fun close() {
        link?.close()
        link = null
    }

    private fun connected(): AdbLink = link ?: throw WatchUnreachableException("Нет подключения к часам")

    private suspend fun shell(command: String, timeout: Long = SHELL_TIMEOUT): String =
        withContext(Dispatchers.IO) { connected().run("shell:$command", timeout) }

    /**
     * Блокирующий вызов в отдельном потоке с таймаутом. Чтение сокета внутри libadb не прерывается,
     * поэтому по таймауту поток просто оставляется: он закончится, когда часы закроют соединение,
     * а следующая попытка его не ждёт.
     */
    private suspend fun <T> detached(timeoutMs: Long, block: () -> T): T? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val thread = Thread {
                val result = runCatching(block)
                if (cont.isActive) cont.resumeWith(result)
            }
            thread.isDaemon = true
            thread.start()
            cont.invokeOnCancellation { thread.interrupt() }
        }
    }

    private fun explain(output: String): String = when {
        "MISSING_SHARED_LIBRARY" in output || "OLDER_SDK" in output ->
            "На часах нет Wear OS 6. Обновите ПО часов (Galaxy Wearable или приложение Pixel Watch) и повторите."
        "INSUFFICIENT_STORAGE" in output -> "На часах не хватает памяти."
        "VERSION_DOWNGRADE" in output -> "На часах более новая версия SyncTheme. Удалите её и повторите."
        else -> "Часы отказали в установке: ${output.trim().ifEmpty { "нет ответа" }}"
    }

    companion object {
        const val ASSET = "watch_app.apk"
        const val PACKAGE = "app.synctheme"
        private const val PAIR_TIMEOUT = 30_000L
        private const val INFO_TIMEOUT = 12_000L
        private const val SHELL_TIMEOUT = 20_000L
        private const val INSTALL_TIMEOUT = 180_000L
    }
}
