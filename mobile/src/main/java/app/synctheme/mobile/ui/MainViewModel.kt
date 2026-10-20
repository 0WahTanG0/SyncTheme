package app.synctheme.mobile.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
import app.synctheme.core.ClockStyle
import app.synctheme.core.Theme
import app.synctheme.core.WatchSettings
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.data.WeatherRepository
import app.synctheme.mobile.install.WatchAppInstaller
import app.synctheme.mobile.update.UpdateInfo
import app.synctheme.mobile.update.Updater
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.render.WallpaperApplier
import app.synctheme.mobile.watch.TimeCheck
import app.synctheme.mobile.watch.TimeCheckResult
import app.synctheme.mobile.watch.WatchConnection
import app.synctheme.mobile.watch.WatchFaceBuilder
import app.synctheme.mobile.watch.WatchStatusStore
import app.synctheme.mobile.watch.WatchSync
import app.synctheme.mobile.widget.PlacedWidget
import app.synctheme.mobile.widget.WidgetKind
import app.synctheme.mobile.widget.WidgetScheduler
import app.synctheme.mobile.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = ThemeRepository.get(app)
    private val sync = WatchSync(app)

    val theme: StateFlow<Theme> = repo.draft
    /** Тема, которая сейчас стоит на устройствах: если отличается от черновика — есть что применить. */
    val applied: StateFlow<Theme> = repo.applied
    val saved: StateFlow<List<Theme>> = repo.saved
    val watchReport = WatchStatusStore.observe(app)

    private val _phonePreview = MutableStateFlow<ImageBitmap?>(null)
    val phonePreview: StateFlow<ImageBitmap?> = _phonePreview.asStateFlow()
    private val _watchPreview = MutableStateFlow<ImageBitmap?>(null)
    val watchPreview: StateFlow<ImageBitmap?> = _watchPreview.asStateFlow()
    private val _lockPreview = MutableStateFlow<ImageBitmap?>(null)
    val lockPreview: StateFlow<ImageBitmap?> = _lockPreview.asStateFlow()

    /** Виджеты SyncTheme, которые сейчас стоят на экранах. */
    private val _placed = MutableStateFlow<List<PlacedWidget>>(emptyList())
    val placed: StateFlow<List<PlacedWidget>> = _placed.asStateFlow()

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

    private val updater = Updater(app)
    private val _update = MutableStateFlow(UpdateUi(updater.currentVersionName, updater.currentVersionCode, updater.repo))
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Миниатюры всех стилей циферблата в цветах текущей темы — для галереи на вкладке «Часы». */
    private val _stylePreviews = MutableStateFlow<Map<ClockStyle, ImageBitmap>>(emptyMap())
    val stylePreviews: StateFlow<Map<ClockStyle, ImageBitmap>> = _stylePreviews.asStateFlow()
    private val stylesWanted = MutableStateFlow(false)

    /** Галерею открыли — начинаем рисовать миниатюры (и обновлять их при смене цветов). */
    fun wantStylePreviews() {
        stylesWanted.value = true
    }

    private suspend fun renderStylePreviews(t: Theme) = withContext(Dispatchers.Default) {
        _stylePreviews.value = ClockStyle.entries.associateWith { style ->
            val variant = t.watch?.let { t.copy(watch = it.copy(clockStyle = style)) } ?: t.copy(clockStyle = style)
            val bg = ThemeRenderer.watchBackground(app, variant)
            ThemeRenderer.watchPreview(app, variant.watchSettings(), bg, 200).asImageBitmap()
        }
    }

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            theme.debounce(60).collectLatest { renderPreviews(it) }
        }
        // Стиль на миниатюры не влияет — перерисовываем, только когда меняется остальное.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(theme, stylesWanted) { t, wanted -> if (wanted) t.watchSettings().copy(clockStyle = ClockStyle.DIGITAL) to t else null }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.first == b.first }
                .debounce(300)
                .collectLatest { (_, t) -> renderStylePreviews(t) }
        }
        refreshWatch()
        WidgetUpdater.updateAll(app)
        WidgetScheduler.ensure(app)
        refreshPlaced()
        refreshAutoWeather()
        viewModelScope.launch(Dispatchers.IO) {
            _bundledWatchVersion.value = runCatching { WatchAppInstaller(app).bundledVersion }.getOrDefault(0L)
        }
        // Тихая проверка обновлений не чаще раза в 6 часов.
        if (updater.repo.isNotBlank() && System.currentTimeMillis() - updater.lastCheck > 6 * 3600_000L) {
            checkUpdates(silent = true)
        }
    }

    fun setUpdateRepo(repo: String) {
        updater.repo = repo
        _update.value = _update.value.copy(repo = updater.repo, status = null)
        if (updater.repo.isNotBlank()) checkUpdates()
    }

    fun checkUpdates(silent: Boolean = false) = viewModelScope.launch {
        _update.value = _update.value.copy(checking = true, status = null)
        val result = runCatching { updater.check() }
        _update.value = _update.value.copy(
            checking = false,
            available = result.getOrNull(),
            status = when {
                result.isFailure -> "Не удалось проверить: ${result.exceptionOrNull()?.message}"
                result.getOrNull() == null -> "У вас последняя версия"
                else -> null
            },
        )
        result.getOrNull()?.let { if (silent) _messages.tryEmit("Доступна версия ${it.versionName} — раздел «Обновления»") }
    }

    fun downloadAndInstall() = viewModelScope.launch {
        val info = _update.value.available ?: return@launch
        installApk { updater.download(info) { p -> _update.value = _update.value.copy(progress = p) } }
    }

    fun installFromFile(uri: Uri) = viewModelScope.launch { installApk { updater.copyFromUri(uri) } }

    private suspend fun installApk(obtain: suspend () -> java.io.File) {
        if (!updater.canInstall()) {
            _update.value = _update.value.copy(needsPermission = true, status = "Разрешите SyncTheme устанавливать обновления и повторите")
            return
        }
        _update.value = _update.value.copy(progress = 0f, status = "Загружаю обновление…", needsPermission = false)
        try {
            val apk = obtain()
            updater.verify(apk)
            _update.value = _update.value.copy(status = "Устанавливаю… Приложение перезапустится")
            updater.install(apk)
        } catch (e: Exception) {
            _update.value = _update.value.copy(status = "Ошибка обновления: ${e.message}")
        } finally {
            _update.value = _update.value.copy(progress = null)
        }
    }

    fun installPermissionIntent() = updater.installPermissionIntent()

    private suspend fun renderPreviews(t: Theme) = withContext(Dispatchers.Default) {
        val phone = ThemeRenderer.phonePreview(app, t, 540, 1170)
        val lock = ThemeRenderer.lockPreview(app, t, 540, 1170)
        val bg = ThemeRenderer.watchBackground(app, t)
        val watch = ThemeRenderer.watchPreview(app, t.watchSettings(), bg, 450)
        bg.recycle()
        _phonePreview.value = phone.asImageBitmap()
        _lockPreview.value = lock.asImageBitmap()
        _watchPreview.value = watch.asImageBitmap()
    }

    fun update(transform: (Theme) -> Theme) = repo.setDraft(transform(theme.value))

    fun select(t: Theme) = repo.setDraft(t)

    fun setTargets(t: ApplyTargets) {
        _targets.value = t
        ApplyTargets.save(app, t)
    }

    /**
     * Фото из галереи. Для рабочего стола под снимок подстраиваются цвета цифр, акцента,
     * виджетов и циферблата; для экрана блокировки и часов меняется только их фон.
     */
    fun pickPhoto(uri: Uri, target: BgTarget = BgTarget.HOME) = viewModelScope.launch {
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
            val photo = Background(BackgroundType.PHOTO, colors, photoFile = name)
            when (target) {
                BgTarget.HOME -> update {
                    it.copy(
                        background = photo,
                        accentColor = accent,
                        textColor = if (light) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt(),
                        dim = if (light) 0f else maxOf(it.dim, 0.15f),
                    )
                }
                BgTarget.LOCK -> update { it.copy(lockBackground = photo, lockDim = it.lockDim ?: it.dim) }
                BgTarget.WATCH -> updateWatch { it.copy(background = photo) }
            }
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось открыть фото: ${e.message}")
        } finally {
            _busy.value = null
        }
    }

    /** Отдельные обои экрана блокировки (по умолчанию — как на рабочем столе). */
    fun setLockSeparate(enabled: Boolean) = update {
        if (enabled) it.copy(lockBackground = it.background, lockDim = it.dim) else it.copy(lockBackground = null, lockDim = null)
    }

    /** Отдельные настройки циферблата (по умолчанию часы повторяют телефон). */
    fun setWatchSeparate(enabled: Boolean) = update { it.copy(watch = if (enabled) it.followPhone() else null) }

    fun updateWatch(transform: (WatchSettings) -> WatchSettings) = update { it.copy(watch = transform(it.watchSettings())) }

    fun refreshPlaced() {
        _placed.value = WidgetUpdater.placed(app)
    }

    /** Погода «по местоположению»: координаты обновляем, пока приложение открыто. */
    private fun refreshAutoWeather() = viewModelScope.launch {
        val weather = WeatherRepository(app)
        val place = weather.place ?: return@launch
        if (place.auto && app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            weather.locate()?.let { weather.place = it }
        }
        runCatching { weather.refresh() }.onSuccess { WidgetUpdater.update(app, WidgetKind.WEATHER) }
    }

    /** Прозрачность подложки: по умолчанию для новых виджетов и сразу для всех размещённых. */
    fun setOpacityForAll(opacity: Float) {
        update { it.copy(widgetOpacity = opacity) }
        WidgetUpdater.setOpacityForAll(app, opacity)
        refreshPlaced()
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

    private val _timeCheck = MutableStateFlow<TimeCheckResult?>(null)
    val timeCheck: StateFlow<TimeCheckResult?> = _timeCheck.asStateFlow()
    private val _timeChecking = MutableStateFlow(false)
    val timeChecking: StateFlow<Boolean> = _timeChecking.asStateFlow()

    /** Сверяет время часов с телефоном и телефона — с интернетом. */
    fun checkTime() {
        if (_timeChecking.value) return
        viewModelScope.launch {
            _timeChecking.value = true
            if (!_connection.value.checked) _connection.value = sync.connection()
            _timeCheck.value = TimeCheck.run(app, _connection.value.appNodeIds.firstOrNull())
            _timeChecking.value = false
        }
    }

    fun openOnWatch() = viewModelScope.launch {
        runCatching { sync.openOnWatch(_connection.value.appNodeIds.firstOrNull()) }
            .onSuccess { _messages.tryEmit("SyncTheme открыт на часах") }
            .onFailure { _messages.tryEmit("Не удалось открыть на часах: ${it.message}") }
    }

    /** Добавляет виджет на рабочий стол сразу в цветах текущей темы. */
    /** Добавляет виджет на рабочий стол; после добавления откроется его настройка. */
    fun pinWidget(kind: WidgetKind) {
        repo.setApplied(theme.value)
        WidgetUpdater.updateAll(app)
        if (!WidgetUpdater.requestPin(app, kind)) {
            _messages.tryEmit("Добавьте вручную: долгое нажатие на рабочий стол → Виджеты → SyncTheme")
        }
    }
}

/** Состояние раздела «Обновления». */
data class UpdateUi(
    val currentName: String,
    val currentCode: Long,
    val repo: String,
    val available: UpdateInfo? = null,
    val checking: Boolean = false,
    val progress: Float? = null,
    val status: String? = null,
    val needsPermission: Boolean = false,
)
