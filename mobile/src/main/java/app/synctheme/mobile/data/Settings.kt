package app.synctheme.mobile.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import app.synctheme.mobile.watch.WatchStatusStore

/** Куда применять тему по кнопке «Применить». */
data class ApplyTargets(
    val homeWallpaper: Boolean = true,
    val lockWallpaper: Boolean = true,
    val widgets: Boolean = true,
    val watch: Boolean = true,
    val tablet: Boolean = true,
) {
    /** Что из выбранного есть на самом деле: часы и планшет — только если подключены. */
    fun on(devices: DeviceSet) = copy(watch = watch && devices.watch, tablet = tablet && devices.tablet)

    val any get() = homeWallpaper || lockWallpaper || widgets || watch || tablet

    companion object {
        fun load(context: Context): ApplyTargets {
            val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return ApplyTargets(
                p.getBoolean("home", true), p.getBoolean("lock", true),
                p.getBoolean("widgets", true), p.getBoolean("watch", true), p.getBoolean("tablet", true),
            )
        }

        fun save(context: Context, t: ApplyTargets) {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean("home", t.homeWallpaper).putBoolean("lock", t.lockWallpaper)
                .putBoolean("widgets", t.widgets).putBoolean("watch", t.watch).putBoolean("tablet", t.tablet)
                .apply()
        }
    }
}

/**
 * Какие устройства у пользователя: телефон есть всегда, часы и планшет подключаются по желанию.
 * [tablet] — к телефону привязан планшет, которому уходит тема.
 */
data class DeviceSet(val watch: Boolean = false, val tablet: Boolean = false)

object DeviceSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Часы выключены, пока их не подключили. Кто уже пользовался часами (с ними связывалось
     * приложение или их выбирали в «Устройствах»), после обновления их не теряет.
     */
    fun watch(context: Context): Boolean {
        val p = prefs(context)
        if (p.contains("dev_watch")) return p.getBoolean("dev_watch", false)
        val used = WatchStatusStore.knownAppNodes(context).isNotEmpty() || p.contains("my_watch")
        p.edit().putBoolean("dev_watch", used).apply()
        return used
    }

    fun setWatch(context: Context, on: Boolean) = prefs(context).edit().putBoolean("dev_watch", on).apply()

    /** Планшет (или большой складной экран): ему достаётся роль «получать тему с телефона». */
    fun isTablet(context: Context): Boolean = context.resources.configuration.smallestScreenWidthDp >= 600 ||
        context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE

    /** Имя устройства, как оно задано в настройках («Galaxy S25+», «Galaxy Tab S9 FE»), иначе модель. */
    fun name(context: Context): String =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android"
}
