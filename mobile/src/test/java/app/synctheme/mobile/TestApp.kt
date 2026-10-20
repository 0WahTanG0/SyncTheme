package app.synctheme.mobile

import androidx.work.testing.WorkManagerTestInitHelper

/** В Robolectric App Startup не запускается, поэтому WorkManager инициализируем сами. */
class TestApp : SyncThemeApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.onCreate()
    }
}
