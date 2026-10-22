package app.synctheme.mobile.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.synctheme.core.Theme
import app.synctheme.mobile.render.ThemeRenderer
import app.synctheme.mobile.ui.AccentButton
import app.synctheme.mobile.ui.AppBackdrop
import app.synctheme.mobile.ui.LocalLook
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Размер экрана телефона, px: линия хранится в его долях. */
fun screenSize(context: Context): Size {
    val b = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
    return Size(b.width().toFloat(), b.height().toFloat())
}

/** Скриншот рабочего стола, подложенный под линию: хранится, пока его не уберут. */
private fun backdropFile(context: Context) = File(context.filesDir, "folder_backdrop.jpg")

private fun loadBackdrop(context: Context): ImageBitmap? =
    backdropFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

/** Картинка из галереи → уменьшенная копия у приложения (галерея не нужна, если картинку удалят). */
private fun saveBackdrop(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    backdropFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    bmp.asImageBitmap()
}.getOrNull()

/**
 * Своя линия папки: пальцем рисуется линия поверх рабочего стола — откуда раскрывается папка и куда
 * ложатся приложения; изгибы любые. Под линией — обои темы с часами или скриншот рабочего стола,
 * чтобы обойти значки и виджеты. Значки сразу показываются там, где они лягут.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderLineEditor(
    config: WidgetConfig,
    theme: Theme,
    onPreview: (List<Float>) -> Unit,
    onCancel: () -> Unit,
    onDone: (List<Float>) -> Unit,
) {
    val context = LocalContext.current
    val look = LocalLook.current
    val density = LocalDensity.current
    val screen = remember { screenSize(context) }
    var line by remember { mutableStateOf(config.line.takeIf { config.shape == FolderShape.CUSTOM }.orEmpty()) }
    val drawing = remember { mutableStateListOf<Offset>() }
    var shot by remember { mutableStateOf(loadBackdrop(context)) }
    val wallpaper by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            val w = 540
            ThemeRenderer.phonePreview(context, theme, w, (w * screen.height / screen.width).roundToInt(), widgets = false).asImageBitmap()
        }
    }
    val apps = remember(config.apps) { folderApps(context, config) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { saveBackdrop(context, it) }?.let { shot = it }
    }

    AppBackdrop {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Text("Своя линия", style = look.sectionTitle, color = look.text)
            Text(
                "Проведите пальцем линию: от места, откуда раскрывается папка, туда, куда лягут приложения. " +
                    "Изгибы — любые. Чтобы обойти значки и виджеты, подложите скриншот своего рабочего стола.",
                style = MaterialTheme.typography.bodySmall, color = look.muted,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                // Холст — уменьшенный экран телефона в тех же пропорциях.
                val k = minOf(constraints.maxWidth / screen.width, constraints.maxHeight / screen.height)
                val canvas = Size(screen.width * k, screen.height * k)
                Box(
                    Modifier
                        .size(with(density) { canvas.width.toDp() }, with(density) { canvas.height.toDp() })
                        .clip(RoundedCornerShape(22.dp))
                        .semantics { contentDescription = "Холст линии" }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { drawing.clear(); drawing.add(it) },
                                onDrag = { change, _ ->
                                    if ((change.position - drawing.last()).getDistance() > 3f) drawing.add(change.position)
                                },
                                onDragEnd = {
                                    val drawn = drawing.map { Offset(it.x / k, it.y / k) }
                                    FolderLines.normalize(drawn, screen).takeIf { it.size >= 4 }?.let { line = it }
                                    drawing.clear()
                                },
                                onDragCancel = { drawing.clear() },
                            )
                        },
                ) {
                    (shot ?: wallpaper)?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    LineCanvas(line, drawing, canvas, screen, k, apps, config.labels, look.primary)
                }
            }
            val count = apps.size
            Text(
                when {
                    line.isEmpty() -> "Линии пока нет — проведите её пальцем."
                    else -> {
                        val geo = FolderLines.layout(FolderShape.CUSTOM, line, count, Offset.Zero, screen, density.density, config.labels)
                        if (geo.icon < 55 * density.density) "Линия коротковата для $count приложений — значки будут меньше. Проведите линию длиннее."
                        else "Приложений: $count — значки лягут так, как на картинке."
                    }
                },
                style = MaterialTheme.typography.bodySmall, color = look.muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { line = emptyList() }, enabled = line.isNotEmpty()) { Text("Очистить") }
                if (shot == null) {
                    OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Подложить скриншот")
                    }
                } else {
                    OutlinedButton(onClick = { backdropFile(context).delete(); shot = null }) { Text("Убрать скриншот") }
                }
                TextButton(onClick = { onPreview(line) }, enabled = line.isNotEmpty()) { Text("Посмотреть") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
                Box(Modifier.weight(1f)) { AccentButton("Готово", { onDone(line) }, enabled = line.isNotEmpty()) }
            }
        }
    }
}

/** Линия (или та, что рисуется сейчас) и значки там, где они лягут. */
@Composable
private fun LineCanvas(
    line: List<Float>, drawing: List<Offset>, canvas: Size, screen: Size, k: Float, apps: List<FolderApp>, labels: Boolean, accent: Color,
) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        val pts = if (drawing.isNotEmpty()) drawing.toList() else FolderLines.points(line, canvas)
        if (pts.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Начало — «откуда» раскрывается папка.
        drawCircle(Color.White, 9.dp.toPx(), pts.first())
        drawCircle(accent, 6.dp.toPx(), pts.first())
        if (drawing.isNotEmpty()) return@Canvas
        // Значки — в масштабе холста (он меньше настоящего экрана в k раз).
        val geo = FolderLines.layout(FolderShape.CUSTOM, line, apps.size, Offset.Zero, screen, density.density, labels)
        geo.targets.forEachIndexed { i, t ->
            val c = t * k
            val size = geo.icon * k
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
