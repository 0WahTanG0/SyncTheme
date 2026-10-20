package app.synctheme.mobile.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.install.WatchAppInstaller
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.render.WallpaperApplier
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.watch.WatchStatusStore
import app.synctheme.mobile.watch.WatchSync
import app.synctheme.mobile.widget.ThemedWidget
import app.synctheme.mobile.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = ThemeRepository.get(app)
    private val sync = WatchSync(app)

    val theme: StateFlow<Theme> = repo.draft
    val saved: StateFlow<List<Theme>> = repo.saved
    val watchReport = WatchStatusStore.observe(app)

    private val _phonePreview = MutableStateFlow<ImageBitmap?>(null)
    val phonePreview: StateFlow<ImageBitmap?> = _phonePreview.asStateFlow()
    private val _watchPreview = MutableStateFlow<ImageBitmap?>(null)
    val watchPreview: StateFlow<ImageBitmap?> = _watchPreview.asStateFlow()

    private val _connection = MutableStateFlow(WatchConnection())
    val connection: StateFlow<WatchConnection> = _connection.asStateFlow()

    private val _targets = MutableStateFlow(ApplyTargets.load(app))
    val targets: StateFlow<ApplyTargets> = _targets.asStateFlow()

    /** Текст текущей операции или null, если ничего не выполняется. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    /** Версия приложения для часов, вшитого в приложение телефона. */
    private val _bundledWatchVersion = MutableStateFlow(0L)
    val bundledWatchVersion: StateFlow<Long> = _bundledWatchVersion.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            theme.debounce(60).collectLatest { renderPreviews(it) }
        }
        refreshWatch()
        WidgetUpdater.updateAll(app)
        viewModelScope.launch(Dispatchers.IO) {
            _bundledWatchVersion.value = runCatching { WatchAppInstaller(app).bundledVersion }.getOrDefault(0L)
        }
    }

    private suspend fun renderPreviews(t: Theme) = withContext(Dispatchers.Default) {
        val phone = ThemeRenderer.phonePreview(app, t, 540, 1170)
        val bg = ThemeRenderer.watchBackground(app, t)
        val watch = ThemeRenderer.watchPreview(t, bg, 450)
        bg.recycle()
        _phonePreview.value = phone.asImageBitmap()
        _watchPreview.value = watch.asImageBitmap()
    }

    fun update(transform: (Theme) -> Theme) = repo.setDraft(transform(theme.value))

    fun select(t: Theme) = repo.setDraft(t)

    fun setTargets(t: ApplyTargets) {
        _targets.value = t
        ApplyTargets.save(app, t)
    }

    /** Фото из галереи: делаем фоном и подбираем цвета по палитре снимка. */
    fun pickPhoto(uri: Uri) = viewModelScope.launch {
        _busy.value = "Загружаю фото…"
        try {
            val name = repo.importPhoto(uri)
            val palette = withContext(Dispatchers.Default) {
                val small = BitmapFactory.decodeFile(
                    repo.photoFile(name).path, BitmapFactory.Options().apply { inSampleSize = 8 },
                )
                Palette.from(small).generate().also { small.recycle() }
            }
            val dominant = palette.dominantSwatch?.rgb ?: 0xFF000000.toInt()
            val accent = palette.vibrantSwatch?.rgb ?: palette.lightVibrantSwatch?.rgb ?: dominant
            val colors = listOfNotNull(
                palette.darkMutedSwatch?.rgb ?: dominant, palette.vibrantSwatch?.rgb, palette.lightMutedSwatch?.rgb,
            ).distinct()
            val light = ColorUtils.calculateLuminance(dominant) > 0.6
            update {
                it.copy(
                    background = Background(BackgroundType.PHOTO, colors, photoFile = name),
                    accentColor = accent,
                    textColor = if (light) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt(),
                    dim = if (light) 0f else maxOf(it.dim, 0.15f),
                )
            }
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось открыть фото: ${e.message}")
        } finally {
            _busy.value = null
        }
    }

    fun saveCurrent() {
        val stored = repo.save(theme.value)
        repo.setDraft(stored)
        _messages.tryEmit("Тема «${stored.name}» сохранена")
    }

    fun delete(t: Theme) = repo.delete(t)

    /** Применяет тему ко всем выбранным местам; ошибка в одном шаге не мешает остальным. */
    fun apply() = viewModelScope.launch {
        val t = theme.value
        val targets = _targets.value
        val done = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()
        suspend fun step(label: String, busyText: String, block: suspend () -> Unit) {
            _busy.value = busyText
            try {
                block()
                done += label
            } catch (e: Exception) {
                errors += "$label: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        if (targets.homeWallpaper || targets.lockWallpaper) {
            step("обои", "Ставлю обои…") {
                WallpaperApplier.apply(app, t, targets.homeWallpaper, targets.lockWallpaper)
            }
        }
        if (targets.widgets) {
            step("виджеты", "Обновляю виджеты…") {
                repo.setApplied(t)
                WidgetUpdater.updateAll(app)
            }
        }
        if (targets.watch) {
            step("часы", "Собираю циферблат для часов…") {
                val built = WatchFaceBuilder(app).build(t)
                _busy.value = "Отправляю на часы…"
                sync.push(t, built)
                WatchStatusStore.markPending(t.name)
            }
            _connection.value = sync.connection()
            if ("часы" in done && !_connection.value.hasApp) {
                notes += "часы не на связи — циферблат установится, когда они подключатся"
            }
        }
        _busy.value = null
        val msg = buildString {
            if (done.isNotEmpty()) append("Готово: ${done.joinToString()}")
            if (notes.isNotEmpty()) append(" (${notes.joinToString("; ")})")
            if (errors.isNotEmpty()) {
                if (isNotEmpty()) append(". ")
                append("Ошибки — ${errors.joinToString("; ")}")
            }
        }
        if (msg.isNotEmpty()) _messages.tryEmit(msg)
    }

    fun refreshWatch() = viewModelScope.launch { _connection.value = sync.connection() }

    fun openOnWatch() = viewModelScope.launch {
        runCatching { sync.openOnWatch(_connection.value.appNodeIds.firstOrNull()) }
            .onSuccess { _messages.tryEmit("SyncTheme открыт на часах") }
            .onFailure { _messages.tryEmit("Не удалось открыть на часах: ${it.message}") }
    }

    /** Добавляет виджет на рабочий стол сразу в цветах текущей темы. */
    fun pinWidget(cls: Class<out ThemedWidget>) {
        repo.setApplied(theme.value)
        WidgetUpdater.updateAll(app)
        if (!WidgetUpdater.requestPin(app, cls)) {
            _messages.tryEmit("Добавьте вручную: долгое нажатие на рабочий стол → Виджеты → SyncTheme")
        }
    }
}
