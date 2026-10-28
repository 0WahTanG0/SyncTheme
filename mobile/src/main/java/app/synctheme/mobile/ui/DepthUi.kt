package app.synctheme.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
            depth -> "Объект с фото стоит перед часами и закрывает их — как на iPhone. Часы встают с противоположной " +
                "от объекта стороны; если телефон выбрал не тот объект или не нашёл его — «Выбрать объект»."
            else -> "Объект с фото встанет перед часами, а сами часы — рядом с ним."
        },
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
    if (depth) OutlinedButton(onClick = { vm.editSubject(theme.background) }) { Text("Выбрать объект") }
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
    SwitchRow("Часы на экране блокировки", wall.onLock) { on -> vm.setWallClock(wall.copy(onLock = on)) }
    LockClockHint(wall.onLock)
    SwitchRow("Дата над часами", wall.showDate) { on -> vm.setWallClock(wall.copy(showDate = on)) }
    SwitchRow("Погода под часами", wall.weather) { on -> vm.setWallClock(wall.copy(weather = on)) }
    SwitchRow("Заряд под часами", wall.battery) { on -> vm.setWallClock(wall.copy(battery = on)) }
    if (wall.weather || wall.battery) {
        Text(
            "Они на обоях, как и часы, — поэтому тоже уходят за объект фото." +
                if (wall.weather) " Погода — для города из «Виджеты» → «Город для погоды»." else "",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
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

/** Часы SyncTheme на экране блокировки работают, только если живые обои стоят и там. */
@Composable
private fun LockClockHint(onLock: Boolean) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var live by remember { mutableStateOf(ClockWallpaper.isActiveOnLock(context)) }
    LaunchedEffect(onLock) { live = ClockWallpaper.isActiveOnLock(context) }
    Text(
        when {
            !onLock -> "На экране блокировки — только часы самого телефона."
            live -> "Часы SyncTheme есть и на экране блокировки ✓. Часы самого телефона там тоже останутся — " +
                "на Samsung их можно уменьшить: зажмите экран блокировки → «Изменить» → часы."
            else -> "Чтобы часы были и на экране блокировки, поставьте живые обои SyncTheme на «Главный экран и экран блокировки»."
        },
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
    if (onLock && !live) {
        FilledTonalButton(onClick = {
            runCatching { context.startActivity(ClockWallpaper.setIntent(context)) }
        }) { Text("Поставить живые обои на оба экрана") }
    }
}

/**
 * Экран блокировки: свои часы SyncTheme (живые обои) с эффектом глубины — одинаково на любом телефоне.
 * Часы самого телефона там остаются: их убрать нельзя, только уменьшить в настройках телефона.
 */
@Composable
fun LockClockContent(theme: Theme, vm: MainViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val wall = theme.wallClock
    val photo = theme.lockScreenBackground().type == BackgroundType.PHOTO
    SwitchRow("Часы SyncTheme на экране блокировки", wall?.onLock == true) { on ->
        vm.setWallClock((wall ?: WallClock(depth = photo)).copy(onLock = on))
    }
    if (wall == null || !wall.onLock) {
        Text(
            "Крупные часы прямо на обоях экрана блокировки — с эффектом глубины: объект с фото встаёт перед ними.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        return
    }
    SwitchRow("За объектом фото", wall.depth && photo, enabled = photo) { on -> vm.setWallClock(wall.copy(depth = on)) }
    if (!photo) {
        Text("Эффект глубины работает с фото на экране блокировки.", style = MaterialTheme.typography.bodySmall, color = muted)
    } else if (wall.depth) {
        OutlinedButton(onClick = { vm.editSubject(theme.lockScreenBackground()) }) { Text("Выбрать объект") }
    }
    Text("Шрифт, место, погода и заряд — в «Обои» → «Часы на обоях».", style = MaterialTheme.typography.bodySmall, color = muted)
    LockClockHint(true)
}
