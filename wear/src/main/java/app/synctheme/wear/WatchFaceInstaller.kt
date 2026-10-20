package app.synctheme.wear

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import app.synctheme.wear.InstallStatus.State
import java.io.File

/**
 * Обёртка над Watch Face Push: у приложения на Wear OS 6 один «слот»,
 * поэтому каждая новая тема просто обновляет установленный циферблат.
 * На Wear OS 3–5 тема достаётся классическому циферблату ([ClassicFace]).
 */
class WatchFaceInstaller(private val context: Context) {
    private val prefs = context.getSharedPreferences("installer", Context.MODE_PRIVATE)

    val installedTimestamp: Long get() = prefs.getLong("installed_ts", 0L)

    private fun manager(): WatchFacePushManager? =
        if (WatchFacePushManagerFactory.isSupported()) {
            WatchFacePushManagerFactory.createWatchFacePushManager(context)
        } else null

    suspend fun install(apk: File, token: String, themeName: String, timestamp: Long, activate: Boolean): InstallStatus {
        val manager = manager() ?: return save(
            InstallStatus(State.UNSUPPORTED, themeName, "Watch Face Push недоступен на этих часах.", timestamp)
        )
        save(InstallStatus(State.INSTALLING, themeName, "", timestamp))
        return try {
            val existing = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull()
            // Активность проверяем ДО обновления (так советует документация Watch Face Push).
            val wasActive = existing != null && runCatching { manager.isWatchFaceActive(existing.packageName) }.getOrDefault(false)
            val details = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                if (existing != null) manager.updateWatchFace(existing.slotId, fd, token)
                else manager.addWatchFace(fd, token)
            }
            prefs.edit().putLong("installed_ts", timestamp).apply()
            Log.i(TAG, "Installed ${details.packageName} in slot ${details.slotId}")
            if (wasActive || !activate) {
                save(InstallStatus(State.INSTALLED, themeName, "", timestamp, isActive = wasActive))
            } else {
                save(activateSlot(manager, details.slotId, themeName, timestamp))
            }
        } catch (e: WatchFacePushManager.AddWatchFaceException) {
            error(themeName, timestamp, e)
        } catch (e: WatchFacePushManager.UpdateWatchFaceException) {
            error(themeName, timestamp, e)
        } catch (e: WatchFacePushManager.ListWatchFacesException) {
            error(themeName, timestamp, e)
        }
    }

    /** Делает наш циферблат активным (вызывается из UI после выдачи разрешения). */
    suspend fun activate(): InstallStatus {
        val current = InstallState.current(context)
        val manager = manager() ?: return current
        return try {
            val slot = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull()
                ?: return save(current.copy(state = State.WAITING, message = "Циферблат ещё не получен с телефона."))
            save(activateSlot(manager, slot.slotId, current.themeName, current.timestamp))
        } catch (e: WatchFacePushManager.ListWatchFacesException) {
            error(current.themeName, current.timestamp, e)
        }
    }

    /** Тема сохранена для классического циферблата (часы без Watch Face Push). */
    fun classicInstalled(themeName: String, timestamp: Long): InstallStatus {
        prefs.edit().putLong("installed_ts", timestamp).apply()
        val active = ClassicFace.isActive(context)
        return save(
            InstallStatus(
                State.CLASSIC, themeName,
                if (active) "" else "Зажмите текущий циферблат и выберите «SyncTheme».", timestamp, isActive = active,
            )
        )
    }

    /** Обновляет флаг «активен» по факту (пользователь мог сменить циферблат вручную). */
    suspend fun refreshActive(): InstallStatus {
        val current = InstallState.current(context)
        if (ClassicFace.needed) {
            val active = ClassicFace.isActive(context)
            return save(
                if (current.state == State.WAITING) current.copy(isActive = active)
                else current.copy(
                    state = State.CLASSIC, isActive = active,
                    message = if (active) "" else "Зажмите текущий циферблат и выберите «SyncTheme».",
                )
            )
        }
        val manager = manager() ?: return save(
            current.copy(state = State.UNSUPPORTED, message = "Watch Face Push недоступен на этих часах.")
        )
        return runCatching {
            val slot = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull() ?: return current
            val active = manager.isWatchFaceActive(slot.packageName)
            val resolved = active && (current.state == State.NEEDS_PERMISSION || current.state == State.NEEDS_MANUAL_SELECT)
            save(
                if (resolved) current.copy(isActive = true, state = State.INSTALLED, message = "")
                else current.copy(isActive = active)
            )
        }.getOrDefault(current)
    }

    private suspend fun activateSlot(
        manager: WatchFacePushManager, slotId: String, themeName: String, timestamp: Long,
    ): InstallStatus {
        if (ContextCompat.checkSelfPermission(context, PERMISSION_SET_ACTIVE) != PackageManager.PERMISSION_GRANTED) {
            return InstallStatus(
                State.NEEDS_PERMISSION, themeName,
                "Откройте SyncTheme на часах и разрешите менять циферблат.", timestamp,
            )
        }
        return try {
            manager.setWatchFaceAsActive(slotId)
            InstallStatus(State.INSTALLED, themeName, "", timestamp, isActive = true)
        } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
            when (e.errorCode) {
                WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MAXIMUM_ATTEMPTS_REACHED ->
                    InstallStatus(
                        State.NEEDS_MANUAL_SELECT, themeName,
                        "Wear OS разрешает приложению включить циферблат только один раз. " +
                            "Зажмите текущий циферблат и выберите «SyncTheme».", timestamp,
                    )
                WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MISSING_PERMISSION ->
                    InstallStatus(State.NEEDS_PERMISSION, themeName, "Нужно разрешение на смену циферблата.", timestamp)
                else -> InstallStatus(State.ERROR, themeName, e.message.orEmpty(), timestamp)
            }
        }
    }

    private fun error(themeName: String, timestamp: Long, e: Exception): InstallStatus {
        Log.e(TAG, "Watch Face Push failed", e)
        return save(InstallStatus(State.ERROR, themeName, e.message ?: e.toString(), timestamp))
    }

    private fun save(status: InstallStatus): InstallStatus {
        InstallState.update(context) { status }
        return status
    }

    companion object {
        private const val TAG = "WatchFaceInstaller"
        const val PERMISSION_SET_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
    }
}
