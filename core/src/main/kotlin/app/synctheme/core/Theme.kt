package app.synctheme.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Тип фона темы. */
@Serializable
enum class BackgroundType { LINEAR, RADIAL, AURORA, SOLID, PHOTO }

/**
 * Раскладка циферблата часов. Первые три — классические, остальные — «живые»
 * стили с анимацией и своими шрифтами (см. WatchFaceXml).
 */
@Serializable
enum class ClockStyle {
    DIGITAL, STACKED, ANALOG,
    /** Огромные узкие цифры друг под другом. */
    BOLD,
    /** Кольца часов, минут и секунд с бегущими точками. */
    ORBIT,
    /** Светящееся кольцо, «комета» секунд, время с ореолом. */
    NEON,
    /** Стрелки на «печенье» акцентного цвета. */
    BLOB,
    /** Матовый стеклянный диск и бегущие по его краю секунды. */
    GLASS,
    /** Тонкие цифры, деления и одна точка, которая обходит циферблат за час. */
    MINIMAL,
    /** Кольца с цифрами часов и минут вращаются, текущее время — в окошке справа. */
    DRUM,
    /** Прошлый, текущий и следующий час и минута столбиками, секунды — шкала между ними. */
    COLUMNS,
    /** Часы и минуты — «планеты» на своих орбитах. */
    PLANETS,
    /** Точечные цифры и 60 огоньков секунд по кругу. */
    MATRIX,
    /** Циферблат за минуту заполняется «жидкостью» с пузырьками. */
    LIQUID,
    /** Часы слева сверху, минуты справа снизу, секунды бегут по диагонали. */
    DIAGONAL;

    /** Классические раскладки: у них настраиваются насыщенность шрифта и циферблат стрелок. */
    val classic get() = this == DIGITAL || this == STACKED || this == ANALOG

    /** Есть ли цифры времени (у стрелочных нет — им шрифт цифр не нужен). */
    val hasDigits get() = this != ANALOG && this != BLOB

    /**
     * Время — отдельный блок цифр, который можно подвинуть по циферблату. У стрелок, «Планет»
     * и «Барабана» время привязано к центру циферблата.
     */
    val movableTime get() = this != ANALOG && this != BLOB && this != PLANETS && this != DRUM

    /** Шрифт цифр, с которым стиль задуман; null — системный шрифт с выбранной насыщенностью. */
    val defaultDigitFont: DigitFont?
        get() = when (this) {
            BOLD -> DigitFont.NARROW
            ORBIT -> DigitFont.SPACE
            NEON -> DigitFont.TUBE
            GLASS -> DigitFont.ITALIC
            MINIMAL -> DigitFont.THIN
            DRUM -> DigitFont.MONO
            COLUMNS -> DigitFont.SERIF
            PLANETS -> DigitFont.WIDE
            MATRIX -> DigitFont.DOTS
            LIQUID -> DigitFont.BUBBLE
            DIAGONAL -> DigitFont.BLOCK
            DIGITAL, STACKED, ANALOG, BLOB -> null
        }
}

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
    /** Кадр фото: центр видимой части (доли ширины и высоты фото) … */
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    /** … и увеличение: 1 — фото заполняет экран по меньшей стороне. */
    val zoom: Float = 1f,
) {
    /** Кадр фото по умолчанию — середина, без увеличения. */
    val framed get() = focusX != 0.5f || focusY != 0.5f || zoom != 1f
}

/** Где на обоях стоят часы. */
@Serializable
enum class WallClockLayout {
    /** Крупно в одну строку в верхней трети. */
    TOP,
    /** Часы над минутами во всю ширину. */
    STACKED,
    /** Небольшие часы по центру экрана. */
    CENTER,
}

/**
 * Часы, нарисованные прямо на обоях телефона (живые обои SyncTheme).
 * С [depth] главный объект фото закрывает цифры — часы стоят «за» ним.
 */
@Serializable
data class WallClock(
    val layout: WallClockLayout = WallClockLayout.TOP,
    val depth: Boolean = true,
    /** Шрифт цифр; null — как у циферблата часов. */
    val font: DigitFont? = null,
    val showDate: Boolean = true,
    /** Место, куда часы передвинули вручную; null — само: по объекту фото (с глубиной) или по [layout]. */
    val place: ClockPlace? = null,
    /** Открывать приложение по нажатию на часы. */
    val tapToOpen: Boolean = true,
    /** Что открывать: «пакет/активити»; null — будильники. */
    val openApp: String? = null,
    /** Показывать эти часы и на экране блокировки; иначе там только часы самого телефона. */
    val onLock: Boolean = false,
    /** Погода строкой под часами — на обоях, поэтому за объектом фото, как и время. */
    val weather: Boolean = false,
    /** Заряд батареи строкой под часами. */
    val battery: Boolean = false,
)

/**
 * Часы на обоях, передвинутые вручную: центр цифр (доли ширины и высоты экрана), размер
 * относительно обычного и раскладка — одной строкой или часы над минутами.
 */
@Serializable
data class ClockPlace(val x: Float, val y: Float, val scale: Float = 1f, val stacked: Boolean = false)

/** Кадр фото: центр видимой части (доли фото) и увеличение. */
@Serializable
data class PhotoCrop(val focusX: Float = 0.5f, val focusY: Float = 0.5f, val zoom: Float = 1f)

/**
 * Сдвиг блока времени на циферблате (в точках 450×450) и его масштаб вокруг центра блока.
 * Работает у стилей с [ClockStyle.movableTime].
 */
@Serializable
data class TimeShift(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f) {
    val identity get() = dx == 0f && dy == 0f && scale == 1f
}

/**
 * Настройки циферблата часов. Если в теме их нет (`Theme.watch == null`),
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
    /** Шрифт цифр; null — как задумано в стиле ([ClockStyle.defaultDigitFont]). */
    val digitFont: DigitFont? = null,
    /** Эффект глубины: главный объект фото фона рисуется поверх цифр. */
    val depth: Boolean = false,
    /** Время, передвинутое вручную; null — само: напротив объекта фото (с глубиной) или как в стиле. */
    val timeShift: TimeShift? = null,
) {
    /** Итоговый шрифт цифр; null — системный шрифт с насыщенностью [fontWeight]. */
    fun digits(): DigitFont? = digitFont ?: clockStyle.defaultDigitFont

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): WatchSettings = json.decodeFromString(serializer(), text)
    }
}

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
    /** Шрифт цифр циферблата; null — как задумано в стиле. */
    val digitFont: DigitFont? = null,
    /** Эффект глубины на циферблате. */
    val watchDepth: Boolean = false,
    /** Время на циферблате, передвинутое вручную (когда часы повторяют телефон). */
    val watchTimeShift: TimeShift? = null,
    /** Кадр фото на часах, когда у них тот же фон, что у телефона: его двигают отдельно от телефона. */
    val watchPhotoCrop: PhotoCrop? = null,
    /** Часы на обоях телефона; null — обычные обои без часов. */
    val wallClock: WallClock? = null,
    /** Устарело (до 1.19 — «Адаптивные часы» Samsung); глубина на экране блокировки теперь у [WallClock.onLock]. */
    val lockDepth: Boolean = false,
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
        digitFont = digitFont,
        depth = watchDepth,
        timeShift = watchTimeShift,
    )

    fun watchBackground(): Background = watchSettings().background
        ?: watchPhotoCrop?.let { background.copy(focusX = it.focusX, focusY = it.focusY, zoom = it.zoom) }
        ?: background

    fun lockScreenBackground(): Background = lockBackground ?: background

    fun lockScreenDim(): Float = lockDim ?: dim

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): Theme = json.decodeFromString(serializer(), text)
    }
}
