package app.synctheme.mobile

import android.app.Application
import app.synctheme.mobile.data.ThemeRepository

class SyncThemeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeRepository.get(this)
    }
}
