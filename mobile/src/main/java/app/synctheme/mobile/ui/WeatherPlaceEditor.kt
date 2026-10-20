package app.synctheme.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.synctheme.mobile.data.WeatherData
import app.synctheme.mobile.data.WeatherPlace
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetScheduler
import app.synctheme.mobile.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Выбор города для погоды: по местоположению телефона или поиском. */
@Composable
fun WeatherPlaceEditor(onUpdated: (WeatherData?) -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { WeatherRepository(context) }
    val scope = rememberCoroutineScope()
    var place by remember { mutableStateOf(repo.place) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<WeatherPlace>()) }
    var status by remember { mutableStateOf<String?>(null) }

    fun choose(p: WeatherPlace) {
        repo.place = p
        place = p
        results = emptyList()
        scope.launch {
            status = "Загружаю погоду…"
            runCatching { repo.refresh() }
                .onSuccess {
                    status = null
                    onUpdated(it)
                    WidgetUpdater.update(context, WidgetKind.WEATHER)
                    WidgetScheduler.ensure(context)
                }
                .onFailure { status = "Не удалось загрузить погоду: ${it.message}" }
        }
    }

    fun useLocation() {
        scope.launch {
            status = "Определяю местоположение…"
            val p = runCatching { repo.locate() }.getOrNull()
            if (p == null) status = "Не удалось определить местоположение — найдите город по названию" else choose(p)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useLocation() else status = "Без доступа к местоположению можно найти город по названию"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Город: " + (place?.let { if (it.auto) "${it.name} (по местоположению)" else it.name } ?: "не выбран"),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) useLocation() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }) { Text("Моё местоположение") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Город") }, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (query.isNotBlank()) scope.launch {
                    status = "Ищу…"
                    results = runCatching { repo.search(query) }
                        .onFailure { status = "Поиск не удался: ${it.message}" }
                        .getOrDefault(emptyList())
                    if (results.isNotEmpty()) status = null else if (status == "Ищу…") status = "Ничего не найдено"
                }
            }) { Text("Найти") }
        }
        results.forEach { r ->
            Text(
                listOf(r.name, r.region).filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable { choose(r) }.padding(vertical = 8.dp),
            )
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}
