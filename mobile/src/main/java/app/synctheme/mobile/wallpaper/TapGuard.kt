package app.synctheme.mobile.wallpaper

/**
 * Решает, открывать ли приложение по нажатию на часы на обоях. Касания рабочего стола система
 * дублирует обоям всегда — и тогда, когда палец попал в папку, меню или список последних приложений
 * над часами. Поэтому нажатие засчитывается, только если поверх обоев ничего не раскрыто ни в момент
 * нажатия, ни за [delayMs] после него (за это время приложение из папки или «последних» успевает
 * начать открываться — и обои скрываются).
 *
 * Время — в миллисекундах одних и тех же часов (uptime).
 */
class TapGuard(val delayMs: Long = DELAY_MS) {
    /** Насколько лаунчер сейчас «отодвинул» обои: больше нуля — открыты папка, список приложений или «последние». */
    private var zoom = 0f

    /** Когда в последний раз что-то раскрылось поверх обоев или они скрылись. */
    private var coveredAt = Long.MIN_VALUE / 2

    /** Открыт список последних приложений (с этого момента), null — закрыт. */
    private var recentsAt: Long? = null

    fun onZoom(value: Float, now: Long) {
        zoom = value
        if (value > ZOOM_EPSILON) coveredAt = now
    }

    /** Обои закрыло приложение. Список последних приложений, если был открыт, закрылся вместе с ними. */
    fun onHidden(now: Long) {
        coveredAt = now
        recentsAt = null
    }

    /** Система открыла список последних приложений. */
    fun onRecents(now: Long) {
        recentsAt = now
        coveredAt = now
    }

    /** Нажали «Домой» — список последних приложений закрыт. */
    fun onHome() {
        recentsAt = null
    }

    /** Можно ли сейчас считать касание нажатием на часы. */
    fun accepts(now: Long): Boolean {
        if (zoom > ZOOM_EPSILON) return false
        val recents = recentsAt ?: return true
        // Если «Домой» не пришло (жест «назад»), через RECENTS_MS список наверняка закрыт.
        return now - recents > RECENTS_MS
    }

    /** Ожидание после нажатия в [tapAt] закончилось: открывать, только если поверх так ничего и не раскрылось. */
    fun confirms(tapAt: Long, now: Long): Boolean = accepts(now) && coveredAt < tapAt - BEFORE_MS

    companion object {
        const val DELAY_MS = 650L
        /** Папка, открытая этим же касанием, начинает раскрываться чуть раньше, чем палец отпущен. */
        const val BEFORE_MS = 150L
        const val RECENTS_MS = 15_000L
        private const val ZOOM_EPSILON = 0.001f
    }
}
