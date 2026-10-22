package app.synctheme.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSet

/** Нижнее меню: четыре раздела под большим пальцем. */
@Composable
fun BottomNav(tab: Tab, tabs: List<Tab> = Tab.entries, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    NavigationBar(containerColor = look.bar, tonalElevation = 0.dp) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = tab == t,
                onClick = { onTab(t) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(if (look.style == AppStyle.NEON) t.title.uppercase() else t.title, maxLines = 1, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = look.onPrimary,
                    selectedTextColor = look.text,
                    indicatorColor = look.primary,
                    unselectedIconColor = look.muted,
                    unselectedTextColor = look.muted,
                ),
            )
        }
    }
}

/** Меню сбоку — для широкого экрана (телефон боком, планшет). */
@Composable
fun SideNav(tab: Tab, tabs: List<Tab>, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    NavigationRail(containerColor = look.bar, modifier = Modifier.fillMaxHeight()) {
        Spacer(Modifier.weight(1f))
        tabs.forEach { t ->
            NavigationRailItem(
                selected = tab == t,
                onClick = { onTab(t) },
                icon = { Icon(t.icon, contentDescription = null) },
                // Рейка узкая: «Настройки» капсом в «Неоне» не помещаются — подпись чуть мельче.
                label = { Text(if (look.style == AppStyle.NEON) t.title.uppercase() else t.title, maxLines = 1, softWrap = false, fontSize = if (t.title.length > 7) 10.sp else 12.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = look.onPrimary,
                    selectedTextColor = look.text,
                    indicatorColor = look.primary,
                    unselectedIconColor = look.muted,
                    unselectedTextColor = look.muted,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

val Tab.icon: ImageVector
    get() = when (this) {
        Tab.WALLPAPER -> NavIcons.Wallpaper
        Tab.WIDGETS -> NavIcons.Widgets
        Tab.WATCH -> NavIcons.Watch
        Tab.MORE -> NavIcons.Settings
    }

/**
 * Строка «Применить»: кнопка и рядом — куда применять (значки выбранных мест; по нажатию —
 * меню с галочками). dirty — есть изменения, которые ещё не применены; тогда кнопка яркая.
 */
@Composable
fun ApplyStrip(
    targets: ApplyTargets,
    busy: String?,
    dirty: Boolean,
    devices: DeviceSet,
    onTargets: (ApplyTargets) -> Unit,
    onPeer: (id: String, on: Boolean) -> Unit,
    onApply: () -> Unit,
) {
    val look = LocalLook.current
    Column(Modifier.fillMaxWidth().background(look.bar).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (busy != null) {
            Text(busy, style = MaterialTheme.typography.bodyMedium, color = look.text)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetsButton(targets, devices, onTargets, onPeer)
            val any = targets.on(devices).any
            AccentButton(
                if (!any) "Выберите, куда" else if (dirty) "Применить" else "Применено",
                onApply,
                Modifier.weight(1f),
                enabled = any,
                quiet = !dirty,
                height = 48.dp,
            )
        }
    }
}

/** Куда применять: значки выбранных мест (часы и устройства — если подключены), по нажатию — меню с галочками. */
@Composable
private fun TargetsButton(targets: ApplyTargets, devices: DeviceSet, onTargets: (ApplyTargets) -> Unit, onPeer: (String, Boolean) -> Unit) {
    val look = LocalLook.current
    var open by remember { mutableStateOf(false) }
    val items = listOfNotNull(
        Triple(NavIcons.Home, "Обои рабочего стола", targets.homeWallpaper) to { on: Boolean -> targets.copy(homeWallpaper = on) },
        Triple(NavIcons.Lock, "Экран блокировки", targets.lockWallpaper) to { on: Boolean -> targets.copy(lockWallpaper = on) },
        Triple(NavIcons.Widgets, "Виджеты", targets.widgets) to { on: Boolean -> targets.copy(widgets = on) },
        if (devices.watch) Triple(NavIcons.Watch, "Циферблат часов", targets.watch) to { on: Boolean -> targets.copy(watch = on) } else null,
    )
    // Каждое устройство по Wi‑Fi — своей галочкой: например, тема на планшет, но не на телевизор.
    val peers = devices.peers.map { p -> Triple(p.kind.icon(), p.name, p.enabled) to { on: Boolean -> onPeer(p.id, on) } }
    val shape = RoundedCornerShape(24.dp)
    Box {
        Row(
            Modifier.height(48.dp).clip(shape).background(look.control).border(1.dp, look.cardBorder, shape)
                .clickable { open = true }.padding(horizontal = 12.dp)
                .semantics { contentDescription = "Куда применять" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (items.map { it.first } + peers.map { it.first }).forEach { item ->
                Icon(item.first, null, tint = if (item.third) look.text else look.muted.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("Куда применять", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            items.forEach { (item, change) ->
                DropdownMenuItem(
                    text = { Text(item.second) },
                    leadingIcon = { Icon(item.first, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = { Checkbox(checked = item.third, onCheckedChange = null) },
                    onClick = { onTargets(change(!item.third)) },
                )
            }
            peers.forEach { (item, change) ->
                DropdownMenuItem(
                    text = { Text(item.second, maxLines = 1) },
                    leadingIcon = { Icon(item.first, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = { Checkbox(checked = item.third, onCheckedChange = null) },
                    onClick = { change(!item.third) },
                )
            }
        }
    }
}

/** Раскрыть все сворачиваемые секции сразу (для скриншотов во всю высоту). */
val LocalExpandAll = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Секция, которая сворачивается: заголовок с кратким итогом, подробности — по нажатию. */
@Composable
fun ExpandableSection(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val expandAll = LocalExpandAll.current
    var open by rememberSaveable(title) { mutableStateOf(initiallyExpanded || expandAll) }
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.radius)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape)
            .background(look.card).border(1.dp, look.cardBorder, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(look.title(title), style = look.sectionTitle, color = look.text)
                summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = look.muted, maxLines = 1) }
            }
            trailing?.let {
                it()
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(look.control), contentAlignment = Alignment.Center) {
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = look.text)
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalContentColor provides look.text, content = content)
            }
        }
    }
}

/**
 * Превью сжимается, пока список настроек прокручивается вверх, и раскрывается обратно, когда
 * список вернулся к началу. height — текущая высота в пикселях.
 */
class CollapsingHeader(private val maxPx: Float, private val minPx: Float) : NestedScrollConnection {
    var height by mutableStateOf(maxPx)
        private set

    fun expand() {
        height = maxPx
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0) return Offset.Zero
        val next = (height + available.y).coerceIn(minPx, maxPx)
        val used = next - height
        height = next
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0) return Offset.Zero
        val next = (height + available.y).coerceIn(minPx, maxPx)
        val used = next - height
        height = next
        return Offset(0f, used)
    }
}
