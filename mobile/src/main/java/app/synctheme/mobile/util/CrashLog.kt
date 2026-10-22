package app.synctheme.mobile.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Последний сбой приложения — в файл: в «Настройках» его можно скопировать и прислать, а по нему
 * видно, где именно упало. Обработчик системы после записи вызывается как обычно.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { Compat.packageInfo(context.packageManager, context.packageName, 0).versionName }.getOrNull()
        val text = buildString {
            appendLine("SyncTheme ${version.orEmpty()} · Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()) + " · поток ${thread.name}")
            append(trace.take(12_000))
        }
        File(context.filesDir, FILE).writeText(text)
    }

    /** Текст последнего сбоя или null, если сбоев не было (или отчёт удалён). */
    fun last(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
