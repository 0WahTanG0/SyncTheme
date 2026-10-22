package app.synctheme.mobile.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.link.LinkReceiver
import app.synctheme.mobile.link.LinkService
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.AppBackdrop
import app.synctheme.mobile.ui.AppTheme
import app.synctheme.mobile.ui.LinkRequestDialog
import app.synctheme.mobile.ui.LocalLook
import app.synctheme.mobile.ui.Section
import app.synctheme.mobile.ui.SwitchRow
import app.synctheme.mobile.update.UpdateInfo
import app.synctheme.mobile.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SyncTheme на телевизоре (Google TV / Android TV): тема с телефона показывается заставкой —
 * обои и крупные часы. Здесь — превью заставки, подключение к телефону и кнопки для пульта.
 */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Телевизор только принимает тему — приём включён сразу, без лишних шагов.
        if (!LinkStore.receiving(this)) LinkStore.setReceiving(this, true)
        LinkService.ensure(this)
        setContent { TvScreen() }
    }
}

/** [initialPreview] — уже готовое превью заставки (для снимков экрана в тестах). */
@Composable
fun TvScreen(initialPreview: ImageBitmap? = null) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository.get(context) }
    val theme by repo.applied.collectAsStateWithLifecycle()
    val state by LinkReceiver.state.collectAsStateWithLifecycle()
    var receiving by remember { mutableStateOf(LinkStore.receiving(context)) }
    var preview by remember { mutableStateOf(initialPreview) }
    var hint by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(theme) {
        if (initialPreview != null) return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { ThemeRenderer.tvFrame(context, theme, 960, 540).asImageBitmap() }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    AppTheme(theme.accentColor) {
        val look = LocalLook.current
        AppBackdrop {
            Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Column(Modifier.weight(0.55f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text("SyncTheme", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = look.text)
                    Text("Заставка в цветах темы с вашего телефона", style = MaterialTheme.typography.titleMedium, color = look.muted)
                    Spacer(Modifier.height(20.dp))
                    val shape = RoundedCornerShape(20.dp)
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color.Black).border(2.dp, look.accent.copy(alpha = 0.5f), shape)) {
                        preview?.let { Image(it, contentDescription = "Заставка", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Сейчас: «${theme.name}»", style = MaterialTheme.typography.titleMedium, color = look.text)
                }
                Column(
                    Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { context.startActivity(Intent(context, ThemeShowActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().focusRequester(first),
                    ) { Text("Показать заставку", fontSize = 18.sp) }
                    OutlinedButton(onClick = { hint = openDreamSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Сделать заставкой", fontSize = 18.sp)
                    }
                    hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = look.muted) }
                    Section("Телефон") {
                        SwitchRow("Принимать тему с телефона", receiving) { on ->
                            receiving = on
                            LinkStore.setReceiving(context, on)
                            LinkService.ensure(context)
                        }
                        Text(
                            state.phone?.let { "Подключён к телефону «$it» ✓ — тема меняется сама, когда на телефоне нажимают «Применить»." }
                                ?: "Ждёт подключения. На телефоне: SyncTheme → «Настройки» → «Устройства» → «Добавить устройство» → выберите «${DeviceSettings.name(context)}».",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        state.last?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = look.muted) }
                        state.address?.let { Text("Адрес телевизора: $it", style = MaterialTheme.typography.bodyMedium, color = look.muted) }
                    }
                    UpdateSection()
                }
            }
            LinkRequestDialog()
        }
    }
}

/** Обновление на самом телевизоре: та же версия, что на телефоне, — из релизов GitHub. */
@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val updater = remember { Updater(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    val look = LocalLook.current
    Section("Обновление") {
        Text("Версия ${runCatching { updater.currentVersionName }.getOrDefault("—")}", style = MaterialTheme.typography.bodyLarge)
        val update = available
        OutlinedButton(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        if (update == null) {
                            status = "Проверяю…"
                            val found = runCatching { updater.check() }
                            available = found.getOrNull()
                            status = found.fold(
                                { it?.let { u -> "Есть версия ${u.versionName}" } ?: "Установлена последняя версия" },
                                { "Не удалось проверить: ${it.message ?: it.javaClass.simpleName}" },
                            )
                        } else if (!updater.canInstall()) {
                            status = "Разрешите SyncTheme устанавливать приложения и нажмите ещё раз"
                            runCatching { context.startActivity(updater.installPermissionIntent()) }
                        } else {
                            status = "Загружаю ${update.versionName}…"
                            val apk = updater.download(update) {}
                            updater.verify(apk)
                            status = "Устанавливаю… SyncTheme перезапустится"
                            updater.install(apk)
                        }
                    } catch (e: Exception) {
                        status = "Ошибка обновления: ${e.message ?: e.javaClass.simpleName}"
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (update == null) "Проверить обновления" else "Обновить до ${update.versionName}", fontSize = 18.sp) }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = look.muted) }
    }
}

/**
 * Настройки заставки. На многих Google TV стороннюю заставку там не выбрать — тогда подсказка:
 * телефон включит её сам при установке SyncTheme на телевизор.
 */
private fun openDreamSettings(context: Context): String? {
    val opened = runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }.isSuccess
    return if (opened) "Выберите «SyncTheme» в списке заставок. Если её там нет — на телефоне «Добавить устройство» → «Телевизор» → «Установить на телевизор»: заставка включится сама."
    else "На этом телевизоре заставку выбирают с телефона: «Добавить устройство» → «Телевизор» → «Установить на телевизор» — заставка SyncTheme включится сама."
}
