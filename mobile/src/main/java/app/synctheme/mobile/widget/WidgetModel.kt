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
 * Разметка виджета под его форму: квадратные (S, M, L), вытянутые по горизонтали (BAR, STRIP, WIDE)
 * и по вертикали (NARROW, COLUMN, TALL). Какую показать, решает [classify] по размерам, которые
 * сообщает лаунчер. minW × minH (dp) — «идеальный» размер, когда размеры ещё неизвестны;
 * previewW/H — размер превью в настройке.
 */
enum class WidgetSize(val key: String, val title: String, val minW: Float, val minH: Float, val previewW: Int, val previewH: Int) {
    S("s", "Квадратик", 40f, 40f, 100, 100),
    BAR("bar", "Полоска", 100f, 40f, 200, 84),
    STRIP("strip", "Длинная полоска", 170f, 40f, 320, 84),
    NARROW("narrow", "Столбик", 40f, 100f, 72, 200),
    COLUMN("column", "Высокий столбик", 40f, 200f, 72, 330),
    M("m", "Квадрат", 90f, 90f, 170, 170),
    TALL("tall", "Высокий", 90f, 170f, 170, 330),
    WIDE("wide", "Широкий", 170f, 90f, 330, 170),
    L("l", "Большой", 200f, 200f, 330, 330);

    val sizeF get() = SizeF(minW, minH)

    /** Вытянут по вертикали: главное значение подбирается по ширине (см. [VerticalFit]). */
    val vertical get() = this == NARROW || this == COLUMN || this == TALL

    companion object {
        /**
         * Разметка для виджета w × h dp: сначала по пропорциям (вытянут ли он), потом по размеру —
         * так и столбик 1×5, и полоска 4×1, и квадрат 2×2 показывают своё, а не растянутый квадратик.
         */
        fun classify(w: Float, h: Float): WidgetSize {
            val aspect = w / h
            return when {
                aspect >= 1.6f -> when {
                    h >= 100f -> WIDE
                    w < 150f -> BAR
                    else -> STRIP
                }
                aspect <= 0.62f -> when {
                    w >= 75f -> TALL
                    h < 170f -> NARROW
                    else -> COLUMN
                }
                minOf(w, h) < 95f -> S
                minOf(w, h) < 200f -> M
                else -> L
            }
        }
    }
}

@Serializable
enum class WidgetAlign(val title: String) { START("Слева"), CENTER("По центру"), END("Справа") }

/** Как «Приложения» выглядят на рабочем столе. */
@Serializable
enum class FolderMode(val title: String) {
    /** Значки приложений прямо на виджете. */
    ICONS("Значки"),
    /** Спокойный значок папки; нажатие раскрывает папку. */
    ICON("Значок папки"),
    /** Виджета не видно; нажатие на его место раскрывает папку. */
    HIDDEN("Невидимый"),
}

/** Как раскрывается папка. */
@Serializable
enum class FolderAnim(val title: String) {
    REVEAL("Раскрытие"),
    SPRING("Пружина"),
    FAN("Веер"),
    CASCADE("Каскад"),
    SPIRAL("Спираль"),
}

/** Рисунок на значке папки. */
@Serializable
enum class FolderGlyph(val title: String) {
    DOTS("Точки"),
    SILHOUETTES("Силуэты"),
}

/** Приложений в папке: на виджете со значками — 8, в раскрывающейся — 12. */
const val APPS_ON_WIDGET = 8
const val APPS_IN_FOLDER = 12

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
    /** Погода: фон — небо под текущую погоду (днём и ночью) вместо подложки стиля. */
    val scene: Boolean = true,
    /** Часы: фон — небо под время суток (рассвет, день, закат, вечер, ночь). */
    val sky: Boolean = false,
    /** Приложения в папке: «пакет/активити». */
    val apps: List<String> = emptyList(),
    /** Подпись папки (пусто — без подписи). */
    val title: String = "",
    /** Что открывать по нажатию: пакет приложения или null — стандартное для вида виджета. */
    val openApp: String? = null,
    /** Шрифт цифр часов ([DigitFont]); null — как задумано в стиле. */
    val font: String? = null,
    /** «Приложения»: значки на виджете, значок папки или невидимый виджет. */
    val folder: FolderMode = FolderMode.ICONS,
    val animation: FolderAnim = FolderAnim.REVEAL,
    val glyph: FolderGlyph = FolderGlyph.DOTS,
) {
    /** Приложения раскрываются из папки, а не лежат на виджете. */
    val isFolder get() = folder != FolderMode.ICONS

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
