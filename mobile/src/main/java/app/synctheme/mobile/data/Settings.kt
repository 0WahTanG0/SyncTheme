package app.synctheme.mobile.data

import android.content.Context

/** Куда применять тему по кнопке «Применить». */
data class ApplyTargets(
    val homeWallpaper: Boolean = true,
    val lockWallpaper: Boolean = true,
    val widgets: Boolean = true,
    val watch: Boolean = true,
) {
    val any get() = homeWallpaper || lockWallpaper || widgets || watch

    companion object {
        fun load(context: Context): ApplyTargets {
            val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return ApplyTargets(
                p.getBoolean("home", true), p.getBoolean("lock", true),
                p.getBoolean("widgets", true), p.getBoolean("watch", true),
            )
        }

        fun save(context: Context, t: ApplyTargets) {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean("home", t.homeWallpaper).putBoolean("lock", t.lockWallpaper)
                .putBoolean("widgets", t.widgets).putBoolean("watch", t.watch)
                .apply()
        }
    }
}
