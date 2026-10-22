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

/** Куда ставится SyncTheme с телефона: приложение для часов или само приложение — на телевизор. */
enum class InstallTarget(val title: String) {
    WATCH("часы"), TV("телевизор");

    /** Выбор формы слова: «часы не найдены» / «телевизор не найден». */
    fun <T> pick(watch: T, tv: T): T = if (this == TV) tv else watch
}

/** Сведения о подключённом устройстве (часах или телевизоре). */
data class WatchDevice(val model: String, val sdk: Int, val isWatch: Boolean, val installedVersion: Long?, val isTv: Boolean = false) {
    /** Watch Face Push появился в Wear OS 6 (Android 16, API 36). */
    val supportsWatchFacePush get() = sdk >= 36
}

class InstallException(message: String) : Exception(message)

/** Часы не отвечают по сети (уснули, выключили Wi‑Fi, другой порт). */
class WatchUnreachableException(message: String) : Exception(message)

/**
 * Ставит приложение для часов, вшитое в приложение телефона (assets/watch_app.apk), или само
 * приложение телефона — на телевизор, по протоколу ADB через «Отладку по Wi‑Fi». Сопряжение
 * по коду делает libadb, а подключение и команды — [AdbLink]: у него таймаут на каждое чтение,
 * поэтому установка не зависает.
 */
class WatchAppInstaller(private val context: Context, private val log: (String) -> Unit = {}) {
    private val adb get() = PhoneAdbManager.get(context)

    @Volatile
    var target = InstallTarget.WATCH
    private val name get() = target.pick("часы", "телевизор")

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
        } ?: throw WatchUnreachableException("${target.pick("Часы не ответили", "Телевизор не ответил")} на сопряжение за ${PAIR_TIMEOUT / 1000} с")
        log("Сопряжение: успешно")
    }

    /** [onConfirm] — устройство показало «Разрешить отладку?» (телевизор без сопряжения по коду). */
    suspend fun connect(host: String, port: Int, onConfirm: () -> Unit = {}): WatchDevice {
        disconnect()
        log("Подключение к $host:$port")
        val l = withContext(Dispatchers.IO) {
            val wifi = AdbLink.wifiFor(context, host)
            if (AdbLink.vpnActive(context)) log("На телефоне включён VPN")
            AdbLink.open(host, port, wifi, adb.key, adb.cert, log, onConfirm)
        }
        link = l
        log("Подключено: ${l.model.ifBlank { name }}, читаю сведения")
        // Одной командой: каждый лишний запрос — ещё одно ожидание ответа по Wi‑Fi.
        val lines = shell(
            "getprop ro.build.version.sdk; getprop ro.product.brand; getprop ro.product.model; " +
                "getprop ro.build.characteristics; cmd package list packages --show-versioncode $PACKAGE",
            INFO_TIMEOUT,
        ).lines().map { it.trim() }
        val sdk = lines.getOrNull(0)?.toIntOrNull() ?: 0
        val brand = lines.getOrNull(1).orEmpty()
        val model = lines.getOrNull(2).orEmpty().ifBlank { l.model }
        val characteristics = lines.getOrNull(3).orEmpty()
        val isWatch = "watch" in characteristics
        val isTv = "tv" in characteristics
        val installed = lines.firstNotNullOfOrNull { line ->
            Regex("^package:${Regex.escape(PACKAGE)} versionCode:(\\d+)$").find(line)?.groupValues?.get(1)?.toLongOrNull()
        }
        log("${target.pick("Часы", "Телевизор")}: $brand $model, API $sdk, $characteristics, SyncTheme=${installed ?: "нет"}")
        return WatchDevice(listOf(brand, model).filter { it.isNotBlank() }.joinToString(" "), sdk, isWatch, installed, isTv)
    }

    /**
     * Передаёт APK потоком в `cmd package install` (так же делает `adb install`). На часы — вшитое
     * приложение для часов, на телевизор — само приложение телефона: заставка и приём темы живут в нём.
     */
    suspend fun install(onProgress: (Float) -> Unit) {
        val apk = withContext(Dispatchers.IO) {
            if (target == InstallTarget.TV) {
                val info = context.applicationInfo
                if (!info.splitSourceDirs.isNullOrEmpty()) {
                    throw InstallException("Эта сборка SyncTheme разбита на части — поставьте на телевизор файл SyncTheme-phone.apk из GitHub (подсказка ниже).")
                }
                File(info.sourceDir).readBytes()
            } else {
                context.assets.open(ASSET).use { it.readBytes() }
            }
        }
        log("Передаю приложение (${apk.size / 1024} КБ)")
        var result = installOnce(apk, onProgress)
        if ("INSTALL_FAILED_UPDATE_INCOMPATIBLE" in result) {
            log("${target.pick("На часах", "На телевизоре")} SyncTheme с другой подписью — удаляю и ставлю заново")
            shell("pm uninstall $PACKAGE")
            result = installOnce(apk, onProgress)
        }
        log("Ответ установщика: ${result.trim()}")
        if ("Success" !in result) throw InstallException(explain(result))
    }

    private suspend fun installOnce(apk: ByteArray, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        connected().run("exec:cmd package install -r -d -S ${apk.size}", INSTALL_TIMEOUT, apk, onProgress)
    }

    /**
     * Часы: разрешение «менять циферблат» и запуск приложения. Телевизор: SyncTheme — заставкой
     * (на Google TV стороннюю заставку в настройках не выбрать), работа в фоне и запуск приложения.
     */
    suspend fun finishSetup() {
        if (target == InstallTarget.TV) {
            shell("settings put secure screensaver_components $PACKAGE/$PACKAGE.mobile.tv.ThemeDreamService")
            shell("settings put secure screensaver_enabled 1")
            log("Заставка: " + shell("settings get secure screensaver_components").trim())
            // Уведомления и фон — чтобы приём темы работал и после перезагрузки телевизора.
            shell("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
            shell("cmd deviceidle whitelist +$PACKAGE")
            shell("am start -n $PACKAGE/.mobile.tv.TvActivity")
            log("SyncTheme открыт на телевизоре")
            return
        }
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

    private fun connected(): AdbLink = link ?: throw WatchUnreachableException("Нет подключения к ${target.pick("часам", "телевизору")}")

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
        target == InstallTarget.TV && "OLDER_SDK" in output -> "На телевизоре слишком старый Android — нужен Android TV 9 или новее."
        "MISSING_SHARED_LIBRARY" in output || "OLDER_SDK" in output ->
            "На часах нет Wear OS 6. Обновите ПО часов (Galaxy Wearable или приложение Pixel Watch) и повторите."
        "INSUFFICIENT_STORAGE" in output -> "${target.pick("На часах", "На телевизоре")} не хватает памяти."
        "VERSION_DOWNGRADE" in output -> "${target.pick("На часах", "На телевизоре")} более новая версия SyncTheme. Удалите её и повторите."
        else -> "${target.pick("Часы отказали", "Телевизор отказал")} в установке: ${output.trim().ifEmpty { "нет ответа" }}"
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
