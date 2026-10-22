package app.synctheme.mobile

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.synctheme.core.BackgroundType
import app.synctheme.core.Presets
import app.synctheme.mobile.data.SharedImage
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.ui.MainViewModel
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * «Поделиться» → SyncTheme: картинка по ссылке на страницу (как у Pinterest: короткая ссылка,
 * перенаправление, og:image) и по прямой ссылке — без сохранения в галерею.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedImageTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val jpeg = run {
        val bmp = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 90, 40)) }
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun route(path: String, code: Int, type: String?, body: ByteArray, location: String? = null) =
            server.createContext(path) { ex ->
                type?.let { ex.responseHeaders.add("Content-Type", it) }
                location?.let { ex.responseHeaders.add("Location", it) }
                ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
                ex.close()
            }
        val page = """<html><head><meta name="twitter:card" content="summary">
            <meta content="/i/736x/ab/cd.jpg?a=1&amp;b=2" property="og:image"></head><body>pin</body></html>""".toByteArray()
        route("/pin.it/abc", 302, null, ByteArray(0), "/pin/123/")
        route("/pin/123/", 200, "text/html; charset=utf-8", page)
        route("/i/", 200, "image/jpeg", jpeg)
        route("/direct.jpg", 200, "image/jpeg", jpeg)
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun findsLinkAndPageImage() {
        assertEquals("https://pin.it/xYz", SharedImage.firstUrl("Посмотрите этот пин в Pinterest: https://pin.it/xYz."))
        val html = """<meta property="og:image" content="https://i.pinimg.com/736x/0a/1b/2c.jpg">"""
        assertEquals("https://i.pinimg.com/736x/0a/1b/2c.jpg", SharedImage.pageImage(html, "https://www.pinterest.com/pin/1/"))
        assertEquals("https://i.pinimg.com/originals/0a/1b/2c.jpg", SharedImage.original("https://i.pinimg.com/736x/0a/1b/2c.jpg"))
        assertEquals("https://site.ru/img/a.png", SharedImage.pageImage("""<link rel="image_src" href="/img/a.png">""", "https://site.ru/page"))
    }

    @Test
    fun downloadsFromPageAndDirectLink() = runBlocking<Unit> {
        val fromPage = SharedImage.download(app, "$base/pin.it/abc")
        assertNotNull(BitmapFactory.decodeFile(fromPage.path))
        val direct = SharedImage.download(app, "$base/direct.jpg")
        assertEquals(jpeg.size.toLong(), direct.length())
    }

    /** Ссылка из «Поделиться» становится фоном рабочего стола в черновике. */
    @Test
    fun sharedLinkBecomesWallpaper() {
        ThemeRepository.get(app).setDraft(Presets.all[0])
        val vm = MainViewModel(app)
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Смотри: $base/pin.it/abc")
        assertTrue(SharedImage.accepts(intent))
        vm.importShared(intent)
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.theme.value.background.type == BackgroundType.PHOTO) return@repeat
            Thread.sleep(150)
        }
        val bg = vm.theme.value.background
        assertEquals(BackgroundType.PHOTO, bg.type)
        assertTrue(ThemeRepository.get(app).photoFile(bg.photoFile!!).exists())
    }
}
