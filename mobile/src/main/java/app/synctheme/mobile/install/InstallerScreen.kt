package app.synctheme.mobile.install

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.devices.InstallGuide
import app.synctheme.mobile.devices.MyDevices
import app.synctheme.mobile.ui.AppBackdrop
import app.synctheme.mobile.ui.StepText
import app.synctheme.mobile.ui.WatchPicker
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.Section

/** Установка приложения SyncTheme на часы прямо с телефона. */
@Composable
fun WatchInstallerScreen(vm: InstallerViewModel, accentColor: Int, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.startDiscovery()
        onDispose { vm.stopDiscovery() }
    }

    AppTheme(accentColor) {
        AppBackdrop {
            LazyColumn(
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                        Text("Установка на часы", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Приложение для часов уже внутри SyncTheme — сторонние программы и компьютер не нужны. " +
                            "Нужны часы на Wear OS 6 или новее: Galaxy Watch4–Watch8 и Watch Ultra с One UI 8 Watch, " +
                            "Pixel Watch 2–4.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                item { PrepareStep() }
                item { FindStep(ui, vm) }
                item { CodeStep(ui, vm) }
                item { ActionStep(ui, vm, onClose) }
            }
        }
    }
}


@Composable
private fun PrepareStep() {
    val context = LocalContext.current
    var watch by remember { mutableStateOf(MyDevices.watch(context)) }
    Section("1. Подготовьте часы") {
        // Пункты меню у Galaxy Watch и Pixel Watch разные — инструкция под выбранные часы.
        WatchPicker(watch) {
            watch = it
            MyDevices.setWatch(context, it)
        }
        val chosen = watch
        if (chosen == null) {
            Text("Выберите свои часы — покажу, где что включить.", style = MaterialTheme.typography.bodyMedium)
        } else {
            // Последний шаг общей инструкции — про выключение отладки после установки, он ниже, в «Готово».
            InstallGuide.watch(chosen).dropLast(1).forEachIndexed { i, step -> StepText(i + 1, step) }
            StepText(InstallGuide.watch(chosen).size, "Пока идёт установка, не давайте экрану часов погаснуть — касайтесь его.")
        }
    }
}

@Composable
private fun FindStep(ui: InstallerUi, vm: InstallerViewModel) {
    Section("2. Часы в сети") {
        val hosts = (ui.pairing.map { it.host } + ui.connect.map { it.host }).distinct()
        if (hosts.isEmpty() && !ui.manual) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Ищу часы с включённой «Отладкой по Wi‑Fi»…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!ui.manual) {
            hosts.forEach { host ->
                val states = buildList {
                    ui.pairing.firstOrNull { it.host == host }?.let { add("ждут сопряжения, порт ${it.port}") }
                    ui.connect.firstOrNull { it.host == host }?.let { add("отладка включена, порт ${it.port}") }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { vm.selectHost(host) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = ui.host == host, onClick = { vm.selectHost(host) })
                    Column {
                        Text("Часы $host", style = MaterialTheme.typography.bodyLarge)
                        Text(states.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = { vm.setManual(!ui.manual) }) {
            Text(if (ui.manual) "Искать автоматически" else "Ввести адрес вручную")
        }
        if (ui.manual) {
            OutlinedTextField(
                value = ui.host, onValueChange = { vm.edit(host = it.trim()) }, singleLine = true,
                label = { Text("IP-адрес часов") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.pairPort, onValueChange = { vm.edit(pairPort = it.filter(Char::isDigit)) }, singleLine = true,
                    label = { Text("Порт сопряжения") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = ui.connectPort, onValueChange = { vm.edit(connectPort = it.filter(Char::isDigit)) }, singleLine = true,
                    label = { Text("Порт подключения") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(
                "Порт сопряжения — в окне «Сопряжение нового устройства», порт подключения — " +
                    "на экране «Отладка по Wi‑Fi» (после двоеточия в IP-адресе).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeStep(ui: InstallerUi, vm: InstallerViewModel) {
    Section("3. Код сопряжения") {
        OutlinedTextField(
            value = ui.code, onValueChange = { vm.edit(code = it) }, singleLine = true,
            label = { Text("6 цифр с экрана часов") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (ui.knownHost) {
            Text(
                "С этими часами уже было сопряжение — код можно не вводить.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = vm::resetPairing) { Text("Сделать сопряжение заново") }
        }
    }
}

@Composable
private fun ActionStep(ui: InstallerUi, vm: InstallerViewModel, onClose: () -> Unit) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.device?.let { d ->
            Text(
                "Подключено: ${d.model.ifBlank { "часы" }} · Android API ${d.sdk}" +
                    (d.installedVersion?.let { " · установлена версия $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ui.error?.let { Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodyMedium) }
        if (ui.networkHint) NetworkHints(ui.vpn)
        when {
            ui.done -> {
                Text(
                    "Готово! SyncTheme установлен и открыт на часах.\n" +
                        "Вернитесь назад и нажмите «Применить» — циферблат появится на часах.\n" +
                        "Включите на часах Bluetooth, если выключали, а «Отладку по Wi‑Fi» теперь можно выключить.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Готово") }
            }
            ui.busy != null -> {
                Text(ui.busy, style = MaterialTheme.typography.bodyMedium)
                val progress = ui.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            else -> Button(
                onClick = { vm.install() },
                enabled = ui.host.isNotBlank() && (ui.code.length == 6 || ui.knownHost),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (ui.error != null) "Попробовать ещё раз" else "Установить на часы", style = MaterialTheme.typography.titleMedium) }
        }
        if (ui.log.isNotEmpty()) LogCard(ui.log)
    }
}

@Composable
private fun NetworkHints(vpn: Boolean) {
    Section("Если часы не подключаются") {
        if (vpn) {
            Text(
                "На телефоне включён VPN. SyncTheme старается подключаться к часам мимо него, но некоторые VPN этого не дают — " +
                    "выключите VPN на время установки.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
        }
        StepText(1, "Разбудите экран часов и не давайте ему погаснуть, пока идёт установка.")
        StepText(2, "Проверьте, что «Отладка по Wi‑Fi» на часах всё ещё включена: она выключается при смене сети Wi‑Fi.")
        StepText(
            3,
            "Многие часы (например, Galaxy Watch) отключают Wi‑Fi, пока связаны с телефоном по Bluetooth. На время установки выключите на часах " +
                "Bluetooth («Настройки» → «Подключения» → Bluetooth), включите Wi‑Fi и повторите. После установки Bluetooth включите обратно.",
        )
        StepText(4, "Сравните адрес и порт на экране «Отладка по Wi‑Fi» с найденными выше; если отличаются — введите вручную.")
        StepText(5, "Не помогло — «Сделать сопряжение заново» в шаге 3 и новый код с часов.")
    }
}

@Composable
private fun LogCard(log: List<String>) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Section("Журнал") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть" else "Показать подробности") }
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("SyncTheme log", log.joinToString("\n")))
            }) { Text("Скопировать") }
        }
        if (expanded) {
            Text(
                log.takeLast(60).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
