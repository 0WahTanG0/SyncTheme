package app.synctheme.mobile

import android.app.Application
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.widget.WidgetScheduler

open class SyncThemeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeRepository.get(this)
        WidgetScheduler.ensure(this)
    }
}
