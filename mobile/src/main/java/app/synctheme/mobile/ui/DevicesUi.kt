package app.synctheme.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.synctheme.mobile.devices.InstallGuide
import app.synctheme.mobile.devices.MyDevices
import app.synctheme.mobile.devices.Phone
import app.synctheme.mobile.devices.PhoneInfo
import app.synctheme.mobile.devices.Watch

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
 * «Мои устройства»: телефон и часы из списка поддерживаемых, а под ними — инструкция, как всё
 * установить именно на них.
 */
@Composable
fun MyDevicesSection(connectedWatch: String?) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val info = remember { PhoneInfo.detect() }
    var phone by remember { mutableStateOf(MyDevices.phone(context)) }
    var watch by remember(connectedWatch) { mutableStateOf(MyDevices.watch(context, connectedWatch)) }
    ExpandableSection(
        "Мои устройства",
        summary = listOfNotNull(phone?.title?.substringBefore(" /"), watch?.title?.substringBefore(" /")).joinToString(" + ").ifEmpty { "Выберите телефон и часы" },
        initiallyExpanded = phone == null || watch == null,
    ) {
        Text(
            "SyncTheme работает целиком на телефонах Samsung Galaxy с One UI 8 и новее и на часах с Wear OS 6 и новее.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        val status = when {
            !info.samsung -> "Этот телефон не Samsung: эффект глубины на экране блокировки и часть функций работать не будут."
            info.oneUi != null && info.oneUi < 8.0 -> "На этом телефоне One UI ${PhoneInfo.format(info.oneUi)} — обновите его до One UI 8."
            else -> "Этот телефон: ${info.phone?.title ?: info.model}" + (info.oneUi?.let { ", One UI ${PhoneInfo.format(it)}" } ?: "") + " ✓"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = if (info.supported) LocalLook.current.text else MaterialTheme.colorScheme.error)
        Text("Телефон", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ChoiceList(Phone.entries, phone, { it.title }) {
            phone = it
            MyDevices.setPhone(context, it)
        }
        Text("Часы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        WatchPicker(watch) {
            watch = it
            MyDevices.setWatch(context, it)
        }
    }
    val chosenWatch = watch
    if (phone != null || chosenWatch != null) {
        ExpandableSection("Как установить", summary = "Пошагово для ваших устройств") {
            if (phone != null) {
                Text("Телефон: ${phone?.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                InstallGuide.phone().forEachIndexed { i, step -> StepText(i + 1, step) }
            }
            if (chosenWatch != null) {
                Text("Часы: ${chosenWatch.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                InstallGuide.watch(chosenWatch).forEachIndexed { i, step -> StepText(i + 1, step) }
            }
        }
    }
}
