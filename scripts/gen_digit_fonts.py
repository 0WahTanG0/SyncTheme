#!/usr/bin/env python3
"""Шрифты цифр SyncTheme — свои, нарисованные кодом. Каждая цифра задана осевой линией (отрезки и
дуги), линия обводится пером постоянной толщины (skia-pathops), обрезается по рамке цифры и
сохраняется в TrueType. Так у каждого шрифта свой характер, а метрики точные и одинаковые для
виджетов, обоев и циферблата.

  «Растянутый» — высокие узкие цифры одной тонкой линией;
  «Колонна»    — те же высокие цифры, но жирные;
  «Контур»     — скруглённые «техно»-цифры, нарисованные только контуром (полые);
  «Блок»       — очень жирные узкие квадратные цифры с узкими просветами;
  «Техно»      — широкие жирные цифры со срезанными углами и перечёркнутым нулём;
  «Трафарет»   — жирные округлые цифры с разрывами, как по трафарету.

Кладёт <res>.ttf в модуль :faces и в шаблон циферблата, пишет core/DigitFont.kt (метрики)
и лицензию. Запуск: python3 scripts/gen_digit_fonts.py [--preview out.png]
(нужны fonttools и skia-pathops; интернет не нужен).
"""
import io
import math
import os
import sys

import pathops
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
FONT_DIRS = [os.path.join(ROOT, "faces", "src", "main", "res", "font"),
             os.path.join(ROOT, "watchface", "src", "main", "res", "font")]
KT = os.path.join(ROOT, "core", "src", "main", "kotlin", "app", "synctheme", "core", "DigitFont.kt")
LICENSE = os.path.join(ROOT, "mobile", "src", "main", "assets", "licenses", "fonts-digits.txt")

UPM = 1000
H = 1000          # высота цифр у всех шрифтов (в долях кегля — 1.0)
ASCENT, DESCENT = 1060, 200
K = 0.5523        # четверть эллипса кубической кривой
DIGITS = "0123456789"
NAMES = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]


# ---------------------------------------------------------------- осевые линии

class Line:
    """Осевая линия: отрезки и четверти эллипсов, каждая следующая часть — от конца прежней."""

    def __init__(self, x, y):
        self.p = pathops.Path()
        self.p.moveTo(x, y)
        self.x, self.y = x, y

    def to(self, x, y):
        self.p.lineTo(x, y)
        self.x, self.y = x, y
        return self

    def arc(self, x, y, vertical_first):
        """Четверть эллипса до (x, y); vertical_first — сначала идёт вертикально."""
        x0, y0 = self.x, self.y
        if vertical_first:
            c1, c2 = (x0, y0 + K * (y - y0)), (x - K * (x - x0), y)
        else:
            c1, c2 = (x0 + K * (x - x0), y0), (x, y - K * (y - y0))
        self.p.cubicTo(*c1, *c2, x, y)
        self.x, self.y = x, y
        return self

    def curve(self, c1, c2, x, y):
        self.p.cubicTo(*c1, *c2, x, y)
        self.x, self.y = x, y
        return self

    def close(self):
        self.p.close()
        return self


def poly(points, closed=False, corner="square", size=0.0):
    """Ломаная с обработкой углов: square — острые, round — скруглены радиусом size, chamfer — срезаны на size."""
    pts = list(points)
    n = len(pts)

    def toward(a, b, d):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        return (a[0] + dx / length * d, a[1] + dy / length * d), length

    path = Line(*(((pts[0][0] + pts[1][0]) / 2, (pts[0][1] + pts[1][1]) / 2) if closed else pts[0]))
    corners = list(range(1, n)) + [0] if closed else list(range(1, n - 1))
    for i in corners:
        v, prev, nxt = pts[i], pts[i - 1], pts[(i + 1) % n]
        if corner == "square" or size <= 0:
            path.to(*v)
            continue
        _, l1 = toward(v, prev, 0)
        _, l2 = toward(v, nxt, 0)
        s = min(size, l1 / 2, l2 / 2)
        a, _ = toward(v, prev, s)
        b, _ = toward(v, nxt, s)
        path.to(*a)
        if corner == "round":
            path.curve((a[0] + (v[0] - a[0]) * K, a[1] + (v[1] - a[1]) * K), (b[0] + (v[0] - b[0]) * K, b[1] + (v[1] - b[1]) * K), *b)
        else:
            path.to(*b)
    if closed:
        path.close()
    else:
        path.to(*pts[-1])
    return path


def rotated(lines, w):
    """Поворот на 180° вокруг центра цифры (из «6» получается «9»)."""
    out = []
    for line in lines:
        p = pathops.Path()
        line.p.draw(_Rot(p.getPen(), w))
        holder = Line(0, 0)
        holder.p = p
        out.append(holder)
    return out


class _Rot:
    def __init__(self, pen, w):
        self.pen, self.w = pen, w

    def _t(self, pts):
        return [(self.w - x, H - y) for x, y in pts]

    def moveTo(self, p):
        self.pen.moveTo(*self._t([p]))

    def lineTo(self, p):
        self.pen.lineTo(*self._t([p]))

    def curveTo(self, *pts):
        self.pen.curveTo(*self._t(pts))

    def qCurveTo(self, *pts):
        self.pen.qCurveTo(*self._t(pts))

    def closePath(self):
        self.pen.closePath()

    def endPath(self):
        self.pen.endPath()


def rect(x0, y0, x1, y1):
    p = pathops.Path()
    p.moveTo(x0, y0)
    p.lineTo(x1, y0)
    p.lineTo(x1, y1)
    p.lineTo(x0, y1)
    p.close()
    return p


def circle(cx, cy, r):
    p = pathops.Path()
    k = K * r
    p.moveTo(cx + r, cy)
    p.cubicTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    p.cubicTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    p.cubicTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    p.cubicTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    p.close()
    return p


def union(a, b):
    return pathops.op(a, b, pathops.PathOp.UNION)


def stroke(lines, width, join=pathops.LineJoin.MITER_JOIN):
    shape = pathops.Path()
    for line in lines:
        s = pathops.Path()
        s.addPath(line.p)
        s.stroke(width, pathops.LineCap.BUTT_CAP, join, 4)
        s.convertConicsToQuads()  # круглые стыки skia даёт коническими кривыми
        shape = union(shape, s)
    return shape


# ---------------------------------------------------------------- шрифты

class Font:
    """Общее: рамка цифры W × H, поля SIDE, двоеточие."""
    res = title = const = family = ""
    W = 300
    S = 20
    SIDE = 40
    COLON_ADV = 200

    @property
    def adv(self):
        return self.W + 2 * self.SIDE

    def glyph(self, ch):
        raise NotImplementedError

    def colon(self):
        raise NotImplementedError

    def clip(self, shape):
        return pathops.op(shape, rect(0, 0, self.W, H), pathops.PathOp.INTERSECTION)


class Curvy(Font):
    """Высокие округлые цифры: прямые бока и дуги-эллипсы (по образцу «растянутого» шрифта)."""
    DOT = 21
    SLOT = 0          # ширина разрыва «трафарета»; 0 — без разрывов

    def lines(self, ch):
        W, S = self.W, self.S
        L, R, B, T = S / 2, W - S / 2, S / 2, H - S / 2
        CX, RX = W / 2, (W - S) / 2
        ry = min(1.5 * RX, 0.3 * H)

        def stadium(y0, y1, r, l=L, rr=R):
            cx = (l + rr) / 2
            s = Line(l, y0 + r)
            s.arc(cx, y0, True).arc(rr, y0 + r, False).to(rr, y1 - r).arc(cx, y1, True).arc(l, y1 - r, False).to(l, y0 + r)
            return s.close()

        if ch == "0":
            return [stadium(B, T, ry)]
        if ch == "1":
            sx = 0.64 * W
            return [Line(sx - 0.5 * W, T - 0.13 * H).to(sx, T).to(sx, -S)]
        if ch == "2":
            top = Line(L, T - 0.17 * H).arc(CX, T, True).arc(R, T - 0.17 * H, False)
            top.to(R, 0.66 * H).curve((R, 0.47 * H), (L, 0.30 * H), L, B).to(W + S, B)
            return [top]
        if ch == "3":
            ym, ryt, ryb = 0.555 * H, 0.155 * H, 0.19 * H
            top = Line(L, T - ryt * 0.95).arc(CX, T, True).arc(R, T - ryt, False).to(R, ym + 0.11 * H).arc(CX - 0.06 * W, ym, True).to(0.28 * W, ym)
            bottom = Line(0.28 * W, ym).to(CX - 0.06 * W, ym).arc(R, ym - ryb, False).to(R, B + ryb).arc(CX, B, True).arc(L, B + ryb * 0.95, False)
            return [top, bottom]
        if ch == "4":
            sx, yc = 0.74 * W, 0.30 * H
            return [Line(W + S, yc).to(L, yc).to(sx, H + S), Line(sx, H + S).to(sx, -S)]
        if ch == "5":
            yb, r = 0.60 * H, 0.19 * H
            s = Line(W + S, T).to(L, T).to(L, yb - 0.035 * H).curve((L + 0.22 * W, yb), (CX - 0.12 * W, yb), CX, yb)
            s.arc(R, yb - r, False).to(R, B + r).arc(CX, B, True).arc(L, B + r * 0.92, False)
            return [s]
        if ch == "6":
            yb, ryb = 0.60 * H, 0.20 * H
            s = Line(R, T - 0.13 * H).arc(CX, T, True).arc(L, T - ry, False).to(L, B + ryb).arc(CX, B, True).arc(R, B + ryb, False)
            s.to(R, yb - ryb).arc(CX, yb, True).arc(L, yb - ryb, False)
            return [s]
        if ch == "9":
            return rotated(self.lines("6"), W)
        if ch == "7":
            return [Line(-S, T).to(R, T).curve((R - 0.02 * W, 0.62 * H), (0.36 * W, 0.30 * H), 0.30 * W, -S)]
        if ch == "8":
            ym = 0.545 * H
            # Петли касаются в одной точке — в талии линия той же толщины.
            return [stadium(ym, T, 0.17 * H, L + 0.035 * W, R - 0.035 * W), stadium(B, ym, 0.20 * H)]
        raise KeyError(ch)

    def glyph(self, ch):
        shape = self.clip(stroke(self.lines(ch), self.S))
        if self.SLOT:
            # Разрыв по центру (у «1», «4» и «7» — поперёк, на середине высоты).
            g = self.SLOT
            cut = rect(-10, H * 0.5 - g / 2, self.W + 10, H * 0.5 + g / 2) if ch in "147" else rect(self.W / 2 - g / 2, -10, self.W / 2 + g / 2, H + 10)
            shape = pathops.op(shape, cut, pathops.PathOp.DIFFERENCE)
        return shape

    def colon(self):
        c = self.COLON_ADV / 2
        return union(circle(c, 0.34 * H, self.DOT), circle(c, 0.66 * H, self.DOT))


class Stretch(Curvy):
    const, res, title, family = "STRETCH", "stretch", "Растянутый", "Stretch"
    W, S, SIDE, COLON_ADV, DOT = 270, 18, 46, 150, 21


class Column(Curvy):
    const, res, title, family = "COLUMN", "column", "Колонна", "Column"
    W, S, SIDE, COLON_ADV, DOT = 380, 84, 40, 220, 46


class Stencil(Curvy):
    const, res, title, family = "STENCIL", "stencil", "Трафарет", "Stencil"
    W, S, SIDE, COLON_ADV, DOT, SLOT = 520, 128, 40, 250, 56, 38


class Ortho(Font):
    """Цифры из прямых, как у «техно»-шрифтов: углы острые, скруглённые или срезанные."""
    CORNER = "square"
    CORNER_SIZE = 0
    MID = 0.52        # высота средней черты
    INSET = None      # осевая линия от края рамки; по умолчанию — половина пера

    def box(self):
        i = self.S / 2 if self.INSET is None else self.INSET
        return i, self.W - i, i, H - i

    def lines(self, ch):
        W = self.W
        l, r, b, t = self.box()
        m = self.MID * H
        c, z = self.CORNER, self.CORNER_SIZE

        def P(points, closed=False):
            return poly(points, closed, c, z)

        x0, x1 = (0, W) if self.INSET is None else (l, r)
        y0, y1 = (0, H) if self.INSET is None else (b, t)
        if ch == "0":
            return [P([(l, b), (r, b), (r, t), (l, t)], True)] + self.zero_extra(l, r, b, t)
        if ch == "1":
            sx = 0.62 * W
            return [P(self.one_flag(sx, l, t) + [(sx, t), (sx, y0)])]
        if ch == "2":
            return [P([(x0, t), (r, t), (r, m), (l, m), (l, b), (x1, b)])]
        if ch == "3":
            return [P([(x0, t), (r, t), (r, b), (x0, b)]), P([(l + 0.3 * (r - l), m), (r, m)])]
        if ch == "4":
            return [P([(l, y1), (l, m), (r, m)]), P([(r, y1), (r, y0)])]
        if ch == "5":
            return [P([(x1, t), (l, t), (l, m), (r, m), (r, b), (x0, b)])]
        if ch == "6":
            return [P([(x1, t), (l, t), (l, b), (r, b), (r, m), (l, m)])]
        if ch == "7":
            return [P(self.seven(x0, l, r, t, y0))]
        if ch == "8":
            return self.eight(P, l, r, b, t, m)
        if ch == "9":
            return [P([(r, m), (l, m), (l, t), (r, t), (r, b), (x0, b)])]
        raise KeyError(ch)

    def zero_extra(self, l, r, b, t):
        return []

    def eight(self, P, l, r, b, t, m):
        """Две коробки, верхняя чуть уже: талия отличает «8» от «0» с чертой."""
        d = 0.07 * (r - l)
        return [P([(l, b), (r, b), (r, m), (l, m)], True), P([(l + d, m), (r - d, m), (r - d, t), (l + d, t)], True)]

    def one_flag(self, sx, l, t):
        return [(l + 0.08 * self.W, t)]

    def seven(self, x0, l, r, t, y0):
        return [(x0, t), (r, t), (r, y0)]

    def glyph(self, ch):
        return self.clip(stroke(self.lines(ch), self.S))


class Block(Ortho):
    """Очень жирные узкие квадратные цифры: просветы — узкие щели."""
    const, res, title, family = "BLOCK", "block", "Блок", "Block"
    W, S, SIDE, COLON_ADV = 420, 150, 30, 210
    MID = 0.52

    def seven(self, x0, l, r, t, y0):
        return [(x0, t), (r, t), (r, 0.68 * H), (l + 0.28 * (r - l), y0 - self.S)]

    def eight(self, P, l, r, b, t, m):
        # У жирных узких цифр — коробка с перемычкой: две щели против одной у «0».
        return [P([(l, b), (r, b), (r, t), (l, t)], True), P([(l, m), (r, m)])]

    def colon(self):
        c, d = self.COLON_ADV / 2, 66
        return union(rect(c - d, 0.30 * H - d, c + d, 0.30 * H + d), rect(c - d, 0.70 * H - d, c + d, 0.70 * H + d))


class Techno(Ortho):
    """Широкие жирные цифры со срезанными углами и перечёркнутым нулём."""
    const, res, title, family = "TECHNO", "techno", "Техно", "Techno"
    W, S, SIDE, COLON_ADV = 660, 150, 40, 270
    CORNER, CORNER_SIZE = "chamfer", 150
    MID = 0.5

    def zero_extra(self, l, r, b, t):
        w, h = r - l, t - b
        return [Line(l + 0.30 * w, b + 0.30 * h).to(r - 0.30 * w, t - 0.30 * h)]

    def one_flag(self, sx, l, t):
        return [(sx - 0.34 * self.W, t - 0.2 * H)]

    def lines(self, ch):
        lines = super().lines(ch)
        if ch == "0":
            # Черта внутри нуля — тоньше основной линии.
            frame, slash = lines
            self._slash = slash
            return [frame]
        self._slash = None
        return lines

    def glyph(self, ch):
        shape = stroke(self.lines(ch), self.S)
        if self._slash is not None:
            shape = union(shape, stroke([self._slash], self.S * 0.62))
        return self.clip(shape)

    def colon(self):
        c, d, k = self.COLON_ADV / 2, 72, 30

        def octagon(cy):
            return poly([(c - d, cy - d), (c + d, cy - d), (c + d, cy + d), (c - d, cy + d)], True, "chamfer", k).p

        return union(octagon(0.30 * H), octagon(0.70 * H))


class Outline(Ortho):
    """Скруглённые «техно»-цифры, нарисованные только контуром: толстая линия, от которой остаются края."""
    const, res, title, family = "OUTLINE", "outline", "Контур", "Outline"
    W, SIDE, COLON_ADV = 600, 48, 290
    THICK = 150       # толщина «невидимой» линии, контур которой рисуется
    S = 24            # толщина контура
    CORNER, CORNER_SIZE = "round", 150
    MID = 0.53

    @property
    def INSET(self):
        return self.THICK / 2 + self.S / 2

    def one_flag(self, sx, l, t):
        return [(sx - 0.36 * self.W, t - 0.16 * H)]

    def seven(self, x0, l, r, t, y0):
        return [(x0, t), (r, t), (r, 0.72 * H), (l + 0.3 * (r - l), y0)]

    def outline(self, thick):
        edge = pathops.Path()
        edge.addPath(thick)
        edge.stroke(self.S, pathops.LineCap.BUTT_CAP, pathops.LineJoin.ROUND_JOIN, 4)
        edge.convertConicsToQuads()
        return union(pathops.Path(), edge)

    def glyph(self, ch):
        return self.clip(self.outline(stroke(self.lines(ch), self.THICK, pathops.LineJoin.ROUND_JOIN)))

    def colon(self):
        c, d, rr = self.COLON_ADV / 2, 70, 40

        def dot(cy):
            return poly([(c - d, cy - d), (c + d, cy - d), (c + d, cy + d), (c - d, cy + d)], True, "round", rr).p

        return self.outline(union(dot(0.32 * H), dot(0.68 * H)))


FONTS = [Stretch(), Column(), Outline(), Block(), Techno(), Stencil()]


# ---------------------------------------------------------------- TrueType

def tt_glyph(path, dx):
    pen = TTGlyphPen(None)
    path.transform(1, 0, 0, 1, dx, 0).draw(Cu2QuPen(pen, 1.0, reverse_direction=True))
    return pen.glyph()


def build(font):
    names = [".notdef", "space"] + NAMES + ["colon"]
    glyphs, metrics = {}, {}
    nd = pathops.Path()
    for (x0, y0, x1, y1), cw in [((40, 0, 260, 700), True), ((60, 20, 240, 680), False)]:
        pts = [(x0, y0), (x0, y1), (x1, y1), (x1, y0)] if cw else [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
        nd.moveTo(*pts[0])
        for pt in pts[1:]:
            nd.lineTo(*pt)
        nd.close()
    glyphs[".notdef"], metrics[".notdef"] = tt_glyph(nd, 0), (300, 40)
    glyphs["space"], metrics["space"] = TTGlyphPen(None).glyph(), (font.adv // 2, 0)
    shapes = [(name, font.glyph(ch), font.SIDE, font.adv) for ch, name in zip(DIGITS, NAMES)]
    shapes.append(("colon", font.colon(), 0, font.COLON_ADV))
    for name, shape, dx, adv in shapes:
        glyphs[name] = tt_glyph(shape, dx)
        # Левый отступ — от левого края самого рисунка (так требует TrueType).
        metrics[name] = (adv, round(shape.bounds[0] + dx))

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(names)
    cmap = {ord(" "): "space", ord(":"): "colon"}
    cmap.update({ord(c): n for c, n in zip(DIGITS, NAMES)})
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=ASCENT, descent=-DESCENT)
    fb.setupNameTable({"familyName": f"SyncTheme {font.family}", "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=ASCENT, sTypoDescender=-DESCENT, sTypoLineGap=0, usWinAscent=ASCENT, usWinDescent=DESCENT,
                usWeightClass=400, sCapHeight=H, sxHeight=int(H * 0.7))
    fb.setupPost()
    buf = io.BytesIO()
    fb.save(buf)
    return buf.getvalue()


def measure(data):
    """Метрики в долях кегля: строка, верх и низ цифр, ширина цифры и двоеточия."""
    font = TTFont(io.BytesIO(data))
    gs, cmap = font.getGlyphSet(), font.getBestCmap()
    ymin, ymax, adv = 10 ** 9, -10 ** 9, 0
    for ch in DIGITS:
        g = cmap[ord(ch)]
        pen = BoundsPen(gs)
        gs[g].draw(pen)
        ymin, ymax = min(ymin, pen.bounds[1]), max(ymax, pen.bounds[3])
        adv = max(adv, font["hmtx"][g][0])
    return dict(ascent=ASCENT / UPM, descent=DESCENT / UPM, top=ymax / UPM, bottom=ymin / UPM, digit=adv / UPM,
                colon=font["hmtx"][cmap[ord(":")]][0] / UPM)


def write_kotlin(rows):
    kt = ["package app.synctheme.core", "", "import kotlinx.serialization.Serializable", "",
          "// Сгенерировано scripts/gen_digit_fonts.py — не редактировать вручную.", "",
          "/**",
          " * Шрифт цифр часов (виджеты, часы на обоях и циферблат). res — имя ресурса в res/font приложения",
          " * и шаблона циферблата; null — системный тонкий шрифт. Метрики — в долях кегля: ascent/descent строки,",
          " * верх и низ цифр над базовой линией, ширина цифры и двоеточия.",
          " */",
          "@Serializable(with = DigitFontSerializer::class)",
          "enum class DigitFont(",
          "    val res: String?, val title: String,",
          "    val ascent: Float, val descent: Float, val top: Float, val bottom: Float, val digit: Float, val colon: Float,",
          ") {"]
    for font, m in rows:
        kt.append(f'    {font.const}("{font.res}", "{font.title}", {m["ascent"]:.3f}f, {m["descent"]:.3f}f, {m["top"]:.3f}f, '
                  f'{m["bottom"]:.3f}f, {m["digit"]:.3f}f, {m["colon"]:.3f}f),')
    # Системный Roboto Thin: метрики Roboto.
    kt.append('    THIN(null, "Тонкий", 0.928f, 0.244f, 0.711f, -0.010f, 0.562f, 0.240f);')
    kt += ["",
           "    /** Высота строки в долях кегля. */",
           "    val line get() = ascent + descent",
           "",
           "    /** Высота цифр в долях кегля. */",
           "    val digitHeight get() = top - bottom",
           "",
           "    /**",
           "     * Во сколько раз выше обычной рамка для строки времени. Высоким узким цифрам («Растянутый»)",
           "     * ширины хватает с запасом — их ограничивает высота, и в обычной рамке они вышли бы мелкими.",
           "     */",
           "    val tallness get() = if (digitHeight > 2 * digit) 2.1f else 1f",
           "",
           "    /** Ширина текста из цифр и двоеточий в долях кегля. */",
           "    fun width(text: String) = text.sumOf { (if (it == ':') colon else digit).toDouble() }.toFloat()",
           "}", ""]
    with open(KT, "w", encoding="utf-8") as f:
        f.write("\n".join(kt))


def preview(fonts_data, path):
    from PIL import Image, ImageDraw, ImageFont
    rows = len(fonts_data)
    im = Image.new("RGB", (1500, 300 * rows + 40), (24, 26, 32))
    d = ImageDraw.Draw(im)
    for i, (font, data) in enumerate(fonts_data):
        big = ImageFont.truetype(io.BytesIO(data), 230)
        small = ImageFont.truetype(io.BytesIO(data), 110)
        y = 30 + i * 300
        d.text((30, y), "10:37", font=big, fill=(235, 238, 245))
        d.text((640, y + 40), "0123456789", font=small, fill=(120, 230, 160))
        d.text((640, y + 200), font.title, fill=(180, 180, 190))
    im.save(path)


if __name__ == "__main__":
    built = [(font, build(font)) for font in FONTS]
    if "--preview" in sys.argv:
        preview(built, sys.argv[sys.argv.index("--preview") + 1])
        sys.exit(0)
    for font, data in built:
        for d in FONT_DIRS:
            os.makedirs(d, exist_ok=True)
            with open(os.path.join(d, f"{font.res}.ttf"), "wb") as f:
                f.write(data)
    write_kotlin([(font, measure(data)) for font, data in built])
    with open(LICENSE, "w", encoding="utf-8") as f:
        f.write("Шрифты цифр SyncTheme — «" + "», «".join(font.title for font in FONTS) + "» — собственные шрифты приложения,\n"
                "нарисованные кодом (scripts/gen_digit_fonts.py).\n")
    print("fonts:", ", ".join(f"{font.res} {len(data)} B" for font, data in built))
