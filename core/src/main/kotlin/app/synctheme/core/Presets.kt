package app.synctheme.core

/**
 * Набор готовых тем. У каждой свой стиль циферблата, а цвета подобраны так, чтобы хорошо смотреться
 * и на AMOLED телефона, и на часах. Светлые темы — с плотной подложкой виджетов и без затемнения.
 */
object Presets {
    private fun c(hex: Long) = hex.toInt()

    val all: List<Theme> = listOf(
        Theme(
            id = "aurora", name = "Аврора",
            background = Background(BackgroundType.AURORA, listOf(c(0xFF060B1E), c(0xFF19E3B1), c(0xFF6C4BF4), c(0xFF1A8CFF))),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFF5EF2C6),
            clockStyle = ClockStyle.GLASS, fontWeight = FontWeight.LIGHT, dim = 0.1f,
        ),
        Theme(
            id = "sunset", name = "Закат",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF1B0B3A), c(0xFFC2185B), c(0xFFFF8A3D), c(0xFFFFD27A)), angle = 165f),
            textColor = c(0xFFFFF6EC), accentColor = c(0xFFFFC857),
            clockStyle = ClockStyle.BOLD, fontWeight = FontWeight.BOLD,
        ),
        Theme(
            id = "ocean", name = "Океан",
            background = Background(BackgroundType.RADIAL, listOf(c(0xFF00C2E0), c(0xFF0A3D91), c(0xFF020A1F))),
            textColor = c(0xFFE6FBFF), accentColor = c(0xFF00E5FF),
            clockStyle = ClockStyle.ORBIT, fontWeight = FontWeight.REGULAR,
        ),
        Theme(
            id = "lagoon", name = "Лагуна",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF023E8A), c(0xFF0077B6), c(0xFF00B4A6)), angle = 20f),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFFFFE66D),
            clockStyle = ClockStyle.LIQUID, fontWeight = FontWeight.BOLD,
        ),
        Theme(
            id = "neon", name = "Неон",
            background = Background(BackgroundType.AURORA, listOf(c(0xFF0B0220), c(0xFFFF2A6D), c(0xFF05D9E8), c(0xFF7B2CBF))),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFFFF2A6D),
            clockStyle = ClockStyle.NEON, fontWeight = FontWeight.REGULAR,
        ),
        Theme(
            id = "cosmos", name = "Космос",
            background = Background(BackgroundType.AURORA, listOf(c(0xFF05030F), c(0xFF3D1E6D), c(0xFF8E44AD), c(0xFF1B1464))),
            textColor = c(0xFFF3E8FF), accentColor = c(0xFFFFB86C),
            clockStyle = ClockStyle.PLANETS, fontWeight = FontWeight.REGULAR,
        ),
        Theme(
            id = "graphite", name = "Графит",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF2A2D31), c(0xFF101113)), angle = 90f),
            textColor = c(0xFFF2F2F2), accentColor = c(0xFFFF6B1A),
            clockStyle = ClockStyle.DRUM, fontWeight = FontWeight.REGULAR, dim = 0f,
        ),
        Theme(
            id = "terminal", name = "Терминал",
            background = Background(BackgroundType.RADIAL, listOf(c(0xFF0B2414), c(0xFF040A06), c(0xFF000000))),
            textColor = c(0xFFC8FFD4), accentColor = c(0xFF39FF6A),
            clockStyle = ClockStyle.MATRIX, fontWeight = FontWeight.REGULAR,
        ),
        Theme(
            id = "forest", name = "Лес",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFF0E2A1F), c(0xFF1F5136), c(0xFF6B8F3A)), angle = 200f),
            textColor = c(0xFFF1F8E9), accentColor = c(0xFFC8E6A0),
            clockStyle = ClockStyle.ANALOG, fontWeight = FontWeight.LIGHT, dialStyle = DialStyle.NUMBERS,
        ),
        Theme(
            id = "sakura", name = "Сакура",
            background = Background(BackgroundType.AURORA, listOf(c(0xFFFFF1F5), c(0xFFFF8FAB), c(0xFFFFC2D1), c(0xFFE7A6B8))),
            textColor = c(0xFF3D1A2A), accentColor = c(0xFFD6336C),
            clockStyle = ClockStyle.BLOB, fontWeight = FontWeight.BOLD, dim = 0f, widgetOpacity = 0.5f,
        ),
        Theme(
            id = "latte", name = "Латте",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFFF7EBDD), c(0xFFDCC0A3), c(0xFFA9805E)), angle = 175f),
            textColor = c(0xFF2B1D14), accentColor = c(0xFF9C4A26),
            clockStyle = ClockStyle.COLUMNS, fontWeight = FontWeight.REGULAR, dim = 0f, widgetOpacity = 0.5f,
        ),
        Theme(
            id = "pop", name = "Поп-арт",
            background = Background(BackgroundType.LINEAR, listOf(c(0xFFFFD23F), c(0xFFFF8E3C), c(0xFFFF5D73)), angle = 135f),
            textColor = c(0xFF1B1B3A), accentColor = c(0xFF3A0CA3),
            clockStyle = ClockStyle.DIAGONAL, fontWeight = FontWeight.BOLD, dim = 0f, widgetOpacity = 0.5f,
        ),
        Theme(
            id = "mono", name = "Минимал",
            background = Background(BackgroundType.SOLID, listOf(c(0xFF000000))),
            textColor = c(0xFFFFFFFF), accentColor = c(0xFFFF3B30),
            clockStyle = ClockStyle.MINIMAL, fontWeight = FontWeight.THIN, dialStyle = DialStyle.MINIMAL, dim = 0f,
        ),
    )

    val default: Theme get() = all.first()
}
