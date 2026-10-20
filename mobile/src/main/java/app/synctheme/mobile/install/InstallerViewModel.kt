package app.synctheme.mobile.install

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Job
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
    val device: WatchDevice? = null,
    val done: Boolean = false,
) {
    /** Код нужен, если с этими часами ещё не было сопряжения. */
    val needsCode get() = !knownHost
}

class InstallerViewModel(app: Application) : AndroidViewModel(app) {
    private val installer = WatchAppInstaller(app)
    private val prefs = app.getSharedPreferences("watch_install", Context.MODE_PRIVATE)
    private val pairedHosts get() = prefs.getStringSet("paired", emptySet()).orEmpty()

    private val _ui = MutableStateFlow(InstallerUi())
    val ui: StateFlow<InstallerUi> = _ui.asStateFlow()

    val bundledVersion: Long get() = installer.bundledVersion

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

    fun install() = viewModelScope.launch {
        val s = _ui.value
        if (s.host.isBlank()) return@launch fail("Часы не найдены. Проверьте, что часы и телефон в одной сети Wi‑Fi, или введите адрес вручную.")
        _ui.update { it.copy(error = null, done = false, device = null, progress = null) }
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

            busy("Подключаюсь к часам…")
            val connectPort = _ui.value.connectPort.toIntOrNull() ?: waitForConnectPort(s.host)
                ?: return@launch fail("Не найден порт подключения. Он указан на часах на экране «Отладка по Wi‑Fi» (IP-адрес и порт) — введите его вручную.")
            val device = installer.connect(s.host, connectPort)
            _ui.update { it.copy(device = device) }
            if (!device.supportsWatchFacePush) {
                return@launch fail(
                    "На часах ${device.model} Android API ${device.sdk}. Для установки циферблатов нужен Wear OS 6 (API 36): " +
                        "обновите ПО часов и повторите."
                )
            }

            busy("Устанавливаю SyncTheme на ${device.model.ifBlank { "часы" }}…")
            installer.install { p -> _ui.update { it.copy(progress = p) } }
            busy("Настраиваю приложение на часах…")
            installer.finishSetup()
            installer.disconnect()
            _ui.update { it.copy(busy = null, progress = null, done = true) }
        } catch (e: AdbPairingRequiredException) {
            prefs.edit().putStringSet("paired", pairedHosts - s.host).apply()
            _ui.update { it.copy(knownHost = false) }
            fail("Часы просят сопряжение заново: откройте «Сопряжение нового устройства» и введите код.")
        } catch (e: SSLException) {
            fail("Сопряжение не удалось — проверьте код (он меняется каждый раз при открытии окна сопряжения).")
        } catch (e: InstallException) {
            fail(e.message.orEmpty())
        } catch (e: Exception) {
            fail("Ошибка: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** После сопряжения часы объявляют порт подключения в сети — ждём его немного. */
    private suspend fun waitForConnectPort(host: String): Int? = withTimeoutOrNull(10_000) {
        _ui.first { ui -> ui.connect.any { it.host == host } }.connect.first { it.host == host }.port
    }

    private fun busy(text: String) = _ui.update { it.copy(busy = text, error = null) }

    private suspend fun fail(message: String) {
        installer.disconnect()
        _ui.update { it.copy(busy = null, progress = null, error = message) }
    }

    override fun onCleared() {
        stopDiscovery()
    }
}
