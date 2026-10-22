package app.synctheme.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.devices.InstallGuide
import app.synctheme.mobile.devices.MyDevices
import app.synctheme.mobile.devices.PhoneInfo
import app.synctheme.mobile.devices.Watch
import app.synctheme.mobile.install.AdbService
import app.synctheme.mobile.tablet.TabletClient
import app.synctheme.mobile.tablet.TabletReceiver
import app.synctheme.mobile.tablet.TabletStore

/** Список для выбора: строки с радиокнопкой. */
@Composable
private fun <T> ChoiceList(items: List<T>, selected: T?, title: (T) -> String, onPick: (T) -> Unit) {
    Column {
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = item == selected, onClick = { onPick(item) })
                Text(title(item), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Выбор часов из поддерживаемых (Wear OS 6+). */
@Composable
fun WatchPicker(selected: Watch?, onPick: (Watch) -> Unit) {
    ChoiceList(Watch.entries, selected, { it.title }, onPick)
}

/**
 * «Устройства»: этот телефон (работает любой Android 9+), а часы и планшет — по желанию.
 * По умолчанию — только телефон: часы и планшет появляются в приложении, когда их подключили.
 */
@Composable
fun DevicesSection(vm: MainViewModel, connectedWatch: String?, onOpenInstaller: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val devices by vm.devices.collectAsStateWithLifecycle()
    val info = remember { PhoneInfo.detect() }
    val isTablet = remember { DeviceSettings.isTablet(context) }
    Section("Устройства") {
        Text(
            "SyncTheme работает на любом телефоне и планшете с Android 9 и новее. Часы и планшет подключаются по желанию.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        Text(
            (if (isTablet) "Этот планшет: " else "Этот телефон: ") + DeviceSettings.name(context) +
                " · Android ${Build.VERSION.RELEASE}" + (info.oneUi?.let { " · One UI ${PhoneInfo.format(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )

        // ---------------------------------------------------------------- часы
        // Часы на Wear OS сопрягаются только с телефоном — на планшете этого раздела нет.
        if (!isTablet || devices.watch) {
            Text("Часы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            WatchBlock(vm, devices.watch, connectedWatch, onOpenInstaller)
        }

        // ---------------------------------------------------------------- планшет
        Text("Планшет", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (isTablet) ReceiverBlock(vm) else TabletBlock(vm, devices.tablet)
    }
}

/** Часы: включить по желанию, выбрать модель и установить циферблат. */
@Composable
private fun WatchBlock(vm: MainViewModel, enabled: Boolean, connectedWatch: String?, onOpenInstaller: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SwitchRow("Часы на Wear OS", enabled) { vm.setWatchEnabled(it) }
    if (!enabled) {
        Text(
            "Циферблат в цветах темы для Galaxy Watch и Pixel Watch на Wear OS 6 и новее. Включите — появятся вкладка «Часы» и отправка циферблата по «Применить».",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    } else {
        var watch by remember(connectedWatch) { mutableStateOf(MyDevices.watch(context, connectedWatch)) }
        WatchPicker(watch) {
            watch = it
            MyDevices.setWatch(context, it)
        }
        watch?.let { w ->
            ExpandableSection("Как установить на ${w.title.substringBefore(" /")}", summary = "Пошагово для ваших часов") {
                InstallGuide.watch(w).forEachIndexed { i, step -> StepText(i + 1, step) }
            }
        }
        OutlinedButton(onClick = onOpenInstaller) { Text("Установка на часы") }
    }
}

/** Телефон: подключить планшет, чтобы тема уходила и на него. */
@Composable
private fun TabletBlock(vm: MainViewModel, paired: Boolean) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var pairing by remember { mutableStateOf(false) }
    if (paired) {
        val name = remember(paired) { TabletStore.tablet(context)?.name.orEmpty() }
        Text("Подключён: $name ✓ — тема уходит на него по «Применить»", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pairing = true }) { Text("Связать заново") }
            TextButton(onClick = vm::unpairTablet) { Text("Отключить") }
        }
    } else {
        Text(
            "Та же тема — и на планшете: обои, экран блокировки и виджеты. Установите SyncTheme на планшет, " +
                "включите там «Принимать тему с телефона» и нажмите «Связать с телефоном». Устройства должны быть в одной сети Wi‑Fi.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        FilledTonalButton(onClick = { pairing = true }) { Text("Подключить планшет") }
    }
    if (pairing) PairDialog(vm) { pairing = false }
}

/** Связывание: планшет из найденных в сети (или адрес вручную) и код с его экрана. */
@Composable
private fun PairDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val found by remember { TabletClient.discover(context) }.collectAsState(emptyList())
    var chosen by remember { mutableStateOf<AdbService?>(null) }
    var manual by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val target = chosen ?: manual.trim().let { m ->
        val host = m.substringBeforeLast(':', "")
        m.substringAfterLast(':', "").toIntOrNull()?.takeIf { host.isNotBlank() }?.let { AdbService("", host, it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключить планшет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (found.isEmpty()) "Ищу планшет в сети… На планшете включите «Принимать тему с телефона» и нажмите «Связать с телефоном»."
                    else "Выберите планшет:",
                    style = MaterialTheme.typography.bodySmall,
                )
                ChoiceList(found, chosen, { it.name.removePrefix("SyncTheme · ") }) { chosen = it }
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("Код с планшета") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (found.isEmpty()) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it.trim(); chosen = null }, singleLine = true,
                        label = { Text("Адрес с планшета (если не нашёлся)") }, placeholder = { Text("192.168.1.20:40123") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = target ?: return@TextButton
                vm.pairTablet(t.host, t.port, code, t.name.takeIf { it.isNotBlank() }, onDismiss)
            }, enabled = target != null && code.length == 6) { Text("Связать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Планшет: принимать тему с телефона и связаться с ним по коду. */
@Composable
private fun ReceiverBlock(vm: MainViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val receiving by vm.receiving.collectAsStateWithLifecycle()
    val state by vm.receiver.collectAsStateWithLifecycle()
    // Уведомление о приёме в фоне — тихое; без разрешения служба тоже работает, просто без него.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SwitchRow("Принимать тему с телефона", receiving) { on ->
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.setReceiving(on)
    }
    if (!receiving) {
        Text(
            "Тема с телефона — и на этом планшете: обои, экран блокировки и виджеты обновятся сами, когда на телефоне нажмут «Применить».",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    Text(
        state.phone?.let { "Связан с телефоном «$it» ✓" } ?: "Пока не связан с телефоном",
        style = MaterialTheme.typography.bodyMedium,
    )
    state.last?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    val code = state.code
    if (code == null) {
        OutlinedButton(onClick = { TabletReceiver.openPairing() }) { Text(if (state.phone == null) "Связать с телефоном" else "Связать с другим телефоном") }
    } else {
        Text("На телефоне: «Ещё» → «Устройства» → «Подключить планшет», выберите этот планшет и введите код:", style = MaterialTheme.typography.bodySmall)
        Text(code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = LocalLook.current.primary)
        state.address?.let { Text("Если телефон не найдёт планшет — адрес: $it", style = MaterialTheme.typography.bodySmall, color = muted) }
        Text("Код действует 10 минут.", style = MaterialTheme.typography.bodySmall, color = muted)
        TextButton(onClick = TabletReceiver::closePairing) { Text("Отмена") }
    }
}
