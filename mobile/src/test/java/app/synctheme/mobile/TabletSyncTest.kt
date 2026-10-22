package app.synctheme.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSet
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.tablet.TabletClient
import app.synctheme.mobile.tablet.TabletReceiver
import app.synctheme.mobile.tablet.TabletStore
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Телефон и планшет в одном процессе: планшет слушает localhost, телефон связывается по коду
 * и отправляет тему с фото. Проверяется и то, что чужой код и чужой секрет не проходят.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletSyncTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun stop() = TabletReceiver.stop(app)

    @Test
    fun pairsAndSendsTheme() = runBlocking {
        TabletReceiver.closePairing()
        TabletReceiver.start(app)
        val port = TabletReceiver.port
        assertTrue(port > 0)

        // Без открытого окна связывания и с неверным кодом связаться нельзя.
        expectError("Связать с телефоном") { TabletClient.pair(app, "127.0.0.1", port, "123456") }
        val code = TabletReceiver.openPairing()
        val wrong = if (code == "111111") "222222" else "111111"
        expectError("Неверный код") { TabletClient.pair(app, "127.0.0.1", port, wrong) }

        val peer = TabletClient.pair(app, "127.0.0.1", port, code)
        assertEquals(DeviceSettings.name(app), peer.name)
        assertNotNull("телефон запомнил планшет", TabletStore.tablet(app))
        assertNotNull("планшет запомнил телефон", TabletStore.phone(app))
        assertEquals("окно связывания закрылось", null, TabletReceiver.state.value.code)

        // Тема с фото уходит на планшет: фото приезжает с ней, тема становится применённой.
        // (Телефон и планшет здесь — один процесс с общей папкой фото.)
        val repo = ThemeRepository.get(app)
        val photo = "photo_tablet.jpg"
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        repo.photoFile(photo).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val sent = repo.photoFile(photo).readBytes()
        val theme = Presets.all[2].copy(name = "С телефона", background = Background(BackgroundType.PHOTO, listOf(Color.BLACK), photoFile = photo))
        val reply = TabletClient.push(app, theme)
        assertTrue("ответ планшета: $reply", reply.contains("С телефона"))
        assertEquals("С телефона", repo.applied.value.name)
        assertTrue(repo.photoFile(photo).readBytes().contentEquals(sent))

        // Планшет связали с другим телефоном — старый секрет не подходит.
        TabletStore.setPhone(app, TabletStore.phone(app)!!.copy(secret = ByteArray(32)))
        expectError("другим телефоном") { TabletClient.push(app, theme) }
    }

    /** По умолчанию — только телефон: часы и планшет выключены, «Применить» их не трогает. */
    @Test
    fun phoneOnlyByDefault() {
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        app.getSharedPreferences("watch_status", 0).edit().clear().commit()
        assertFalse("часы выключены на новой установке", DeviceSettings.watch(app))
        val targets = ApplyTargets().on(DeviceSet())
        assertFalse(targets.watch)
        assertFalse(targets.tablet)
        assertTrue(targets.homeWallpaper && targets.lockWallpaper && targets.widgets)
        // Кто уже пользовался часами, их не теряет.
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        app.getSharedPreferences("watch_status", 0).edit().putStringSet("app_nodes", setOf("node1")).commit()
        assertTrue(DeviceSettings.watch(app))
    }

    private suspend fun expectError(part: String, block: suspend () -> Unit) {
        try {
            block()
            fail("ожидалась ошибка «$part»")
        } catch (e: IOException) {
            assertTrue("«${e.message}» содержит «$part»", e.message.orEmpty().contains(part))
        }
    }
}
