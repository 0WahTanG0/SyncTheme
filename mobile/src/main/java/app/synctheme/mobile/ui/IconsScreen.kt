package app.synctheme.mobile.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import app.synctheme.mobile.icons.IconPinner
import app.synctheme.mobile.icons.IconStyle
import app.synctheme.mobile.icons.IconStyler
import app.synctheme.mobile.widget.AppCatalog
import app.synctheme.mobile.widget.AppEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Стиль значков, который подходит к оформлению приложения. */
fun defaultIconStyle(look: Look): IconStyle = when {
    look.style == AppStyle.SOFT -> IconStyle.SOFT
    look.style == AppStyle.GLASS -> IconStyle.THEME
    look.dark -> IconStyle.GRAPHITE
    else -> IconStyle.CLAY
}

/**
 * Значки приложений в стиле оформления: выбрать стиль и приложения — SyncTheme поставит на рабочий
 * стол ярлыки с новыми значками (лаунчер спрашивает про каждый). Поставленные значки при смене
 * стиля перерисовываются сами.
 */
@Composable
fun IconsScreen(accent: Int, onClose: () -> Unit) = AppTheme(accent) {
    val context = LocalContext.current
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    val apps by produceState(emptyList<AppEntry>()) { value = withContext(Dispatchers.IO) { AppCatalog.launchable(context) } }
    var style by remember { mutableStateOf(IconPinner.style(context, defaultIconStyle(look))) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var pinned by remember { mutableStateOf(IconPinner.pinned(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    val supported = remember { IconPinner.supported(context) }
    val queue = remember { PinQueue(context, scope) { pinned = IconPinner.pinned(context) } }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> queue.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    queue.onResume()
                    pinned = IconPinner.pinned(context)
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val sample = selected.firstOrNull() ?: pinned.firstOrNull() ?: remember(apps) { AppCatalog.defaults(context).firstOrNull() } ?: apps.firstOrNull()?.component

    AppBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().wrapContentWidth().widthIn(max = 720.dp)) {
            Box(Modifier.padding(horizontal = 16.dp)) { DetailHeader("Иконки приложений", onClose) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                item(key = "hint") {
                    Text(
                        "Android не даёт менять значки других приложений, поэтому SyncTheme ставит на рабочий стол ярлыки с новыми значками — " +
                            "они открывают те же приложения. Старые значки можно убрать с рабочего стола: приложения останутся в меню.",
                        color = look.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
                item(key = "style") {
                    Section("Стиль значков") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)) {
                            items(IconStyle.entries, key = { it.name }) { s ->
                                StyleTile(s, sample, accent, s == style) {
                                    style = s
                                    IconPinner.setStyle(context, s)
                                    if (pinned.isNotEmpty()) scope.launch {
                                        val n = withContext(Dispatchers.Default) { IconPinner.restyle(context, s, accent) }
                                        note = if (n > 0) "Перерисовано значков на рабочем столе: $n" else null
                                    }
                                }
                            }
                        }
                        Text(style.note, color = look.muted, fontSize = 13.sp)
                    }
                }
                if (!supported) item(key = "unsupported") {
                    Notice("Этот рабочий стол не принимает ярлыки от приложений — значки поставить не получится.", ClayAlertKind.WARNING)
                }
                if (pinned.isNotEmpty()) item(key = "pinned") {
                    Notice(note ?: "На рабочем столе значков SyncTheme: ${pinned.size}. При смене стиля они перерисуются сами.", ClayAlertKind.SUCCESS)
                }
                item(key = "apps_title") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Приложения", style = look.sectionTitle, color = look.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selected = selected + AppCatalog.defaults(context) }) { Text("Частые") }
                        if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet() }) { Text("Снять все") }
                    }
                }
                if (apps.isEmpty()) item(key = "empty") { Text("Ищу приложения…", color = look.muted, fontSize = 14.sp) }
                items(apps.chunked(4), key = { row -> row.first().component }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { app ->
                            AppCell(app, style, accent, app.component in selected, app.component in pinned) {
                                selected = if (app.component in selected) selected - app.component else selected + app.component
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(look.bar).padding(horizontal = 16.dp, vertical = 10.dp)) {
                queue.status?.let { Text(it, color = look.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                val chosen = apps.filter { it.component in selected }
                AccentButton(
                    if (chosen.isEmpty()) "Выберите приложения" else "Поставить на рабочий стол · ${chosen.size}",
                    { queue.start(chosen, style, accent); selected = emptySet() },
                    enabled = supported && chosen.isNotEmpty() && !queue.active,
                )
            }
        }
    }
}

@Composable
private fun StyleTile(style: IconStyle, sample: String?, accent: Int, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier.size(60.dp)
                .then(
                    when {
                        selected && look.isClay -> Modifier.clayGlow(look, 18.dp, 2.5.dp)
                        selected -> Modifier.border(2.5.dp, look.primary, shape)
                        else -> Modifier
                    },
                )
                .clip(shape),
        ) { StyledIcon(sample, style, accent, 60.dp) }
        Spacer(Modifier.height(6.dp))
        Text(style.title, color = if (selected) look.text else look.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun RowScope.AppCell(app: AppEntry, style: IconStyle, accent: Int, selected: Boolean, pinned: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Checkbox, onClick = onClick).padding(vertical = 6.dp)
            .semantics { contentDescription = app.label + if (selected) ", выбрано" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp)) {
            StyledIcon(app.component, style, accent, 54.dp, Modifier.align(Alignment.Center))
            if (selected || pinned) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                        .background(if (selected) (if (look.isClay) look.clay.mint.face else look.primary) else look.control),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NavIcons.Check, null, tint = if (selected) look.onPrimary else look.muted, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.label, color = look.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Значок в стиле — рисуется в фоне и запоминается. */
@Composable
private fun StyledIcon(component: String?, style: IconStyle, accent: Int, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(null, component, style, accent, px) {
        value = component?.let { c -> withContext(Dispatchers.Default) { IconStyler.preview(context, c, style, accent, px)?.asImageBitmap() } }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(androidx.compose.ui.graphics.Color(IconStyler.tones(style, accent).face))) {
        image?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

/** Сообщение: в «Мяте» — цветная плашка из образца, в остальных — строка текста. */
@Composable
private fun Notice(text: String, kind: ClayAlertKind) {
    val look = LocalLook.current
    if (look.isClay) {
        Box(Modifier.padding(vertical = 6.dp)) { ClayAlert(text, kind) }
    } else {
        Text(text, color = if (kind == ClayAlertKind.WARNING) MaterialTheme.colorScheme.error else look.text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
}

/**
 * Очередь «поставить значки»: лаунчер спрашивает про каждый ярлык своим окном. Следующий просим,
 * когда окно закрылось (экран вернулся) — или через пару секунд, если лаунчер добавил молча.
 */
private class PinQueue(private val context: Context, private val scope: CoroutineScope, private val onDone: () -> Unit) {
    private var items = emptyList<AppEntry>()
    private var style = IconStyle.CLAY
    private var accent = 0
    private var index by mutableIntStateOf(0)
    private var total by mutableIntStateOf(0)
    private var awaiting = false
    private var paused = false
    private var timer: Job? = null

    val active get() = index < total

    val status: String?
        get() = if (active) "Ставлю ${index + 1} из $total — подтвердите в окне рабочего стола" else null

    fun start(list: List<AppEntry>, style: IconStyle, accent: Int) {
        items = list
        this.style = style
        this.accent = accent
        total = list.size
        index = 0
        next()
    }

    private fun next() {
        while (index < total) {
            if (IconPinner.request(context, items[index], style, accent)) {
                awaiting = true
                paused = false
                timer?.cancel()
                timer = scope.launch {
                    delay(2500)
                    if (awaiting && !paused) advance()
                }
                return
            }
            index++
        }
        onDone()
    }

    private fun advance() {
        awaiting = false
        timer?.cancel()
        index++
        scope.launch {
            delay(350)
            next()
        }
    }

    fun onPause() {
        if (awaiting) paused = true
    }

    fun onResume() {
        if (awaiting && paused) advance()
    }
}

/** Раздел в «Настройках»: четыре значка в выбранном стиле и кнопка «Сменить иконки». */
@Composable
fun IconsSection(accent: Int, onOpen: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val style = remember(look.style, look.dark) { IconPinner.style(context, defaultIconStyle(look)) }
    val apps by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { AppCatalog.defaults(context) } }
    Section("Иконки приложений") {
        Text(
            "Значки на рабочем столе — в том же стиле, что и приложение: «${style.title}». Выберите приложения, SyncTheme поставит их значки.",
            color = look.muted, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (apps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                apps.take(4).forEach { StyledIcon(it, style, accent, 48.dp) }
            }
        }
        AccentButton("Сменить иконки", onOpen, quiet = true, height = 48.dp)
    }
}
