package app.synctheme.mobile.widget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ломаная с длиной пути: точка на расстоянии s от начала. */
class Polyline(val points: List<Offset>) {
    private val cum = FloatArray(points.size)
    val length: Float

    init {
        for (i in 1 until points.size) cum[i] = cum[i - 1] + (points[i] - points[i - 1]).getDistance()
        length = cum.lastOrNull() ?: 0f
    }

    fun at(s: Float): Offset {
        if (points.size < 2) return points.firstOrNull() ?: Offset.Zero
        val d = s.coerceIn(0f, length)
        var i = cum.indexOfFirst { it >= d }.coerceAtLeast(1)
        if (i >= points.size) i = points.size - 1
        val seg = cum[i] - cum[i - 1]
        val t = if (seg > 0f) (d - cum[i - 1]) / seg else 0f
        return points[i - 1] + (points[i] - points[i - 1]) * t
    }

    /** Те же очертания из [n] точек через равные промежутки — для хранения своей линии. */
    fun resample(n: Int): List<Offset> = if (points.size < 2) points else List(n) { at(length * it / (n - 1)) }
}

/**
 * Раскрытая папка-линия: откуда вылетают значки ([origin]), где каждый ложится ([targets]) и
 * какой дорогой летит: по самой линии (на расстояние stops[i] от её начала) или, у «Кольца», напрямую.
 * [icon] — размер значка, px; [labels] — помещаются ли подписи.
 */
class FolderLine(
    val origin: Offset,
    val path: Polyline?,
    val stops: FloatArray,
    val targets: List<Offset>,
    val icon: Float,
    val labels: Boolean,
) {
    /** Где i-й значок, пройдя долю [f] своего пути (у «Пружины» бывает больше 1 — перелёт). */
    fun track(i: Int, f: Float): Offset = when (path) {
        null -> origin + (targets[i] - origin) * f
        else -> {
            val s = stops[i] * f
            if (s <= path.length) path.at(s)
            else path.at(path.length) + (path.at(path.length) - path.at(path.length - 1f)) * (s - path.length)
        }
    }
}

/** Раскладки папки вдоль линии: готовые линии от виджета и своя, нарисованная пальцем. */
object FolderLines {
    /** Своя линия: x0, y0, x1, y1 … в долях экрана → точки на экране. */
    fun points(line: List<Float>, screen: Size): List<Offset> =
        line.chunked(2).filter { it.size == 2 }.map { (x, y) -> Offset(x * screen.width, y * screen.height) }

    /** Нарисованная пальцем линия → не больше 64 точек через равные промежутки, в долях экрана. */
    fun normalize(drawn: List<Offset>, screen: Size): List<Float> {
        val line = Polyline(drawn)
        if (line.length < 1f) return emptyList()
        val n = (line.length / 12f).roundToInt().coerceIn(8, 64)
        return line.resample(n).flatMap { listOf(round4(it.x / screen.width), round4(it.y / screen.height)) }
    }

    private fun round4(v: Float) = (v.coerceIn(0f, 1f) * 10000).roundToInt() / 10000f

    /**
     * @param origin середина виджета на экране (или начало своей линии); [dp] — плотность экрана.
     */
    fun layout(shape: FolderShape, line: List<Float>, n: Int, origin: Offset, screen: Size, dp: Float, labels: Boolean): FolderLine {
        val count = max(n, 1)
        if (shape == FolderShape.CUSTOM) {
            val pts = points(line, screen)
            if (pts.size >= 2 && Polyline(pts).length > 40 * dp) return along(Polyline(pts), count, dp, labels, custom = true)
            return layout(FolderShape.ARC, line, n, origin, screen, dp, labels)
        }
        // Шаг вдоль линии: значок и подпись под ним не наезжают на соседа, даже когда линия идёт вверх.
        val spacing = (if (labels) 92 else 70) * dp
        if (shape == FolderShape.RING) return ring(count, origin, screen, dp, (if (labels) 84 else 68) * dp, labels)
        val gap0 = 76 * dp
        val need = gap0 + (count - 1) * spacing
        // Направление — от виджета к середине экрана (снизу вверх, от края к центру); линия вписывается
        // в свободное место до края экрана. Из четырёх сторон берём ту, где значкам просторнее.
        val dx0 = if (origin.x < screen.width / 2) 1f else -1f
        val dy0 = if (origin.y > screen.height * 0.45f) -1f else 1f
        val half = 34 * dp
        return listOf(dx0 to dy0, -dx0 to dy0, dx0 to -dy0, -dx0 to -dy0).map { (dx, dy) ->
            val roomX = (if (dx > 0) screen.width - 12 * dp - origin.x else origin.x - 12 * dp) - half
            val roomY = (if (dy < 0) origin.y - 40 * dp else screen.height - 48 * dp - origin.y) - half - (if (labels) 20 * dp else 0f)
            along(Polyline(preset(shape, origin, need, dx, dy, dp, max(roomX, 0f), max(roomY, 0f))), count, dp, labels, custom = false, gap0 = gap0)
        }.minWith(compareBy<FolderLine> { overflow(it, screen, dp) }.thenByDescending { it.icon })
    }

    /** Точки готовой линии от виджета длиной не больше [need], в пределах [roomX] × [roomY] от него. */
    private fun preset(shape: FolderShape, o: Offset, need: Float, dx: Float, dy: Float, dp: Float, roomX: Float, roomY: Float): List<Offset> =
        when (shape) {
            FolderShape.COLUMN -> listOf(o, o + Offset(0f, dy * min(need, roomY)))
            FolderShape.ROW -> listOf(o, o + Offset(dx * min(need, roomX), 0f))
            FolderShape.WAVE -> {
                // Вертикальная волна-змейка: качается только в сторону свободного места (туда — обратно — туда);
                // путь длиннее высоты, поэтому высоту берём меньше.
                val height = min(need * 0.74f, roomY)
                val amp = min(110 * dp, roomX * 0.8f)
                List(64) { k ->
                    val t = k / 63f
                    o + Offset(dx * amp * (1 - cos(t * 3f * PI.toFloat())) / 2, dy * height * t)
                }
            }
            else -> {
                // Дуга — четверть эллипса: сначала вверх (или вниз) от виджета, потом заворачивает к середине.
                var a = min(roomX, roomY * 0.8f)
                var b = min(roomY, a * 2.2f)
                val quarter = (PI * (3 * (a + b) - kotlin.math.sqrt((3 * a + b) * (a + 3 * b))) / 4).toFloat()
                if (quarter > need && quarter > 0f) {
                    a *= need / quarter
                    b *= need / quarter
                }
                List(48) { k ->
                    val phi = (PI / 2).toFloat() * k / 47f
                    o + Offset(dx * a * (1 - cos(phi)), dy * b * sin(phi))
                }
            }
        }

    /** Значки через равные промежутки вдоль линии. */
    private fun along(line: Polyline, n: Int, dp: Float, labels: Boolean, custom: Boolean, gap0: Float = 0f): FolderLine {
        val stops = FloatArray(n) { i ->
            if (custom) line.length * (i + 1) / n
            else if (n == 1) min(gap0, line.length) else gap0 + (line.length - gap0) * i / (n - 1)
        }
        val spacing = if (n > 1) stops[1] - stops[0] else line.length
        // На короткой своей линии значки мельче, а подписи прячутся, чтобы не наезжать друг на друга.
        val icon = (spacing * 0.8f).coerceIn(34 * dp, 56 * dp)
        return FolderLine(line.points.first(), line, stops, stops.map(line::at), icon, labels && spacing >= 64 * dp)
    }

    /** «Кольцо»: значки по кругу вокруг виджета, каждый летит к своему месту напрямую. */
    private fun ring(n: Int, o: Offset, screen: Size, dp: Float, spacing: Float, labels: Boolean): FolderLine {
        val r = max(104 * dp, n * spacing / (2 * PI.toFloat()))
        val inset = 28 * dp + r
        // Круг целиком на экране: если виджет у края, круг сдвигается внутрь.
        val c = Offset(o.x.coerceIn(inset, max(inset, screen.width - inset)), o.y.coerceIn(inset + 24 * dp, max(inset + 24 * dp, screen.height - inset)))
        val targets = List(n) { i ->
            val a = -PI.toFloat() / 2 + 2 * PI.toFloat() * i / n
            c + Offset(r * cos(a), r * sin(a))
        }
        return FolderLine(o, null, FloatArray(n) { 1f }, targets, 56 * dp, labels)
    }

    /** Насколько значки вылезают за экран (с полями под строку состояния и навигацию). */
    private fun overflow(geo: FolderLine, screen: Size, dp: Float): Float {
        val half = geo.icon / 2 + 6 * dp
        val label = if (geo.labels) 20 * dp else 0f
        return geo.targets.sumOf { t ->
            val l = max(0f, (12 * dp + half) - t.x)
            val r = max(0f, t.x + half - (screen.width - 12 * dp))
            val top = max(0f, (40 * dp + half) - t.y)
            val b = max(0f, t.y + half + label - (screen.height - 48 * dp))
            (l + r + top + b).toDouble()
        }.toFloat()
    }
}

/**
 * Линия в редакторе — с точками, за которые её можно поправить: плавная кривая через опорные
 * точки (из рисунка пальцем) или ровная дуга от [Arc.a] до [Arc.b] (без изгиба — прямая).
 */
sealed interface EditableLine {
    /** Точки линии на экране, через небольшие промежутки. */
    fun points(): List<Offset>

    /** Точки-ручки, за которые линию можно тянуть. */
    fun handles(): List<Offset>

    fun moveHandle(i: Int, to: Offset): EditableLine

    /** Плавная кривая через опорные точки (сплайн Катмулла — Рома). */
    data class Curve(val keys: List<Offset>) : EditableLine {
        override fun points() = LineShapes.catmullRom(keys)
        override fun handles() = keys
        override fun moveHandle(i: Int, to: Offset) = Curve(keys.toMutableList().also { it[i] = to })

        /** Ровнее: убирает опорную точку, без которой линия меняется меньше всего (концы остаются). */
        fun smoother(): Curve {
            if (keys.size <= 2) return this
            val drop = (1 until keys.size - 1).minBy { LineShapes.distanceToSegment(keys[it], keys[it - 1], keys[it + 1]) }
            return Curve(keys.filterIndexed { i, _ -> i != drop })
        }
    }

    /** Дуга окружности от [a] до [b]; [bend] — насколько середина отходит от прямой, в долях её длины. */
    data class Arc(val a: Offset, val b: Offset, val bend: Float) : EditableLine {
        override fun points() = LineShapes.arc(a, b, bend)
        override fun handles() = listOf(a, LineShapes.arcMiddle(a, b, bend), b)
        override fun moveHandle(i: Int, to: Offset): Arc = when (i) {
            0 -> copy(a = to)
            2 -> copy(b = to)
            else -> copy(bend = LineShapes.bendFor(a, b, to))
        }
    }
}

/** Геометрия линий редактора: упрощение рисунка, сплайн, дуга по трём точкам. */
object LineShapes {
    /** Не больше стольких опорных точек у кривой из рисунка: ровно, но изгибы на месте. */
    private const val MAX_KEYS = 8

    /** Рисунок пальцем → плавная кривая: дрожание руки уходит, изгибы остаются. */
    fun curve(drawn: List<Offset>, dp: Float): EditableLine.Curve {
        var eps = 8 * dp
        var keys = simplify(drawn, eps)
        while (keys.size > MAX_KEYS) {
            eps *= 1.4f
            keys = simplify(drawn, eps)
        }
        return EditableLine.Curve(keys)
    }

    /** Упрощение Рамера — Дугласа — Пекера: точки, без которых ломаная отходит не дальше [eps]. */
    fun simplify(points: List<Offset>, eps: Float): List<Offset> {
        if (points.size < 3) return points
        var worst = 0
        var dist = 0f
        for (i in 1 until points.size - 1) {
            val d = distanceToSegment(points[i], points.first(), points.last())
            if (d > dist) {
                dist = d
                worst = i
            }
        }
        if (dist <= eps) return listOf(points.first(), points.last())
        return simplify(points.subList(0, worst + 1), eps).dropLast(1) + simplify(points.subList(worst, points.size), eps)
    }

    fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 == 0f) return (p - a).getDistance()
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
        return (p - (a + ab * t)).getDistance()
    }

    /** Центростремительный сплайн Катмулла — Рома через [keys]: без петель и выбросов на крутых поворотах. */
    fun catmullRom(keys: List<Offset>, perSegment: Int = 16): List<Offset> {
        if (keys.size < 3) return keys
        val p = listOf(keys[0] * 2f - keys[1]) + keys + listOf(keys.last() * 2f - keys[keys.size - 2])
        val out = ArrayList<Offset>()
        for (i in 1 until p.size - 2) {
            val (p0, p1, p2, p3) = listOf(p[i - 1], p[i], p[i + 1], p[i + 2])
            fun knot(t: Float, a: Offset, b: Offset) = t + max(kotlin.math.sqrt((b - a).getDistance()), 1e-3f)
            val t0 = 0f
            val t1 = knot(t0, p0, p1)
            val t2 = knot(t1, p1, p2)
            val t3 = knot(t2, p2, p3)
            for (k in 0 until perSegment) {
                val t = t1 + (t2 - t1) * k / perSegment
                val a1 = p0 * ((t1 - t) / (t1 - t0)) + p1 * ((t - t0) / (t1 - t0))
                val a2 = p1 * ((t2 - t) / (t2 - t1)) + p2 * ((t - t1) / (t2 - t1))
                val a3 = p2 * ((t3 - t) / (t3 - t2)) + p3 * ((t - t2) / (t3 - t2))
                val b1 = a1 * ((t2 - t) / (t2 - t0)) + a2 * ((t - t0) / (t2 - t0))
                val b2 = a2 * ((t3 - t) / (t3 - t1)) + a3 * ((t - t1) / (t3 - t1))
                out += b1 * ((t2 - t) / (t2 - t1)) + b2 * ((t - t1) / (t2 - t1))
            }
        }
        out += keys.last()
        return out
    }

    /** Нормаль к отрезку a→b (единичная) и его длина. */
    private fun normal(a: Offset, b: Offset): Pair<Offset, Float> {
        val c = b - a
        val len = c.getDistance()
        return (if (len > 0f) Offset(-c.y / len, c.x / len) else Offset.Zero) to len
    }

    fun arcMiddle(a: Offset, b: Offset, bend: Float): Offset {
        val (n, len) = normal(a, b)
        return (a + b) / 2f + n * (bend * len)
    }

    /** Изгиб, при котором середина дуги — в точке [to] (насколько она отходит от прямой). */
    fun bendFor(a: Offset, b: Offset, to: Offset): Float {
        val (n, len) = normal(a, b)
        if (len <= 0f) return 0f
        val d = to - (a + b) / 2f
        return ((d.x * n.x + d.y * n.y) / len).coerceIn(-0.9f, 0.9f)
    }

    /** Дуга окружности через a, середину с изгибом [bend] и b; почти без изгиба — прямая. */
    fun arc(a: Offset, b: Offset, bend: Float, n: Int = 48): List<Offset> {
        val (nrm, len) = normal(a, b)
        if (len <= 0f || kotlin.math.abs(bend) < 0.01f) return List(n) { a + (b - a) * (it / (n - 1f)) }
        val s = bend * len
        val half = len / 2
        val d = (s * s - half * half) / (2 * s)
        val c = (a + b) / 2f + nrm * d
        val r = kotlin.math.abs(s - d)
        val m = (a + b) / 2f + nrm * s
        fun angle(p: Offset) = kotlin.math.atan2(p.y - c.y, p.x - c.x)
        val two = (2 * PI).toFloat()
        fun wrap(x: Float) = ((x % two) + two) % two
        val ta = angle(a)
        val ccw = wrap(angle(b) - ta)
        val sweep = if (wrap(angle(m) - ta) < ccw) ccw else ccw - two
        return List(n) { k ->
            val t = ta + sweep * k / (n - 1f)
            c + Offset(r * cos(t), r * sin(t))
        }
    }
}
