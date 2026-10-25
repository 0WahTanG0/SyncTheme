package app.synctheme.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Modifier
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.mobile.devices.PhoneInfo
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.wallpaper.ClockWallpaper
import app.synctheme.mobile.widget.AppCatalog
import app.synctheme.mobile.widget.AppPickerDialog

// ---------------------------------------------------------------- часы на обоях

fun wallClockSummary(theme: Theme): String {
    val wall = theme.wallClock ?: return "Выключены"
    if (wall.place != null) return "Вкл · где вы поставили"
    if (wall.depth && theme.background.type == BackgroundType.PHOTO) return "Вкл · рядом с объектом фото"
    val layout = when (wall.layout) {
        WallClockLayout.TOP -> "вверху"
        WallClockLayout.STACKED -> "столбиком"
        WallClockLayout.CENTER -> "по центру"
    }
    return "Вкл · $layout"
}

/** Часы прямо на обоях рабочего стола (живые обои) и эффект глубины. */
@Composable
fun WallClockContent(theme: Theme, vm: MainViewModel, onArrange: () -> Unit = {}) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val wall = theme.wallClock
    val photo = theme.background.type == BackgroundType.PHOTO
    SwitchRow("Часы на обоях", wall != null) { on -> vm.setWallClock(if (on) WallClock(depth = photo) else null) }
    if (wall == null) {
        Text(
            "Крупные часы рисуются прямо на обоях рабочего стола, а главный объект фото — человек, питомец, гора — " +
                "встаёт поверх цифр, как будто часы за ним.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    val depth = wall.depth && photo
    SwitchRow("Эффект глубины", depth, enabled = photo) { on -> vm.setWallClock(wall.copy(depth = on)) }
    Text(
        when {
            !photo -> "Эффект глубины работает с фото — выберите фото в «Фон»."
            depth -> "Объект с фото стоит перед часами и закрывает часть цифр — как на iPhone. Сами часы встают " +
                "с противоположной от объекта стороны; передвинуть их можно кнопкой ниже."
            else -> "Объект с фото встанет перед часами, а сами часы — рядом с ним."
        },
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
    if (depth) {
        OutlinedButton(onClick = { vm.editSubject(theme.background) }) { Text("Выбрать объект") }
        Text(
            "Если телефон не увидел объект или выбрал не тот — выберите его касанием или обведите пальцем.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
    SwitchRow("Цифры из стекла", wall.glass) { on -> vm.setWallClock(wall.copy(glass = on)) }
    Text("Высота цифр", style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var stretch by remember(wall.stretch) { mutableStateOf(wall.stretch) }
        Slider(
            stretch, { stretch = it }, valueRange = 1f..ThemeRenderer.MAX_STRETCH,
            onValueChangeFinished = { vm.setWallClock(wall.copy(stretch = stretch)) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(false, vm::setWallClockFullScreen, label = { Text("Во весь экран") })
    }
    SwitchRow("И на экране блокировки", wall.onLock) { on -> vm.setWallClock(wall.copy(onLock = on)) }
    if (wall.onLock) LockClockHint()
    val place = wall.place
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onArrange) { Text(arrangeLabel(theme, ArrangeTarget.HOME) ?: "Подвинуть часы") }
        if (place != null) TextButton(onClick = { vm.setWallClock(wall.copy(place = null)) }) { Text("Вернуть само") }
    }
    if (place != null) {
        Text("Часы стоят там, куда вы их передвинули.", style = MaterialTheme.typography.bodySmall, color = muted)
    } else if (!depth) {
        // С глубиной место часов выбирается по объекту; без неё — одна из раскладок.
        ChipRow {
            listOf(WallClockLayout.TOP to "Вверху", WallClockLayout.STACKED to "Столбиком", WallClockLayout.CENTER to "По центру").forEach { (l, label) ->
                FilterChip(wall.layout == l, { vm.setWallClock(wall.copy(layout = l)) }, label = { Text(label) })
            }
        }
    }
    SwitchRow("Дата над часами", wall.showDate) { on -> vm.setWallClock(wall.copy(showDate = on)) }
    SwitchRow("Открывать приложение по нажатию", wall.tapToOpen) { on -> vm.setWallClock(wall.copy(tapToOpen = on)) }
    if (wall.tapToOpen) {
        var picking by remember { mutableStateOf(false) }
        Text(
            "Нажатие на часы открывает: ${AppCatalog.wallClockTarget(context, wall.openApp)}.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picking = true }) { Text("Выбрать приложение") }
            if (wall.openApp != null) TextButton(onClick = { vm.setWallClock(wall.copy(openApp = null)) }) { Text("Будильники") }
        }
        if (picking) {
            AppPickerDialog("Что открывать по нажатию на часы", multi = false, selected = listOfNotNull(wall.openApp), onDismiss = { picking = false }) {
                vm.setWallClock(wall.copy(openApp = it.firstOrNull()))
                picking = false
            }
        }
    }
    Text("Шрифт", style = MaterialTheme.typography.labelMedium)
    DigitFontPicker(wall.font, ThemeRenderer.wallClockFont(theme.copy(wallClock = wall.copy(font = null)))) { f -> vm.setWallClock(wall.copy(font = f)) }
    var active by remember { mutableStateOf(ClockWallpaper.isActive(context)) }
    LaunchedEffect(theme) { active = ClockWallpaper.isActive(context) }
    if (active) {
        Text("Живые обои SyncTheme включены ✓ — часы обновляются сами.", style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            "Часам на обоях нужны живые обои SyncTheme. Нажмите кнопку и выберите «Главный экран» — " +
                "дальше время и тема меняются на них сами.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        FilledTonalButton(onClick = {
            runCatching { context.startActivity(ClockWallpaper.setIntent(context)) }
                .onFailure { android.widget.Toast.makeText(context, "Живые обои не поддерживаются этим телефоном", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text("Включить живые обои") }
    }
}

// ---------------------------------------------------------------- экран блокировки

/**
 * Свои часы SyncTheme на экране блокировки — как высокие стеклянные часы iPhone: те же, что «Часы на обоях»,
 * с эффектом глубины по фото блокировки.
 */
@Composable
fun LockClockContent(theme: Theme, vm: MainViewModel) {
    val wall = theme.wallClock
    val photo = theme.lockScreenBackground().type == BackgroundType.PHOTO
    SwitchRow("Часы SyncTheme на экране блокировки", wall?.onLock == true) { on ->
        vm.setWallClock((wall ?: WallClock(depth = photo, glass = true)).copy(onLock = on))
    }
    if (wall == null || !wall.onLock) {
        Text(
            "Крупные часы прямо на обоях: из стекла, во весь экран, за объектом фото.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    SwitchRow("Цифры из стекла", wall.glass) { on -> vm.setWallClock(wall.copy(glass = on)) }
    SwitchRow("За объектом фото", wall.depth && photo, enabled = photo) { on -> vm.setWallClock(wall.copy(depth = on)) }
    Text("Высота цифр", style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var stretch by remember(wall.stretch) { mutableStateOf(wall.stretch) }
        Slider(
            stretch, { stretch = it }, valueRange = 1f..ThemeRenderer.MAX_STRETCH,
            onValueChangeFinished = { vm.setWallClock(wall.copy(stretch = stretch)) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(false, vm::setWallClockFullScreen, label = { Text("Во весь экран") })
    }
    if (wall.depth && photo) OutlinedButton(onClick = { vm.editSubject(theme.lockScreenBackground()) }) { Text("Выбрать объект") }
    Text(
        "Шрифт и место часов — в «Обои» → «Часы на обоях».",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LockClockHint()
}

/** Свои часы на экране блокировки: живые обои SyncTheme должны стоять и там. */
@Composable
fun LockClockHint() {
    val context = LocalContext.current
    var onLock by remember { mutableStateOf(ClockWallpaper.isActiveOnLock(context)) }
    LaunchedEffect(Unit) { onLock = ClockWallpaper.isActiveOnLock(context) }
    Text(
        if (onLock) "Живые обои SyncTheme стоят и на экране блокировки ✓ — часы там свои, с эффектом глубины."
        else "Поставьте живые обои SyncTheme на «Главный экран и экран блокировки» — тогда эти часы будут и там.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!onLock) {
        FilledTonalButton(onClick = {
            runCatching { context.startActivity(ClockWallpaper.setIntent(context)) }
        }) { Text("Поставить живые обои") }
    }
    Text(
        "Часы самого телефона на экране блокировки убрать нельзя — их можно сделать маленькими: на Samsung зажмите " +
            "экран блокировки → «Изменить» → часы.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Глубина на экране блокировки. Часы там рисует One UI: начиная с One UI 8 его «Адаптивные часы»
 * сами встают за объект фото. SyncTheme ставит фото без затемнения и подсказывает, что включить.
 */
@Composable
fun LockDepthContent(theme: Theme, vm: MainViewModel) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val photo = theme.lockScreenBackground().type == BackgroundType.PHOTO
    val phone = remember { PhoneInfo.detect() }
    // Часы экрана блокировки рисует система; за объект их ставят «Адаптивные часы» One UI 8 у Samsung.
    if (!phone.samsung) {
        Text(
            "На этом телефоне часы экрана блокировки рисует система — поставить их за объект фото она не умеет. " +
                "Эффект глубины на экране блокировки есть у Samsung с One UI 8. Обои блокировки SyncTheme ставит и здесь, " +
                "а часы за объектом работают на рабочем столе («Часы на обоях») и на циферблате.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    SwitchRow("Эффект глубины", theme.lockDepth && photo, enabled = photo) { vm.setLockDepth(it) }
    if (!photo) {
        Text(
            "Нужно фото: поставьте фото на рабочий стол или включите «Свои обои для экрана блокировки» и выберите фото.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    val oneUi = phone.oneUi
    if (oneUi != null && oneUi < 8.0) {
        Text(
            "Нужен One UI 8 или новее, на телефоне — One UI ${PhoneInfo.format(oneUi)}. Обновите телефон: «Настройки» → «Обновление ПО».",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
        )
    }
    if (!theme.lockDepth) {
        Text(
            "Часы экрана блокировки встанут за главный объект фото, как на iPhone.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    Text("Один раз на телефоне:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    StepText(1, "Нажмите «Применить» (значок «Блок.» включён) — SyncTheme поставит фото на экран блокировки без затемнения.")
    StepText(2, "Заблокируйте телефон, зажмите пустое место на экране блокировки → «Изменить».")
    StepText(3, "Нажмите на часы → вкладка «Шрифт и цвет» → «Адаптивные часы». Если пункта нет — выберите на вкладке «Стиль» простые цифровые часы.")
    StepText(4, "Готово: часы обтекают объект. One UI 8 узнаёт людей и животных, One UI 8.5 — и другие объекты.")
    Text(
        "Часы не встают за объект? Поставьте эти обои через приложение обоев Samsung — так телефон заново найдёт объект:",
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
    OutlinedButton(onClick = { vm.lockViaSystem { context.startActivity(it) } }) { Text("Поставить через «Обои Samsung»") }
}
