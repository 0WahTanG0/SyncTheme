package app.synctheme.mobile.widget

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.Colors
import app.synctheme.mobile.render.DayNight
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.json.Json

/**
 * Папка «Приложений»: раскрывается поверх рабочего стола из того места, где стоит виджет
 * (или из начала своей линии), выбранной анимацией ([FolderAnim]) — сеткой или вдоль линии
 * ([FolderShape]). Нажатие на приложение открывает его, мимо или «Назад» — сворачивает папку обратно.
 */
class FolderActivity : ComponentActivity() {
    private var closing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val config = intent.getStringExtra(EXTRA_CONFIG)?.let { runCatching { Json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
            ?: WidgetUpdater.config(this, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0), WidgetKind.APPS)
        val theme = DayNight.of(this, ThemeRepository.get(this).applied.value)
        val origin = intent.sourceBounds
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val blur = config.dim && blurBehind()
        onBackPressedDispatcher.addCallback(this) { closing = true }
        setContent {
            FolderScreen(
                config, theme, origin, closing, blurred = blur,
                onOpen = ::launch, onDismiss = { closing = true }, onClosed = ::close,
                // Долгое нажатие на пустом месте — нарисовать линию прямо на этой странице рабочего стола.
                onRedraw = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || intent.hasExtra(EXTRA_CONFIG)) null else {
                    {
                        startActivity(FolderDrawActivity.forWidget(this, widgetId))
                        close()
                    }
                },
            )
        }
    }

    /** Ушли на рабочий стол или в другое приложение — папка не должна остаться открытой. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) close()
    }

    /** Размытие рабочего стола за папкой, если телефон его поддерживает (может выключаться экономией). */
    private fun blurBehind(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val wm = getSystemService(WindowManager::class.java)
        if (!wm.isCrossWindowBlurEnabled) return false
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = (24 * resources.displayMetrics.density).roundToInt() }
        return true
    }

    /** Приложение открывается, «вырастая» из своего значка. */
    private fun launch(component: String, from: Rect) {
        val intent = AppCatalog.launchIntent(this, component) ?: return
        val options = ActivityOptions.makeScaleUpAnimation(window.decorView, from.left, from.top, from.width(), from.height())
        runCatching { startActivity(intent, options.toBundle()) }.onFailure { runCatching { startActivity(intent) } }
        finish()
    }

    private fun close() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        /** Настройки папки для просмотра анимации из настройки виджета (виджета ещё может не быть). */
        const val EXTRA_CONFIG = "folder_config"

        fun preview(context: Context, config: WidgetConfig, from: Rect?): Intent =
            Intent(context, FolderActivity::class.java)
                .putExtra(EXTRA_CONFIG, Json.encodeToString(WidgetConfig.serializer(), config))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .also { it.sourceBounds = from }
    }
}

/** Приложение в папке. */
class FolderApp(val component: String, val label: String, val icon: ImageBitmap?)

fun folderApps(context: Context, config: WidgetConfig): List<FolderApp> {
    val circle = config.style != "glass"
    val px = (56 * context.resources.displayMetrics.density).roundToInt()
    return config.apps.ifEmpty { AppCatalog.defaults(context) }.take(APPS_IN_FOLDER).map {
        FolderApp(it, AppCatalog.label(context, it) ?: it.substringBefore('/'), AppCatalog.icon(context, it, px, circle)?.asImageBitmap())
    }
}

/** Открытие — у каждой анимации своё (по линии — чуть дольше: значкам дальше лететь), закрытие — одно быстрое для всех. */
private fun openSpec(anim: FolderAnim, line: Boolean): AnimationSpec<Float> = when (anim) {
    FolderAnim.REVEAL -> tween(if (line) 560 else 460, easing = FastOutSlowInEasing)
    FolderAnim.SPRING -> if (line) tween(720, easing = LinearOutSlowInEasing) else spring(dampingRatio = 0.58f, stiffness = 240f)
    FolderAnim.FAN -> tween(if (line) 700 else 620, easing = LinearOutSlowInEasing)
    FolderAnim.CASCADE -> tween(if (line) 820 else 640, easing = LinearOutSlowInEasing)
    FolderAnim.SPIRAL -> tween(if (line) 780 else 700, easing = FastOutSlowInEasing)
}

@Composable
fun FolderScreen(
    config: WidgetConfig,
    theme: Theme,
    origin: Rect?,
    closing: Boolean,
    blurred: Boolean = false,
    onOpen: (String, Rect) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    onRedraw: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val apps = remember(config) { folderApps(context, config) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(closing) {
        if (!closing) {
            progress.animateTo(1f, openSpec(config.animation, config.shape != FolderShape.GRID))
        } else {
            progress.animateTo(0f, tween(260, easing = FastOutLinearInEasing))
            onClosed()
        }
    }
    if (config.shape == FolderShape.GRID) FolderOverlay(config, theme, apps, origin, progress.value, blurred, onOpen, onDismiss, onRedraw)
    else FolderLineOverlay(config, theme, apps, origin, progress.value, blurred, onOpen, onDismiss, onRedraw)
}

/** Сетка папки: до 4 приложений — 2 в ряд, до 9 — по 3, больше — по 4. */
fun folderColumns(n: Int) = when {
    n <= 2 -> max(n, 1)
    n <= 4 -> 2
    n <= 9 -> 3
    else -> 4
}

private const val CELL_W = 84
private const val CELL_H = 98
private const val ICON = 56
private const val PAD = 20
private const val TITLE_H = 34

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val u = t - 1f
    return 1f + (c1 + 1f) * u * u * u + c1 * u * u
}
private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

/** Доля анимации i-го из n приложений: они стартуют друг за другом в пределах [span]. */
private fun stagger(i: Int, n: Int, p: Float, span: Float): Float {
    val start = if (n > 1) span * i / (n - 1) else 0f
    return clamp01((p - start) / (1f - span))
}

/** Внешний вид папки в стиле виджета. */
private class FolderLook(config: WidgetConfig, theme: Theme) {
    private val themeAccent = config.accentColor ?: theme.accentColor
    private val surface = Colors.widgetSurface(theme)
    val accent = Color(themeAccent)
    val text: Color = Color(
        config.textColor ?: when (config.style) {
            "poster" -> if (Colors.isLight(themeAccent)) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            "paper" -> 0xFF2B2622.toInt()
            "board" -> 0xFFF2F2F2.toInt()
            else -> theme.textColor
        },
    )
    val radius = when (config.style) {
        "glass" -> 34.dp
        "neon" -> 26.dp
        "poster" -> 14.dp
        "paper" -> 12.dp
        else -> 18.dp
    }
    val background: Color = when (config.style) {
        "glass" -> Color(Colors.withAlpha(ColorUtils.blendARGB(surface, 0xFFFFFFFF.toInt(), 0.12f), 0.55f))
        "neon" -> Color(0xEE0B0B12)
        "poster" -> Color(Colors.withAlpha(themeAccent, 0.97f))
        "paper" -> Color(0xFAF6F1E6)
        else -> Color(0xF5161619)
    }
    val border: Color? = when (config.style) {
        "glass" -> Color.White.copy(alpha = 0.28f)
        "neon" -> accent
        "paper" -> Color(0x335A4630)
        "board" -> Color(0xFF2E2E34)
        else -> null
    }
    val glow = config.style == "neon"
}

/**
 * Папка в состоянии [p]: 0 — свёрнута в виджет, 1 — раскрыта (у «Пружины» по пути бывает больше 1).
 * Всё положение считается из размеров сетки, а не измеряется: так первый кадр анимации уже верный.
 */
@Composable
fun FolderOverlay(
    config: WidgetConfig,
    theme: Theme,
    apps: List<FolderApp>,
    origin: Rect?,
    p: Float,
    blurred: Boolean = false,
    onOpen: (String, Rect) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    onRedraw: (() -> Unit)? = null,
) {
    val look = remember(config, theme) { FolderLook(config, theme) }
    val density = LocalDensity.current
    val q = clamp01(p)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .then(if (config.dim) Modifier.drawBehind { drawRect(Color.Black.copy(alpha = (if (blurred) 0.28f else 0.5f) * q)) } else Modifier)
            .pointerInput(onRedraw) { detectTapGestures(onLongPress = onRedraw?.let { r -> { r() } }) { onDismiss() } },
    ) {
        val px = { v: Float -> with(density) { v.dp.toPx() } }
        val n = apps.size
        val cols = folderColumns(n)
        val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
        val title = config.title.trim()
        val titleH = if (title.isEmpty()) 0 else TITLE_H
        val panelW = px((cols * CELL_W + 2 * PAD).toFloat())
        val panelH = px((rows * CELL_H + 2 * PAD + titleH).toFloat())
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val left = (screenW - panelW) / 2
        val top = (screenH - panelH) / 2
        // Виджет на экране; без него (превью) — внизу по центру.
        val from = origin?.let { androidx.compose.ui.geometry.Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            ?: androidx.compose.ui.geometry.Rect(Offset(screenW / 2, screenH * 0.82f), px(32f))
        val o = Offset(from.center.x - left, from.center.y - top) // виджет в координатах папки
        val panelCenter = Offset(panelW / 2, panelH / 2)
        fun cellCenter(i: Int) = Offset(
            px((PAD + (i % cols) * CELL_W + CELL_W / 2).toFloat()),
            px((PAD + titleH + (i / cols) * CELL_H + 6 + ICON / 2).toFloat()),
        )
        val maxDist = listOf(Offset.Zero, Offset(panelW, 0f), Offset(0f, panelH), Offset(panelW, panelH))
            .maxOf { (it - o).getDistance() }
        val shape = RoundedCornerShape(look.radius)

        Column(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { panelW.toDp() }, with(density) { panelH.toDp() })
                .graphicsLayer {
                    when (config.animation) {
                        FolderAnim.REVEAL -> {
                            scaleX = lerp(0.94f, 1f, q); scaleY = scaleX
                        }
                        FolderAnim.SPRING -> {
                            // Папка вырастает из прямоугольника виджета, с отскоком.
                            transformOrigin = TransformOrigin.Center
                            scaleX = lerp(from.width / panelW, 1f, p)
                            scaleY = lerp(from.height / panelH, 1f, p)
                            translationX = lerp(from.center.x - (left + panelW / 2), 0f, p)
                            translationY = lerp(from.center.y - (top + panelH / 2), 0f, p)
                            alpha = clamp01(p * 4f)
                        }
                        FolderAnim.FAN -> {
                            alpha = clamp01(p * 2.5f); scaleX = lerp(0.9f, 1f, q); scaleY = scaleX
                        }
                        FolderAnim.CASCADE -> {
                            alpha = clamp01(p * 2f); translationY = lerp(-px(28f), 0f, easeOutCubic(q))
                        }
                        FolderAnim.SPIRAL -> {
                            alpha = clamp01(p * 2f); scaleX = lerp(0.86f, 1f, q); scaleY = scaleX
                        }
                    }
                }
                .then(
                    if (config.animation == FolderAnim.REVEAL) Modifier.drawWithContent {
                        // Круг раскрытия растёт от виджета.
                        val r = lerp(min(from.width, from.height) / 2, maxDist, q)
                        clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(o, r)) }) { this@drawWithContent.drawContent() }
                    } else Modifier,
                )
                .then(
                    if (!config.panel) Modifier
                    else Modifier
                        .then(if (look.glow) Modifier.shadow(18.dp, shape, ambientColor = look.accent, spotColor = look.accent) else Modifier)
                        .clip(shape)
                        .background(look.background)
                        .then(look.border?.let { Modifier.border(if (look.glow) 1.5.dp else 1.dp, it, shape) } ?: Modifier),
                )
                .pointerInput(Unit) { detectTapGestures { } } // нажатие по папке её не закрывает
                .padding(PAD.dp),
        ) {
            if (title.isNotEmpty()) {
                Text(
                    title, color = look.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = if (config.panel) TextStyle.Default else Shadowed,
                    modifier = Modifier.width((cols * CELL_W).dp).height(TITLE_H.dp).graphicsLayer { alpha = clamp01((p - 0.3f) / 0.5f) },
                )
            }
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        val app = apps.getOrNull(i) ?: continue
                        val center = cellCenter(i)
                        FolderCell(
                            app, look, config.labels, shadow = !config.panel,
                            Modifier.graphicsLayer { itemTransform(config.animation, i, n, p, center, o, panelCenter, maxDist, px) },
                        ) {
                            val x = (left + center.x).roundToInt()
                            val y = (top + center.y).roundToInt()
                            val half = px(ICON / 2f).roundToInt()
                            onOpen(app.component, Rect(x - half, y - half, x + half, y + half))
                        }
                    }
                }
            }
        }
    }
}

/** Движение одного значка: каждая анимация задаёт, откуда и как он прилетает на своё место. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.itemTransform(
    anim: FolderAnim, i: Int, n: Int, p: Float, center: Offset, o: Offset, panelCenter: Offset, maxDist: Float, px: (Float) -> Float,
) {
    when (anim) {
        FolderAnim.REVEAL -> {
            // Значки проявляются, когда до них доходит круг.
            val front = clamp01(p) * maxDist
            val a = clamp01((front - (center - o).getDistance()) / px(90f) + 0.15f)
            alpha = a
            scaleX = lerp(0.7f, 1f, a); scaleY = scaleX
        }
        FolderAnim.SPRING -> {
            alpha = clamp01((p - 0.4f) / 0.35f)
            val s = lerp(0.6f, 1f, p)
            scaleX = s; scaleY = s
        }
        FolderAnim.FAN -> {
            // Веером из виджета: по дуге, с поворотом и небольшим перелётом.
            val t = stagger(i, n, p, 0.45f)
            val e = easeOutBack(t)
            val s = o - center
            val len = s.getDistance().coerceAtLeast(1f)
            val perp = Offset(-s.y / len, s.x / len) * (len * 0.25f * sin(PI.toFloat() * t))
            translationX = s.x * (1 - e) + perp.x
            translationY = s.y * (1 - e) + perp.y
            rotationZ = (1 - e) * -100f
            scaleX = lerp(0.35f, 1f, e); scaleY = scaleX
            alpha = clamp01(t * 3f)
        }
        FolderAnim.CASCADE -> {
            // Ряд за рядом падают сверху и чуть подпрыгивают на месте.
            val t = stagger(i, n, p, 0.55f)
            val e = easeOutBack(t)
            translationY = (1 - e) * -px(56f)
            scaleX = lerp(0.85f, 1f, e); scaleY = scaleX
            alpha = clamp01(t * 2.5f)
        }
        FolderAnim.SPIRAL -> {
            // Раскручиваются из центра папки по спирали.
            val t = stagger(i, n, p, 0.3f)
            val e = easeOutCubic(t)
            val v = (center - panelCenter) * e
            val angle = (1 - e) * PI.toFloat() * 1.5f
            val rotated = Offset(v.x * cos(angle) - v.y * sin(angle), v.x * sin(angle) + v.y * cos(angle))
            val target = panelCenter + rotated
            translationX = target.x - center.x
            translationY = target.y - center.y
            rotationZ = (1 - e) * -270f
            scaleX = lerp(0.2f, 1f, e); scaleY = scaleX
            alpha = clamp01(t * 2.5f)
        }
    }
}

/** Подписи без подложки — с тенью, чтобы читались на любых обоях. */
private val Shadowed = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1.5f), 6f))

@Composable
private fun FolderCell(
    app: FolderApp, look: FolderLook, labels: Boolean, shadow: Boolean, modifier: Modifier,
    iconDp: Float = ICON.toFloat(), onClick: () -> Unit,
) {
    Column(
        modifier
            .width(CELL_W.dp)
            .height(CELL_H.dp)
            .semantics { contentDescription = app.label }
            .pointerInput(app.component) { detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        if (app.icon != null) {
            Image(app.icon, null, modifier = Modifier.size(iconDp.dp))
        } else {
            Box(Modifier.size(iconDp.dp).clip(CircleShape).background(look.text.copy(alpha = 0.2f)))
        }
        if (labels) {
            Text(
                app.label, color = look.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, style = if (shadow) Shadowed else TextStyle.Default,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Папка вдоль линии: значки вылетают из виджета (или из начала своей линии) и ложатся вдоль неё,
 * без подложки. [p] — как у сетки: 0 — свёрнута, 1 — раскрыта.
 */
@Composable
fun FolderLineOverlay(
    config: WidgetConfig,
    theme: Theme,
    apps: List<FolderApp>,
    origin: Rect?,
    p: Float,
    blurred: Boolean = false,
    onOpen: (String, Rect) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    onRedraw: (() -> Unit)? = null,
) {
    val look = remember(config, theme) { FolderLook(config, theme) }
    val density = LocalDensity.current
    val q = clamp01(p)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .then(if (config.dim) Modifier.drawBehind { drawRect(Color.Black.copy(alpha = (if (blurred) 0.28f else 0.5f) * q)) } else Modifier)
            .pointerInput(onRedraw) { detectTapGestures(onLongPress = onRedraw?.let { r -> { r() } }) { onDismiss() } },
    ) {
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val from = origin?.let { Offset(it.exactCenterX(), it.exactCenterY()) } ?: Offset(screen.width / 2, screen.height * 0.82f)
        // Своя линия — та, что нарисована в этой ориентации экрана.
        val line = config.lineFor(screen.width > screen.height)
        val geo = remember(config.shape, line, apps.size, from, screen, config.labels) {
            FolderLines.layout(config.shape, line, apps.size, from, screen, density.density, config.labels)
        }
        val px = { v: Float -> v * density.density }
        val cellW = px(CELL_W.toFloat())
        val iconDp = geo.icon / density.density
        apps.forEachIndexed { i, app ->
            val n = apps.size
            FolderCell(
                app, look, geo.labels, shadow = true, iconDp = iconDp,
                modifier = Modifier
                    .offset {
                        val pos = lineTransform(config.animation, i, n, p, geo, px).position
                        IntOffset((pos.x - cellW / 2).roundToInt(), (pos.y - px(6f) - geo.icon / 2).roundToInt())
                    }
                    .graphicsLayer {
                        val t = lineTransform(config.animation, i, n, p, geo, px)
                        alpha = t.alpha
                        scaleX = t.scale; scaleY = t.scale
                        rotationZ = t.rotation
                        // Поворот и масштаб — вокруг самого значка, а не всей клетки с подписью.
                        transformOrigin = TransformOrigin(0.5f, (px(6f) + geo.icon / 2) / px(CELL_H.toFloat()))
                    },
            ) {
                val c = geo.targets[i]
                val half = (geo.icon / 2).roundToInt()
                onOpen(app.component, Rect(c.x.roundToInt() - half, c.y.roundToInt() - half, c.x.roundToInt() + half, c.y.roundToInt() + half))
            }
        }
    }
}

private class LineState(val position: Offset, val scale: Float, val rotation: Float, val alpha: Float)

/** Значок i вдоль линии: у каждой анимации свой полёт от начала линии к его месту. */
private fun lineTransform(anim: FolderAnim, i: Int, n: Int, p: Float, geo: FolderLine, px: (Float) -> Float): LineState = when (anim) {
    FolderAnim.REVEAL -> {
        // Проявляются на своих местах по очереди, от начала линии к концу.
        val t = stagger(i, n, p, 0.6f)
        LineState(geo.targets[i], lerp(0.3f, 1f, easeOutBack(t)), 0f, clamp01(t * 3f))
    }
    FolderAnim.SPRING -> {
        // Летят по линии и чуть проскакивают своё место, возвращаясь назад.
        val t = stagger(i, n, p, 0.35f)
        LineState(geo.track(i, easeOutBack(t)), lerp(0.5f, 1f, clamp01(t * 1.5f)), 0f, clamp01(t * 4f))
    }
    FolderAnim.FAN -> {
        // Катятся по линии, вращаясь.
        val t = stagger(i, n, p, 0.45f)
        val e = easeOutCubic(t)
        LineState(geo.track(i, e), lerp(0.3f, 1f, e), (1 - e) * -360f, clamp01(t * 3f))
    }
    FolderAnim.CASCADE -> {
        // Друг за другом, как вагончики: каждый дожидается, пока уедет предыдущий.
        val t = stagger(i, n, p, 0.7f)
        LineState(geo.track(i, easeOutCubic(t)), 1f, 0f, clamp01(t * 5f))
    }
    FolderAnim.SPIRAL -> {
        // Летят по линии, кружась вокруг неё и сходясь к своему месту.
        val t = stagger(i, n, p, 0.3f)
        val e = easeOutCubic(t)
        val r = (1 - e) * px(36f)
        val a = (1 - e) * PI.toFloat() * 3 + i
        LineState(geo.track(i, e) + Offset(r * cos(a), r * sin(a)), lerp(0.2f, 1f, e), (1 - e) * -270f, clamp01(t * 2.5f))
    }
}
