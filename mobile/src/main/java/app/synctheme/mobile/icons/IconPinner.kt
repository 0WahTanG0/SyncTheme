package app.synctheme.mobile.icons

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.synctheme.mobile.widget.AppCatalog
import app.synctheme.mobile.widget.AppEntry

/**
 * Значки чужих приложений Android поменять не даёт, поэтому SyncTheme ставит на рабочий стол
 * ярлыки: тот же запуск приложения, но значок — в стиле оформления. У ярлыка постоянный id по
 * приложению, так что при смене стиля уже поставленные значки перерисовываются на месте, без окон.
 */
object IconPinner {
    private const val PREFIX = "icon:"

    fun supported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun style(context: Context, fallback: IconStyle): IconStyle =
        prefs(context).getString("icon_style", null)?.let { runCatching { IconStyle.valueOf(it) }.getOrNull() } ?: fallback

    fun setStyle(context: Context, style: IconStyle) = prefs(context).edit().putString("icon_style", style.name).apply()

    /** Приложения, чьи значки SyncTheme уже поставил на рабочий стол. */
    fun pinned(context: Context): List<String> = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .filter { it.id.startsWith(PREFIX) }.map { it.id.removePrefix(PREFIX) }
    }.getOrDefault(emptyList())

    private fun shortcut(context: Context, app: AppEntry, style: IconStyle, accent: Int): ShortcutInfoCompat? {
        val intent = AppCatalog.launchIntent(context, app.component) ?: return null
        val bitmap = IconStyler.render(context, app.component, style, accent) ?: return null
        return ShortcutInfoCompat.Builder(context, PREFIX + app.component)
            .setShortLabel(app.label.ifBlank { app.packageName })
            .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
            .setIntent(intent)
            .build()
    }

    /** Просит лаунчер поставить значок: он покажет окно «Добавить на главный экран». */
    fun request(context: Context, app: AppEntry, style: IconStyle, accent: Int): Boolean {
        val info = shortcut(context, app, style, accent) ?: return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** Перерисовывает уже поставленные значки в новом стиле (или цвете темы). Возвращает, сколько обновлено. */
    fun restyle(context: Context, style: IconStyle, accent: Int): Int {
        val pinned = pinned(context)
        if (pinned.isEmpty()) return 0
        val apps = AppCatalog.launchable(context).associateBy { it.component }
        val infos = pinned.mapNotNull { component ->
            val app = apps[component] ?: AppEntry(component, AppCatalog.label(context, component) ?: return@mapNotNull null)
            shortcut(context, app, style, accent)
        }
        return if (runCatching { ShortcutManagerCompat.updateShortcuts(context, infos) }.getOrDefault(false)) infos.size else 0
    }

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
