package app.synctheme.mobile.widget

import android.content.Context
import android.util.SizeF
import app.synctheme.core.DigitFont
import app.synctheme.core.DigitFonts
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Пять стилей, общих для всех видов виджетов, — непохожих друг на друга (см. scripts/gen_widgets.py). */
private val FAMILY_STYLES = listOf(
    WidgetStyle("glass", "Стекло"),
    WidgetStyle("poster", "Плакат"),
    WidgetStyle("neon", "Неон"),
    WidgetStyle("paper", "Бумага"),
    WidgetStyle("board", "Табло"),
)

/** Стили до 1.21 — самый похожий из новых. */
private val LEGACY_STYLES = mapOf(
    "bold" to "poster", "blob" to "poster", "diag" to "board", "minimal" to "paper",
    "analog_blob" to "poster", "analog_neon" to "neon", "analog_numbers" to "glass", "analog_minimal" to "paper",
    "flower" to "poster", "cookie" to "poster", "capsule" to "glass",
)

/** Вид виджета: у каждого свой набор стилей; prefix — начало имён его разметки. */
enum class WidgetKind(val title: String, val description: String, val prefix: String, val styles: List<WidgetStyle>) {
    CLOCK("Часы", "Время любым из шести шрифтов; на большом виджете — дата и будильник", "clock", FAMILY_STYLES),
    CALENDAR("Календарь", "Число, ближайшие события и месяц", "cal", FAMILY_STYLES),
    WEATHER("Погода", "Сейчас, по часам и на 5 дней", "wx", FAMILY_STYLES),
    BATTERY("Батарея", "Заряд, время до полной зарядки, температура", "bat", FAMILY_STYLES),
    APPS("Приложения", "Папка с любимыми приложениями", "apps", FAMILY_STYLES);

    val defaultStyle: String get() = styles.first().id

    /** Стиль по id; стиль прошлых версий — самый похожий из новых, неизвестный — первый. */
    fun style(id: String): WidgetStyle = (LEGACY_STYLES[id] ?: id).let { key -> styles.firstOrNull { it.id == key } } ?: styles.first()
}

data class WidgetStyle(val id: String, val title: String)

/**
 * Шрифт цифр, с которым стиль часов выглядит задуманно: тонкие системные цифры на стекле, узкий
 * гротеск на плакате, светящиеся линии в неоне, антиква на бумаге и точки на табло.
 */
fun defaultDigitFont(style: String): DigitFont = when (style) {
    "glass" -> DigitFont.THIN
    "neon" -> DigitFont.RETRO
    "paper" -> DigitFont.SERIF
    "board" -> DigitFont.DOTS
    else -> DigitFont.NARROW
}

/**
 * Разметка виджета под его форму: квадратные (S, M, L), вытянутые по горизонтали (BAR, STRIP, WIDE)
 * и по вертикали (NARROW, COLUMN, TALL). Какую показать, решает [classify] по размерам, которые
 * сообщает лаунчер. minW × minH (dp) — «идеальный» размер, когда размеры ещё неизвестны;
 * previewW/H — размер превью в настройке.
 */
/** Размеры превью; [title] — размер в клетках рабочего стола (ширина × высота). */
enum class WidgetSize(val key: String, val title: String, val minW: Float, val minH: Float, val previewW: Int, val previewH: Int) {
    S("s", "1×1", 40f, 40f, 100, 100),
    BAR("bar", "2×1", 100f, 40f, 200, 84),
    STRIP("strip", "4×1", 170f, 40f, 320, 84),
    NARROW("narrow", "1×2", 40f, 100f, 72, 200),
    COLUMN("column", "1×4", 40f, 200f, 72, 330),
    M("m", "2×2", 90f, 90f, 170, 170),
    TALL("tall", "2×4", 90f, 170f, 170, 330),
    WIDE("wide", "4×2", 170f, 90f, 330, 170),
    L("l", "4×4", 200f, 200f, 330, 330);

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

/**
 * Как ложатся приложения раскрытой папки: сеткой, вдоль готовой линии от виджета (она сама
 * поворачивает к середине экрана) или вдоль своей линии, нарисованной пальцем.
 */
@Serializable
enum class FolderShape(val title: String) {
    ARC("Дуга"),
    COLUMN("Столбик"),
    ROW("Строка"),
    WAVE("Волна"),
    RING("Кольцо"),
    GRID("Сетка"),
    CUSTOM("Своя линия"),
}

/** Рисунок на значке папки. */
@Serializable
enum class FolderGlyph(val title: String) {
    DOTS("Точки"),
    ICONS("Значки"),
    SILHOUETTES("Силуэты"),
    TILES("Плитки"),
    PETALS("Лепестки"),
    SPARKLE("Искра"),
    ORBIT("Орбита"),
    LETTER("Буква"),
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
    /** Раскладка раскрытой папки. */
    val shape: FolderShape = FolderShape.GRID,
    /** Своя линия ([FolderShape.CUSTOM]): x0, y0, x1, y1 … в долях экрана — от «откуда» к «куда». */
    val line: List<Float> = emptyList(),
    /** Своя линия для альбомной ориентации (рабочий стол планшета боком устроен иначе). */
    val lineLand: List<Float> = emptyList(),
    /** Подложка и контур у папки-сетки. */
    val panel: Boolean = false,
    /** Затемнять и размывать рабочий стол, пока папка открыта. */
    val dim: Boolean = false,
    /** Подписи под значками в раскрытой папке. */
    val labels: Boolean = true,
) {
    /** Своя линия для ориентации экрана; если для неё линии нет — раскладка «Дуга». */
    fun lineFor(landscape: Boolean): List<Float> = if (landscape) lineLand else line

    /** Своя линия, нарисованная в этой ориентации. */
    fun withLine(line: List<Float>, landscape: Boolean): WidgetConfig =
        if (landscape) copy(shape = FolderShape.CUSTOM, lineLand = line) else copy(shape = FolderShape.CUSTOM, line = line)

    /** Приложения раскрываются из папки, а не лежат на виджете. */
    val isFolder get() = folder != FolderMode.ICONS

    fun digitFont(): DigitFont = DigitFonts.of(font) ?: defaultDigitFont(style)
}

/** Хранит настройки каждого виджета по его appWidgetId. */
object WidgetConfigStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_configs", Context.MODE_PRIVATE)

    fun get(context: Context, id: Int, kind: WidgetKind, defaultOpacity: Float): WidgetConfig {
        val stored = prefs(context).getString(id.toString(), null)
            ?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
            ?: return WidgetConfig(style = kind.defaultStyle, opacity = defaultOpacity)
        // Стили прошлых версий заменяются на самый похожий из новых, шрифты — тоже ([DigitFonts.of]).
        return stored.copy(style = kind.style(stored.style).id, font = DigitFonts.of(stored.font)?.name)
    }

    fun set(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit().putString(id.toString(), json.encodeToString(WidgetConfig.serializer(), config)).apply()
    }

    fun delete(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(it.toString()) } }.apply()
    }
}

/** Свои линии папок: нарисованные один раз, их можно выбрать и для других виджетов. */
object FolderLineStore {
    private const val MAX = 6
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) = context.getSharedPreferences("folder_lines", Context.MODE_PRIVATE)

    fun all(context: Context): List<List<Float>> = prefs(context).getString("lines", null)
        ?.let { runCatching { json.decodeFromString<List<List<Float>>>(it) }.getOrNull() }.orEmpty()

    /** Новая линия — первой; та же линия второй раз не сохраняется. */
    fun add(context: Context, line: List<Float>) {
        if (line.size < 4) return
        save(context, (listOf(line) + all(context).filter { it != line }).take(MAX))
    }

    fun remove(context: Context, line: List<Float>) = save(context, all(context).filter { it != line })

    /** Линия, нарисованная поверх рабочего стола для настройки виджета: настройка заберёт её, когда вернётся на экран. */
    fun setPending(context: Context, line: List<Float>, landscape: Boolean) {
        prefs(context).edit().putString("pending", json.encodeToString(line)).putBoolean("pending_land", landscape).apply()
    }

    /** Линия и нарисована ли она в альбомной ориентации. */
    fun takePending(context: Context): Pair<List<Float>, Boolean>? {
        val p = prefs(context)
        val raw = p.getString("pending", null) ?: return null
        val land = p.getBoolean("pending_land", false)
        p.edit().remove("pending").remove("pending_land").apply()
        return runCatching { json.decodeFromString<List<Float>>(raw) }.getOrNull()?.let { it to land }
    }

    private fun save(context: Context, lines: List<List<Float>>) {
        prefs(context).edit().putString("lines", json.encodeToString(lines)).apply()
    }
}
