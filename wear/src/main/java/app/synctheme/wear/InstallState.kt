package app.synctheme.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Последний результат установки циферблата (переживает перезапуск процесса). */
data class InstallStatus(
    val state: State = State.WAITING,
    val themeName: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isActive: Boolean = false,
) {
    /** CLASSIC — часы без Watch Face Push: тема у классического циферблата SyncTheme. */
    enum class State { WAITING, INSTALLING, INSTALLED, NEEDS_PERMISSION, NEEDS_MANUAL_SELECT, UNSUPPORTED, CLASSIC, ERROR }
}

object InstallState {
    private const val PREFS = "install_state"
    private val flow = MutableStateFlow(InstallStatus())
    private var loaded = false

    fun observe(context: Context): StateFlow<InstallStatus> {
        load(context)
        return flow.asStateFlow()
    }

    fun current(context: Context): InstallStatus {
        load(context)
        return flow.value
    }

    fun update(context: Context, transform: (InstallStatus) -> InstallStatus) {
        load(context)
        val next = transform(flow.value)
        flow.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", next.state.name)
            .putString("theme", next.themeName)
            .putString("message", next.message)
            .putLong("ts", next.timestamp)
            .putBoolean("active", next.isActive)
            .apply()
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        flow.value = InstallStatus(
            state = runCatching { InstallStatus.State.valueOf(p.getString("state", null)!!) }
                .getOrDefault(InstallStatus.State.WAITING),
            themeName = p.getString("theme", "").orEmpty(),
            message = p.getString("message", "").orEmpty(),
            timestamp = p.getLong("ts", 0L),
            isActive = p.getBoolean("active", false),
        )
        loaded = true
    }
}
