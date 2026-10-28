package app.synctheme.mobile

import android.app.Application
import android.content.res.Configuration
import androidx.work.WorkManager
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.util.CrashLog
import app.synctheme.mobile.widget.WidgetScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class SyncThemeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ThemeRepository.get(this)
        WidgetScheduler.ensure(this)
        DayNight.ensure(this)
        // В 1.8.0 был «Хамелеон» с фоновой работой — убираем её и его настройки.
        runCatching {
            WorkManager.getInstance(this).cancelUniqueWork("chameleon")
            WorkManager.getInstance(this).cancelUniqueWork("chameleon-now")
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit().remove("chameleon").apply()
        getSharedPreferences("themes", MODE_PRIVATE).edit().remove("mood").apply()
    }

    /** Телефон перешёл на светлое или тёмное оформление, пока приложение запущено. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scope.launch { DayNight.refresh(this@SyncThemeApp) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
