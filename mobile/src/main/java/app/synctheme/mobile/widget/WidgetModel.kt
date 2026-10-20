package app.synctheme.mobile.widget

import android.content.Context
import app.synctheme.R
import app.synctheme.core.FontWeight
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Вид виджета: у каждого свой набор стилей. */
enum class WidgetKind(val title: String, val description: String, val styles: List<WidgetStyle>) {
    CLOCK(
        "Часы", "Цифровые и стрелочные часы",
        listOf(
            WidgetStyle("classic", "Классика"),
            WidgetStyle("big", "Крупно"),
            WidgetStyle("stacked", "Вертикально"),
            WidgetStyle("side", "Дата сбоку"),
            WidgetStyle("seconds", "С секундами"),
            WidgetStyle("analog_ticks", "Стрелки: деления"),
            WidgetStyle("analog_numbers", "Стрелки: цифры"),
            WidgetStyle("analog_minimal", "Стрелки: минимал"),
        ),
    ),
    DATE(
        "Дата", "День недели, число и месяц",
        listOf(
            WidgetStyle("big_day", "Крупное число"),
            WidgetStyle("line", "Строка"),
            WidgetStyle("tear", "Отрывной календарь"),
            WidgetStyle("compact", "Компактно"),
        ),
    ),
    CALENDAR(
        "Календарь", "Месяц и ближайшие события",
        listOf(
            WidgetStyle("month", "Месяц"),
            WidgetStyle("agenda", "События"),
            WidgetStyle("month_agenda", "Месяц + события"),
        ),
    ),
    WEATHER(
        "Погода", "Сейчас, по часам и на неделю",
        listOf(
            WidgetStyle("compact", "Компактно"),
            WidgetStyle("details", "Подробно"),
            WidgetStyle("hourly", "По часам"),
            WidgetStyle("daily", "На 5 дней"),
            WidgetStyle("full", "Всё сразу"),
        ),
    ),
    BATTERY(
        "Батарея", "Заряд телефона",
        listOf(
            WidgetStyle("ring", "Кольцо"),
            WidgetStyle("bar", "Полоса"),
            WidgetStyle("big", "Крупные цифры"),
        ),
    );

    val defaultStyle: String get() = styles.first().id

    fun style(id: String): WidgetStyle = styles.firstOrNull { it.id == id } ?: styles.first()
}

data class WidgetStyle(val id: String, val title: String)

@Serializable
enum class WidgetAlign(val title: String) { START("Слева"), CENTER("По центру"), END("Справа") }

@Serializable
enum class WidgetShape(val title: String, val drawable: Int) {
    ROUND("Скруглённый", R.drawable.widget_bg_round),
    SOFT("Мягкий", R.drawable.widget_bg_soft),
    SQUARE("Квадратный", R.drawable.widget_bg_square),
    PILL("Капсула", R.drawable.widget_bg_pill),
    OVAL("Овал", R.drawable.widget_bg_oval),
}

/** Настройки одного размещённого виджета; цвета и шрифт по умолчанию берутся из темы. */
@Serializable
data class WidgetConfig(
    val style: String,
    val align: WidgetAlign = WidgetAlign.CENTER,
    val shape: WidgetShape = WidgetShape.ROUND,
    /** Непрозрачность подложки 0..1 (0 — без фона). */
    val opacity: Float = 0.35f,
    val showDate: Boolean = true,
    val showSeconds: Boolean = true,
    /** null — как в теме. */
    val fontWeight: FontWeight? = null,
    val textColor: Int? = null,
    val accentColor: Int? = null,
    /** Иконки погоды в своих цветах или в цвете текста. */
    val colorIcons: Boolean = true,
)

/** Хранит настройки каждого виджета по его appWidgetId. */
object WidgetConfigStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_configs", Context.MODE_PRIVATE)

    fun get(context: Context, id: Int, kind: WidgetKind, defaultOpacity: Float): WidgetConfig =
        prefs(context).getString(id.toString(), null)
            ?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
            ?: WidgetConfig(style = kind.defaultStyle, opacity = defaultOpacity)

    fun set(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit().putString(id.toString(), json.encodeToString(WidgetConfig.serializer(), config)).apply()
    }

    fun delete(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(it.toString()) } }.apply()
    }
}
