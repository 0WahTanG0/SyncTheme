package app.synctheme.mobile.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import app.synctheme.R
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Город для погоды; [auto] — брать координаты телефона при каждом открытии приложения. */
@Serializable
data class WeatherPlace(val name: String, val lat: Double, val lon: Double, val auto: Boolean = false, val region: String = "")

@Serializable
data class WeatherNow(val temp: Double, val feels: Double, val humidity: Int, val wind: Double, val code: Int, val isDay: Boolean)

@Serializable
data class WeatherHour(val time: String, val temp: Double, val code: Int, val isDay: Boolean)

@Serializable
data class WeatherDay(val date: String, val max: Double, val min: Double, val code: Int)

@Serializable
data class WeatherData(
    val place: String,
    val updatedAt: Long,
    val now: WeatherNow,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
)

/** Погода Open-Meteo (без ключа и регистрации): кэш для виджетов и настройки города. */
class WeatherRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    var place: WeatherPlace?
        get() = prefs.getString("place", null)?.let { runCatching { json.decodeFromString(WeatherPlace.serializer(), it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove("place") else putString("place", json.encodeToString(WeatherPlace.serializer(), value))
        }.apply()

    val cached: WeatherData?
        get() = prefs.getString("data", null)?.let { runCatching { json.decodeFromString(WeatherData.serializer(), it) }.getOrNull() }

    /** Загружает прогноз для выбранного города и кладёт в кэш. */
    suspend fun refresh(): WeatherData = withContext(Dispatchers.IO) {
        val p = place ?: throw IllegalStateException("Город не выбран")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=6&wind_speed_unit=ms"
        val data = parseForecast(get(url), p.name, System.currentTimeMillis())
        prefs.edit().putString("data", json.encodeToString(WeatherData.serializer(), data)).apply()
        data
    }

    /** Поиск города по названию (на русском). */
    suspend fun search(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        parseSearch(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=ru&format=json"))
    }

    /** Координаты телефона (нужно разрешение на приблизительное местоположение). */
    @SuppressLint("MissingPermission")
    suspend fun locate(): WeatherPlace? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(LocationManager::class.java)
        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    // Ответ сразу продолжает корутину — отдельный поток (и его утечка) не нужен.
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, Runnable::run) {
                        cont.resume(it)
                    }
                }.onFailure { cont.resume(null) }
            }
        }
        val loc = fresh ?: listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            ?: return@withContext null
        @Suppress("DEPRECATION")
        val name = runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: context.getString(R.string.weather_my_location)
        WeatherPlace(name, loc.latitude, loc.longitude, auto = true)
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "SyncTheme")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Сервер погоды ответил ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Разбор ответа /v1/forecast: текущая погода, 6 ближайших часов и 5 дней. */
        fun parseForecast(text: String, place: String, now: Long): WeatherData {
            val root = json.parseToJsonElement(text).jsonObject
            val cur = root.obj("current")
            val hourly = root.obj("hourly")
            val daily = root.obj("daily")
            val currentTime = cur.str("time")
            val times = hourly.arr("time").map { it.jsonPrimitive.content }
            // Первый час после текущего (время в часовом поясе города).
            val start = times.indexOfFirst { it > currentTime }.coerceAtLeast(0)
            val hours = (start until minOf(start + 6, times.size)).map { i ->
                WeatherHour(
                    time = times[i].substringAfter('T').take(5),
                    temp = hourly.arr("temperature_2m")[i].jsonPrimitive.double,
                    code = hourly.arr("weather_code")[i].jsonPrimitive.int,
                    isDay = hourly.arr("is_day")[i].jsonPrimitive.int == 1,
                )
            }
            val days = daily.arr("time").indices.take(5).map { i ->
                WeatherDay(
                    date = daily.arr("time")[i].jsonPrimitive.content,
                    max = daily.arr("temperature_2m_max")[i].jsonPrimitive.double,
                    min = daily.arr("temperature_2m_min")[i].jsonPrimitive.double,
                    code = daily.arr("weather_code")[i].jsonPrimitive.int,
                )
            }
            return WeatherData(
                place = place,
                updatedAt = now,
                now = WeatherNow(
                    temp = cur.num("temperature_2m"),
                    feels = cur.num("apparent_temperature"),
                    humidity = cur.num("relative_humidity_2m").toInt(),
                    wind = cur.num("wind_speed_10m"),
                    code = cur.num("weather_code").toInt(),
                    isDay = cur.num("is_day").toInt() == 1,
                ),
                hours = hours,
                days = days,
            )
        }

        fun parseSearch(text: String): List<WeatherPlace> {
            val results = json.parseToJsonElement(text).jsonObject["results"] as? JsonArray ?: return emptyList()
            return results.map { it.jsonObject }.map { r ->
                WeatherPlace(
                    name = r.str("name"),
                    lat = r.num("latitude"),
                    lon = r.num("longitude"),
                    region = listOfNotNull(r["admin1"]?.jsonPrimitive?.content, r["country"]?.jsonPrimitive?.content)
                        .joinToString(", "),
                )
            }
        }

        private fun JsonObject.obj(key: String) = getValue(key).jsonObject
        private fun JsonObject.arr(key: String) = getValue(key).jsonArray
        private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
        private fun JsonObject.num(key: String) = getValue(key).jsonPrimitive.double
    }
}

/** Код погоды WMO → иконка и описание. */
object WeatherCodes {
    fun icon(code: Int, isDay: Boolean): Int = when (code) {
        0 -> if (isDay) R.drawable.wx_clear_day else R.drawable.wx_clear_night
        1, 2 -> if (isDay) R.drawable.wx_partly_day else R.drawable.wx_partly_night
        3 -> R.drawable.wx_cloudy
        45, 48 -> R.drawable.wx_fog
        in 51..57 -> R.drawable.wx_drizzle
        in 61..67, in 80..82 -> R.drawable.wx_rain
        in 71..77, 85, 86 -> R.drawable.wx_snow
        in 95..99 -> R.drawable.wx_thunder
        else -> R.drawable.wx_cloudy
    }

    /** Небо для «Фона по погоде» (см. SkyScenes). */
    fun scene(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "clear_day" else "clear_night"
        45, 48 -> "fog"
        in 51..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storm"
        else -> if (isDay) "cloudy_day" else "cloudy_night"
    }

    fun description(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снег"
        75 -> "Сильный снег"
        77 -> "Снежная крупа"
        80, 81 -> "Ливень"
        82 -> "Сильный ливень"
        85, 86 -> "Снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "—"
    }
}
