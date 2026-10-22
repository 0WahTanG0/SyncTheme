package app.synctheme.mobile.render

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import app.synctheme.mobile.util.Compat
import androidx.core.content.FileProvider
import app.synctheme.core.Theme
import java.io.File

/** Обои экрана блокировки файлом — для системного «Установить как». */
object LockScreenImage {
    fun export(context: Context, theme: Theme): Uri {
        val (w, h) = Compat.screen(context)
        val bitmap = ThemeRenderer.lockBackground(context, theme, w, h)
        val file = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("lock.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun setAsIntent(uri: Uri): Intent {
        val attach = Intent(Intent.ACTION_ATTACH_DATA)
            .setDataAndType(uri, "image/jpeg")
            .putExtra("mimeType", "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(attach, "Установить как обои").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
