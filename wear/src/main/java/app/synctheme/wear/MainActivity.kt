package app.synctheme.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.synctheme.wear.InstallStatus.State
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WearApp(this) } }
    }

    fun openPhoneApp() {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(SyncPaths.PHONE_OPEN_URI))
        RemoteActivityHelper(this).startRemoteActivity(intent, null)
    }

    /** Настройки даты и времени часов: там включается синхронизация с телефоном. */
    fun openDateSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }
    }

    /** Классический циферблат: системный экран выбора циферблата (или подсказка, если его нет). */
    fun chooseClassicFace(): Boolean = runCatching { startActivity(ClassicFace.chooseIntent(this)) }.isSuccess

    fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}

@Composable
private fun WearApp(activity: MainActivity) {
    val context = LocalContext.current
    val status by InstallState.observe(context).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val installer = WatchFaceInstaller(context)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { installer.activate() }
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, WatchFaceInstaller.PERMISSION_SET_ACTIVE)) {
            // Повторно спросить нельзя — только через настройки.
            activity.openAppSettings()
        }
    }

    LaunchedEffect(Unit) {
        runCatching { ThemeSync.checkPending(context) }
        // Сообщаем телефону состояние и версию приложения на часах.
        ThemeSync.sendStatus(context, null, installer.refreshActive())
    }

    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                item { ListHeader { Text("SyncTheme") } }
                item {
                    Text(
                        text = title(status),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val details = buildString {
                    append(status.message)
                    if (status.timestamp > 0 && status.state == State.INSTALLED) {
                        append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.timestamp)))
                    }
                }
                if (details.isNotBlank()) {
                    item {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (status.state == State.CLASSIC && !status.isActive) {
                    item {
                        Button(
                            onClick = {
                                if (!activity.chooseClassicFace()) {
                                    android.widget.Toast.makeText(context, "Зажмите циферблат и выберите «SyncTheme»", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Сделать циферблатом") }
                    }
                } else if (!status.isActive && status.state != State.UNSUPPORTED && status.state != State.INSTALLING && !ClassicFace.needed) {
                    item {
                        Button(
                            onClick = { permissionLauncher.launch(WatchFaceInstaller.PERMISSION_SET_ACTIVE) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Сделать циферблатом") }
                    }
                }
                item {
                    FilledTonalButton(onClick = { activity.openPhoneApp() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть на телефоне")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching { ThemeSync.checkPending(context, force = true) }
                                installer.refreshActive()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Установить ещё раз") }
                }
                item {
                    OutlinedButton(onClick = { activity.openDateSettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Настройки времени")
                    }
                }
            }
        }
    }
}

private fun title(status: InstallStatus): String = when (status.state) {
    State.WAITING -> "Выберите тему в приложении на телефоне"
    State.INSTALLING -> "Устанавливаю «${status.themeName}»…"
    State.INSTALLED -> if (status.isActive) "«${status.themeName}» на циферблате ✓" else "«${status.themeName}» установлен"
    State.NEEDS_PERMISSION -> "Разрешите сменить циферблат"
    State.NEEDS_MANUAL_SELECT -> "Выберите циферблат SyncTheme вручную"
    State.UNSUPPORTED -> "Часы не поддерживают установку"
    State.CLASSIC -> if (status.isActive) "«${status.themeName}» на циферблате ✓" else "«${status.themeName}» готов"
    State.ERROR -> "Ошибка установки"
}
