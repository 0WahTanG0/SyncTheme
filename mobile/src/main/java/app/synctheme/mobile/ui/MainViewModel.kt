package app.synctheme.mobile.ui

import android.Manifest
import android.graphics.Bitmap
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
import app.synctheme.core.ThemeMode
import app.synctheme.core.resolved
import app.synctheme.core.WatchSettings
import app.synctheme.core.WallClock
import app.synctheme.mobile.data.ApplyTargets
import app.synctheme.mobile.data.DeviceSet
import app.synctheme.mobile.data.DeviceSettings
import app.synctheme.mobile.devices.MyDevices
import app.synctheme.mobile.devices.Watch
import app.synctheme.mobile.link.LinkClient
import app.synctheme.mobile.link.LinkRetry
import app.synctheme.mobile.link.LinkReceiver
import app.synctheme.mobile.link.LinkStore
import app.synctheme.mobile.link.LinkService
import app.synctheme.mobile.data.NotAnImageException
import app.synctheme.mobile.data.SharedImage
import app.synctheme.mobile.data.ThemeApplier
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.render.DepthException
import app.synctheme.mobile.render.DepthMasks
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
import app.synctheme.mobile.link.DeviceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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

/** Идёт подключение устройства: [code] — код сверки (одинаковый на телефоне и устройстве), когда он уже есть. */
data class Linking(val device: String, val code: String? = null)

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

    /** Маленькие телефоны для выбора картинками на шаге 2: каждая версия яркости и каждый вариант часов. */
    private val _choicePreviews = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val choicePreviews: StateFlow<Map<String, ImageBitmap>> = _choicePreviews.asStateFlow()
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

    /** Подключённые устройства: телефон всегда, часы и устройства по Wi‑Fi (планшет, ТВ, телефон) — по желанию. */
    private val _watchOn = MutableStateFlow(DeviceSettings.watch(app))
    private val _pro = MutableStateFlow(DeviceSettings.pro(app))

    /** «Проф. версия» включена: другие устройства и установка на них. */
    val pro: StateFlow<Boolean> = _pro.asStateFlow()

    /** Подключённые устройства; без «Проф. версии» — только телефон: на экране и по «Применить» их нет. */
    val devices: StateFlow<DeviceSet> = combine(_watchOn, LinkStore.observePeers(app), _pro) { w, p, pro -> devicesOf(w, p, pro) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, devicesOf(_watchOn.value, LinkStore.peers(app), _pro.value))

    private fun devicesOf(watch: Boolean, peers: List<app.synctheme.mobile.link.Peer>, pro: Boolean) =
        if (pro) DeviceSet(watch, peers) else DeviceSet()

    fun setPro(on: Boolean) {
        DeviceSettings.setPro(app, on)
        _pro.value = on
        // Без «Проф. версии» этот телефон и тему с другого не принимает.
        if (!on && _receiving.value) setReceiving(false)
        if (on) refreshWatch()
    }

    /** Приложение открыто на планшете: превью — его экран, а не телефона. */
    val selfTablet = DeviceSettings.isTablet(app)

    /** Превью экранов подключённых планшета и телевизора; как прошла последняя отправка на каждое устройство. */
    private val _tabletPreview = MutableStateFlow<ImageBitmap?>(null)
    val tabletPreview: StateFlow<ImageBitmap?> = _tabletPreview.asStateFlow()
    private val _tvPreview = MutableStateFlow<ImageBitmap?>(null)
    val tvPreview: StateFlow<ImageBitmap?> = _tvPreview.asStateFlow()
    val linkPush = LinkStore.push(app)

    /** Подключение устройства: к какому и код сверки, пока на нём не нажали «Подключить». */
    private val _linking = MutableStateFlow<Linking?>(null)
    val linking: StateFlow<Linking?> = _linking.asStateFlow()

    /** Это устройство принимает тему с телефона. */
    private val _receiving = MutableStateFlow(LinkStore.receiving(app))
    val receiving: StateFlow<Boolean> = _receiving.asStateFlow()
    val receiver = LinkReceiver.state

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

    private suspend fun renderStylePreviews(stored: Theme) = withContext(Dispatchers.Default) {
        val t = DayNight.forWatch(stored)
        _stylePreviews.value = ClockStyle.entries.associateWith { style ->
            val variant = t.watch?.let { t.copy(watch = it.copy(clockStyle = style)) } ?: t.copy(clockStyle = style)
            ThemeRenderer.watchPreview(app, variant, 200).asImageBitmap()
        }
    }

    /** Растёт, когда появилась новая маска эффекта глубины, — превью нужно перерисовать. */
    private val masks = MutableStateFlow(0)

    /** На телефоне тёмное оформление — от него зависит, какая версия темы видна («Как в системе»). */
    private val night = MutableStateFlow(DayNight.isNight(app))

    /** Вернулись в приложение: оформление телефона могло смениться — обновить превью, обои и виджеты. */
    fun onResumed() {
        night.value = DayNight.isNight(app)
        viewModelScope.launch(Dispatchers.Default) { DayNight.refresh(app) }
    }

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            // Подключили или отключили планшет либо телевизор — нужны (или больше не нужны) их превью.
            val kinds = devices.map { d -> d.peers.map { it.kind }.toSet() }.distinctUntilChanged()
            combine(theme, masks, kinds, SubjectMask.version, night) { t, _, _, _, _ -> t }.debounce(60).collectLatest { renderPreviews(it) }
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
        // Пока тему настраивают, циферблат собирается заранее: «Применить» сразу отправит его на часы.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(theme, devices, _targets, masks) { t, d, tg, _ -> t.takeIf { d.watch && tg.watch } }
                .filterNotNull()
                .debounce(1_200)
                .collectLatest { WatchFaceBuilder(app).prebuild(DayNight.forWatch(it)) }
        }
        if (devices.value.watch) refreshWatch()
        LinkService.ensure(app)
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
        // Тихая проверка обновлений при каждом открытии (не чаще раза в час): о новой версии скажет плашка сверху.
        if (updater.repo.isNotBlank() && System.currentTimeMillis() - updater.lastCheck > 3600_000L) {
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
            // Тихая проверка ничего не пишет: о новой версии и так скажет плашка.
            status = if (silent) null else when {
                result.isFailure -> "Не удалось проверить: ${result.exceptionOrNull()?.message}"
                result.getOrNull() == null -> "У вас последняя версия"
                else -> null
            },
        )
        // Уже видели в приложении — уведомление об этой версии больше не нужно.
        result.getOrNull()?.let { updater.notifiedCode = maxOf(updater.notifiedCode, it.versionCode) }
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

    /** Превью — в той версии темы (светлой или тёмной), которая будет видна сейчас; циферблат — всегда тёмный. */
    private suspend fun renderPreviews(stored: Theme) = withContext(Dispatchers.Default) {
        val t = DayNight.of(app, stored)
        val face = DayNight.forWatch(stored)
        // На планшете своё превью — планшет в альбомной ориентации.
        val phone = if (selfTablet) ThemeRenderer.tabletPreview(app, t, 800, 500) else ThemeRenderer.phonePreview(app, t, 540, 1170)
        val lock = if (selfTablet) ThemeRenderer.lockPreview(app, t, 800, 500) else ThemeRenderer.lockPreview(app, t, 540, 1170)
        val watch = ThemeRenderer.watchPreview(app, face, 450)
        _ambientPreview.value = ThemeRenderer.watchAmbientPreview(app, face, 240).asImageBitmap()
        _phonePreview.value = phone.asImageBitmap()
        if (!selfTablet) _choicePreviews.value = runCatching { choiceShots(stored) }.getOrDefault(emptyMap())
        _lockPreview.value = lock.asImageBitmap()
        _watchPreview.value = watch.asImageBitmap()
        val kinds = devices.value.peers.map { it.kind }
        _tabletPreview.value = if (DeviceKind.TABLET in kinds) ThemeRenderer.tabletPreview(app, t, 800, 500).asImageBitmap() else null
        _tvPreview.value = if (DeviceKind.TV in kinds) ThemeRenderer.tvFrame(app, t, 960, 540).asImageBitmap() else null
    }

    /** Без виджетов — только фон и часы: так варианты рисуются быстро и отличаются тем, что выбирают. */
    private fun choiceShots(stored: Theme): Map<String, ImageBitmap> {
        val w = 160
        val h = 347
        val night = DayNight.isNight(app)
        fun shot(t: Theme): Bitmap = ThemeRenderer.phonePreview(app, t, w, h, widgets = false)
        val shown = DayNight.of(app, stored)
        val light = shot(stored.copy(mode = ThemeMode.LIGHT).resolved(night))
        val dark = shot(stored.copy(mode = ThemeMode.DARK).resolved(night))
        // «Днём светло, ночью темно» — половина светлой версии и половина тёмной.
        val split = light.copy(Bitmap.Config.ARGB_8888, true)
        android.graphics.Canvas(split).drawBitmap(dark, android.graphics.Rect(w / 2, 0, w, h), android.graphics.Rect(w / 2, 0, w, h), null)
        val wall = stored.wallClock ?: WallClock()
        return mapOf(
            ThemeMode.ORIGINAL.name to shot(stored.copy(mode = ThemeMode.ORIGINAL).resolved(night)),
            ThemeMode.LIGHT.name to light,
            ThemeMode.DARK.name to dark,
            ThemeMode.AUTO.name to split,
            "none" to shot(shown.copy(wallClock = null)),
            "on" to shot(shown.copy(wallClock = wall.copy(depth = false))),
            "depth" to shot(shown.copy(wallClock = wall.copy(depth = true))),
        ).mapValues { it.value.asImageBitmap() }
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

    /** Цвета темы по снимку: градиент (подложки виджетов), цифры, акцент; [light] — фото светлое. */
    private class PhotoColors(val gradient: List<Int>, val text: Int, val accent: Int, val light: Boolean)

    private suspend fun photoColors(name: String): PhotoColors = withContext(Dispatchers.Default) {
        val small = BitmapFactory.decodeFile(repo.photoFile(name).path, BitmapFactory.Options().apply { inSampleSize = 8 })
        val palette = Palette.from(small).generate()
        // Палитра пропускает почти белые, почти чёрные и бежевые тона — тогда берём средний цвет снимка.
        val dominant = palette.dominantSwatch?.rgb ?: average(small)
        small.recycle()
        val light = ColorUtils.calculateLuminance(dominant) > 0.6
        // Акцент — сочный цвет снимка, который на нём читается: на светлом фото не слишком светлый, на тёмном — не тёмный.
        val accent = listOfNotNull(palette.vibrantSwatch, palette.lightVibrantSwatch, palette.darkVibrantSwatch, palette.mutedSwatch)
            .map { it.rgb }
            .firstOrNull { c -> ColorUtils.calculateLuminance(c).let { if (light) it < 0.5 else it > 0.12 } }
            ?: palette.vibrantSwatch?.rgb ?: palette.lightVibrantSwatch?.rgb ?: dominant
        val gradient = listOfNotNull(palette.darkMutedSwatch?.rgb ?: dominant, palette.vibrantSwatch?.rgb, palette.lightMutedSwatch?.rgb).distinct()
        PhotoColors(gradient, if (light) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt(), accent, light)
    }

    private fun average(bmp: android.graphics.Bitmap): Int {
        val one = android.graphics.Bitmap.createScaledBitmap(bmp, 1, 1, true)
        return one.getPixel(0, 0).also { one.recycle() } or 0xFF000000.toInt()
    }

    /**
     * Фото становится фоном. Для рабочего стола под снимок подстраиваются цвета цифр, акцента,
     * виджетов и циферблата; для экрана блокировки и часов меняется только их фон.
     */
    private suspend fun setPhoto(uri: Uri, target: BgTarget) {
        val name = repo.importPhoto(uri)
        val c = photoColors(name)
        val photo = Background(BackgroundType.PHOTO, c.gradient, photoFile = name)
        when (target) {
            // Новое фото — новый объект: часы снова встают сами, кадр фото — по центру.
            BgTarget.HOME -> update {
                it.copy(
                    background = photo,
                    accentColor = c.accent,
                    textColor = c.text,
                    dim = if (c.light) 0f else maxOf(it.dim, 0.15f),
                    wallClock = it.wallClock?.copy(place = null),
                    watchTimeShift = null,
                    watchPhotoCrop = null,
                )
            }
            BgTarget.LOCK -> update { it.copy(lockBackground = photo, lockDim = it.lockDim ?: it.dim) }
            BgTarget.WATCH -> update { it.copy(watch = it.watchSettings().copy(background = photo, timeShift = null), watchPhotoCrop = null) }
        }
    }

    /**
     * Тема из фото в один шаг: снимок — фоном рабочего стола, экрана блокировки и циферблата, цвета цифр,
     * акцента и виджетов — по нему. Если часы на обоях или циферблат с эффектом глубины, объект на фото
     * ищется сразу. Тема сохраняется в «Мои темы» и открывается — остаётся нажать «Применить».
     */
    fun themeFromPhoto(uri: Uri) = viewModelScope.launch {
        _busy.value = "Собираю тему из фото…"
        try {
            val name = repo.importPhoto(uri)
            val c = photoColors(name)
            val base = theme.value
            val dim = if (c.light) 0f else 0.15f
            var t = base.copy(
                name = "Из фото",
                background = Background(BackgroundType.PHOTO, c.gradient, photoFile = name),
                textColor = c.text,
                accentColor = c.accent,
                dim = dim,
                lockBackground = null,
                lockDim = null,
                wallClock = base.wallClock?.copy(place = null),
                watch = base.watch?.copy(background = null, textColor = c.text, accentColor = c.accent, dim = dim, timeShift = null),
                watchTimeShift = null,
                watchPhotoCrop = null,
            )
            val wantsDepth = t.wallClock != null || t.watchSettings().depth
            if (wantsDepth) {
                _busy.value = "Ищу главный объект на фото…"
                val found = runCatching { SubjectMask.ensure(app, name) }.isSuccess
                if (!found) _messages.tryEmit("Объект на фото не нашёлся сам — его можно обвести пальцем: «Часы на обоях» → «Выбрать объект»")
                t = t.copy(wallClock = t.wallClock?.copy(depth = found || t.wallClock?.depth == true))
            }
            val stored = repo.save(t)
            repo.setDraft(stored)
            _messages.tryEmit("Тема «${stored.name}» готова и сохранена в «Мои темы» — нажмите «Применить»")
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось открыть фото: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    /** «Попробовать на примере»: тема из готовой картинки — закат над холмами или море. */
    fun themeFromSample(sea: Boolean) = viewModelScope.launch {
        val file = withContext(Dispatchers.Default) {
            val bmp = SamplePhotos.draw(sea)
            java.io.File(app.cacheDir, if (sea) "sample_sea.jpg" else "sample_sunset.jpg").also { f ->
                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            }
        }
        themeFromPhoto(Uri.fromFile(file)).join()
        update { it.copy(name = if (sea) "Море" else "Закат") }
        repo.save(theme.value, replace = true).let(repo::setDraft)
    }

    /** «Поставить на телефон»: правки своей темы запоминаются в «Мои темы», и тема применяется. */
    fun applyAndKeep() {
        if (repo.saved.value.any { it.id == theme.value.id }) repo.setDraft(repo.save(theme.value, replace = true))
        apply()
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

    /** Сохранить черновик новой темой или ([replace]) поверх своей темы, из которой он открыт. */
    fun saveCurrent(replace: Boolean = false) {
        val stored = repo.save(theme.value, replace)
        repo.setDraft(stored)
        _messages.tryEmit(if (replace) "Тема «${stored.name}» обновлена" else "Тема «${stored.name}» сохранена")
    }

    fun delete(t: Theme) = repo.delete(t)

    /** Применяет тему ко всем выбранным местам; ошибка в одном шаге не мешает остальным. */
    fun apply() = viewModelScope.launch {
        val targets = _targets.value.on(devices.value)
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
            // Объект не нашёлся сам — глубину всё равно включаем и сразу даём обвести его пальцем.
            enable()
            _messages.tryEmit(e.message.orEmpty())
            _subjectEditor.value = photo
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

    private val _subjectEditor = MutableStateFlow<String?>(null)

    /** Фото, на котором сейчас вручную выделяют объект для глубины; null — редактор закрыт. */
    val subjectEditor: StateFlow<String?> = _subjectEditor.asStateFlow()

    /** Выбрать объект вручную: обвести пальцем на фото [bg]. */
    fun editSubject(bg: Background) {
        val photo = photoOf(bg) ?: return run { _messages.tryEmit("Эффект глубины работает с фото: выберите фото для фона") }
        _subjectEditor.value = photo
    }

    fun closeSubjectEditor() {
        _subjectEditor.value = null
    }

    fun refreshWatch() = viewModelScope.launch { _connection.value = sync.connection() }

    // ---------------------------------------------------------------- часы и планшет

    /** Часы подключены — появляются вкладка «Часы», статус и циферблат в «Применить». */
    fun setWatchEnabled(on: Boolean) {
        DeviceSettings.setWatch(app, on)
        _watchOn.value = on
        if (on) refreshWatch()
    }

    /** «Добавить устройство» → часы: модель — по имени часов из Bluetooth, инструкция — под неё. */
    fun connectWatch(name: String?) {
        name?.let(Watch::of)?.let { MyDevices.setWatch(app, it) }
        setWatchEnabled(true)
        _messages.tryEmit(if (_connection.value.hasApp) "Часы подключены — циферблат обновится по «Применить»" else "Часы добавлены — осталось установить на них SyncTheme")
    }

    /** Отправить на устройство тему, которая сейчас применена на телефоне. */
    fun sendTo(id: String) = viewModelScope.launch {
        val peer = LinkStore.peer(app, id) ?: return@launch
        _busy.value = "Отправляю на «${peer.name}»…"
        try {
            _messages.tryEmit(LinkClient.push(app, repo.applied.value, peer))
        } catch (e: Exception) {
            _messages.tryEmit("${peer.name}: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    private var linkJob: Job? = null

    /**
     * Подключить устройство из найденных в сети (или по адресу): телефон и устройство показывают
     * одинаковый код, на устройстве нажимают «Подключить». Сразу после — на него уходит текущая тема.
     */
    fun linkDevice(host: String, port: Int, name: String, service: String?) {
        linkJob?.cancel()
        linkJob = viewModelScope.launch {
            _linking.value = Linking(name)
            try {
                val peer = LinkClient.link(app, host, port, service) { code, device -> _linking.value = Linking(device, code) }
                _linking.value = null
                renderPreviews(theme.value)
                _busy.value = "Отправляю тему на «${peer.name}»…"
                val sent = runCatching { LinkClient.push(app, repo.applied.value, peer) }
                _messages.tryEmit(
                    if (sent.isSuccess) "«${peer.name}» подключён, тема уже на нём. Дальше — по «Применить»"
                    else "«${peer.name}» подключён. ${sent.exceptionOrNull()?.message.orEmpty()}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось подключить: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _linking.value = null
                _busy.value = null
            }
        }
    }

    /** Закрыть ожидание подключения (на устройстве так и не нажали «Подключить»). */
    fun cancelLinking() {
        linkJob?.cancel()
        _linking.value = null
    }

    fun unlink(id: String) {
        LinkStore.removePeer(app, id)
        if (LinkStore.peers(app).isEmpty()) LinkRetry.cancel(app)
        viewModelScope.launch { renderPreviews(theme.value) }
    }

    /** Получает ли устройство тему по «Применить». */
    fun setPeerEnabled(id: String, on: Boolean) {
        LinkStore.peer(app, id)?.let { LinkStore.updatePeer(app, it.copy(enabled = on)) }
    }

    /** Устройство: принимать тему с телефона (в фоне, с тихим уведомлением). */
    fun setReceiving(on: Boolean) {
        LinkStore.setReceiving(app, on)
        _receiving.value = on
        LinkService.ensure(app)
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
