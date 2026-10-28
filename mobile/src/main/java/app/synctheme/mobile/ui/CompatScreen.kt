package app.synctheme.mobile.ui

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.synctheme.mobile.widget.ClockTicker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Насколько работает возможность на этом телефоне: всё, частично (есть обходной путь) или никак. */
enum class CompatLevel { OK, PARTIAL, NO }

/** Строка проверки: что это, как работает на этом телефоне и, если можно исправить, — кнопка. */
data class CompatItem(val title: String, val level: CompatLevel, val text: String, val action: String? = null, val intent: Intent? = null)

/**
 * Проверка совместимости: какие возможности SyncTheme работают на этом телефоне — любой марки.
 * Показывается при первом запуске и открывается из «Настроек».
 */
object CompatCheck {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun shown(context: Context) = prefs(context).getBoolean("compat_shown", false)

    fun markShown(context: Context) = prefs(context).edit().putBoolean("compat_shown", true).apply()

    fun items(context: Context): List<CompatItem> = buildList {
        val pm = context.packageManager
        val wallpapers = WallpaperManager.getInstance(context)
        val canWallpaper = runCatching { wallpapers.isWallpaperSupported && wallpapers.isSetWallpaperAllowed }.getOrDefault(false)
        add(
            if (canWallpaper) CompatItem("Обои", CompatLevel.OK, "Рабочий стол и экран блокировки — в цветах темы.")
            else CompatItem("Обои", CompatLevel.NO, "Телефон не разрешает приложениям менять обои (ограничения работы или детского режима).")
        )
        val live = pm.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)
        add(
            if (live) CompatItem("Часы на обоях", CompatLevel.OK, "Крупные часы прямо на обоях — на рабочем столе и экране блокировки.")
            else CompatItem("Часы на обоях", CompatLevel.NO, "Живые обои не поддерживаются этим телефоном — часы на обоях и эффект глубины недоступны.")
        )
        val google = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
        add(
            when {
                !live -> CompatItem("Эффект глубины", CompatLevel.NO, "Нужны живые обои.")
                google -> CompatItem("Эффект глубины", CompatLevel.OK, "Объект на фото находится сам; обведите его пальцем, если телефон выбрал не тот.")
                else -> CompatItem(
                    "Эффект глубины", CompatLevel.PARTIAL,
                    "Без сервисов Google объект сам не находится — обведите его пальцем в «Выбрать объект».",
                )
            }
        )
        val widgets = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
        add(
            when {
                widgets == null -> CompatItem("Виджеты", CompatLevel.NO, "На этом устройстве нет виджетов.")
                widgets.isRequestPinAppWidgetSupported -> CompatItem("Виджеты", CompatLevel.OK, "Добавляются прямо из приложения.")
                else -> CompatItem(
                    "Виджеты", CompatLevel.PARTIAL,
                    "Рабочий стол не даёт добавить виджет из приложения — зажмите пустое место на рабочем столе → «Виджеты» → SyncTheme.",
                )
            }
        )
        if (widgets != null) add(
            if (ClockTicker.canTick(context)) CompatItem("Шрифт часов на виджетах", CompatLevel.OK, "Цифры выбранным шрифтом, время обновляется каждую минуту.")
            else CompatItem(
                "Шрифт часов на виджетах", CompatLevel.PARTIAL,
                "Без разрешения на точные будильники часы на виджетах показываются системным шрифтом.",
                "Разрешить", exactAlarms(context),
            )
        )
        val power = context.getSystemService(PowerManager::class.java)
        val free = runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)
        val brand = autostartBrand()
        add(
            when {
                !free -> CompatItem(
                    "Работа в фоне", CompatLevel.PARTIAL,
                    "Экономия заряда может задерживать обновление погоды и календаря на виджетах." +
                        (brand?.let { " На $it разрешите SyncTheme ещё и «Автозапуск»." } ?: ""),
                    "Разрешить", Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
                brand != null -> CompatItem(
                    "Работа в фоне", CompatLevel.PARTIAL, "На $brand разрешите SyncTheme «Автозапуск», чтобы виджеты обновлялись в фоне.",
                    "Открыть", appDetails(context),
                )
                else -> CompatItem("Работа в фоне", CompatLevel.OK, "Погода и календарь на виджетах обновляются сами.")
            }
        )
        add(CompatItem("Android ${Build.VERSION.RELEASE}", CompatLevel.OK, "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"))
    }

    /** Марки, где фоновую работу ещё ограничивает свой «Автозапуск». */
    private fun autostartBrand(): String? = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi"
        "huawei" -> "Huawei"
        "honor" -> "Honor"
        "oppo" -> "OPPO"
        "vivo" -> "vivo"
        "realme" -> "realme"
        "oneplus" -> "OnePlus"
        "meizu" -> "Meizu"
        "tecno", "infinix", "itel" -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        else -> null
    }

    private fun exactAlarms(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else null

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}

/** Экран проверки совместимости: зелёное работает, жёлтое — с оговоркой, красное — нет. */
@Composable
fun CompatScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    var items by remember { mutableStateOf(CompatCheck.items(context)) }
    // Вернулись из настроек телефона — проверяем заново.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) items = CompatCheck.items(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AppBackdrop {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().wrapContentWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Что работает на этом телефоне", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = look.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "SyncTheme работает на любом Android 9 и новее. Здесь — какие возможности есть именно у вас и что можно включить.",
                    style = MaterialTheme.typography.bodyMedium, color = look.muted,
                )
            }
            items(items) { item ->
                Section(item.title) {
                    Row(verticalAlignment = Alignment.Top) {
                        val color = when (item.level) {
                            CompatLevel.OK -> StatusOk
                            CompatLevel.PARTIAL -> StatusWait
                            CompatLevel.NO -> StatusBad
                        }
                        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = look.text)
                            val intent = item.intent
                            if (item.action != null && intent != null) {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                                }) { Text(item.action) }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Понятно") }
            }
        }
    }
}
