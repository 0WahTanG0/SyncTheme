package app.synctheme.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Тип фона темы. */
@Serializable
enum class BackgroundType { LINEAR, RADIAL, AURORA, SOLID, PHOTO }

/** Раскладка часов: на телефоне (виджеты) и на Galaxy Watch. */
@Serializable
enum class ClockStyle { DIGITAL, STACKED, ANALOG }

/** Насыщенность шрифта часов. */
@Serializable
enum class FontWeight { THIN, LIGHT, REGULAR, BOLD }

/** Оформление стрелочного циферблата часов. */
@Serializable
enum class DialStyle { TICKS, NUMBERS, MINIMAL }

@Serializable
data class Background(
    val type: BackgroundType = BackgroundType.AURORA,
    /** ARGB-цвета градиента (для SOLID берётся первый). */
    val colors: List<Int> = listOf(0xFF0B1026.toInt(), 0xFF3A1C71.toInt(), 0xFFD76D77.toInt()),
    /** Угол линейного градиента в градусах. */
    val angle: Float = 135f,
    /** Имя файла фото в каталоге приложения на телефоне (только для PHOTO). */
    val photoFile: String? = null,
)

/**
 * Настройки циферблата Galaxy Watch. Если в теме их нет (`Theme.watch == null`),
 * часы повторяют стиль телефона — см. [Theme.watchSettings].
 */
@Serializable
data class WatchSettings(
    /** null — тот же фон, что на рабочем столе телефона. */
    val background: Background? = null,
    val textColor: Int,
    val accentColor: Int,
    val clockStyle: ClockStyle,
    val fontWeight: FontWeight,
    val dialStyle: DialStyle = DialStyle.TICKS,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val dim: Float = 0.15f,
    val complications: Boolean = true,
)

@Serializable
data class Theme(
    val id: String,
    val name: String,
    val background: Background = Background(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFFFFB86C.toInt(),
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val fontWeight: FontWeight = FontWeight.LIGHT,
    val dialStyle: DialStyle = DialStyle.TICKS,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    /** Затемнение фона 0..0.8 — чтобы цифры читались на ярких фото. */
    val dim: Float = 0.15f,
    /** Непрозрачность подложки новых виджетов 0..1. */
    val widgetOpacity: Float = 0.35f,
    /** Показывать два мини-виджета (батарея, шаги) на циферблате часов. */
    val watchComplications: Boolean = true,
    /** Отдельные обои экрана блокировки; null — как на рабочем столе. */
    val lockBackground: Background? = null,
    val lockDim: Float? = null,
    /** Отдельные настройки часов; null — часы подстраиваются под телефон. */
    val watch: WatchSettings? = null,
) {
    /** Итоговые настройки циферблата: свои или повторяющие телефон. */
    fun watchSettings(): WatchSettings = watch ?: followPhone()

    /** Настройки часов, повторяющие телефон, — с них начинается «отдельная настройка». */
    fun followPhone(): WatchSettings = WatchSettings(
        background = null,
        textColor = textColor,
        accentColor = accentColor,
        clockStyle = clockStyle,
        fontWeight = fontWeight,
        dialStyle = dialStyle,
        showSeconds = showSeconds,
        showDate = showDate,
        dim = dim,
        complications = watchComplications,
    )

    fun watchBackground(): Background = watchSettings().background ?: background

    fun lockScreenBackground(): Background = lockBackground ?: background

    fun lockScreenDim(): Float = lockDim ?: dim

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): Theme = json.decodeFromString(serializer(), text)
    }
}
