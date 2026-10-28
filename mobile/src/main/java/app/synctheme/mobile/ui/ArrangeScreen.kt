package app.synctheme.mobile.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.synctheme.core.Background
import app.synctheme.core.BackgroundType
import app.synctheme.core.ClockPlace
import app.synctheme.core.PhotoCrop
import app.synctheme.core.Theme
import app.synctheme.core.TimeShift
import app.synctheme.mobile.data.ThemeRepository
import app.synctheme.mobile.render.Crop
import app.synctheme.mobile.render.DayNight
import app.synctheme.mobile.render.ThemeRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** Что двигают в редакторе расположения. */
enum class ArrangeTarget(val title: String) { HOME("Рабочий стол"), WATCH("Циферблат") }

private enum class ArrangeMode { CLOCK, PHOTO }

/** Размеры кадров редактора: телефон и часы (точки 450×450). */
private const val PHONE_W = 540
private const val PHONE_H = 1170
private const val WATCH = 450

/**
 * Редактор расположения: часы на обоях (или время на циферблате) и фото двигаются пальцем,
 * щипком меняется размер. «Само» возвращает часы к расстановке по объекту фото.
 * Изменения попадают в тему по «Готово», применяются — кнопкой «Применить».
 */
@Composable
fun ArrangeScreen(vm: MainViewModel, target: ArrangeTarget, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    // Состояние читается в обработчике жестов напрямую: за один кадр приходит несколько сдвигов пальца.
    var edited by remember { mutableStateOf(vm.theme.value) }
    val canMoveClock = when (target) {
        ArrangeTarget.HOME -> edited.wallClock != null
        ArrangeTarget.WATCH -> edited.watchSettings().clockStyle.movableTime
    }
    val photoBg = photoBackground(edited, target)
    var mode by remember { mutableStateOf(if (canMoveClock) ArrangeMode.CLOCK else ArrangeMode.PHOTO) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    // Размер фото — чтобы переводить движение пальца в сдвиг кадра.
    val photoSize = remember(photoBg?.photoFile) {
        photoBg?.photoFile?.let { name ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ThemeRepository.get(context).photoFile(name).path, o)
            o.outWidth to o.outHeight
        }
    }
    val frameW = if (target == ArrangeTarget.WATCH) WATCH else PHONE_W
    val frameH = if (target == ArrangeTarget.WATCH) WATCH else PHONE_H

    // Перерисовка при каждом изменении; пока рисуется кадр, промежуточные состояния пропускаются.
    LaunchedEffect(target) {
        snapshotFlow { edited }.conflate().collect { t ->
            preview = withContext(Dispatchers.Default) {
                when (target) {
                    // Цвета — той версии темы (светлой или тёмной), что будет на экране; место от неё не зависит.
                    ArrangeTarget.HOME -> ThemeRenderer.phonePreview(context, DayNight.of(context, t), PHONE_W, PHONE_H, widgets = false)
                    ArrangeTarget.WATCH -> ThemeRenderer.watchPreview(context, DayNight.forWatch(t), WATCH)
                }.asImageBitmap()
            }
        }
    }

    AppTheme(edited.accentColor) {
        AppBackdrop {
            val header: @Composable () -> Unit = {
                Text("Расположение · ${target.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = look.text)
                Text(
                    when (mode) {
                        ArrangeMode.CLOCK -> "Двигайте ${if (target == ArrangeTarget.HOME) "часы" else "время"} пальцем, щипком меняйте размер."
                        ArrangeMode.PHOTO -> "Двигайте фото пальцем, щипком увеличивайте."
                    },
                    style = MaterialTheme.typography.bodySmall, color = look.muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
            }
            val canvas: @Composable (Modifier) -> Unit = { modifier ->
                BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
                    val aspect = frameW.toFloat() / frameH
                    val shape = if (target == ArrangeTarget.WATCH) CircleShape else RoundedCornerShape(22.dp)
                    Box(
                        Modifier
                            .then(if (maxWidth / maxHeight > aspect) Modifier.fillMaxSize().aspectRatio(aspect, matchHeightConstraintsFirst = true) else Modifier.fillMaxWidth().aspectRatio(aspect))
                            .clip(shape)
                            .background(Color.Black)
                            .border(2.dp, look.accent.copy(alpha = 0.6f), shape)
                            .pointerInput(mode, target) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val fx = pan.x / size.width
                                    val fy = pan.y / size.height
                                    edited = when (mode) {
                                        ArrangeMode.CLOCK -> moveClock(context, edited, target, fx, fy, zoom)
                                        ArrangeMode.PHOTO -> movePhoto(edited, target, photoSize, frameW, frameH, fx, fy, zoom)
                                    }
                                }
                            },
                    ) {
                        preview?.let { Image(it, contentDescription = "Расположение", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
            val controls: @Composable () -> Unit = {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canMoveClock) {
                        FilterChip(mode == ArrangeMode.CLOCK, { mode = ArrangeMode.CLOCK }, label = { Text(if (target == ArrangeTarget.HOME) "Часы" else "Время") })
                    }
                    if (photoBg != null) FilterChip(mode == ArrangeMode.PHOTO, { mode = ArrangeMode.PHOTO }, label = { Text("Фото") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (mode) {
                        ArrangeMode.CLOCK -> {
                            TextButton(onClick = { edited = autoClock(edited, target) }) { Text("Автоматически") }
                            val place = edited.wallClock?.place
                            if (target == ArrangeTarget.HOME && place != null) {
                                TextButton(onClick = {
                                    val switched = ThemeRenderer.clampPlace(edited, place.copy(stacked = !place.stacked), PHONE_W, PHONE_H)
                                    edited = edited.copy(wallClock = edited.wallClock!!.copy(place = switched))
                                }) {
                                    Text(if (place.stacked) "Одной строкой" else "Часы над минутами")
                                }
                            }
                        }
                        ArrangeMode.PHOTO -> TextButton(onClick = { edited = setCrop(edited, target, PhotoCrop()) }) { Text("По центру") }
                    }
                }
            }
            val buttons: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(onClick = {
                        val result = edited
                        vm.update { result }
                        onClose()
                    }, modifier = Modifier.weight(1f)) { Text("Готово") }
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                if (maxWidth > maxHeight && maxWidth >= 600.dp) {
                    // Телефон боком: холст во всю высоту слева, всё остальное — справа.
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        canvas(Modifier.weight(0.5f).fillMaxHeight())
                        Column(Modifier.weight(0.5f).fillMaxHeight()) {
                            header()
                            controls()
                            Spacer(Modifier.weight(1f))
                            buttons()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        header()
                        canvas(Modifier.weight(1f).fillMaxWidth())
                        controls()
                        buttons()
                    }
                }
            }
        }
    }
}

/** Фото, которое двигают (null — фон не фото). */
private fun photoBackground(t: Theme, target: ArrangeTarget): Background? {
    val bg = if (target == ArrangeTarget.WATCH) t.watchBackground() else t.background
    return bg.takeIf { it.type == BackgroundType.PHOTO && it.photoFile != null }
}

private fun moveClock(context: android.content.Context, t: Theme, target: ArrangeTarget, fx: Float, fy: Float, zoom: Float): Theme = when (target) {
    ArrangeTarget.HOME -> {
        val wall = t.wallClock ?: return t
        val place = wall.place ?: ThemeRenderer.clockPlaceOf(context, t, PHONE_W, PHONE_H) ?: ClockPlace(0.5f, 0.2f)
        val moved = place.copy(x = place.x + fx, y = place.y + fy, scale = (place.scale * zoom).coerceIn(0.35f, 2.2f))
        // Часы целиком на экране: у края они останавливаются.
        t.copy(wallClock = wall.copy(place = ThemeRenderer.clampPlace(t, moved, PHONE_W, PHONE_H)))
    }
    ArrangeTarget.WATCH -> {
        val start = ThemeRenderer.watchTimeShift(context, t) ?: TimeShift()
        val shift = TimeShift(start.dx + fx * WATCH, start.dy + fy * WATCH, (start.scale * zoom).coerceIn(0.4f, 1.4f))
        // Время целиком внутри круга экрана.
        setShift(t, ThemeRenderer.fitShift(context, t, shift))
    }
}

/** Часы снова «сами»: по объекту фото (с глубиной) или по выбранной раскладке. */
private fun autoClock(t: Theme, target: ArrangeTarget): Theme = when (target) {
    ArrangeTarget.HOME -> t.copy(wallClock = t.wallClock?.copy(place = null))
    ArrangeTarget.WATCH -> setShift(t, null)
}

private fun setShift(t: Theme, shift: TimeShift?): Theme =
    t.watch?.let { t.copy(watch = it.copy(timeShift = shift)) } ?: t.copy(watchTimeShift = shift)

/**
 * Сдвиг кадра фото: палец тянет фото, поэтому кадр едет в обратную сторону — на долю своего
 * размера; щипок меняет увеличение. Центр кадра сразу ограничивается краями фото.
 */
private fun movePhoto(
    t: Theme, target: ArrangeTarget, photo: Pair<Int, Int>?, frameW: Int, frameH: Int, fx: Float, fy: Float, zoom: Float,
): Theme {
    val bg = photoBackground(t, target) ?: return t
    val (pw, ph) = photo ?: return t
    val crop = Crop.of(pw, ph, frameW.toFloat(), frameH.toFloat(), bg)
    val moved = bg.copy(
        focusX = crop.centerX() - fx * crop.width(),
        focusY = crop.centerY() - fy * crop.height(),
        zoom = (bg.zoom * zoom).coerceIn(1f, 4f),
    )
    val clamped = Crop.of(pw, ph, frameW.toFloat(), frameH.toFloat(), moved)
    return setCrop(t, target, PhotoCrop(clamped.centerX(), clamped.centerY(), moved.zoom))
}

private fun setCrop(t: Theme, target: ArrangeTarget, c: PhotoCrop): Theme = when (target) {
    ArrangeTarget.HOME -> t.copy(background = t.background.copy(focusX = c.focusX, focusY = c.focusY, zoom = c.zoom))
    ArrangeTarget.WATCH -> {
        val watch = t.watch
        val own = watch?.background
        if (watch != null && own != null) t.copy(watch = watch.copy(background = own.copy(focusX = c.focusX, focusY = c.focusY, zoom = c.zoom)))
        else t.copy(watchPhotoCrop = c)
    }
}

/** Подпись для кнопки на вкладках: что можно подвинуть. */
fun arrangeLabel(t: Theme, target: ArrangeTarget): String? {
    val photo = photoBackground(t, target) != null
    val clock = when (target) {
        ArrangeTarget.HOME -> t.wallClock != null
        ArrangeTarget.WATCH -> t.watchSettings().clockStyle.movableTime
    }
    return when {
        clock && photo -> if (target == ArrangeTarget.HOME) "Подвинуть часы и фото" else "Подвинуть время и фото"
        clock -> if (target == ArrangeTarget.HOME) "Подвинуть часы" else "Подвинуть время"
        photo -> "Подвинуть фото"
        else -> null
    }
}

/** Пометка, что расположение задано вручную (для подписей). */
fun arrangedManually(t: Theme, target: ArrangeTarget): Boolean = when (target) {
    ArrangeTarget.HOME -> t.wallClock?.place != null || t.background.focusX != 0.5f || t.background.focusY != 0.5f || t.background.zoom != 1f
    ArrangeTarget.WATCH -> t.watchSettings().timeShift != null || t.watchPhotoCrop != null || t.watchSettings().background?.framed == true
}
