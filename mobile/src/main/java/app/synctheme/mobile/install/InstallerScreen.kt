package app.synctheme.mobile.install

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
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
                            "Подойдут любые часы на Wear OS 6 и новее.",
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
private fun StepText(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) { Text("$number", style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrepareStep() {
    Section("1. Подготовьте часы") {
        StepText(1, "Часы: «Настройки» → «О часах» → «Программное обеспечение» → 7 раз нажмите «Версия ПО».")
        StepText(2, "«Настройки» → «Параметры разработчика» → включите «Отладка ADB» и «Отладка по Wi‑Fi».")
        StepText(3, "Подключите часы к той же сети Wi‑Fi, что и телефон («Настройки» → «Подключения» → Wi‑Fi).")
        StepText(4, "Откройте «Отладка по Wi‑Fi» → «Сопряжение нового устройства». На часах появится 6-значный код.")
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
                    if (ui.pairing.any { it.host == host }) add("ждут сопряжения")
                    if (ui.connect.any { it.host == host }) add("отладка включена")
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
        when {
            ui.done -> {
                Text(
                    "Готово! SyncTheme установлен и открыт на часах.\n" +
                        "Вернитесь назад и нажмите «Применить» — циферблат появится на часах.\n" +
                        "«Отладку по Wi‑Fi» на часах теперь можно выключить.",
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
            ) { Text("Установить на часы", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
