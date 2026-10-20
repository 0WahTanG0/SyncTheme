package app.synctheme.mobile.install

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class InstallerUi(
    val pairing: List<AdbService> = emptyList(),
    val connect: List<AdbService> = emptyList(),
    val host: String = "",
    val pairPort: String = "",
    val connectPort: String = "",
    val code: String = "",
    val manual: Boolean = false,
    val knownHost: Boolean = false,
    val busy: String? = null,
    val progress: Float? = null,
    val error: String? = null,
    /** Показать подсказки про Wi‑Fi и Bluetooth на часах. */
    val networkHint: Boolean = false,
    val device: WatchDevice? = null,
    val done: Boolean = false,
    val log: List<String> = emptyList(),
)

class InstallerViewModel(app: Application) : AndroidViewModel(app) {
    private val installer = WatchAppInstaller(app, ::log)
    private val prefs = app.getSharedPreferences("watch_install", Context.MODE_PRIVATE)
    private val pairedHosts get() = prefs.getStringSet("paired", emptySet()).orEmpty()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private val _ui = MutableStateFlow(InstallerUi())
    val ui: StateFlow<InstallerUi> = _ui.asStateFlow()

    private var discoveryJobs = emptyList<Job>()

    /** Поиск часов в сети работает, пока открыт экран установки. */
    fun startDiscovery() {
        if (discoveryJobs.isNotEmpty()) return
        discoveryJobs = listOf(
            viewModelScope.launch {
                AdbDiscovery.services(getApplication(), AdbDiscovery.PAIRING).collect { list ->
                    _ui.update { it.copy(pairing = list).autoSelect() }
                }
            },
            viewModelScope.launch {
                AdbDiscovery.services(getApplication(), AdbDiscovery.CONNECT).collect { list ->
                    _ui.update { it.copy(connect = list).autoSelect() }
                }
            },
        )
    }

    fun stopDiscovery() {
        discoveryJobs.forEach { it.cancel() }
        discoveryJobs = emptyList()
    }

    /** Подставляет найденные адрес и порты, если пользователь не ввёл их вручную. */
    private fun InstallerUi.autoSelect(): InstallerUi {
        if (manual) return this
        val host = host.takeIf { h -> pairing.any { it.host == h } || connect.any { it.host == h } }
            ?: pairing.firstOrNull()?.host ?: connect.firstOrNull()?.host ?: ""
        return copy(
            host = host,
            pairPort = pairing.firstOrNull { it.host == host }?.port?.toString().orEmpty(),
            connectPort = connect.firstOrNull { it.host == host }?.port?.toString().orEmpty(),
            knownHost = host in pairedHosts,
        )
    }

    fun selectHost(host: String) = _ui.update { it.copy(manual = false, host = host).autoSelect() }

    fun setManual(manual: Boolean) = _ui.update { if (manual) it.copy(manual = true) else it.copy(manual = false).autoSelect() }

    fun edit(host: String? = null, pairPort: String? = null, connectPort: String? = null, code: String? = null) =
        _ui.update {
            val newHost = host ?: it.host
            it.copy(
                host = newHost,
                pairPort = pairPort ?: it.pairPort,
                connectPort = connectPort ?: it.connectPort,
                code = (code ?: it.code).filter(Char::isDigit).take(6),
                knownHost = newHost in pairedHosts,
                error = null,
            )
        }

    /** Забыть сопряжение: в следующий раз понадобится новый код с часов. */
    fun resetPairing() {
        prefs.edit().remove("paired").apply()
        _ui.update { it.copy(knownHost = false, error = null) }
        log("Сопряжение сброшено")
    }

    fun install() = viewModelScope.launch {
        val s = _ui.value
        if (s.host.isBlank()) return@launch fail("Часы не найдены. Проверьте, что часы и телефон в одной сети Wi‑Fi, или введите адрес вручную.")
        _ui.update { it.copy(error = null, networkHint = false, done = false, device = null, progress = null) }
        log("Старт: часы ${s.host}, сопряжение ${s.pairPort.ifBlank { "—" }}, подключение ${s.connectPort.ifBlank { "—" }}")
        try {
            if (s.code.length == 6) {
                val port = s.pairPort.toIntOrNull()
                    ?: return@launch fail("Откройте на часах «Сопряжение нового устройства», чтобы появился порт сопряжения.")
                busy("Сопряжение с часами…")
                installer.pair(s.host, port, s.code)
                prefs.edit().putStringSet("paired", pairedHosts + s.host).apply()
                _ui.update { it.copy(knownHost = true, code = "") }
            } else if (!s.knownHost) {
                return@launch fail("Введите 6-значный код с экрана «Сопряжение нового устройства» на часах.")
            }

            val device = connectWithRetry(s.host)
            _ui.update { it.copy(device = device) }
            if (!device.supported) {
                return@launch fail(
                    "На часах ${device.model} Android API ${device.sdk}. SyncTheme работает с Wear OS 3 (API 30) и новее: " +
                        "обновите ПО часов и повторите."
                )
            }

            busy("Устанавливаю SyncTheme на ${device.model.ifBlank { "часы" }}…")
            installer.install { p -> _ui.update { it.copy(progress = p) } }
            busy("Настраиваю приложение на часах…")
            installer.finishSetup(device)
            installer.disconnect()
            log("Готово")
            _ui.update { it.copy(busy = null, progress = null, done = true) }
        } catch (e: AdbPairingRequiredException) {
            logError(e)
            prefs.edit().putStringSet("paired", pairedHosts - s.host).apply()
            _ui.update { it.copy(knownHost = false) }
            fail("Часы не узнали телефон — нужно сопряжение заново: откройте на часах «Сопряжение нового устройства» и введите код.")
        } catch (e: SSLException) {
            logError(e)
            fail("Сопряжение не удалось — проверьте код (он новый при каждом открытии окна сопряжения).")
        } catch (e: WatchUnreachableException) {
            logError(e)
            fail("${e.message}. Разбудите экран часов и попробуйте ещё раз — подсказки ниже.", networkHint = true)
        } catch (e: InstallException) {
            logError(e)
            fail(e.message.orEmpty())
        } catch (e: Exception) {
            logError(e)
            fail("Ошибка: ${e.message ?: e.javaClass.simpleName}", networkHint = true)
        }
    }

    /**
     * Порт подключения меняется при каждом включении «Отладки по Wi‑Fi», а часы могли
     * задремать — поэтому до 3 попыток, каждый раз с актуальным портом из сети.
     */
    private suspend fun connectWithRetry(host: String): WatchDevice {
        var last: Exception? = null
        repeat(3) { attempt ->
            busy(if (attempt == 0) "Подключаюсь к часам…" else "Подключаюсь к часам… (попытка ${attempt + 1} из 3)")
            val port = _ui.value.connectPort.toIntOrNull() ?: waitForConnectPort(host)
                ?: throw WatchUnreachableException(
                    "Не найден порт подключения. Он указан на часах на экране «Отладка по Wi‑Fi» после двоеточия"
                )
            try {
                return installer.connect(host, port)
            } catch (e: WatchUnreachableException) {
                last = e
                log("Попытка ${attempt + 1}: ${e.message}")
                delay(2_000)
            }
        }
        throw last ?: WatchUnreachableException("Не удалось подключиться к часам")
    }

    /** После сопряжения часы объявляют порт подключения в сети — ждём его немного. */
    private suspend fun waitForConnectPort(host: String): Int? = withTimeoutOrNull(10_000) {
        _ui.first { ui -> ui.connect.any { it.host == host } }.connect.first { it.host == host }.port
    }

    private fun busy(text: String) {
        log(text)
        _ui.update { it.copy(busy = text, error = null) }
    }

    private fun log(text: String) {
        _ui.update { it.copy(log = (it.log + "${time.format(Date())}  $text").takeLast(200)) }
    }

    private fun logError(e: Throwable) {
        log("Ошибка: " + generateSequence(e) { it.cause }.take(4)
            .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" })
    }

    private suspend fun fail(message: String, networkHint: Boolean = false) {
        installer.disconnect()
        log(message)
        _ui.update { it.copy(busy = null, progress = null, error = message, networkHint = networkHint) }
    }

    override fun onCleared() {
        stopDiscovery()
    }
}
