package app.synctheme.mobile

import android.app.Application
import androidx.work.WorkManager
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.widget.WidgetScheduler

open class SyncThemeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeRepository.get(this)
        WidgetScheduler.ensure(this)
        // В 1.8.0 был «Хамелеон» с фоновой работой — убираем её и его настройки.
        runCatching {
            WorkManager.getInstance(this).cancelUniqueWork("chameleon")
            WorkManager.getInstance(this).cancelUniqueWork("chameleon-now")
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit().remove("chameleon").apply()
        getSharedPreferences("themes", MODE_PRIVATE).edit().remove("mood").apply()
    }
}
