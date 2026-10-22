package app.synctheme.mobile.install

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.synctheme.mobile.link.LinkProtocol
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class InstallerUi(
    val target: InstallTarget = InstallTarget.WATCH,
    val pairing: List<AdbService> = emptyList(),
    val connect: List<AdbService> = emptyList(),
    /** Телевизоры Google TV в сети (по их пульту-службе) — адрес для «Отладки по сети». */
    val tvs: List<AdbService> = emptyList(),
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
    /** На телефоне включён VPN: он может мешать подключению к часам в домашней сети. */
    val vpn: Boolean = false,
    val device: WatchDevice? = null,
    val done: Boolean = false,
    /** Телевизор: SyncTheme на нём уже ждёт телефон — адрес для подключения в одно касание. */
    val linkPort: Int? = null,
    val log: List<String> = emptyList(),
) {
    /** Найденные устройства: сначала с включённой отладкой, затем телевизоры по имени. */
    val hosts: List<String> get() = (pairing.map { it.host } + connect.map { it.host } +
        (if (target == InstallTarget.TV) tvs.map { it.host } else emptyList())).distinct()

    /** Имя телевизора в сети («Tuvio TV»), если он нашёлся по своей службе. */
    fun tvName(host: String): String? = tvs.firstOrNull { it.host == host }?.name

    /** Код нужен для первой установки на часы; телевизор может обойтись подтверждением на экране. */
    val canInstall: Boolean get() = host.isNotBlank() && (code.length == 6 || knownHost || target == InstallTarget.TV)
}

class InstallerViewModel(app: Application) : AndroidViewModel(app) {
    private val installer = WatchAppInstaller(app, ::log)
    private val prefs = app.getSharedPreferences("watch_install", Context.MODE_PRIVATE)
    private val pairedHosts get() = prefs.getStringSet("paired", emptySet()).orEmpty()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private val _ui = MutableStateFlow(InstallerUi())
    val ui: StateFlow<InstallerUi> = _ui.asStateFlow()

    private var discoveryJobs = emptyList<Job>()

    /** Что ставим: приложение на часы или SyncTheme на телевизор. */
    fun setTarget(target: InstallTarget) {
        installer.target = target
        _ui.update {
            if (it.target == target) it
            else it.copy(target = target, manual = false, error = null, networkHint = false, device = null, done = false, linkPort = null).autoSelect()
        }
    }

    /** Поиск часов и телевизоров в сети работает, пока открыт экран установки. */
    fun startDiscovery() {
        if (discoveryJobs.isNotEmpty()) return
        discoveryJobs = listOf(
            viewModelScope.launch {
                AdbDiscovery.services(getApplication(), AdbDiscovery.TV).collect { list ->
                    _ui.update { it.copy(tvs = list).autoSelect() }
                }
            },
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
        val host = host.takeIf { it in hosts } ?: hosts.firstOrNull() ?: ""
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
        val tv = s.target == InstallTarget.TV
        if (s.host.isBlank()) {
            return@launch fail(
                if (tv) "Телевизор не найден. Проверьте, что телевизор и телефон в одной сети Wi‑Fi, или введите его адрес вручную."
                else "Часы не найдены. Проверьте, что часы и телефон в одной сети Wi‑Fi, или введите адрес вручную."
            )
        }
        _ui.update {
            it.copy(error = null, networkHint = false, done = false, device = null, progress = null, linkPort = null, vpn = AdbLink.vpnActive(getApplication()))
        }
        log("Старт: ${s.target.title} ${s.host}, сопряжение ${s.pairPort.ifBlank { "—" }}, подключение ${s.connectPort.ifBlank { "—" }}")
        try {
            if (s.code.length == 6) {
                val port = s.pairPort.toIntOrNull()
                    ?: return@launch fail(
                        if (tv) "Откройте на телевизоре «Отладка по Wi‑Fi» → «Подключить устройство с помощью кода», чтобы появился порт сопряжения."
                        else "Откройте на часах «Сопряжение нового устройства», чтобы появился порт сопряжения."
                    )
                busy(s.target.pick("Сопряжение с часами…", "Сопряжение с телевизором…"))
                installer.pair(s.host, port, s.code)
                prefs.edit().putStringSet("paired", pairedHosts + s.host).apply()
                _ui.update { it.copy(knownHost = true, code = "") }
            } else if (!s.knownHost && (!tv || s.pairPort.isNotBlank())) {
                return@launch fail(
                    if (tv) "Введите 6-значный код из окна «Подключить устройство с помощью кода» на телевизоре."
                    else "Введите 6-значный код с экрана «Сопряжение нового устройства» на часах."
                )
            }

            val device = connectWithRetry(s.host)
            _ui.update { it.copy(device = device) }
            if (tv && device.sdk in 1 until 28) {
                return@launch fail("На телевизоре ${device.model} Android API ${device.sdk}. SyncTheme нужен Android TV 9 или новее.")
            }
            if (!tv && !device.supportsWatchFacePush) {
                return@launch fail(
                    "На часах ${device.model} Android API ${device.sdk}. Для установки циферблатов нужен Wear OS 6 (API 36): " +
                        "обновите ПО часов и повторите."
                )
            }

            busy("Устанавливаю SyncTheme на ${device.model.ifBlank { s.target.title }}…")
            installer.install { p -> _ui.update { it.copy(progress = p) } }
            busy(s.target.pick("Настраиваю приложение на часах…", "Включаю заставку SyncTheme на телевизоре…"))
            installer.finishSetup()
            installer.disconnect()
            val linkPort = if (tv) {
                busy("Жду SyncTheme на телевизоре…")
                waitForReceiver(s.host)
            } else null
            log("Готово")
            _ui.update { it.copy(busy = null, progress = null, done = true, linkPort = linkPort) }
        } catch (e: AdbPairingRequiredException) {
            logError(e)
            prefs.edit().putStringSet("paired", pairedHosts - s.host).apply()
            _ui.update { it.copy(knownHost = false) }
            fail(
                if (tv) "Телевизор не узнал телефон — нужно сопряжение: «Отладка по Wi‑Fi» → «Подключить устройство с помощью кода» и введите код."
                else "Часы не узнали телефон — нужно сопряжение заново: откройте на часах «Сопряжение нового устройства» и введите код."
            )
        } catch (e: SSLException) {
            logError(e)
            fail("Сопряжение не удалось — проверьте код (он новый при каждом открытии окна сопряжения).")
        } catch (e: WatchUnreachableException) {
            logError(e)
            fail(
                if (tv) "${e.message}. Проверьте, что отладка на телевизоре включена, и попробуйте ещё раз — подсказки ниже."
                else "${e.message}. Разбудите экран часов и попробуйте ещё раз — подсказки ниже.",
                networkHint = true,
            )
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
     * задремать — поэтому до 3 попыток, каждый раз с актуальным портом из сети. Телевизор без
     * «Отладки по Wi‑Fi» слушает постоянный порт 5555 («Отладка по сети»).
     */
    private suspend fun connectWithRetry(host: String): WatchDevice {
        val tv = _ui.value.target == InstallTarget.TV
        val what = if (tv) "телевизору" else "часам"
        var last: Exception? = null
        repeat(3) { attempt ->
            busy(if (attempt == 0) "Подключаюсь к $what…" else "Подключаюсь к $what… (попытка ${attempt + 1} из 3)")
            val port = _ui.value.connectPort.toIntOrNull() ?: waitForConnectPort(host, if (tv && !_ui.value.knownHost) 3_000 else 10_000)
                ?: if (tv) LEGACY_PORT else throw WatchUnreachableException(
                    "Не найден порт подключения. Он указан на часах на экране «Отладка по Wi‑Fi» после двоеточия"
                )
            try {
                return installer.connect(host, port) {
                    busy("На телевизоре появилось «Разрешить отладку?» — выберите «Всегда разрешать» и нажмите «OK» пультом")
                }
            } catch (e: WatchUnreachableException) {
                last = e
                log("Попытка ${attempt + 1}: ${e.message}")
                delay(2_000)
            }
        }
        throw last ?: WatchUnreachableException("Не удалось подключиться к $what")
    }

    /** После сопряжения часы объявляют порт подключения в сети — ждём его немного. */
    private suspend fun waitForConnectPort(host: String, timeoutMs: Long): Int? = withTimeoutOrNull(timeoutMs) {
        _ui.first { ui -> ui.connect.any { it.host == host } }.connect.first { it.host == host }.port
    }

    /** SyncTheme на телевизоре запускается пару секунд — ждём, пока он начнёт слушать телефон. */
    private suspend fun waitForReceiver(host: String): Int? = withContext(Dispatchers.IO) {
        val wifi = AdbLink.wifiFor(getApplication(), host)
        withTimeoutOrNull(20_000) {
            var found: Int? = null
            while (found == null) {
                found = LinkProtocol.PORTS.take(3).firstOrNull { port ->
                    runCatching {
                        Socket().use { socket ->
                            wifi?.let { runCatching { it.bindSocket(socket) } }
                            socket.connect(InetSocketAddress(host, port), 700)
                        }
                    }.isSuccess
                }
                if (found == null) delay(1_000)
            }
            found
        }.also { log(if (it != null) "SyncTheme на телевизоре ждёт телефон, порт $it" else "SyncTheme на телевизоре пока не отвечает") }
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

    private companion object {
        /** «Отладка по сети» на телевизорах без «Отладки по Wi‑Fi». */
        const val LEGACY_PORT = 5555
    }

    override fun onCleared() {
        stopDiscovery()
        installer.close()
    }
}
