package app.synctheme.mobile.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import app.synctheme.mobile.ui.MainActivity

/** Приложение, которое можно запустить с рабочего стола. component — «пакет/активити». */
data class AppEntry(val component: String, val label: String) {
    val packageName get() = component.substringBefore('/')
}

/** Список приложений, их значки и то, что открывается по нажатию на виджет. */
object AppCatalog {
    /** Погода Samsung: на некоторых прошивках у неё нет значка в меню, ищем её экран сами. */
    const val SAMSUNG_WEATHER = "com.sec.android.daemonapp"

    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppEntry("${it.activityInfo.packageName}/${it.activityInfo.name}", it.loadLabel(pm).toString()) }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, component: String): String? = runCatching {
        val pm = context.packageManager
        pm.getActivityInfo(ComponentName.unflattenFromString(component)!!, 0).loadLabel(pm).toString()
    }.getOrNull()

    /** Четыре привычных приложения для новой папки: телефон, сообщения, камера, браузер. */
    fun defaults(context: Context): List<String> {
        val pm = context.packageManager
        val candidates = listOf(
            Intent(Intent.ACTION_DIAL),
            Telephony.Sms.getDefaultSmsPackage(context)?.let { pm.getLaunchIntentForPackage(it) },
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        )
        val launchable = launchable(context)
        return candidates.mapNotNull { intent ->
            val pkg = intent?.let { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            launchable.firstOrNull { it.packageName == pkg }?.component
        }.distinct().take(4).ifEmpty { launchable.take(4).map { it.component } }
    }

    fun launchIntent(context: Context, component: String): Intent? {
        val cn = ComponentName.unflattenFromString(component)
        return if (cn != null) {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } else {
            context.packageManager.getLaunchIntentForPackage(component)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Значок приложения в форме стиля: круг или «сквиркл». */
    fun icon(context: Context, component: String, px: Int, circle: Boolean): Bitmap? = runCatching {
        val pm = context.packageManager
        val cn = ComponentName.unflattenFromString(component) ?: return null
        val drawable = pm.getActivityIcon(cn)
        render(drawable, px, circle)
    }.getOrNull()

    private fun render(drawable: Drawable, px: Int, circle: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val clip = Path().apply {
            if (circle) addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW)
            else addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.3f, px * 0.3f, Path.Direction.CW)
        }
        if (drawable is AdaptiveIconDrawable) {
            // Слои адаптивной иконки больше видимой части на 50 % — растягиваем и обрезаем своей формой.
            canvas.clipPath(clip)
            val pad = px / 4
            for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
                layer.setBounds(-pad, -pad, px + pad, px + pad)
                layer.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /** Что открывает нажатие на виджет: своё приложение пользователя или стандартное для вида. */
    fun clickIntent(context: Context, kind: WidgetKind, config: WidgetConfig): PendingIntent? {
        val intent = config.openApp?.let { launchIntent(context, it) } ?: defaultIntent(context, kind) ?: return null
        return PendingIntent.getActivity(
            context, kind.ordinal + 1000 * (config.openApp?.hashCode()?.and(0xFFFF) ?: 0), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun defaultIntent(context: Context, kind: WidgetKind): Intent? {
        val pm = context.packageManager
        fun Intent.ifResolves() = takeIf { pm.resolveActivity(it, 0) != null }
        val intent = when (kind) {
            WidgetKind.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS).ifResolves()
            WidgetKind.CALENDAR -> Intent(
                Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build(),
            ).ifResolves()
            WidgetKind.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY).ifResolves()
                ?: Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).ifResolves()
            WidgetKind.WEATHER -> weatherApp(context)
                ?: Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_WIDGETS)
            WidgetKind.APPS -> null
        }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Приложение погоды: Samsung Weather — через значок или его главный экран, иначе любое «погодное». */
    fun weatherApp(context: Context): Intent? {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(SAMSUNG_WEATHER)?.let { return it }
        runCatching {
            val info = pm.getPackageInfo(SAMSUNG_WEATHER, PackageManager.GET_ACTIVITIES)
            val main = Intent(Intent.ACTION_MAIN).setPackage(SAMSUNG_WEATHER)
            pm.resolveActivity(main, 0)?.activityInfo?.let { return main.setClassName(it.packageName, it.name) }
            info.activities?.filter { it.exported && it.enabled }
                ?.sortedByDescending { a -> listOf("Main", "Home", "Weather", "Detail").count { a.name.contains(it, true) } }
                ?.firstOrNull()
                ?.let { return Intent().setClassName(it.packageName, it.name) }
        }
        return launchable(context).firstOrNull { it.label.contains("погод", true) || it.label.contains("weather", true) }
            ?.let { launchIntent(context, it.component) }
    }
}
