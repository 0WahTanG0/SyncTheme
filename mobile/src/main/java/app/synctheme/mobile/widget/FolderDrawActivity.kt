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
import androidx.compose.foundation.gestures.detectDragGestures
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
            FolderLineStore.add(this, line)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Из раскрытой папки: линия сразу у виджета.
                val config = WidgetUpdater.config(this, widgetId, WidgetKind.APPS)
                WidgetConfigStore.set(this, widgetId, config.copy(shape = FolderShape.CUSTOM, line = line))
            } else {
                FolderLineStore.setPending(this, line)
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

/**
 * Прозрачный слой для рисования линии: за ним виден рабочий стол. Подсказка сверху и кнопки
 * снизу прячутся, пока палец рисует, чтобы не закрывать значки.
 */
@Composable
fun FolderDrawOverlay(config: WidgetConfig, theme: Theme, onCancel: () -> Unit, onDone: (List<Float>) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var line by remember { mutableStateOf(config.line.takeIf { config.shape == FolderShape.CUSTOM }.orEmpty()) }
    val drawing = remember { mutableStateListOf<Offset>() }
    val apps = remember(config.apps) { folderApps(context, config) }
    val accent = Color(config.accentColor ?: theme.accentColor)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Холст линии" }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drawing.clear(); drawing.add(it) },
                        onDrag = { change, _ ->
                            if ((change.position - drawing.last()).getDistance() > 3f) drawing.add(change.position)
                        },
                        onDragEnd = {
                            FolderLines.normalize(drawing.toList(), screen).takeIf { it.size >= 4 }?.let { line = it }
                            drawing.clear()
                        },
                        onDragCancel = { drawing.clear() },
                    )
                },
        ) {
            LineCanvas(line, drawing, screen, apps, config.labels, accent, density.density)
        }
        val idle = drawing.isEmpty()
        AnimatedVisibility(idle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Text(
                if (line.isEmpty()) "Проведите пальцем линию: откуда раскрывается папка → куда лягут приложения. Изгибы — любые."
                else "Так лягут приложения. Не нравится — проведите линию заново.",
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.statusBarsPadding().padding(16.dp).widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xCC111118)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        AnimatedVisibility(idle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.navigationBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xCC111118)).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onCancel) { Text("Отмена", color = Color.White) }
                TextButton(onClick = { line = emptyList() }, enabled = line.isNotEmpty()) {
                    Text("Очистить", color = Color.White.copy(alpha = if (line.isNotEmpty()) 1f else 0.4f))
                }
                Button(
                    onClick = { onDone(line) }, enabled = line.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) { Text("Готово") }
            }
        }
    }
}

/** Линия (или та, что рисуется сейчас) и значки папки в настоящем размере там, где они лягут. */
@Composable
private fun LineCanvas(line: List<Float>, drawing: List<Offset>, screen: Size, apps: List<FolderApp>, labels: Boolean, accent: Color, dp: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val pts = if (drawing.isNotEmpty()) drawing.toList() else FolderLines.points(line, screen)
        if (pts.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Начало — «откуда» раскрывается папка.
        drawCircle(Color.White, 10.dp.toPx(), pts.first())
        drawCircle(accent, 7.dp.toPx(), pts.first())
        if (drawing.isNotEmpty()) return@Canvas
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
    }
}
