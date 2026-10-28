package app.synctheme.mobile.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.synctheme.mobile.data.SharedImage
import app.synctheme.mobile.install.InstallTarget
import app.synctheme.mobile.install.InstallerViewModel
import app.synctheme.mobile.install.WatchInstallerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val installerViewModel: InstallerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialTab = runCatching { Tab.valueOf(intent.getStringExtra(EXTRA_TAB).orEmpty()) }.getOrDefault(Tab.WALLPAPER)
        // Картинку прислали через «Поделиться» (Pinterest, браузер…): ставим её фоном в черновик.
        if (savedInstanceState == null && SharedImage.accepts(intent)) viewModel.importShared(intent)
        setContent {
            // Открытый установщик: на часы или на телевизор (имя InstallTarget), null — главный экран.
            var installer by rememberSaveable { mutableStateOf<String?>(null) }
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val target = installer?.let { runCatching { InstallTarget.valueOf(it) }.getOrNull() }
            if (target != null) {
                // После перезапуска процесса модель новая — напоминаем ей, что ставим.
                LaunchedEffect(target) { installerViewModel.setTarget(target) }
                val close: () -> Unit = {
                    installer = null
                    viewModel.refreshWatch()
                }
                BackHandler(onBack = close)
                WatchInstallerScreen(
                    installerViewModel, theme.accentColor, onClose = close,
                    onLinkTv = { host, port, name ->
                        close()
                        viewModel.linkDevice(host, port, name, null)
                    },
                )
            } else {
                SyncThemeScreen(
                    viewModel, initialTab,
                    onOpenInstaller = { open(InstallTarget.WATCH) { installer = it } },
                    onOpenTvInstaller = { open(InstallTarget.TV) { installer = it } },
                    firstLaunchCheck = true,
                )
            }
        }
    }

    private fun open(target: InstallTarget, show: (String) -> Unit) {
        installerViewModel.setTarget(target)
        show(target.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (SharedImage.accepts(intent)) viewModel.importShared(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWatch()
        viewModel.refreshPlaced()
        viewModel.onResumed()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        val TAB_WIDGETS = Tab.WIDGETS.name
    }
}
