package app.synctheme.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.install.InstallerViewModel
import app.synctheme.mobile.install.WatchInstallerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val installerViewModel: InstallerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var showInstaller by rememberSaveable { mutableStateOf(false) }
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            if (showInstaller) {
                val close: () -> Unit = {
                    showInstaller = false
                    viewModel.refreshWatch()
                }
                BackHandler(onBack = close)
                WatchInstallerScreen(installerViewModel, theme.accentColor, onClose = close)
            } else {
                SyncThemeScreen(viewModel, onOpenInstaller = { showInstaller = true })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWatch()
    }
}
