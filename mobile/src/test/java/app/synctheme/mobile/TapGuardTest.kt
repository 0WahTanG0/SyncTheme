package app.synctheme.mobile

import app.synctheme.mobile.wallpaper.TapGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Нажатие на часы на обоях не открывает их приложение, когда палец был в папке или в «последних». */
class TapGuardTest {
    private val t0 = 100_000L

    @Test
    fun plainTapOnClockOpensApp() {
        val g = TapGuard()
        assertTrue(g.accepts(t0))
        assertTrue(g.confirms(t0, t0 + g.delayMs))
    }

    /** Папка лаунчера уже открыта, нажимают приложение в ней над часами — часы молчат. */
    @Test
    fun tapInsideOpenFolderIsIgnored() {
        val g = TapGuard()
        g.onZoom(0.6f, t0)
        assertFalse(g.accepts(t0 + 5_000))
        // Папку закрыли — часы снова открываются.
        g.onZoom(0f, t0 + 6_000)
        assertTrue(g.accepts(t0 + 7_000))
        assertTrue(g.confirms(t0 + 7_000, t0 + 7_000 + g.delayMs))
    }

    /** Нажатие открыло папку (или приложение из неё) — за время ожидания обои закрылись. */
    @Test
    fun tapThatOpensSomethingIsIgnored() {
        val g = TapGuard()
        assertTrue(g.accepts(t0))
        g.onZoom(0.5f, t0 + 100)
        assertFalse(g.confirms(t0, t0 + g.delayMs))

        val h = TapGuard()
        assertTrue(h.accepts(t0))
        // Приложение из папки открывается медленно: обои скрылись только через полсекунды.
        h.onHidden(t0 + 500)
        assertFalse(h.confirms(t0, t0 + h.delayMs))
    }

    /** Папка начала раскрываться чуть раньше, чем отпустили палец. */
    @Test
    fun folderOpenedJustBeforeTapUpIsIgnored() {
        val g = TapGuard()
        g.onZoom(0.4f, t0 - 80)
        g.onZoom(0f, t0 - 40)
        assertFalse(g.confirms(t0, t0 + g.delayMs))
    }

    /** В списке последних приложений касания по месту часов не считаются, после «Домой» — снова да. */
    @Test
    fun recentsBlockTaps() {
        val g = TapGuard()
        g.onRecents(t0)
        assertFalse(g.accepts(t0 + 3_000))
        g.onHome()
        assertTrue(g.accepts(t0 + 4_000))
        assertTrue(g.confirms(t0 + 4_000, t0 + 4_000 + g.delayMs))
        // «Домой» не пришло (вышли жестом «назад») — блокировка не вечная.
        g.onRecents(t0 + 10_000)
        assertFalse(g.accepts(t0 + 12_000))
        assertTrue(g.accepts(t0 + 10_000 + TapGuard.RECENTS_MS + 1))
        // Из «последних» открыли приложение — обои скрылись, список закрыт.
        g.onRecents(t0 + 40_000)
        g.onHidden(t0 + 41_000)
        assertTrue(g.accepts(t0 + 45_000))
    }
}
