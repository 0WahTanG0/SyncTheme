package app.synctheme.mobile.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.devices.PhoneInfo
import app.synctheme.mobile.devices.Watch
import app.synctheme.mobile.install.AdbService
import app.synctheme.mobile.tablet.TabletClient
import app.synctheme.mobile.tablet.TabletPush
import app.synctheme.mobile.tablet.TabletReceiver
import app.synctheme.mobile.tablet.TabletStore
import java.text.DateFormat
import kotlinx.coroutines.delay
import java.util.Date

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

/** Цвета точки состояния: на связи, ждёт действия, не отвечает. */
val StatusOk = Color(0xFF39D86A)
val StatusWait = Color(0xFFFFCC00)
val StatusBad = Color(0xFFFF3B30)

/**
 * «Устройства»: это устройство и подключённые к нему часы и планшет — каждое строкой со своим
 * состоянием, а новое — одной кнопкой «Добавить устройство». По умолчанию — только телефон.
 */
@Composable
fun DevicesSection(vm: MainViewModel, onOpenInstaller: () -> Unit) {
    val context = LocalContext.current
    val devices by vm.devices.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val push by vm.tabletPush.collectAsStateWithLifecycle()
    val info = remember { PhoneInfo.detect() }
    val isTablet = remember { DeviceSettings.isTablet(context) }
    var adding by rememberSaveable { mutableStateOf(false) }
    Section("Устройства") {
        DeviceRow(
            if (isTablet) NavIcons.Tablet else NavIcons.Phone,
            DeviceSettings.name(context),
            (if (isTablet) "Этот планшет" else "Этот телефон") + " · Android ${Build.VERSION.RELEASE}" +
                (info.oneUi?.let { " · One UI ${PhoneInfo.format(it)}" } ?: ""),
        )
        if (isTablet) {
            ReceiverBlock(vm)
            return@Section
        }
        if (devices.watch) {
            val (dot, status) = watchStatus(connection)
            DeviceRow(NavIcons.Watch, connection.appNodeName ?: connection.connectedNames.firstOrNull() ?: "Часы", status, dot) {
                if (connection.hasWatch && !connection.hasApp) {
                    FilledTonalButton(onClick = onOpenInstaller) { Text("Установить", maxLines = 1) }
                }
                TextButton(onClick = { vm.setWatchEnabled(false) }) { Text("Отключить", maxLines = 1) }
            }
        }
        if (devices.tablet) {
            val name = remember(devices) { TabletStore.tablet(context)?.name ?: "Планшет" }
            DeviceRow(NavIcons.Tablet, name, pushText(push), pushDot(push)) {
                FilledTonalButton(onClick = vm::sendToTablet) { Text("Отправить тему", maxLines = 1) }
                TextButton(onClick = vm::unpairTablet) { Text("Отключить", maxLines = 1) }
            }
        }
        AccentButton("Добавить устройство", { adding = true }, quiet = devices.watch || devices.tablet, height = 48.dp)
        if (!devices.watch && !devices.tablet) {
            Text(
                "Часы на Wear OS, планшет или другой телефон на Android — одна тема на всех, по одной кнопке «Применить».",
                style = MaterialTheme.typography.bodySmall, color = LocalLook.current.muted,
            )
        }
    }
    if (adding) AddDeviceDialog(vm, onOpenInstaller) { adding = false }
}

/** Состояние часов одной строкой и цвет точки. */
fun watchStatus(connection: app.synctheme.mobile.watch.WatchConnection): Pair<Color, String> = when {
    !connection.checked -> Color.Gray to "Проверяю связь…"
    connection.hasApp -> StatusOk to "На связи — циферблат обновляется по «Применить»"
    connection.hasWatch -> StatusWait to "Осталось установить на часы SyncTheme"
    else -> StatusBad to "Не на связи — проверьте Bluetooth"
}

/** Строка устройства: значок, имя, состояние (с цветной точкой) и кнопки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    dot: Color? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val look = LocalLook.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(look.control), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = look.text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = look.text, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dot != null) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = look.muted)
                }
            }
        }
        if (actions != null) {
            FlowRow(Modifier.padding(start = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

fun pushText(push: TabletPush?): String = when {
    push == null -> "Тема уйдёт на него по «Применить»"
    push.ok -> "${push.text} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(push.at))}"
    else -> push.text
}

fun pushDot(push: TabletPush?): Color = when {
    push == null -> Color.Gray
    push.ok -> StatusOk
    push.pending -> StatusWait
    else -> StatusBad
}

/**
 * «Добавить устройство»: что нашлось рядом — часы по Bluetooth, планшеты и телефоны в той же
 * сети Wi‑Fi — и как подключить то, чего в списке нет.
 */
@Composable
private fun AddDeviceDialog(vm: MainViewModel, onOpenInstaller: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val devices by vm.devices.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val found by remember { TabletClient.discover(context) }.collectAsState(emptyList())
    val paired = remember(devices) { TabletStore.tablet(context) }
    var pairing by remember { mutableStateOf<AdbService?>(null) }
    var other by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshWatch() }

    pairing?.let { target ->
        CodeDialog(vm, target, onBack = { pairing = null }, onDone = onDismiss)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить устройство") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Ищу рядом: часы — по Bluetooth, планшеты и телефоны — в вашей сети Wi‑Fi", style = MaterialTheme.typography.bodySmall, color = look.muted)
                }
                // Часы, подключённые к телефону по Bluetooth (Galaxy Wearable, Pixel Watch).
                if (!devices.watch) {
                    connection.connectedNames.forEach { name ->
                        val ready = connection.hasApp
                        DeviceRow(
                            NavIcons.Watch, name,
                            if (ready) "SyncTheme на часах есть — подключаются сразу" else "На связи по Bluetooth. SyncTheme на часы ставится один раз",
                            if (ready) StatusOk else StatusWait,
                        ) {
                            FilledTonalButton(onClick = {
                                vm.connectWatch(name)
                                onDismiss()
                                if (!ready) onOpenInstaller()
                            }) { Text(if (ready) "Подключить" else "Подключить и установить", maxLines = 1) }
                        }
                    }
                }
                // Планшеты и телефоны с включённым «Принимать тему с телефона».
                found.forEach { service ->
                    val name = service.name.removePrefix("SyncTheme · ")
                    val isPaired = paired?.let { it.service == service.name || it.name == name } == true
                    DeviceRow(NavIcons.Tablet, name, if (isPaired) "Уже подключён" else "В вашей сети Wi‑Fi", if (isPaired) StatusOk else null) {
                        FilledTonalButton(onClick = { pairing = service }) { Text(if (isPaired) "Связать заново" else "Подключить", maxLines = 1) }
                    }
                }
                val nothing = (devices.watch || connection.connectedNames.isEmpty()) && found.isEmpty()
                if (nothing) Text("Пока ничего не нашлось. Как подключить нужное устройство — ниже.", style = MaterialTheme.typography.bodyMedium)

                Text("Нет в списке?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!devices.watch) {
                    OtherDevice(NavIcons.Watch, "Часы на Wear OS", other == "watch", { other = if (other == "watch") null else "watch" }) {
                        Text(
                            "Galaxy Watch4–Watch8, Watch Ultra, Pixel Watch 2–4 с Wear OS 6 и новее. Подключите часы к телефону " +
                                "в приложении Galaxy Wearable или Pixel Watch — они появятся в списке выше.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            vm.connectWatch(null)
                            onDismiss()
                            onOpenInstaller()
                        }) { Text("Установить на часы по Wi‑Fi") }
                    }
                }
                OtherDevice(NavIcons.Tablet, "Планшет или другой телефон", other == "tablet", { other = if (other == "tablet") null else "tablet" }) {
                    Text(
                        "Установите на него SyncTheme (тот же файл, что и на телефон), откройте «Настройки» → «Устройства» и включите " +
                            "«Принимать тему с телефона». Устройства должны быть в одной сети Wi‑Fi — тогда он появится в списке выше.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ManualAddress { pairing = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** «Нет в списке?»: строка, по нажатию — подсказка, как подключить. */
@Composable
private fun OtherDevice(icon: ImageVector, title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(look.control).clickable(onClick = onToggle).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = look.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = look.text, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleMedium, color = look.muted)
        }
        if (open) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** Адрес вручную — если роутер не пропускает поиск устройств. */
@Composable
private fun ManualAddress(onAddress: (AdbService) -> Unit) {
    var manual by remember { mutableStateOf("") }
    val host = manual.substringBeforeLast(':', "")
    val port = manual.substringAfterLast(':', "").toIntOrNull()
    OutlinedTextField(
        value = manual, onValueChange = { manual = it.trim() }, singleLine = true,
        label = { Text("Адрес с его экрана") }, placeholder = { Text("192.168.1.20:47231") },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { onAddress(AdbService("", host, port ?: 0)) }, enabled = host.isNotBlank() && port != null) { Text("Дальше") }
}

/** Код с экрана планшета — шесть цифр; с ними телефон и планшет договариваются о ключе. */
@Composable
private fun CodeDialog(vm: MainViewModel, target: AdbService, onBack: () -> Unit, onDone: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val name = target.name.removePrefix("SyncTheme · ").ifBlank { target.host }
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Код с «$name»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Код из шести цифр — на его экране: «Настройки» → «Устройства», включено «Принимать тему с телефона». " +
                        "Если он уже связан с другим телефоном — нажмите там «Связать с другим телефоном».",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("Код") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalLook.current.muted) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.pairTablet(target.host, target.port, code, target.name.takeIf { it.isNotBlank() }, onDone) },
                enabled = code.length == 6 && busy == null,
            ) { Text("Связать") }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Назад") } },
    )
}

/** Планшет: принимать тему с телефона; пока не связан — сразу показывает код для телефона. */
@SuppressLint("BatteryLife")
@Composable
private fun ReceiverBlock(vm: MainViewModel) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val receiving by vm.receiving.collectAsStateWithLifecycle()
    val state by vm.receiver.collectAsStateWithLifecycle()
    // Уведомление о приёме в фоне — тихое; без разрешения служба тоже работает, просто без него.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var unrestricted by remember { mutableStateOf(isUnrestricted(context)) }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { unrestricted = isUnrestricted(context) }
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
    // Не связан с телефоном — код сразу на экране, без лишнего нажатия; истёк — новый (или окно закрывается).
    LaunchedEffect(state.phone) { if (state.phone == null && state.code == null) TabletReceiver.openPairing() }
    LaunchedEffect(state.code) {
        if (state.code == null) return@LaunchedEffect
        delay(TabletReceiver.PAIRING_MS)
        if (state.phone == null) TabletReceiver.openPairing() else TabletReceiver.closePairing()
    }
    Text(
        state.phone?.let { "Связан с телефоном «$it» ✓" } ?: "Пока не связан с телефоном",
        style = MaterialTheme.typography.bodyMedium,
    )
    state.last?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    val code = state.code
    if (code == null) {
        OutlinedButton(onClick = { TabletReceiver.openPairing() }) { Text("Связать с другим телефоном") }
    } else {
        Text("На телефоне: «Настройки» → «Устройства» → «Добавить устройство», выберите этот планшет и введите код:", style = MaterialTheme.typography.bodySmall)
        Text(code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = LocalLook.current.primary)
        state.address?.let { Text("Если телефон не найдёт планшет — адрес: $it", style = MaterialTheme.typography.bodySmall, color = muted) }
        Text("Код действует 10 минут.", style = MaterialTheme.typography.bodySmall, color = muted)
        if (state.phone != null) TextButton(onClick = TabletReceiver::closePairing) { Text("Отмена") }
    }
    if (!unrestricted) {
        Text(
            "Чтобы планшет принимал тему и с выключенным экраном, разрешите SyncTheme работать в фоне без ограничений.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        FilledTonalButton(onClick = {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            runCatching { battery.launch(intent) }.onFailure { battery.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }) { Text("Работать в фоне без ограничений") }
    }
}

private fun isUnrestricted(context: android.content.Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
