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
import app.synctheme.core.ChameleonMode
import app.synctheme.core.Chameleon
import app.synctheme.core.DayPart
import app.synctheme.core.Mood
import app.synctheme.core.Sky
import app.synctheme.core.WallClock
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.ChameleonSettings
import app.synctheme.mobile.data.ThemeApplier
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.DepthException
import app.synctheme.mobile.render.ExportKind
import app.synctheme.mobile.render.ExportKit
import app.synctheme.mobile.render.SubjectMask
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

    private val _chameleon = MutableStateFlow(ChameleonSettings.mode(app))
    val chameleon: StateFlow<ChameleonMode> = _chameleon.asStateFlow()
    val mood: StateFlow<Mood?> = repo.mood

    /** Как тема будет выглядеть в разное время суток и погоду — миниатюры для раздела «Хамелеон». */
    private val _moodPreviews = MutableStateFlow<List<Pair<String, ImageBitmap>>>(emptyList())
    val moodPreviews: StateFlow<List<Pair<String, ImageBitmap>>> = _moodPreviews.asStateFlow()
    private val moodsWanted = MutableStateFlow(false)

    /** Галерею открыли — начинаем рисовать миниатюры (и обновлять их при смене цветов). */
    fun wantStylePreviews() {
        stylesWanted.value = true
    }

    private suspend fun renderStylePreviews(t: Theme) = withContext(Dispatchers.Default) {
        _stylePreviews.value = ClockStyle.entries.associateWith { style ->
            val variant = t.watch?.let { t.copy(watch = it.copy(clockStyle = style)) } ?: t.copy(clockStyle = style)
            ThemeRenderer.watchPreview(app, variant, 200).asImageBitmap()
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
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(theme, moodsWanted, chameleon) { t, wanted, mode -> if (wanted) t to mode else null }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.first.background == b.first.background && a.first.dim == b.first.dim && a.second == b.second }
                .debounce(300)
                .collectLatest { (t, mode) -> renderMoodPreviews(t, mode) }
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
        val watch = ThemeRenderer.watchPreview(app, t, 450)
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
        val targets = _targets.value
        val report = ThemeApplier(app).apply(theme.value, targets) { _busy.value = it }
        val notes = mutableListOf<String>()
        if (targets.watch) {
            _connection.value = sync.connection()
            if (report.watchSent && !_connection.value.hasApp) {
                notes += "часы не на связи — циферблат установится, когда они подключатся"
            }
        }
        _busy.value = null
        val msg = buildString {
            if (report.done.isNotEmpty()) append("Готово: ${report.done.joinToString()}")
            if (notes.isNotEmpty()) append(" (${notes.joinToString("; ")})")
            if (report.errors.isNotEmpty()) {
                if (isNotEmpty()) append(". ")
                append("Ошибки — ${report.errors.joinToString("; ")}")
            }
        }
        if (msg.isNotEmpty()) _messages.tryEmit(msg)
    }

    // ---------------------------------------------------------------- эффект глубины

    /**
     * Включает эффект глубины: находит главный объект на фото (один раз для каждого фото),
     * затем меняет тему через [enable]. Ошибка — сообщением, тема не меняется.
     */
    private fun withSubject(photo: String?, enable: () -> Unit) = viewModelScope.launch {
        if (photo == null) {
            _messages.tryEmit("Эффект глубины работает с фото: выберите фото для фона")
            return@launch
        }
        _busy.value = "Ищу главный объект на фото…"
        try {
            SubjectMask.ensure(app, photo)
            enable()
        } catch (e: DepthException) {
            _messages.tryEmit(e.message.orEmpty())
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось выделить объект: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    private fun photoOf(bg: Background) = bg.photoFile?.takeIf { bg.type == BackgroundType.PHOTO }

    /** Глубина на циферблате: объект с фото фона часов поверх цифр. */
    fun setWatchDepth(enabled: Boolean, edit: ((WatchSettings) -> WatchSettings) -> Unit) {
        if (!enabled) return edit { it.copy(depth = false) }
        withSubject(photoOf(theme.value.watchBackground())) { edit { it.copy(depth = true) } }
    }

    /** Часы на обоях телефона; с глубиной объект с фото рабочего стола закрывает цифры. */
    fun setWallClock(clock: WallClock?) {
        val wantsDepth = clock?.depth == true && theme.value.wallClock?.depth != true
        if (!wantsDepth) return update { it.copy(wallClock = clock) }
        withSubject(photoOf(theme.value.background)) { update { it.copy(wallClock = clock) } }
    }

    // ---------------------------------------------------------------- Хамелеон


    fun setChameleon(mode: ChameleonMode) {
        _chameleon.value = mode
        ChameleonSettings.setMode(app, mode)
        if (mode.byWeather && WeatherRepository(app).place == null) {
            _messages.tryEmit("Для погоды выберите город: «Виджеты» → «Город для погоды»")
        }
    }


    fun wantMoodPreviews() {
        moodsWanted.value = true
    }

    private suspend fun renderMoodPreviews(t: Theme, mode: ChameleonMode) = withContext(Dispatchers.Default) {
        val moods = buildList {
            if (mode != ChameleonMode.WEATHER) DayPart.entries.forEach { add(Mood(part = it)) }
            if (mode.byWeather) listOf(Sky.CLEAR, Sky.CLOUDS, Sky.RAIN, Sky.SNOW).forEach { add(Mood(sky = it)) }
        }
        _moodPreviews.value = moods.map { m ->
            val v = Chameleon.adapt(t, m)
            m.title.replaceFirstChar { it.uppercase() } to ThemeRenderer.background(app, v, 120, 260).asImageBitmap()
        }
    }

    // ---------------------------------------------------------------- В тон вещи

    /** Снимок вещи и цвета с него — для диалога «В тон вещи». */
    class MatchPhoto(val bitmap: android.graphics.Bitmap, val image: ImageBitmap, val swatches: List<Int>)

    private val _match = MutableStateFlow<MatchPhoto?>(null)
    val match: StateFlow<MatchPhoto?> = _match.asStateFlow()

    fun loadMatch(uri: Uri) = viewModelScope.launch {
        _busy.value = "Смотрю цвета на снимке…"
        try {
            val bmp = withContext(Dispatchers.IO) {
                val source = android.graphics.ImageDecoder.createSource(app.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longSide = maxOf(info.size.width, info.size.height)
                    if (longSide > 1080) {
                        val k = 1080f / longSide
                        decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                    }
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            val swatches = withContext(Dispatchers.Default) { ThemeColors.swatches(bmp) }
            _match.value = MatchPhoto(bmp, bmp.asImageBitmap(), swatches)
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось открыть снимок: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    fun dismissMatch() {
        _match.value = null
    }

    /** Тема в цвет вещи (или только акцент). */
    fun applyMatch(color: Int, wholeTheme: Boolean) {
        val swatches = _match.value?.swatches.orEmpty()
        update { if (wholeTheme) ThemeColors.fromColor(it, color, swatches) else it.copy(accentColor = ThemeColors.accent(color)) }
        _match.value = null
        _messages.tryEmit(if (wholeTheme) "Тема в тон вещи готова — нажмите «Применить»" else "Акцент взят с вещи")
    }

    // ---------------------------------------------------------------- Картинки для Apple и других часов

    fun export(kinds: Set<ExportKind>, toGallery: Boolean, onShare: (android.content.Intent) -> Unit) =
        viewModelScope.launch {
            if (kinds.isEmpty()) return@launch
            _busy.value = "Рисую картинки…"
            try {
                val files = ExportKit.render(app, theme.value, kinds)
                if (toGallery) {
                    ExportKit.saveToGallery(app, files)
                    _messages.tryEmit("Сохранено в «Галерею»: Pictures/SyncTheme (${files.size} шт.)")
                } else {
                    onShare(ExportKit.shareIntent(app, files))
                }
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось сделать картинки: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = null
            }
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
