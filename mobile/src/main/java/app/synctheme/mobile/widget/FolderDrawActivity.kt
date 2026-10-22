package app.synctheme.mobile.widget

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.synctheme.core.Theme
import app.synctheme.mobile.data.ThemeRepository
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

/**
 * Своя линия папки рисуется прямо поверх рабочего стола: окно прозрачное, за ним — настоящий
 * главный экран со значками и виджетами. Значки папки сразу показываются в настоящем размере
 * там, где они лягут.
 *
 * Открывается из настройки виджета (сначала на рабочий стол, потом это окно — линия
 * возвращается в настройку) или из раскрытой папки долгим нажатием (линия сразу
 * сохраняется в виджет — на той странице рабочего стола, где он стоит).
 */
class FolderDrawActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val config = intent.getStringExtra(EXTRA_CONFIG)?.let { runCatching { Json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
            ?: WidgetUpdater.config(this, widgetId, WidgetKind.APPS)
        val theme = ThemeRepository.get(this).applied.value
        onBackPressedDispatcher.addCallback(this) { done(null, widgetId) }
        setContent {
            FolderDrawOverlay(config, theme, onCancel = { done(null, widgetId) }) { line -> done(line, widgetId) }
        }
    }

    private fun done(line: List<Float>?, widgetId: Int) {
        if (line != null) {
            // Линия — для той ориентации, в которой её нарисовали.
            val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (!landscape) FolderLineStore.add(this, line)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Из раскрытой папки: линия сразу у виджета.
                val config = WidgetUpdater.config(this, widgetId, WidgetKind.APPS)
                WidgetConfigStore.set(this, widgetId, config.withLine(line, landscape))
            } else {
                FolderLineStore.setPending(this, line, landscape)
            }
        }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) backToSettings(this)
    }

    companion object {
        private const val EXTRA_CONFIG = "draw_config"

        /** Из настройки виджета: рабочий стол, а над ним — окно рисования. */
        fun startFromSettings(context: Context, config: WidgetConfig) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val draw = Intent(context, FolderDrawActivity::class.java)
                .putExtra(EXTRA_CONFIG, Json.encodeToString(WidgetConfig.serializer(), config))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivities(arrayOf(home, draw))
        }

        /** Из раскрытой папки: рисуем на той же странице рабочего стола. */
        fun forWidget(context: Context, widgetId: Int): Intent =
            Intent(context, FolderDrawActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Вернуть на экран настройка виджета, из которой ушли рисовать. */
        private fun backToSettings(context: Context) {
            val tasks = context.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
            tasks.firstOrNull { it.taskInfo.topActivity?.className == WidgetConfigActivity::class.java.name }?.moveToFront()
        }
    }
}

/** Чем рисовать линию: пальцем (сглаживается в плавную кривую), ровной дугой или прямой. */
private enum class DrawTool(val title: String) { FREE("Пальцем"), ARC("Дуга"), LINE("Прямая") }

/**
 * Прозрачный слой для рисования линии: за ним виден рабочий стол. Подсказка сверху и кнопки
 * снизу прячутся, пока палец рисует, чтобы не закрывать значки. Готовую линию можно поправить:
 * потянуть за её точки, сделать ровнее; у дуги — изменить изгиб точкой посередине.
 */
@Composable
fun FolderDrawOverlay(config: WidgetConfig, theme: Theme, onCancel: () -> Unit, onDone: (List<Float>) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dp = density.density
    val apps = remember(config.apps) { folderApps(context, config) }
    val accent = Color(config.accentColor ?: theme.accentColor)
    var tool by remember { mutableStateOf(DrawTool.FREE) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // Сохранённая линия — снова кривая с точками, чтобы её можно было поправить.
        var shape by remember(screen) {
            mutableStateOf<EditableLine?>(
                config.lineFor(screen.width > screen.height).takeIf { config.shape == FolderShape.CUSTOM }
                    ?.let { FolderLines.points(it, screen) }?.takeIf { it.size >= 2 }?.let { LineShapes.curve(it, dp) },
            )
        }
        val stroke = remember { mutableStateListOf<Offset>() }
        var active by remember { mutableStateOf(false) }
        val line = remember(shape, screen) { shape?.let { FolderLines.normalize(it.points(), screen) }.orEmpty() }
        // Пока палец ведёт дугу или прямую — показываем её сразу ровной.
        val live: List<Offset>? = when {
            stroke.size < 2 -> null
            tool == DrawTool.FREE -> stroke.toList()
            else -> LineShapes.arc(stroke.first(), stroke.last(), if (tool == DrawTool.ARC) defaultBend(stroke.first(), stroke.last(), screen) else 0f)
        }
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Холст линии" }
                .pointerInput(tool, screen) {
                    val grab = 28.dp.toPx()
                    val minLength = 40.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        active = true
                        val hit = shape?.handles()?.withIndex()
                            ?.filter { (it.value - down.position).getDistance() < grab }
                            ?.minByOrNull { (it.value - down.position).getDistance() }?.index
                        if (hit != null) {
                            // Тянем точку линии.
                            drag(down.id) { change ->
                                shape = shape?.moveHandle(hit, change.position)
                                change.consume()
                            }
                        } else {
                            stroke.clear()
                            stroke.add(down.position)
                            drag(down.id) { change ->
                                if ((change.position - stroke.last()).getDistance() > 3f) stroke.add(change.position)
                                change.consume()
                            }
                            val pts = stroke.toList()
                            val made = when {
                                tool == DrawTool.FREE -> pts.takeIf { Polyline(it).length > minLength }?.let { LineShapes.curve(it, dp) }
                                (pts.last() - pts.first()).getDistance() < minLength -> null
                                else -> EditableLine.Arc(pts.first(), pts.last(), if (tool == DrawTool.ARC) defaultBend(pts.first(), pts.last(), screen) else 0f)
                            }
                            if (made != null) shape = made
                            stroke.clear()
                        }
                        active = false
                    }
                },
        ) {
            LineCanvas(line, live, shape?.handles().orEmpty(), shape is EditableLine.Arc, screen, apps, config.labels, accent, dp)
        }
        val idle = !active
        // Сверху — инструменты и подсказка, снизу — только «Отмена / Очистить / Готово»: линия у нижнего края не прячется.
        AnimatedVisibility(idle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.statusBarsPadding().padding(16.dp).widthIn(max = 460.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0xCC111118)).padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DrawTool.entries.forEach { t ->
                        val on = t == tool
                        Text(
                            t.title, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) accent else Color.White.copy(alpha = 0.12f))
                                .clickable { tool = t }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    val curve = shape as? EditableLine.Curve
                    val canSmooth = curve != null && curve.keys.size > 2
                    TextButton(onClick = { shape = curve?.smoother() }, enabled = canSmooth) {
                        Text("Ровнее", color = Color.White.copy(alpha = if (canSmooth) 1f else 0.4f))
                    }
                }
                Text(
                    when {
                        shape != null -> "Так лягут приложения. Тяните точки, чтобы поправить линию, или проведите новую в свободном месте."
                        tool == DrawTool.FREE -> "Проведите пальцем линию: откуда раскрывается папка → куда лягут приложения. Изгибы — любые, линия сама станет плавной."
                        tool == DrawTool.ARC -> "Проведите от начала к концу — получится ровная дуга. Изгиб меняется точкой посередине."
                        else -> "Проведите от начала к концу — получится прямая. Потяните её середину, чтобы выгнуть дугой."
                    },
                    color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        AnimatedVisibility(idle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.navigationBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xCC111118)).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onCancel) { Text("Отмена", color = Color.White) }
                TextButton(onClick = { shape = null }, enabled = shape != null) {
                    Text("Очистить", color = Color.White.copy(alpha = if (shape != null) 1f else 0.4f))
                }
                Button(
                    onClick = { onDone(line) }, enabled = line.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) { Text("Готово") }
            }
        }
    }
}

/** Дуга по умолчанию выгибается к середине экрана — туда, где обычно свободно. */
internal fun defaultBend(a: Offset, b: Offset, screen: Size): Float {
    val mid = (a + b) / 2f
    val c = b - a
    val toCenter = Offset(screen.width / 2, screen.height / 2) - mid
    val side = -c.y * toCenter.x + c.x * toCenter.y
    return if (side >= 0f) 0.25f else -0.25f
}

/** Линия (или та, что рисуется сейчас), её точки-ручки и значки папки в настоящем размере там, где они лягут. */
@Composable
private fun LineCanvas(
    line: List<Float>,
    live: List<Offset>?,
    handles: List<Offset>,
    arc: Boolean,
    screen: Size,
    apps: List<FolderApp>,
    labels: Boolean,
    accent: Color,
    dp: Float,
) {
    Canvas(Modifier.fillMaxSize()) {
        val pts = live ?: FolderLines.points(line, screen)
        if (pts.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (live != null) {
            drawCircle(Color.White, 10.dp.toPx(), pts.first())
            drawCircle(accent, 7.dp.toPx(), pts.first())
            return@Canvas
        }
        val geo = FolderLines.layout(FolderShape.CUSTOM, line, apps.size, Offset.Zero, screen, dp, labels)
        geo.targets.forEachIndexed { i, c ->
            val size = geo.icon
            val icon = apps.getOrNull(i)?.icon
            if (icon != null) {
                drawImage(
                    icon, dstOffset = IntOffset((c.x - size / 2).roundToInt(), (c.y - size / 2).roundToInt()),
                    dstSize = IntSize(size.roundToInt(), size.roundToInt()),
                )
            } else {
                drawCircle(Color.White.copy(alpha = 0.85f), size / 2, c)
            }
        }
        // Точки, за которые линию можно тянуть. Начало — «откуда» раскрывается папка — крупнее;
        // у дуги середина — кольцо: ею меняется изгиб.
        handles.forEachIndexed { i, h ->
            val start = i == 0
            drawCircle(Color.Black.copy(alpha = 0.35f), (if (start) 12 else 10).dp.toPx(), h)
            drawCircle(Color.White, (if (start) 10 else 8).dp.toPx(), h)
            if (arc && i == 1) drawCircle(accent, 8.dp.toPx(), h, style = Stroke(3.dp.toPx()))
            else drawCircle(accent, (if (start) 7 else 5).dp.toPx(), h)
        }
    }
}
