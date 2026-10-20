package app.synctheme.mobile.ui

import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.widget.WidgetConfigActivity

enum class Tab(val title: String) { THEME("Тема"), WIDGETS("Виджеты"), LOCK("Блокировка"), WATCH("Часы"), MORE("Ещё") }

/** Чей фон редактируется. */
enum class BgTarget { HOME, LOCK, WATCH }

/** Что редактируется в диалоге выбора цвета. */
sealed interface ColorTarget {
    data object Text : ColorTarget
    data object Accent : ColorTarget
    data object WatchText : ColorTarget
    data object WatchAccent : ColorTarget
    data class Gradient(val target: BgTarget, val index: Int) : ColorTarget
}

@Composable
fun SyncThemeScreen(vm: MainViewModel, initialTab: Tab = Tab.THEME, onOpenInstaller: () -> Unit = {}) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val phonePreview by vm.phonePreview.collectAsStateWithLifecycle()
    val lockPreview by vm.lockPreview.collectAsStateWithLifecycle()
    val watchPreview by vm.watchPreview.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var photoTarget by remember { mutableStateOf(BgTarget.HOME) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.pickPhoto(it, photoTarget) }
    }
    val actions = remember(vm) {
        ScreenActions(
            vm = vm,
            pickPhoto = { target ->
                photoTarget = target
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            openInstaller = onOpenInstaller,
            configureWidget = { id ->
                context.startActivity(
                    Intent(context, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    AppTheme(theme.accentColor) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { ApplyBar(targets, busy, vm::setTargets, vm::apply) },
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            ) {
                // Шапка и превью закреплены — настройки прокручиваются под ними, изменения видны сразу.
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Header(connection, onOpenInstaller)
                    PreviewRow(if (tab == Tab.LOCK) lockPreview else phonePreview, watchPreview)
                }
                PrimaryScrollableTabRow(
                    selectedTabIndex = tab.ordinal,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    Tab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.title) })
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                ) {
                    when (tab) {
                        Tab.THEME -> themeTab(theme, actions) { colorTarget = it }
                        Tab.WIDGETS -> widgetsTab(theme, actions)
                        Tab.LOCK -> lockTab(theme, actions) { colorTarget = it }
                        Tab.WATCH -> watchTab(theme, actions) { colorTarget = it }
                        Tab.MORE -> item { UpdateSection(vm) }
                    }
                }
            }
        }

        colorTarget?.let { target ->
            val watch = theme.watchSettings()
            val (title, initial) = when (target) {
                ColorTarget.Text -> "Цвет цифр" to theme.textColor
                ColorTarget.Accent -> "Акцентный цвет" to theme.accentColor
                ColorTarget.WatchText -> "Цифры на часах" to watch.textColor
                ColorTarget.WatchAccent -> "Акцент на часах" to watch.accentColor
                is ColorTarget.Gradient -> {
                    val bg = when (target.target) {
                        BgTarget.HOME -> theme.background
                        BgTarget.LOCK -> theme.lockScreenBackground()
                        BgTarget.WATCH -> theme.watchBackground()
                    }
                    "Цвет фона" to (bg.colors.getOrNull(target.index) ?: 0xFF000000.toInt())
                }
            }
            ColorPickerDialog(title, initial, onDismiss = { colorTarget = null }) { c ->
                when (target) {
                    ColorTarget.Text -> vm.update { it.copy(textColor = c) }
                    ColorTarget.Accent -> vm.update { it.copy(accentColor = c) }
                    ColorTarget.WatchText -> vm.updateWatch { it.copy(textColor = c) }
                    ColorTarget.WatchAccent -> vm.updateWatch { it.copy(accentColor = c) }
                    is ColorTarget.Gradient -> actions.editBackground(target.target) { bg ->
                        bg.copy(colors = bg.colors.toMutableList().also { list ->
                            if (target.index < list.size) list[target.index] = c else list += c
                        })
                    }
                }
                colorTarget = null
            }
        }
    }
}

/** Действия, которые нужны вкладкам. */
class ScreenActions(
    val vm: MainViewModel,
    val pickPhoto: (BgTarget) -> Unit,
    val openInstaller: () -> Unit,
    val configureWidget: (Int) -> Unit,
) {
    fun editBackground(target: BgTarget, transform: (app.synctheme.core.Background) -> app.synctheme.core.Background) {
        when (target) {
            BgTarget.HOME -> vm.update { it.copy(background = transform(it.background)) }
            BgTarget.LOCK -> vm.update { it.copy(lockBackground = transform(it.lockScreenBackground())) }
            BgTarget.WATCH -> vm.updateWatch { it.copy(background = transform(it.background ?: vm.theme.value.background)) }
        }
    }
}

@Composable
private fun Header(connection: WatchConnection, onOpenInstaller: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SyncTheme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        val (dot, text) = when {
            !connection.checked -> Color.Gray to "Ищу часы…"
            connection.hasApp -> Color(0xFF4CD964) to "Часы на связи"
            connection.hasWatch -> Color(0xFFFFCC00) to "Установить на часы"
            else -> Color(0xFFFF3B30) to "Часы не найдены"
        }
        AssistChip(
            onClick = { if (connection.hasWatch && !connection.hasApp) onOpenInstaller() },
            label = { Text(text, maxLines = 1) },
            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)) },
        )
    }
}

@Composable
private fun PreviewRow(phone: ImageBitmap?, watch: ImageBitmap?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(210.dp)
                .aspectRatio(540f / 1170f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
                .border(3.dp, Color(0xFF2A2A33), RoundedCornerShape(20.dp)),
        ) {
            phone?.let {
                Image(it, contentDescription = "Превью телефона", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        // Корпус часов: рамка + круглый экран, картинка всегда растягивается на весь экран.
        Box(
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A33))
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.Black),
        ) {
            watch?.let {
                Image(it, contentDescription = "Превью часов", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ApplyBar(targets: ApplyTargets, busy: String?, onTargets: (ApplyTargets) -> Unit, onApply: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = Color(0xFF15151B)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            ChipRow {
                FilterChip(targets.homeWallpaper, { onTargets(targets.copy(homeWallpaper = !targets.homeWallpaper)) }, label = { Text("Рабочий стол") })
                FilterChip(targets.lockWallpaper, { onTargets(targets.copy(lockWallpaper = !targets.lockWallpaper)) }, label = { Text("Экран блокировки") })
                FilterChip(targets.widgets, { onTargets(targets.copy(widgets = !targets.widgets)) }, label = { Text("Виджеты") })
                FilterChip(targets.watch, { onTargets(targets.copy(watch = !targets.watch)) }, label = { Text("Часы") })
            }
            Spacer(Modifier.height(8.dp))
            if (busy != null) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Button(
                    onClick = onApply,
                    enabled = targets.any,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Применить", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun UpdateSection(vm: MainViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editRepo by remember { mutableStateOf(false) }
    var repoText by remember(state.repo) { mutableStateOf(state.repo) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::installFromFile)
    }
    Section("Обновления") {
        Text("Установлена версия ${state.currentName} (${state.currentCode})", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Новая версия ставится поверх текущей: темы, сопряжение с часами и настройки сохраняются.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.available?.let { info ->
            Text("Доступна версия ${info.versionName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodySmall)
            Button(onClick = vm::downloadAndInstall, enabled = state.progress == null, modifier = Modifier.fillMaxWidth()) {
                Text("Скачать и обновить")
            }
        }
        state.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.needsPermission) {
            FilledTonalButton(onClick = { context.startActivity(vm.installPermissionIntent()) }) {
                Text("Разрешить установку обновлений")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.checkUpdates() }, enabled = state.repo.isNotBlank() && !state.checking) {
                Text(if (state.checking) "Проверяю…" else "Проверить")
            }
            OutlinedButton(onClick = {
                filePicker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream"))
            }) { Text("Из файла APK") }
        }
        if (state.repo.isBlank() || editRepo) {
            OutlinedTextField(
                value = repoText, onValueChange = { repoText = it }, singleLine = true,
                label = { Text("Источник: GitHub owner/repo") }, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.setUpdateRepo(repoText); editRepo = false }) { Text("Сохранить источник") }
        } else {
            TextButton(onClick = { editRepo = true }) { Text("Источник: github.com/${state.repo}") }
        }
    }
}
