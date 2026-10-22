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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.devices.PhoneInfo
import app.synctheme.mobile.devices.Watch
import app.synctheme.mobile.install.AdbLink
import app.synctheme.mobile.link.DeviceKind
import app.synctheme.mobile.link.FoundDevice
import app.synctheme.mobile.link.LinkClient
import app.synctheme.mobile.link.LinkPush
import app.synctheme.mobile.link.LinkReceiver
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.link.Peer
import java.text.DateFormat
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

/** Значок устройства по его типу. */
fun DeviceKind.icon(): ImageVector = when (this) {
    DeviceKind.PHONE -> NavIcons.Phone
    DeviceKind.TABLET -> NavIcons.Tablet
    DeviceKind.TV -> NavIcons.Tv
}

/**
 * «Устройства»: это устройство и подключённые к нему часы, планшеты, телевизоры и телефоны — каждое
 * строкой со своим состоянием, а новое — одной кнопкой «Добавить устройство». По умолчанию — только телефон.
 */
@Composable
fun DevicesSection(vm: MainViewModel, onOpenInstaller: () -> Unit, onOpenTvInstaller: () -> Unit = {}) {
    val context = LocalContext.current
    val devices by vm.devices.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val push by vm.linkPush.collectAsStateWithLifecycle()
    val info = remember { PhoneInfo.detect() }
    val kind = remember { DeviceSettings.kind(context) }
    var adding by rememberSaveable { mutableStateOf(false) }
    Section("Устройства") {
        DeviceRow(
            kind.icon(), DeviceSettings.name(context),
            "Это устройство · Android ${Build.VERSION.RELEASE}" + (info.oneUi?.let { " · One UI ${PhoneInfo.format(it)}" } ?: ""),
        )
        // Планшет и телевизор принимают тему с телефона — это их главная роль.
        if (kind != DeviceKind.PHONE) {
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
        devices.peers.forEach { peer -> PeerRow(vm, peer, push[peer.id]) }
        AccentButton("Добавить устройство", { adding = true }, quiet = devices.watch || devices.peers.isNotEmpty(), height = 48.dp)
        if (!devices.watch && devices.peers.isEmpty()) {
            Text(
                "Часы, планшет, телевизор с Google TV или другой телефон — одна тема на всех, по одной кнопке «Применить».",
                style = MaterialTheme.typography.bodySmall, color = LocalLook.current.muted,
            )
        }
        // Этот телефон тоже может получать тему — с другого телефона.
        var receive by rememberSaveable { mutableStateOf(LinkStore.receiving(context)) }
        OtherDevice(NavIcons.Phone, "Получать тему с другого телефона", receive, { receive = !receive }) { ReceiverBlock(vm) }
    }
    if (adding) AddDeviceDialog(vm, onOpenInstaller, onOpenTvInstaller) { adding = false }
}

/** Строка устройства по Wi‑Fi: состояние, получает ли тему, отправить сейчас, отключить. */
@Composable
private fun PeerRow(vm: MainViewModel, peer: Peer, push: LinkPush?) {
    DeviceRow(
        peer.kind.icon(), peer.name, if (peer.enabled) pushText(push) else "Тему не получает", if (peer.enabled) pushDot(push) else Color.Gray,
        trailing = { Switch(checked = peer.enabled, onCheckedChange = { vm.setPeerEnabled(peer.id, it) }) },
    ) {
        if (peer.enabled) FilledTonalButton(onClick = { vm.sendTo(peer.id) }) { Text("Отправить тему", maxLines = 1) }
        TextButton(onClick = { vm.unlink(peer.id) }) { Text("Отключить", maxLines = 1) }
    }
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
    trailing: (@Composable () -> Unit)? = null,
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
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (actions != null) {
            FlowRow(Modifier.padding(start = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) { actions() }
        }
    }
}

fun pushText(push: LinkPush?): String = when {
    push == null -> "Тема уйдёт на него по «Применить»"
    push.ok -> "${push.text} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(push.at))}"
    else -> push.text
}

fun pushDot(push: LinkPush?): Color = when {
    push == null -> Color.Gray
    push.ok -> StatusOk
    push.pending -> StatusWait
    else -> StatusBad
}

/**
 * «Добавить устройство»: что нашлось рядом — часы по Bluetooth, планшеты, телевизоры и телефоны в той же
 * сети Wi‑Fi — и как подключить то, чего в списке нет.
 */
@Composable
private fun AddDeviceDialog(vm: MainViewModel, onOpenInstaller: () -> Unit, onOpenTvInstaller: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val devices by vm.devices.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val found by remember { LinkClient.discover(context) }.collectAsState(emptyList())
    var other by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshWatch() }

    fun link(device: FoundDevice) {
        vm.linkDevice(device.service.host, device.service.port, device.name, device.service.name)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить устройство") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Ищу рядом: часы — по Bluetooth, планшеты, телевизоры и телефоны — в вашей сети Wi‑Fi", style = MaterialTheme.typography.bodySmall, color = look.muted)
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
                // Устройства с включённым «Принимать тему с телефона».
                found.forEach { device ->
                    val linked = devices.peers.any { p -> device.id?.let { it == p.id } ?: (p.name == device.name) }
                    DeviceRow(device.kind.icon(), device.name, if (linked) "Уже подключён" else "${device.kind.title} в вашей сети Wi‑Fi", if (linked) StatusOk else null) {
                        FilledTonalButton(onClick = { link(device) }) { Text(if (linked) "Подключить заново" else "Подключить", maxLines = 1) }
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
                OtherDevice(NavIcons.Tv, "Телевизор с Google TV", other == "tv", { other = if (other == "tv") null else "tv" }) {
                    Text(
                        "Телевизоры и приставки с Google TV или Android TV. Телефон сам поставит на телевизор SyncTheme и включит " +
                            "заставку с темой — нужно один раз включить на телевизоре отладку в «Для разработчиков».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        onDismiss()
                        onOpenTvInstaller()
                    }) { Text("Установить на телевизор") }
                    Text(
                        "Если SyncTheme на телевизоре уже есть — откройте его там: телевизор появится в списке выше.",
                        style = MaterialTheme.typography.bodySmall, color = look.muted,
                    )
                }
                OtherDevice(NavIcons.Tablet, "Планшет или другой телефон", other == "tablet", { other = if (other == "tablet") null else "tablet" }) {
                    Text(
                        "Установите на него SyncTheme (тот же файл, что и на телефон), откройте «Настройки» → «Устройства» и включите " +
                            "«Принимать тему с телефона». Устройства должны быть в одной сети Wi‑Fi — тогда оно появится в списке выше.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ManualAddress { host, port ->
                        vm.linkDevice(host, port, host, null)
                        onDismiss()
                    }
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
private fun ManualAddress(onAddress: (host: String, port: Int) -> Unit) {
    var manual by remember { mutableStateOf("") }
    val host = manual.substringBeforeLast(':', "")
    val port = manual.substringAfterLast(':', "").toIntOrNull()
    OutlinedTextField(
        value = manual, onValueChange = { manual = it.trim() }, singleLine = true,
        label = { Text("Адрес с его экрана") }, placeholder = { Text("192.168.1.20:47231") },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { if (port != null) onAddress(host, port) }, enabled = host.isNotBlank() && port != null) { Text("Подключить") }
}

/**
 * Телефон подключает устройство: пока на устройстве не нажали «Подключить» — код сверки
 * (на устройстве он такой же). Виден на любом экране: телевизор подключается и сразу после установки.
 */
@Composable
fun LinkingDialog(vm: MainViewModel) {
    val linking by vm.linking.collectAsStateWithLifecycle()
    val l = linking ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Подключение «${l.device}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val code = l.code
                if (code == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Связываюсь…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("На «${l.device}» появился запрос. Проверьте, что код там такой же, и нажмите «Подключить»:", style = MaterialTheme.typography.bodyMedium)
                    Text(code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = LocalLook.current.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::cancelLinking) { Text("Отмена") } },
    )
}

/** Устройство: телефон просит подключиться — «Подключить» или «Отклонить» (код должен совпасть с телефоном). */
@Composable
fun LinkRequestDialog() {
    val state by LinkReceiver.state.collectAsStateWithLifecycle()
    val request = state.request ?: return
    // На телевизоре фокус сразу на «Подключить» — одно нажатие на пульте.
    val approve = remember { FocusRequester() }
    LaunchedEffect(request) { runCatching { approve.requestFocus() } }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Подключить телефон «${request.phone}»?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Тема с этого телефона будет приходить сюда сама. Код на телефоне должен быть таким же:", style = MaterialTheme.typography.bodyMedium)
                Text(request.code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = LocalLook.current.primary)
            }
        },
        confirmButton = { TextButton(onClick = { LinkReceiver.answer(true) }, modifier = Modifier.focusRequester(approve)) { Text("Подключить") } },
        dismissButton = { TextButton(onClick = { LinkReceiver.answer(false) }) { Text("Отклонить") } },
    )
}

/** Устройство: принимать тему с телефона. Подключается с телефона — здесь только подтвердить. */
@SuppressLint("BatteryLife")
@Composable
private fun ReceiverBlock(vm: MainViewModel) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val receiving by vm.receiving.collectAsStateWithLifecycle()
    val state by vm.receiver.collectAsStateWithLifecycle()
    // Уведомление о приёме в фоне и о запросе подключения; без разрешения приём тоже работает.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var unrestricted by remember { mutableStateOf(isUnrestricted(context)) }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { unrestricted = isUnrestricted(context) }
    SwitchRow("Принимать тему с телефона", receiving) { on ->
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.setReceiving(on)
    }
    if (!receiving) {
        Text(
            "Тема с телефона — и здесь: обои, экран блокировки и виджеты обновятся сами, когда на телефоне нажмут «Применить».",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    Text(
        state.phone?.let { "Подключён к телефону «$it» ✓" }
            ?: "Ждёт подключения. На телефоне: «Настройки» → «Устройства» → «Добавить устройство» → «${DeviceSettings.name(context)}», затем подтвердите здесь.",
        style = MaterialTheme.typography.bodyMedium,
    )
    state.last?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    state.address?.let { Text("Если телефон не найдёт это устройство — адрес: $it", style = MaterialTheme.typography.bodySmall, color = muted) }
    if (remember { AdbLink.vpnActive(context) }) {
        Text(
            "Здесь включён VPN. Если телефон не может подключиться, выключите VPN или разрешите в нём локальную сеть.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
        )
    }
    if (!unrestricted) {
        Text(
            "Чтобы тема приходила и с выключенным экраном, разрешите SyncTheme работать в фоне без ограничений.",
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
