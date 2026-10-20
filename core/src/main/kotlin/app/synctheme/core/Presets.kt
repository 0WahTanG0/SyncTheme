package app.synctheme.core

/** Набор готовых тем. Цвета подобраны так, чтобы хорошо смотреться и на AMOLED телефона, и на часах. */
object Presets {
    private fun c(hex: Long) = hex.toInt()

    val all: List<Theme> = listOf(
        Theme(
            id = "aurora", name = "Аврора",
            background = Background(BackgroundType.AURORA, listOf(c(0xFF050A1F), c(0xFF1DE9B6), c(0xFF6A3DE8), c(0xFF0091EA))),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFF64FFDA),
            clockStyle = ClockStyle.DIGITAL, fontWeight = FontWeight.LIGHT,
        ),
        Theme(
            id = "sunset", name = "Закат",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF2B1055), c(0xFFD53369), c(0xFFFFB347)), angle = 160f),
            textColor = c(0xFFFFF4E6), accentColor = c(0xFFFFD166),
            clockStyle = ClockStyle.STACKED, fontWeight = FontWeight.BOLD,
        ),
        Theme(
            id = "ocean", name = "Океан",
            background = Background(BackgroundType.RADIAL, listOf(c(0xFF00B4DB), c(0xFF0B3D91), c(0xFF020617))),
            textColor = c(0xFFE0F7FF), accentColor = c(0xFF00E5FF),
            clockStyle = ClockStyle.ANALOG, fontWeight = FontWeight.REGULAR,
        ),
        Theme(
            id = "graphite", name = "Графит",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF232526), c(0xFF0F0F10)), angle = 90f),
            textColor = c(0xFFF5F5F5), accentColor = c(0xFFFF6D00),
            clockStyle = ClockStyle.DIGITAL, fontWeight = FontWeight.THIN,
        ),
        Theme(
            id = "neon", name = "Неон",
            background = Background(BackgroundType.AURORA, listOf(c(0xFF0D0221), c(0xFFFF2A6D), c(0xFF05D9E8), c(0xFF7B2CBF))),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFFFF2A6D),
            clockStyle = ClockStyle.STACKED, fontWeight = FontWeight.REGULAR,
        ),
        Theme(
            id = "forest", name = "Лес",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF0B3D2E), c(0xFF134E3A), c(0xFF5A8F29)), angle = 200f),
            textColor = c(0xFFF1F8E9), accentColor = c(0xFFC5E1A5),
            clockStyle = ClockStyle.ANALOG, fontWeight = FontWeight.LIGHT, dialStyle = DialStyle.NUMBERS,
        ),
        Theme(
            id = "sakura", name = "Сакура",
            background = Background(BackgroundType.AURORA, listOf(c(0xFFFFE4EC), c(0xFFFF8FAB), c(0xFFFFC2D1), c(0xFFB5838D))),
            textColor = c(0xFF3D1A2A), accentColor = c(0xFFD6336C),
            clockStyle = ClockStyle.DIGITAL, fontWeight = FontWeight.BOLD, dim = 0f,
        ),
        Theme(
            id = "mono", name = "Минимал",
            background = Background(BackgroundType.SOLID, listOf(c(0xFF000000))),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFFFF3B30),
            clockStyle = ClockStyle.ANALOG, fontWeight = FontWeight.THIN, dialStyle = DialStyle.MINIMAL, dim = 0f,
        ),
    )

    val default: Theme get() = all.first()
}
