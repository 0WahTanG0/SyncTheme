package app.synctheme.mobile.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * То, что в новых Android делается одним вызовом, а в старых (SyncTheme работает с Android 9) —
 * по-старому. Новое API — всегда первым, старое — только для своих версий.
 */
object Compat {
    /** Есть ли у лаунчера виджеты Android 12: точные размеры, отзывчивые разметки, цвета через ColorStateList. */
    val modernWidgets get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Размер экрана целиком, px: ширина и высота в портретной ориентации. */
    fun screen(context: Context): Pair<Int, Int> {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
        return minOf(w, h) to maxOf(w, h)
    }

    @Suppress("DEPRECATION")
    fun archiveInfo(pm: PackageManager, path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageArchiveInfo(path, flags)

    @Suppress("DEPRECATION")
    fun packageInfo(pm: PackageManager, name: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(name, flags)
}
