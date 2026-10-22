package app.synctheme.mobile

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Presets
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSet
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.install.AdbService
import app.synctheme.mobile.link.DeviceKind
import app.synctheme.mobile.link.LinkClient
import app.synctheme.mobile.link.LinkProtocol
import app.synctheme.mobile.link.LinkReceiver
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.link.LinkUnreachable
import app.synctheme.mobile.link.Peer
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.MainViewModel
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * Телефон и устройство (планшет, телевизор) в одном процессе: устройство слушает localhost, телефон
 * подключается, на устройстве нажимают «Подключить» — и тема с фото уходит на него. Проверяется и то,
 * что без подтверждения и с чужим секретом ничего не проходит.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LinkTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun fresh() = LinkStore.reload()

    @After
    fun stop() = LinkReceiver.stop(app)

    @Test
    fun linksAfterConfirmAndSendsTheme() = runBlocking {
        LinkReceiver.start(app)
        val port = LinkReceiver.port
        assertTrue(port > 0)

        // На устройстве нажали «Отклонить» — телефон не подключён.
        answerOnDevice(false)
        expectError("отказались") { LinkClient.link(app, "127.0.0.1", port, null) { _, _ -> } }
        assertTrue(LinkStore.peers(app).isEmpty())

        // «Подключить»: код на телефоне тот же, что на устройстве.
        var phoneCode = ""
        val deviceCode = answerOnDevice(true)
        val peer = LinkClient.link(app, "127.0.0.1", port, null) { code, _ -> phoneCode = code }
        assertEquals(6, phoneCode.length)
        assertEquals(deviceCode.get(), phoneCode)
        assertEquals(DeviceSettings.name(app), peer.name)
        assertEquals(peer, LinkStore.peers(app).single())
        assertNotNull("устройство запомнило телефон", LinkStore.phone(app))
        assertEquals("запрос закрылся", null, LinkReceiver.state.value.request)

        // Тема с фото уходит на устройство: фото приезжает с ней, тема становится применённой.
        // (Телефон и устройство здесь — один процесс с общей папкой фото.)
        val repo = ThemeRepository.get(app)
        val photo = "photo_tablet.jpg"
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        repo.photoFile(photo).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val sent = repo.photoFile(photo).readBytes()
        val theme = Presets.all[2].copy(name = "С телефона", background = Background(BackgroundType.PHOTO, listOf(Color.BLACK), photoFile = photo))
        val reply = LinkClient.push(app, theme, peer)
        assertTrue("ответ устройства: $reply", reply.contains("С телефона"))
        assertEquals("С телефона", repo.applied.value.name)
        assertTrue(repo.photoFile(photo).readBytes().contentEquals(sent))
        assertEquals(true, LinkStore.push(app).value[peer.id]?.ok)

        // Устройство подключили к другому телефону — старый секрет не подходит.
        LinkStore.setPhone(app, LinkStore.phone(app)!!.copy(secret = ByteArray(32)))
        expectError("другому телефону") { LinkClient.push(app, theme, peer) }
        assertEquals(false, LinkStore.push(app).value[peer.id]?.ok)
    }

    /** Пока на устройстве не ответили одному телефону, второй получает «подождите», а не перебивает первый. */
    @Test
    fun oneRequestAtATime() = runBlocking {
        LinkReceiver.start(app)
        val port = LinkReceiver.port
        val first = thread { runCatching { runBlocking { LinkClient.link(app, "127.0.0.1", port, null) { _, _ -> } } } }
        waitFor { LinkReceiver.state.value.request != null }
        expectError("подключает другой телефон") { LinkClient.link(app, "127.0.0.1", port, null) { _, _ -> } }
        LinkReceiver.answer(true)
        first.join(20_000)
        assertEquals(1, LinkStore.peers(app).size)
    }

    /**
     * Приём на устройстве перезапустился (перезагрузка, система закрыла службу) — порт тот же, и телефон
     * находит устройство по последнему адресу даже без поиска в сети. Устройство выключено — отправка
     * откладывается и повторится сама.
     */
    @Test
    fun survivesRestartAndRetriesWhenAway() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        LinkReceiver.start(app)
        val port = LinkReceiver.port
        assertTrue("постоянный порт", port in LinkProtocol.PORTS)
        answerOnDevice(true)
        val peer = LinkClient.link(app, "127.0.0.1", port, null) { _, _ -> }
        val theme = Presets.all[3].copy(name = "После перезапуска")

        LinkReceiver.stop(app)
        LinkReceiver.start(app)
        // Порт — снова из постоянных (если прежний ещё не освободился — соседний); телефон перебирает их сам.
        assertTrue(LinkReceiver.port in LinkProtocol.PORTS)
        assertTrue(LinkClient.push(app, theme, peer).contains("После перезапуска"))
        assertEquals(true, LinkStore.push(app).value[peer.id]?.ok)

        LinkReceiver.stop(app)
        try {
            LinkClient.push(app, theme, LinkStore.peer(app, peer.id)!!)
            fail("устройство выключено — ожидалась ошибка")
        } catch (e: LinkUnreachable) {
            assertTrue(e.message.orEmpty().contains("появится в сети"))
        }
        val status = LinkStore.push(app).value[peer.id]!!
        assertFalse(status.ok)
        assertTrue("повтор запланирован", status.pending)
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork("link_retry").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }

    /**
     * «Применить» на телефоне: тема уходит на все включённые устройства сразу. Одно с ошибкой не мешает
     * остальным, выключенное в списке («не отправлять») тему не получает.
     */
    @Test
    fun applyReachesEveryEnabledDevice() {
        LinkReceiver.start(app)
        answerOnDevice(true)
        val good = runBlocking { LinkClient.link(app, "127.0.0.1", LinkReceiver.port, null) { _, _ -> } }
        // Второе устройство «подключено к другому телефону»: тот же адрес, но чужой секрет.
        val stale = good.copy(id = "stale-tv", name = "Телевизор в зале", kind = DeviceKind.TV, secret = ByteArray(32) { 7 })
        val off = good.copy(id = "off-tab", name = "Старый планшет", enabled = false)
        LinkStore.savePeer(app, stale)
        LinkStore.savePeer(app, off)
        DeviceSettings.setWatch(app, false)
        val vm = MainViewModel(app)
        assertTrue("устройства среди целей", vm.devices.value.tablet)
        val theme = Presets.all[4]
        vm.select(theme)
        vm.apply()
        waitFor(90_000) {
            shadowOf(Looper.getMainLooper()).idle()
            val push = LinkStore.push(app).value
            push[good.id] != null && push[stale.id] != null
        }
        val push = LinkStore.push(app).value
        assertEquals("Тема «${theme.name}» на месте", push[good.id]?.text)
        assertEquals(false, push[stale.id]?.ok)
        assertTrue(push[stale.id]?.text.orEmpty().contains("другому телефону"))
        assertEquals("выключенному не отправляли", null, push[off.id])
        assertTrue(LinkReceiver.state.value.last.orEmpty().contains(theme.name))
    }

    /** Телевизор: представляется телефону телевизором, тема идёт в заставку (обоев и виджетов у него нет). */
    @Test
    fun tvShowsThemeAsScreensaver() = runBlocking {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertEquals(DeviceKind.TV, DeviceSettings.kind(app))
        LinkReceiver.start(app)
        answerOnDevice(true)
        val peer = LinkClient.link(app, "127.0.0.1", LinkReceiver.port, null) { _, _ -> }
        assertEquals(DeviceKind.TV, peer.kind)
        val theme = Presets.all[1].copy(name = "Вечер")
        val reply = LinkClient.push(app, theme, peer)
        assertEquals("Тема «Вечер» — в заставке телевизора", reply)
        assertEquals("Вечер", ThemeRepository.get(app).applied.value.name)
        val frame = ThemeRenderer.tvFrame(app, theme, 640, 360, 0L)
        assertEquals(640, frame.width)
        assertEquals(360, frame.height)
    }

    /** Планшет, связанный версией до 1.17, остаётся в списке — подключать заново не нужно. */
    @Test
    fun keepsTabletFromOlderVersion() {
        app.getSharedPreferences("tablet_link", 0).edit()
            .putString("tablet_name", "Galaxy Tab S9 FE")
            .putString("tablet_secret", Base64.encodeToString(ByteArray(32) { 1 }, Base64.NO_WRAP))
            .putString("tablet_host", "192.168.1.20").putInt("tablet_port", 47231)
            .commit()
        LinkStore.reload()
        val peer = LinkStore.peers(app).single()
        assertEquals("Galaxy Tab S9 FE", peer.name)
        assertEquals(DeviceKind.TABLET, peer.kind)
        assertEquals("192.168.1.20", peer.host)
        assertTrue(peer.enabled)
        // После первого изменения список хранится в новом виде и читается так же.
        LinkStore.updatePeer(app, peer.copy(enabled = false))
        LinkStore.reload()
        assertEquals(false, LinkStore.peers(app).single().enabled)
    }

    /** Старые записи того же устройства в сети («… (2)», «… (4)») сводятся в одну — живую, на постоянном порту. */
    @Test
    fun oneEntryPerDevice() {
        val list = listOf(
            AdbService("SyncTheme · Tab S9 FE+ Ислам", "192.168.1.20", 40123),
            AdbService("SyncTheme · Tab S9 FE+ Ислам (4)", "192.168.1.20", 47231),
            AdbService("SyncTheme · Tab S9 FE+ Ислам (3)", "192.168.1.20", 38817),
            AdbService("SyncTheme · Tab S9 FE+ Ислам (2)", "192.168.1.20", 41002),
            AdbService("SyncTheme · Galaxy S24", "192.168.1.31", 47231),
            // Телевизор переименовали, но id тот же — это одно устройство.
            AdbService("SyncTheme · Tuvio", "192.168.1.40", 40999, mapOf(LinkProtocol.ATTR_ID to "tv1", LinkProtocol.ATTR_KIND to "TV")),
            AdbService("SyncTheme · Телевизор", "192.168.1.40", 47231, mapOf(LinkProtocol.ATTR_ID to "tv1", LinkProtocol.ATTR_KIND to "TV")),
        )
        val one = LinkClient.distinctDevices(list)
        assertEquals(3, one.size)
        val tab = one.single { it.name == "Tab S9 FE+ Ислам" }
        assertEquals("живая запись — на постоянном порту", 47231, tab.service.port)
        val tv = one.single { it.id == "tv1" }
        assertEquals(DeviceKind.TV, tv.kind)
        assertEquals("Телевизор", tv.name)
        assertEquals("SyncTheme · Tab S9 FE+ Ислам", LinkClient.baseName("SyncTheme · Tab S9 FE+ Ислам (12)"))
    }

    /** По умолчанию — только телефон: часы и другие устройства выключены, «Применить» их не трогает. */
    @Test
    fun phoneOnlyByDefault() {
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        app.getSharedPreferences("watch_status", 0).edit().clear().commit()
        assertFalse("часы выключены на новой установке", DeviceSettings.watch(app))
        val targets = ApplyTargets().on(DeviceSet())
        assertFalse(targets.watch)
        assertFalse(targets.tablet)
        assertTrue(targets.homeWallpaper && targets.lockWallpaper && targets.widgets)
        // Устройство в списке, но выключено — тоже не цель.
        assertFalse(ApplyTargets().on(DeviceSet(peers = listOf(Peer("p", "Планшет", DeviceKind.TABLET, ByteArray(32), enabled = false)))).tablet)
        // Кто уже пользовался часами, их не теряет.
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        app.getSharedPreferences("watch_status", 0).edit().putStringSet("app_nodes", setOf("node1")).commit()
        assertTrue(DeviceSettings.watch(app))
    }

    /** «Сохранить» делает новую тему рядом с прежними, «Обновить» — меняет только эту. */
    @Test
    fun savingKeepsPreviousThemes() {
        val repo = ThemeRepository.get(app)
        repo.saved.value.forEach(repo::delete)
        val first = repo.save(Presets.all[0].copy(name = "Закат"))
        val second = repo.save(first.copy(accentColor = Color.RED))
        assertEquals(listOf("Закат 2", "Закат"), repo.saved.value.map { it.name })
        assertTrue(first.id != second.id)
        repo.save(second.copy(name = "Закат красный"), replace = true)
        assertEquals(listOf("Закат красный", "Закат"), repo.saved.value.map { it.name })
        // «Обновить» у темы, которой нет в списке, всё равно сохраняет её, а не теряет.
        repo.save(Presets.all[1].copy(id = "nope", name = "Новая"), replace = true)
        assertEquals(3, repo.saved.value.size)
    }

    /** На устройстве ответят на ближайший запрос подключения; результат — код, который оно показало. */
    private fun answerOnDevice(approve: Boolean): CompletableFuture<String> {
        val code = CompletableFuture<String>()
        thread(isDaemon = true) {
            val deadline = System.currentTimeMillis() + 20_000
            while (LinkReceiver.state.value.request == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
            code.complete(LinkReceiver.state.value.request?.code.orEmpty())
            LinkReceiver.answer(approve)
        }
        return code
    }

    private fun waitFor(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("не дождались")
            Thread.sleep(50)
        }
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
