package app.synctheme.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Значки нижнего меню и целей «Применить» (в базовом наборе Material их нет). */
object NavIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(pathBuilder: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round, pathBuilder = pathBuilder,
    )

    private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)

    /** Обои: рамка с горами и солнцем. */
    val Wallpaper = icon("Wallpaper") {
        stroke {
            moveTo(5f, 3.5f); lineTo(19f, 3.5f); quadTo(20.5f, 3.5f, 20.5f, 5f); lineTo(20.5f, 19f)
            quadTo(20.5f, 20.5f, 19f, 20.5f); lineTo(5f, 20.5f); quadTo(3.5f, 20.5f, 3.5f, 19f); lineTo(3.5f, 5f)
            quadTo(3.5f, 3.5f, 5f, 3.5f); close()
        }
        stroke { moveTo(4f, 17f); lineTo(9.5f, 11.5f); lineTo(13f, 15f); lineTo(15.5f, 12.5f); lineTo(20f, 17f) }
        fill { moveTo(16f, 6.5f); arcTo(1.8f, 1.8f, 0f, true, true, 16f, 10.1f); arcTo(1.8f, 1.8f, 0f, true, true, 16f, 6.5f); close() }
    }

    /** Виджеты: четыре плитки, одна — круглая. */
    val Widgets = icon("Widgets") {
        stroke {
            moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
            moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
        }
        stroke { moveTo(17f, 4f); arcTo(3f, 3f, 0f, true, true, 17f, 10f); arcTo(3f, 3f, 0f, true, true, 17f, 4f); close() }
    }

    /** Часы: корпус с ремешками и стрелками. */
    val Watch = icon("Watch") {
        stroke { moveTo(9f, 2.5f); lineTo(15f, 2.5f); lineTo(15.5f, 6f) ; moveTo(9f, 2.5f); lineTo(8.5f, 6f) }
        stroke { moveTo(9f, 21.5f); lineTo(15f, 21.5f); lineTo(15.5f, 18f); moveTo(9f, 21.5f); lineTo(8.5f, 18f) }
        stroke { moveTo(12f, 6f); arcTo(6f, 6f, 0f, true, true, 12f, 18f); arcTo(6f, 6f, 0f, true, true, 12f, 6f); close() }
        stroke { moveTo(12f, 9f); lineTo(12f, 12f); lineTo(14f, 13.5f) }
    }

    /** Ещё: три точки. */
    val More = icon("More") {
        for (x in listOf(5f, 12f, 19f)) {
            fill { moveTo(x, 10.3f); arcTo(1.7f, 1.7f, 0f, true, true, x, 13.7f); arcTo(1.7f, 1.7f, 0f, true, true, x, 10.3f); close() }
        }
    }

    /** Рабочий стол: телефон с сеткой значков. */
    val Home = icon("HomeScreen") {
        stroke {
            moveTo(8f, 2.5f); lineTo(16f, 2.5f); quadTo(18f, 2.5f, 18f, 4.5f); lineTo(18f, 19.5f)
            quadTo(18f, 21.5f, 16f, 21.5f); lineTo(8f, 21.5f); quadTo(6f, 21.5f, 6f, 19.5f); lineTo(6f, 4.5f)
            quadTo(6f, 2.5f, 8f, 2.5f); close()
        }
        for ((x, y) in listOf(9.5f to 7.5f, 14.5f to 7.5f, 9.5f to 12f, 14.5f to 12f)) {
            fill { moveTo(x, y - 1.2f); arcTo(1.2f, 1.2f, 0f, true, true, x, y + 1.2f); arcTo(1.2f, 1.2f, 0f, true, true, x, y - 1.2f); close() }
        }
    }

    /** Экран блокировки: замок. */
    val Lock = icon("LockScreen") {
        stroke {
            moveTo(6.5f, 10.5f); lineTo(17.5f, 10.5f); quadTo(19f, 10.5f, 19f, 12f); lineTo(19f, 19f)
            quadTo(19f, 20.5f, 17.5f, 20.5f); lineTo(6.5f, 20.5f); quadTo(5f, 20.5f, 5f, 19f); lineTo(5f, 12f)
            quadTo(5f, 10.5f, 6.5f, 10.5f); close()
        }
        stroke { moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.5f); lineTo(15.5f, 10.5f) }
    }
}
