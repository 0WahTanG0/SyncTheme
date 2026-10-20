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

@Serializable
data class Theme(
    val id: String,
    val name: String,
    val background: Background = Background(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFFFFB86C.toInt(),
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val fontWeight: FontWeight = FontWeight.LIGHT,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    /** Затемнение фона 0..0.8 — чтобы цифры читались на ярких фото. */
    val dim: Float = 0.15f,
    /** Непрозрачность подложки виджетов 0..1. */
    val widgetOpacity: Float = 0.35f,
    /** Показывать два мини-виджета (батарея, шаги) на циферблате часов. */
    val watchComplications: Boolean = true,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): Theme = json.decodeFromString(serializer(), text)
    }
}
