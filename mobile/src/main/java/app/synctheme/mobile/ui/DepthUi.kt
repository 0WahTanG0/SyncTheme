package app.synctheme.mobile.ui

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme
import app.synctheme.core.WallClock
import app.synctheme.core.WallClockLayout
import app.synctheme.mobile.devices.PhoneInfo
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.wallpaper.ClockWallpaper

// ---------------------------------------------------------------- часы на обоях

fun wallClockSummary(theme: Theme): String {
    val wall = theme.wallClock ?: return "Выключены"
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
fun WallClockContent(theme: Theme, vm: MainViewModel) {
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
            depth -> "Часы встают с противоположной от объекта стороны: объект справа — часы слева, слева — справа, " +
                "снизу — сверху, сверху — снизу. Объект лишь слегка заходит на цифры, а их видно сквозь него."
            else -> "Объект с фото встанет перед часами, а сами часы — рядом с ним."
        },
        style = MaterialTheme.typography.bodySmall, color = muted,
    )
    // С глубиной место часов выбирается по объекту; без неё — одна из раскладок.
    if (!depth) {
        ChipRow {
            listOf(WallClockLayout.TOP to "Вверху", WallClockLayout.STACKED to "Столбиком", WallClockLayout.CENTER to "По центру").forEach { (l, label) ->
                FilterChip(wall.layout == l, { vm.setWallClock(wall.copy(layout = l)) }, label = { Text(label) })
            }
        }
    }
    SwitchRow("Дата над часами", wall.showDate) { on -> vm.setWallClock(wall.copy(showDate = on)) }
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
 * Глубина на экране блокировки. Часы там рисует One UI: начиная с One UI 8 его «Адаптивные часы»
 * сами встают за объект фото. SyncTheme ставит фото без затемнения и подсказывает, что включить.
 */
@Composable
fun LockDepthContent(theme: Theme, vm: MainViewModel) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val photo = theme.lockScreenBackground().type == BackgroundType.PHOTO
    val phone = remember { PhoneInfo.detect() }
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
