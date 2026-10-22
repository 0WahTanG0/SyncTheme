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
import app.synctheme.core.WallClock
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSet
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.devices.MyDevices
import app.synctheme.mobile.devices.Watch
import app.synctheme.mobile.tablet.TabletClient
import app.synctheme.mobile.tablet.TabletRetry
import app.synctheme.mobile.tablet.TabletReceiver
import app.synctheme.mobile.tablet.TabletStore
import app.synctheme.mobile.tablet.TabletSyncService
import app.synctheme.mobile.data.NotAnImageException
import app.synctheme.mobile.data.SharedImage
import app.synctheme.mobile.data.ThemeApplier
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.DepthException
import app.synctheme.mobile.render.DepthMasks
import app.synctheme.mobile.render.LockScreenImage
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
    /** Циферблат в режиме Always-On. */
    private val _ambientPreview = MutableStateFlow<ImageBitmap?>(null)
    val ambientPreview: StateFlow<ImageBitmap?> = _ambientPreview.asStateFlow()

    /** Виджеты SyncTheme, которые сейчас стоят на экранах. */
    private val _placed = MutableStateFlow<List<PlacedWidget>>(emptyList())
    val placed: StateFlow<List<PlacedWidget>> = _placed.asStateFlow()

    private val _connection = MutableStateFlow(WatchConnection())
    val connection: StateFlow<WatchConnection> = _connection.asStateFlow()

    private val _targets = MutableStateFlow(ApplyTargets.load(app))
    val targets: StateFlow<ApplyTargets> = _targets.asStateFlow()

    /** Подключённые устройства: телефон всегда, часы и планшет — по желанию. */
    private val _devices = MutableStateFlow(DeviceSet(DeviceSettings.watch(app), TabletStore.tablet(app) != null))
    val devices: StateFlow<DeviceSet> = _devices.asStateFlow()

    /** Приложение открыто на планшете: превью — его экран, а не телефона. */
    val selfTablet = DeviceSettings.isTablet(app)

    /** Планшет: превью его рабочего стола и как прошла последняя отправка. */
    private val _tabletPreview = MutableStateFlow<ImageBitmap?>(null)
    val tabletPreview: StateFlow<ImageBitmap?> = _tabletPreview.asStateFlow()
    val tabletPush = TabletStore.push(app)

    /** Это планшет и он принимает тему с телефона. */
    private val _receiving = MutableStateFlow(TabletStore.receiving(app))
    val receiving: StateFlow<Boolean> = _receiving.asStateFlow()
    val receiver = TabletReceiver.state

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
            ThemeRenderer.watchPreview(app, variant, 200).asImageBitmap()
        }
    }

    /** Растёт, когда появилась новая маска эффекта глубины, — превью нужно перерисовать. */
    private val masks = MutableStateFlow(0)

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(theme, masks) { t, _ -> t }.debounce(60).collectLatest { renderPreviews(it) }
        }
        // Сменили фото, а глубина уже включена, — сразу ищем объект на новом фото (каждое фото — один раз).
        viewModelScope.launch {
            val tried = HashSet<String>()
            theme.collectLatest { t ->
                val missing = DepthMasks.photosFor(t).filter { !SubjectMask.exists(app, it) && tried.add(it) }
                if (missing.isEmpty()) return@collectLatest
                for (photo in missing) {
                    runCatching { SubjectMask.ensure(app, photo) }
                        .onFailure { _messages.tryEmit("Эффект глубины: ${it.message ?: it.javaClass.simpleName}") }
                }
                masks.value++
            }
        }
        // Стиль на миниатюры не влияет — перерисовываем, только когда меняется остальное.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(theme, stylesWanted, masks) { t, wanted, m -> if (wanted) Triple(t.watchSettings().copy(clockStyle = ClockStyle.DIGITAL), m, t) else null }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.first == b.first && a.second == b.second }
                .debounce(300)
                .collectLatest { (_, _, t) -> renderStylePreviews(t) }
        }
        if (_devices.value.watch) refreshWatch()
        TabletSyncService.ensure(app)
        // Виджеты перерисовываются в фоне: на старте приложения это не должно тормозить экран.
        viewModelScope.launch(Dispatchers.Default) {
            WidgetUpdater.updateAll(app)
            WidgetScheduler.ensure(app)
            _placed.value = WidgetUpdater.placed(app)
        }
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
        // На планшете своё превью — планшет в альбомной ориентации.
        val phone = if (selfTablet) ThemeRenderer.tabletPreview(app, t, 800, 500) else ThemeRenderer.phonePreview(app, t, 540, 1170)
        val lock = if (selfTablet) ThemeRenderer.lockPreview(app, t, 800, 500) else ThemeRenderer.lockPreview(app, t, 540, 1170)
        val watch = ThemeRenderer.watchPreview(app, t, 450)
        _ambientPreview.value = ThemeRenderer.watchAmbientPreview(app, t, 240).asImageBitmap()
        _phonePreview.value = phone.asImageBitmap()
        _lockPreview.value = lock.asImageBitmap()
        _watchPreview.value = watch.asImageBitmap()
        if (_devices.value.tablet) _tabletPreview.value = ThemeRenderer.tabletPreview(app, t, 800, 500).asImageBitmap()
    }

    fun update(transform: (Theme) -> Theme) = repo.setDraft(transform(theme.value))

    fun select(t: Theme) = repo.setDraft(t)

    fun setTargets(t: ApplyTargets) {
        _targets.value = t
        ApplyTargets.save(app, t)
    }

    /** Фото из галереи. */
    fun pickPhoto(uri: Uri, target: BgTarget = BgTarget.HOME) = viewModelScope.launch {
        _busy.value = "Загружаю фото…"
        try {
            setPhoto(uri, target)
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось открыть фото: ${e.message}")
        } finally {
            _busy.value = null
        }
    }

    /** Открыть вкладку (например, «Обои» — когда прислали картинку из другого приложения). */
    private val _tabRequests = MutableSharedFlow<Tab>(extraBufferCapacity = 1)
    val tabRequests: SharedFlow<Tab> = _tabRequests

    /**
     * Картинка из другого приложения («Поделиться» → SyncTheme): сама картинка или ссылка на неё
     * (Pinterest, страница сайта). Становится фоном рабочего стола в черновике — дальше её можно
     * подвинуть, поменять цвета и нажать «Применить».
     */
    fun importShared(intent: android.content.Intent) = viewModelScope.launch {
        _tabRequests.tryEmit(Tab.WALLPAPER)
        val source = SharedImage.source(intent)
        _busy.value = "Загружаю картинку из $source…"
        var downloaded: java.io.File? = null
        try {
            val uri = SharedImage.stream(intent)
                ?: SharedImage.url(intent)?.let { link -> SharedImage.download(app, link).also { downloaded = it }.let(Uri::fromFile) }
                ?: throw NotAnImageException("В присланном нет картинки")
            setPhoto(uri, BgTarget.HOME)
            _messages.tryEmit("Картинка из $source — на обоях. Подвиньте фото и часы, если нужно, и нажмите «Применить»")
        } catch (e: NotAnImageException) {
            _messages.tryEmit(e.message.orEmpty())
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось получить картинку: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            downloaded?.delete()
            _busy.value = null
        }
    }

    /**
     * Фото становится фоном. Для рабочего стола под снимок подстраиваются цвета цифр, акцента,
     * виджетов и циферблата; для экрана блокировки и часов меняется только их фон.
     */
    private suspend fun setPhoto(uri: Uri, target: BgTarget) {
        run {
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
                // Новое фото — новый объект: часы снова встают сами, кадр фото — по центру.
                BgTarget.HOME -> update {
                    it.copy(
                        background = photo,
                        accentColor = accent,
                        textColor = if (light) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt(),
                        dim = if (light) 0f else maxOf(it.dim, 0.15f),
                        wallClock = it.wallClock?.copy(place = null),
                        watchTimeShift = null,
                        watchPhotoCrop = null,
                    )
                }
                BgTarget.LOCK -> update { it.copy(lockBackground = photo, lockDim = it.lockDim ?: it.dim) }
                BgTarget.WATCH -> update { it.copy(watch = it.watchSettings().copy(background = photo, timeShift = null), watchPhotoCrop = null) }
            }
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
            runCatching { weather.locate() }.getOrNull()?.let { weather.place = it }
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
        val targets = _targets.value.on(_devices.value)
        val report = try {
            ThemeApplier(app).apply(theme.value, targets) { _busy.value = it }
        } catch (e: Exception) {
            // Шаги сами ловят свои ошибки; сюда попадает только непредвиденное — экран не должен зависнуть.
            ThemeApplier.Report(emptyList(), listOf(e.message ?: e.javaClass.simpleName), false)
        } finally {
            _busy.value = null
        }
        val notes = mutableListOf<String>()
        if (targets.watch) {
            _connection.value = sync.connection()
            if (report.watchSent && !_connection.value.hasApp) {
                notes += "часы не на связи — циферблат установится, когда они подключатся"
            }
        }
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

    /**
     * Глубина на экране блокировки: часы там рисует One UI, его «Адаптивные часы» обтекают объект
     * фото. SyncTheme проверяет, что объект на фото есть, и ставит фото без затемнения.
     */
    fun setLockDepth(enabled: Boolean) {
        if (!enabled) return update { it.copy(lockDepth = false) }
        withSubject(photoOf(theme.value.lockScreenBackground())) { update { it.copy(lockDepth = true) } }
    }

    /**
     * Запасной способ для экрана блокировки: отдать картинку системному «Установить как» — тогда
     * её ставит приложение обоев Samsung, и адаптивные часы точно знают про объект на фото.
     */
    fun lockViaSystem(onIntent: (android.content.Intent) -> Unit) = viewModelScope.launch {
        _busy.value = "Готовлю обои блокировки…"
        try {
            val uri = withContext(Dispatchers.Default) { LockScreenImage.export(app, theme.value) }
            onIntent(LockScreenImage.setAsIntent(uri))
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось подготовить обои: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    fun refreshWatch() = viewModelScope.launch { _connection.value = sync.connection() }

    // ---------------------------------------------------------------- часы и планшет

    /** Часы подключены — появляются вкладка «Часы», статус и циферблат в «Применить». */
    fun setWatchEnabled(on: Boolean) {
        DeviceSettings.setWatch(app, on)
        _devices.value = _devices.value.copy(watch = on)
        if (on) refreshWatch()
    }

    /** «Добавить устройство» → часы: модель — по имени часов из Bluetooth, инструкция — под неё. */
    fun connectWatch(name: String?) {
        name?.let(Watch::of)?.let { MyDevices.setWatch(app, it) }
        setWatchEnabled(true)
        _messages.tryEmit(if (_connection.value.hasApp) "Часы подключены — циферблат обновится по «Применить»" else "Часы добавлены — осталось установить на них SyncTheme")
    }

    /** Отправить на планшет тему, которая сейчас применена на телефоне. */
    fun sendToTablet() = viewModelScope.launch {
        _busy.value = "Отправляю на планшет…"
        try {
            _messages.tryEmit(TabletClient.push(app, repo.applied.value))
        } catch (e: Exception) {
            _messages.tryEmit(e.message ?: e.javaClass.simpleName)
        } finally {
            _busy.value = null
        }
    }

    /** Телефон: связать планшет по коду с его экрана. */
    fun pairTablet(host: String, port: Int, code: String, service: String?, onDone: () -> Unit = {}) = viewModelScope.launch {
        _busy.value = "Связываюсь с планшетом…"
        try {
            val peer = TabletClient.pair(app, host, port, code, service)
            _devices.value = _devices.value.copy(tablet = true)
            renderPreviews(theme.value)
            onDone()
            // Сразу ставим на планшет то, что уже стоит на телефоне, — не дожидаясь нового «Применить».
            _busy.value = "Отправляю тему на «${peer.name}»…"
            val sent = runCatching { TabletClient.push(app, repo.applied.value) }
            _messages.tryEmit(
                if (sent.isSuccess) "«${peer.name}» подключён, тема уже на нём. Дальше — по «Применить»"
                else "«${peer.name}» подключён. ${sent.exceptionOrNull()?.message.orEmpty()}",
            )
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось связать: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    fun unpairTablet() {
        TabletStore.setTablet(app, null)
        TabletRetry.cancel(app)
        _devices.value = _devices.value.copy(tablet = false)
        _tabletPreview.value = null
    }

    /** Планшет: принимать тему с телефона (в фоне, с тихим уведомлением). */
    fun setReceiving(on: Boolean) {
        TabletStore.setReceiving(app, on)
        _receiving.value = on
        TabletSyncService.ensure(app)
    }

    private val _timeCheck = MutableStateFlow<TimeCheckResult?>(null)
    val timeCheck: StateFlow<TimeCheckResult?> = _timeCheck.asStateFlow()
    private val _timeChecking = MutableStateFlow(false)
    val timeChecking: StateFlow<Boolean> = _timeChecking.asStateFlow()

    /** Сверяет время часов с телефоном и телефона — с интернетом. */
    fun checkTime() {
        if (_timeChecking.value) return
        viewModelScope.launch {
            _timeChecking.value = true
            try {
                if (!_connection.value.checked) _connection.value = sync.connection()
                _timeCheck.value = TimeCheck.run(app, _connection.value.appNodeIds.firstOrNull())
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось сверить время: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _timeChecking.value = false
            }
        }
    }

    fun openOnWatch() = viewModelScope.launch {
        runCatching { sync.openOnWatch(_connection.value.appNodeIds.firstOrNull()) }
            .onSuccess { _messages.tryEmit("SyncTheme открыт на часах") }
            .onFailure { _messages.tryEmit("Не удалось открыть на часах: ${it.message}") }
    }

    /**
     * Добавляет виджет на рабочий стол; после добавления откроется его настройка. Виджеты рисуются
     * в цветах применённой темы — черновик при этом не применяется (обои и часы не меняются).
     */
    fun pinWidget(kind: WidgetKind) {
        if (!WidgetUpdater.requestPin(app, kind)) {
            _messages.tryEmit("Добавьте вручную: долгое нажатие на рабочий стол → Виджеты → SyncTheme")
        } else if (theme.value != applied.value) {
            _messages.tryEmit("Виджет возьмёт цвета применённой темы — нажмите «Применить», чтобы обновить их")
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
