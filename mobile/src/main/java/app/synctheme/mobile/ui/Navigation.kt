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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.synctheme.mobile.data.ApplyTargets

/** Нижнее меню: четыре раздела под большим пальцем. */
@Composable
fun BottomNav(tab: Tab, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    NavigationBar(containerColor = look.bar, tonalElevation = 0.dp) {
        Tab.entries.forEach { t ->
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

val Tab.icon: ImageVector
    get() = when (this) {
        Tab.WALLPAPER -> NavIcons.Wallpaper
        Tab.WIDGETS -> NavIcons.Widgets
        Tab.WATCH -> NavIcons.Watch
        Tab.MORE -> NavIcons.More
    }

/**
 * Строка «Применить»: куда применять (значки-переключатели) и сама кнопка. dirty — есть изменения,
 * которые ещё не применены; тогда кнопка яркая.
 */
@Composable
fun ApplyStrip(
    targets: ApplyTargets,
    busy: String?,
    dirty: Boolean,
    onTargets: (ApplyTargets) -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TargetToggle(NavIcons.Home, "Экран", targets.homeWallpaper) { onTargets(targets.copy(homeWallpaper = it)) }
            TargetToggle(NavIcons.Lock, "Блок.", targets.lockWallpaper) { onTargets(targets.copy(lockWallpaper = it)) }
            TargetToggle(NavIcons.Widgets, "Виджеты", targets.widgets) { onTargets(targets.copy(widgets = it)) }
            TargetToggle(NavIcons.Watch, "Часы", targets.watch) { onTargets(targets.copy(watch = it)) }
            Spacer(Modifier.width(4.dp))
            AccentButton(
                if (dirty) "Применить" else "Применено",
                onApply,
                Modifier.weight(1f),
                enabled = targets.any,
                quiet = !dirty,
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun TargetToggle(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(52.dp).clip(shape)
            .background(if (checked) mix(look.control, look.primary, 0.28f) else look.control)
            .border(1.dp, if (checked) look.primary.copy(alpha = 0.6f) else look.cardBorder, shape)
            .clickable { onChange(!checked) }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (checked) look.text else look.muted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Text(
            label, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
            color = if (checked) look.text else look.muted.copy(alpha = 0.6f),
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Секция, которая сворачивается: заголовок с кратким итогом, подробности — по нажатию. */
@Composable
fun ExpandableSection(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
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
