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
import app.synctheme.mobile.install.AdbService
import app.synctheme.mobile.tablet.TabletClient
import app.synctheme.mobile.tablet.TabletReceiver
import app.synctheme.mobile.tablet.TabletProtocol
import app.synctheme.mobile.tablet.TabletStore
import app.synctheme.mobile.tablet.TabletUnreachable
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import android.os.Looper
import app.synctheme.mobile.ui.MainViewModel
import java.io.IOException
import org.robolectric.Shadows.shadowOf
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
        expectError("Код устарел") { TabletClient.pair(app, "127.0.0.1", port, "123456") }
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

    /**
     * Приём на планшете перезапустился (перезагрузка, система закрыла службу) — порт тот же, и телефон
     * находит планшет по последнему адресу даже без поиска в сети. Планшет выключен — отправка
     * откладывается и повторится сама.
     */
    @Test
    fun survivesRestartAndRetriesWhenAway() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        TabletReceiver.closePairing()
        TabletReceiver.start(app)
        val port = TabletReceiver.port
        assertTrue("постоянный порт", port in TabletProtocol.PORTS)
        TabletClient.pair(app, "127.0.0.1", port, TabletReceiver.openPairing())
        val theme = Presets.all[3].copy(name = "После перезапуска")

        TabletReceiver.stop(app)
        TabletReceiver.start(app)
        // Порт — снова из постоянных (если прежний ещё не освободился — соседний); телефон перебирает их сам.
        assertTrue(TabletReceiver.port in TabletProtocol.PORTS)
        assertTrue(TabletClient.push(app, theme).contains("После перезапуска"))
        assertEquals(true, TabletStore.push(app).value?.ok)

        TabletReceiver.stop(app)
        try {
            TabletClient.push(app, theme)
            fail("планшет выключен — ожидалась ошибка")
        } catch (e: TabletUnreachable) {
            assertTrue(e.message.orEmpty().contains("появится в сети"))
        }
        val status = TabletStore.push(app).value!!
        assertFalse(status.ok)
        assertTrue("повтор запланирован", status.pending)
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork("tablet_retry").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }

    /** «Применить» на телефоне: тема уходит на связанный планшет, и он её ставит; состояние видно на телефоне. */
    @Test
    fun applyOnPhoneReachesTablet() {
        TabletReceiver.closePairing()
        TabletReceiver.start(app)
        runBlocking { TabletClient.pair(app, "127.0.0.1", TabletReceiver.port, TabletReceiver.openPairing()) }
        DeviceSettings.setWatch(app, false)
        val vm = MainViewModel(app)
        assertTrue("планшет среди устройств", vm.devices.value.tablet)
        val theme = Presets.all[4]
        vm.select(theme)
        vm.apply()
        val deadline = System.currentTimeMillis() + 90_000
        while (TabletStore.push(app).value?.ok != true && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(100)
        }
        assertEquals("Тема «${theme.name}» на планшете", TabletStore.push(app).value?.text)
        assertTrue(TabletReceiver.state.value.last.orEmpty().contains(theme.name))
    }

    /** Старые записи того же планшета в сети («… (2)», «… (4)») сводятся в одну — живую, на постоянном порту. */
    @Test
    fun oneEntryPerTablet() {
        val list = listOf(
            AdbService("SyncTheme · Tab S9 FE+ Ислам", "192.168.1.20", 40123),
            AdbService("SyncTheme · Tab S9 FE+ Ислам (4)", "192.168.1.20", 47231),
            AdbService("SyncTheme · Tab S9 FE+ Ислам (3)", "192.168.1.20", 38817),
            AdbService("SyncTheme · Tab S9 FE+ Ислам (2)", "192.168.1.20", 41002),
            AdbService("SyncTheme · Galaxy S24", "192.168.1.31", 47231),
        )
        val one = TabletClient.distinctDevices(list)
        assertEquals(2, one.size)
        val tab = one.single { it.name == "SyncTheme · Tab S9 FE+ Ислам" }
        assertEquals("живая запись — на постоянном порту", 47231, tab.port)
        assertEquals("SyncTheme · Tab S9 FE+ Ислам", TabletClient.baseName("SyncTheme · Tab S9 FE+ Ислам (12)"))
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
