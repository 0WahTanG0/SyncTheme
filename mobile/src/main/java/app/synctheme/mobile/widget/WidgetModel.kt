package app.synctheme.mobile.widget

import android.content.Context
import android.util.SizeF
import app.synctheme.core.DigitFont
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val FAMILY_STYLES = listOf(
    WidgetStyle("bold", "Крупно"),
    WidgetStyle("blob", "Облако"),
    WidgetStyle("glass", "Стекло"),
    WidgetStyle("neon", "Неон"),
    WidgetStyle("minimal", "Минимал"),
)

/** Вид виджета: у каждого свой набор стилей; prefix — начало имён его разметки. */
enum class WidgetKind(val title: String, val description: String, val prefix: String, val styles: List<WidgetStyle>) {
    CLOCK(
        "Часы", "Цифры или стрелки; на большом виджете — дата и будильник", "clock",
        FAMILY_STYLES + listOf(
            WidgetStyle("diag", "Диагональ"),
            WidgetStyle("analog_blob", "Стрелки: облако"),
            WidgetStyle("analog_neon", "Стрелки: неон"),
            WidgetStyle("analog_numbers", "Стрелки: цифры"),
            WidgetStyle("analog_minimal", "Стрелки: минимал"),
        ),
    ),
    CALENDAR("Календарь", "Число, ближайшие события и месяц", "cal", FAMILY_STYLES),
    WEATHER("Погода", "Сейчас, по часам и на 5 дней", "wx", FAMILY_STYLES),
    BATTERY("Батарея", "Заряд, время до полной зарядки, температура", "bat", FAMILY_STYLES),
    APPS(
        "Приложения", "Папка с любимыми приложениями", "apps",
        listOf(
            WidgetStyle("flower", "Цветок"),
            WidgetStyle("cookie", "Печенье"),
            WidgetStyle("glass", "Стекло"),
            WidgetStyle("neon", "Неон"),
            WidgetStyle("capsule", "Капсула"),
            WidgetStyle("minimal", "Минимал"),
        ),
    );

    val defaultStyle: String get() = styles.first().id

    fun style(id: String): WidgetStyle = styles.firstOrNull { it.id == id } ?: styles.first()
}

data class WidgetStyle(val id: String, val title: String)

/** Шрифт цифр, с которым стиль цифровых часов выглядит задуманно. */
fun defaultDigitFont(style: String): DigitFont = when (style) {
    "blob" -> DigitFont.BUBBLE
    "glass" -> DigitFont.SERIF
    "neon" -> DigitFont.TUBE
    "minimal" -> DigitFont.THIN
    "diag" -> DigitFont.BLOCK
    else -> DigitFont.NARROW
}

/**
 * Размер виджета. Лаунчер сам выбирает подходящую разметку: самую большую, которая помещается
 * (minW × minH, dp). previewW/H — размер превью в настройке.
 */
enum class WidgetSize(val key: String, val title: String, val minW: Float, val minH: Float, val previewW: Int, val previewH: Int) {
    S("s", "1×1", 50f, 50f, 100, 100),
    STRIP("strip", "4×1", 180f, 50f, 320, 84),
    M("m", "2×2", 110f, 110f, 170, 170),
    WIDE("wide", "4×2", 190f, 110f, 330, 170),
    L("l", "4×4", 190f, 280f, 330, 330);

    val sizeF get() = SizeF(minW, minH)
}

@Serializable
enum class WidgetAlign(val title: String) { START("Слева"), CENTER("По центру"), END("Справа") }

/** Настройки одного размещённого виджета; цвета по умолчанию берутся из темы. */
@Serializable
data class WidgetConfig(
    val style: String,
    val align: WidgetAlign = WidgetAlign.CENTER,
    /** Непрозрачность подложки 0..1 (0 — без фона). */
    val opacity: Float = 0.35f,
    /** Секундная стрелка у стрелочных часов. */
    val showSeconds: Boolean = true,
    /** null — как в теме. */
    val textColor: Int? = null,
    val accentColor: Int? = null,
    /** Иконки погоды в своих цветах или в цвете текста. */
    val colorIcons: Boolean = true,
    /** Приложения в папке: «пакет/активити». */
    val apps: List<String> = emptyList(),
    /** Подпись папки (пусто — без подписи). */
    val title: String = "",
    /** Что открывать по нажатию: пакет приложения или null — стандартное для вида виджета. */
    val openApp: String? = null,
    /** Шрифт цифр часов ([DigitFont]); null — как задумано в стиле. */
    val font: String? = null,
) {
    fun digitFont(): DigitFont = font?.let { f -> DigitFont.entries.firstOrNull { it.name == f } } ?: defaultDigitFont(style)
}

/** Хранит настройки каждого виджета по его appWidgetId. */
object WidgetConfigStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_configs", Context.MODE_PRIVATE)

    fun get(context: Context, id: Int, kind: WidgetKind, defaultOpacity: Float): WidgetConfig {
        val stored = prefs(context).getString(id.toString(), null)
            ?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
            ?: return WidgetConfig(style = kind.defaultStyle, opacity = defaultOpacity)
        // Стили прошлых версий («classic», «big_day» …) заменяются на стиль по умолчанию.
        return stored.copy(style = kind.style(stored.style).id)
    }

    fun set(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit().putString(id.toString(), json.encodeToString(WidgetConfig.serializer(), config)).apply()
    }

    fun delete(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(it.toString()) } }.apply()
    }
}
