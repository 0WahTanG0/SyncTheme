#!/usr/bin/env python3
"""Шрифты цифр SyncTheme — свои, нарисованные кодом. Каждая цифра задана осевой линией (отрезки и
дуги), линия обводится пером постоянной толщины (skia-pathops), обрезается по рамке цифры и
сохраняется в TrueType. Так у каждого шрифта свой характер, а метрики точные и одинаковые для
виджетов, обоев и циферблата.

  «Растянутый» — высокие узкие цифры одной тонкой линией;
  «Эхо»       — жирная узкая цифра и два её контура, отставших по диагонали;
  «Неон»      — стеклянная трубка вывески: два края, круглые концы и тёмные промежутки;
  «Глитч»     — цифровой сбой: полосы цифры сдвинуты, строка развёртки уехала дальше всех;
  «Полутон»   — точки печатного растра под 45°: внизу плотные, кверху тают;
  «Штрихкод»  — высокая жирная цифра из вертикальных полос разной ширины;
  «Объём»     — цифра с заштрихованной гранью, уходящей вглубь, как на гравюре.

Кладёт <res>.ttf в модуль :faces и в шаблон циферблата, пишет core/DigitFont.kt (метрики)
и лицензию. Запуск: python3 scripts/gen_digit_fonts.py [--preview out.png]
(нужны fonttools и skia-pathops; интернет не нужен).
"""
import copy
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
# Знаки для чисел остальных виджетов: «75%», «21°», «−3°». Имя глифа, символы, доля ширины цифры.
EXTRA = [("percent", "%", 1.0), ("degree", "°", 0.62), ("minus", "-\u2212", 0.72), ("plus", "+", 1.0)]


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


# ---------------------------------------------------------------- приёмы над фигурами

def moved(shape, dx, dy):
    return shape.transform(1, 0, 0, 1, dx, dy)


def contour(shape, width):
    """Линия толщины width по краю фигуры (наполовину внутри, наполовину снаружи)."""
    band = pathops.Path()
    band.addPath(shape)
    band.stroke(width, pathops.LineCap.BUTT_CAP, pathops.LineJoin.ROUND_JOIN, 4)
    band.convertConicsToQuads()
    return union(pathops.Path(), band)


def dilate(shape, d):
    """Фигура, раздутая на d во все стороны."""
    return union(shape, contour(shape, 2 * d))


def silhouette(shape):
    """Фигура без внутренних просветов: только внешние контуры, залитые."""
    cs = list(shape.contours)
    if not cs:
        return shape
    outer = max(cs, key=lambda c: abs(c.area)).clockwise
    out = pathops.Path()
    for c in cs:
        if c.clockwise == outer:
            out = union(out, c)
    return out


def without_crumbs(shape, size):
    """Без мелких обрывков: контуры, которые меньше size и по ширине, и по высоте, выбрасываются."""
    out = pathops.Path()
    for c in shape.contours:
        x0, y0, x1, y1 = c.bounds
        if max(x1 - x0, y1 - y0) >= size:
            out.addPath(c)
    return out


def minus(a, b):
    return pathops.op(a, b, pathops.PathOp.DIFFERENCE)


def inter(a, b):
    return pathops.op(a, b, pathops.PathOp.INTERSECTION)


def flatten(path, steps=18):
    """Контуры пути ломаными: [(точки, замкнут ли)]."""
    out, pts, last = [], [], None
    for verb, args in path.segments:
        if verb == "moveTo":
            if len(pts) > 1:
                out.append((pts, False))
            pts = [args[0]]
        elif verb == "lineTo":
            pts.append(args[0])
        elif verb == "curveTo":
            (x0, y0), (x1, y1), (x2, y2), (x3, y3) = pts[-1], *args
            for i in range(1, steps + 1):
                t = i / steps
                u = 1 - t
                pts.append((u ** 3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x3,
                            u ** 3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y3))
        elif verb == "qCurveTo":
            for p in args:
                pts.append(p)
        elif verb in ("closePath", "endPath"):
            if len(pts) > 1:
                out.append((pts, verb == "closePath"))
            pts = []
    if len(pts) > 1:
        out.append((pts, False))
    return out


def cut(pts, closed, fractions, gap):
    """Ломаная без промежутков длины gap в долях fractions её длины — куски-ломаные."""
    if closed:
        pts = pts + [pts[0]]
    cum = [0.0]
    for a, b in zip(pts, pts[1:]):
        cum.append(cum[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    total = cum[-1]

    def at(s):
        s = max(0.0, min(total, s))
        for i in range(1, len(cum)):
            if cum[i] >= s:
                t = (s - cum[i - 1]) / max(1e-9, cum[i] - cum[i - 1])
                a, b = pts[i - 1], pts[i]
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        return pts[-1]

    def piece(s0, s1):
        inner = [pts[i] for i in range(len(pts)) if s0 < cum[i] < s1]
        return [at(s0)] + inner + [at(s1)]

    if not fractions:
        return [(pts, closed)]
    if closed:
        # Начало — в первом промежутке: тогда все куски незамкнутые.
        f0 = fractions[0]
        spans = []
        for f in fractions:
            c = ((f - f0) % 1.0) * total
            spans.append((c - gap / 2, c + gap / 2))
        keep, s = [], gap / 2
        for a, b in sorted(spans)[1:]:
            keep.append((s, a))
            s = b
        keep.append((s, total - gap / 2))
        shift = f0 * total
        out = []
        for a, b in keep:
            a, b = a + shift, b + shift
            if b <= total:
                out.append((piece(a, b), False))
            elif a >= total:
                out.append((piece(a - total, b - total), False))
            else:
                out.append((piece(a, total) + piece(0, b - total)[1:], False))
        return out
    keep, s = [], 0.0
    for f in sorted(fractions):
        c = f * total
        keep.append((s, c - gap / 2))
        s = c + gap / 2
    keep.append((s, total))
    return [(piece(a, b), False) for a, b in keep if b - a > 1]


def stroke_polys(polys, width):
    """Ломаные пером с круглыми концами и стыками (как трубка)."""
    shape = pathops.Path()
    for pts, closed in polys:
        p = pathops.Path()
        p.moveTo(*pts[0])
        for q in pts[1:]:
            p.lineTo(*q)
        if closed:
            p.close()
        p.stroke(width, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4)
        p.convertConicsToQuads()
        shape = union(shape, p)
    return shape


def stripes(x0, x1, y0, y1, widths, gap, start=0):
    """Вертикальные полосы разной ширины, как у штрихкода."""
    out, x, i = pathops.Path(), x0, start
    while x < x1:
        w = widths[i % len(widths)]
        out.addPath(rect(x, y0, min(x + w, x1), y1))
        x += w + gap
        i += 1
    return out


# ---------------------------------------------------------------- шрифты

class Font:
    """Общее: рамка цифры W × H, поля SIDE, двоеточие. EXTRA — добавка справа (тени, отражения)."""
    res = title = const = family = ""
    W = 300
    S = 20
    SIDE = 40
    COLON_ADV = 200
    EXTRA = 0

    @property
    def adv(self):
        return self.W + 2 * self.SIDE + self.EXTRA

    @property
    def colon_adv(self):
        return self.COLON_ADV + self.EXTRA

    def glyph(self, ch):
        raise NotImplementedError

    def colon(self):
        raise NotImplementedError

    def clip(self, shape):
        return inter(shape, rect(0, 0, self.W, H))

    def sized(self, ratio):
        """Тот же шрифт в рамке другой ширины — для узких знаков («°», «−»)."""
        if ratio == 1.0:
            return self
        other = copy.copy(self)
        other.W = self.W * ratio
        return other


class Curvy(Font):
    """Высокие округлые цифры: прямые бока и дуги-эллипсы (по образцу «растянутого» шрифта).
    INSET — осевая линия от края рамки, EXT — насколько концы штрихов выходят за рамку (их срезает clip)."""
    DOT = 21

    @property
    def inset(self):
        return self.S / 2

    @property
    def ext(self):
        return self.S

    def lines(self, ch):
        W, I, E = self.W, self.inset, self.ext
        L, R, B, T = I, W - I, I, H - I
        CX, RX = W / 2, (W - 2 * I) / 2
        xlo, xhi, ylo, yhi = -E, W + E, -E, H + E
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
            return [Line(sx - 0.5 * W, T - 0.13 * H).to(sx, T).to(sx, ylo)]
        if ch == "2":
            top = Line(L, T - 0.17 * H).arc(CX, T, True).arc(R, T - 0.17 * H, False)
            top.to(R, 0.66 * H).curve((R, 0.47 * H), (L, 0.30 * H), L, B).to(xhi, B)
            return [top]
        if ch == "3":
            ym, ryt, ryb = 0.555 * H, 0.155 * H, 0.19 * H
            top = Line(L, T - ryt * 0.95).arc(CX, T, True).arc(R, T - ryt, False).to(R, ym + 0.11 * H).arc(CX - 0.06 * W, ym, True).to(0.28 * W, ym)
            bottom = Line(0.28 * W, ym).to(CX - 0.06 * W, ym).arc(R, ym - ryb, False).to(R, B + ryb).arc(CX, B, True).arc(L, B + ryb * 0.95, False)
            return [top, bottom]
        if ch == "4":
            sx, yc = 0.74 * W, 0.30 * H
            return [Line(xhi, yc).to(L, yc).to(sx, yhi), Line(sx, yhi).to(sx, ylo)]
        if ch == "5":
            yb, r = 0.60 * H, 0.19 * H
            s = Line(xhi, T).to(L, T).to(L, yb - 0.035 * H).curve((L + 0.22 * W, yb), (CX - 0.12 * W, yb), CX, yb)
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
            return [Line(xlo, T).to(R, T).curve((R - 0.02 * W, 0.62 * H), (0.36 * W, 0.30 * H), 0.30 * W, ylo)]
        if ch == "8":
            ym = 0.545 * H
            # Петли касаются в одной точке — в талии линия той же толщины.
            return [stadium(ym, T, 0.17 * H, L + 0.035 * W, R - 0.035 * W), stadium(B, ym, 0.20 * H)]
        if ch == "-":
            return [Line(L, 0.47 * H).to(R, 0.47 * H)]
        if ch == "+":
            arm = min(0.36 * W, 0.2 * H)
            return [Line(L + 0.04 * W, 0.47 * H).to(R - 0.04 * W, 0.47 * H), Line(CX, 0.47 * H - arm).to(CX, 0.47 * H + arm)]
        if ch == "°":
            return [stadium(0.66 * H, T, min(RX, 0.1 * H))]
        if ch == "%":
            rw = 0.40 * W
            r = min(rw / 2 * 1.4, 0.16 * H)
            return [stadium(0.6 * H, T, r, L, L + rw), stadium(B, 0.4 * H, r, R - rw, R), Line(R, yhi).to(L, ylo)]
        raise KeyError(ch)

    def base(self, ch):
        # У маленьких колец «%» и «°» в жирных шрифтах линия тоньше — иначе просвет внутри заплывёт.
        width = self.S if ch not in "%°" else min(self.S, 0.3 * (0.4 * self.W if ch == "%" else self.W))
        return self.clip(stroke(self.lines(ch), width))

    def dots(self):
        c = self.COLON_ADV / 2
        return union(circle(c, 0.34 * H, self.DOT), circle(c, 0.66 * H, self.DOT))

    def effect(self, shape, w, key):
        return shape

    def glyph(self, ch):
        return self.effect(self.base(ch), self.W, ch)

    def colon(self):
        return self.effect(self.dots(), self.COLON_ADV, ":")


class Stretch(Curvy):
    """Высокие узкие цифры одной тонкой линией."""
    const, res, title, family = "STRETCH", "stretch", "Растянутый", "Stretch"
    W, S, SIDE, COLON_ADV, DOT = 270, 18, 46, 150, 21


class Echo(Curvy):
    """Жирная узкая цифра и два её контура, отставших по диагонали, — эхо, движение."""
    const, res, title, family = "ECHO", "echo", "Эхо", "Echo"
    W, S, SIDE, COLON_ADV, DOT = 360, 112, 26, 190, 50
    STEP, LINE, GAP, COUNT = 44, 12, 13, 2
    EXTRA = STEP * COUNT

    def effect(self, shape, w, key):
        # Отражения — по внешнему силуэту: без линий в просветах и обрывков в изгибах.
        body = silhouette(shape)
        out, hidden = shape, body
        for k in range(1, self.COUNT + 1):
            ghost = moved(body, k * self.STEP, -k * self.STEP)
            echo = without_crumbs(minus(contour(ghost, self.LINE), dilate(hidden, self.GAP)), 5 * self.LINE)
            out = union(out, echo)
            hidden = union(hidden, ghost)
        return out


class Neon(Curvy):
    """Неоновая вывеска: стеклянная трубка (два края), круглые концы и тёмные промежутки, где трубка «ныряет»."""
    const, res, title, family = "NEON", "neon", "Неон", "Neon"
    W, SIDE, COLON_ADV = 480, 36, 220
    TUBE, LINE = 78, 15
    GAP = TUBE + 46   # круглые концы трубки съедают по половине толщины — тёмный промежуток остаётся ~46
    S = TUBE
    # Где у каждой цифры промежуток: доли длины линии (по номеру линии в цифре).
    GAPS = {
        "0": {0: [0.08, 0.58]}, "1": {0: [0.55]}, "2": {0: [0.5]}, "3": {0: [0.45], 1: [0.62]},
        "4": {0: [0.55], 1: [0.78]}, "5": {0: [0.3, 0.8]}, "6": {0: [0.22, 0.78]}, "7": {0: [0.62]},
        "8": {0: [0.5], 1: [0.62]}, "9": {0: [0.22, 0.78]},
    }

    @property
    def inset(self):
        return self.TUBE / 2 + self.LINE / 2

    @property
    def ext(self):
        return -self.inset

    def glyph(self, ch):
        pieces = []
        for i, line in enumerate(self.lines(ch)):
            for pts, closed in flatten(line.p):
                pieces += cut(pts, closed, self.GAPS.get(ch, {}).get(i, []), self.GAP)
        return contour(stroke_polys(pieces, self.TUBE), self.LINE)

    def colon(self):
        c, r = self.COLON_ADV / 2, self.TUBE * 0.62
        return contour(union(circle(c, 0.34 * H, r), circle(c, 0.66 * H, r)), self.LINE)


class Glitch(Curvy):
    """Цифровой сбой: цифра разрезана на полосы, полосы сдвинуты, тонкая «строка развёртки» уехала дальше всех."""
    const, res, title, family = "GLITCH", "glitch", "Глитч", "Glitch"
    W, S, SIDE, COLON_ADV, DOT = 440, 122, 64, 230, 54
    CUTS = [(0.31, 0.345), (0.585, 0.62)]
    SCAN = (0.79, 0.82)
    OFFS = {
        "0": (0.00, 0.07, -0.04, 0.16), "1": (0.00, -0.06, 0.05, -0.15), "2": (0.05, -0.04, 0.00, 0.14),
        "3": (-0.04, 0.06, 0.00, -0.16), "4": (0.00, 0.05, -0.06, 0.15), "5": (0.06, 0.00, -0.05, -0.14),
        "6": (-0.05, 0.04, 0.02, 0.16), "7": (0.04, -0.06, 0.00, -0.15), "8": (0.00, -0.05, 0.06, 0.14),
        "9": (-0.06, 0.03, 0.00, -0.16), ":": (0.00, 0.12, 0.00, 0.00),
    }

    def effect(self, shape, w, key):
        if key == ":":
            return shape  # двоеточие целое — по нему читается время
        lo, mid, hi, scan = self.OFFS.get(key, (0.0, 0.06, -0.04, 0.14))
        (a0, a1), (b0, b1) = self.CUTS
        s0, s1 = self.SCAN
        big = 3 * w
        bands = [(-H, a0 * H, lo), (a1 * H, b0 * H, mid), (b1 * H, s0 * H, hi), (s1 * H, 2 * H, hi)]
        out = pathops.Path()
        for y0, y1, off in bands:
            out = union(out, moved(inter(shape, rect(-big, y0, big, y1)), off * w, 0))
        if scan:
            out = union(out, moved(inter(shape, rect(-big, s0 * H, big, s1 * H)), scan * w, 0))
        else:
            out = union(out, moved(inter(shape, rect(-big, s0 * H, big, s1 * H)), hi * w, 0))
        return out


class Halftone(Curvy):
    """Полутон, как в печати: цифра из точек, внизу крупных и плотных, кверху тающих."""
    const, res, title, family = "HALFTONE", "halftone", "Полутон", "Halftone"
    W, S, SIDE, COLON_ADV, DOT = 540, 176, 28, 230, 74
    STEP = 58         # шаг растра; растр повёрнут на 45°, как в печати

    def effect(self, shape, w, key):
        out = pathops.Path()
        dy = self.STEP / 2
        pitch = self.STEP / math.sqrt(2)          # расстояние между соседними точками
        rows = int(H // dy) + 1
        for iy in range(rows):
            y = dy / 2 + iy * dy
            r = pitch * (0.49 - 0.33 * y / H)      # внизу точки почти сливаются, кверху тают
            x = (self.STEP / 2 if iy % 2 else 0) - self.STEP
            while x < w + self.STEP:
                if shape.contains((x, y)):
                    out.addPath(circle(x, y, r))
                x += self.STEP
        return out


class Barcode(Curvy):
    """Штрихкод: высокая жирная цифра из вертикальных полос разной ширины."""
    const, res, title, family = "BARCODE", "barcode", "Штрихкод", "Barcode"
    W, S, SIDE, COLON_ADV, DOT = 400, 150, 30, 190, 52
    BARS = [30, 12, 44, 18, 12, 36, 22, 14, 50, 16, 26, 12, 40, 20]
    GAP = 11

    def effect(self, shape, w, key):
        if key == ":":
            return shape
        start = 3 * int(key) if key.isdigit() else ord(key) % len(self.BARS)
        return inter(shape, stripes(0, w, -50, H + 50, self.BARS, self.GAP, start=start))


class Volume(Curvy):
    """Объём: цифра с гранью, уходящей вглубь, — грань заштрихована, как на гравюре."""
    const, res, title, family = "VOLUME", "volume", "Объём", "Volume"
    W, S, SIDE, COLON_ADV, DOT = 450, 110, 28, 200, 50
    DEPTH, GAP, HATCH, PERIOD = 74, 11, 9, 23
    EXTRA = DEPTH

    def effect(self, shape, w, key):
        side = pathops.Path()
        for k in range(1, 13):
            side = union(side, moved(shape, self.DEPTH * k / 12, -self.DEPTH * k / 12))
        side = minus(side, dilate(shape, self.GAP))
        hatch = pathops.Path()
        for c in range(-2 * H, 2 * H, self.PERIOD):
            line = Line(c - 1500, 1500).to(c + 1500, -1500)
            hatch.addPath(stroke([line], self.HATCH))
        return union(shape, inter(side, hatch))


FONTS = [Stretch(), Echo(), Neon(), Glitch(), Halftone(), Barcode(), Volume()]


# ---------------------------------------------------------------- TrueType

def tt_glyph(path, dx):
    pen = TTGlyphPen(None)
    path.transform(1, 0, 0, 1, dx, 0).draw(Cu2QuPen(pen, 1.0, reverse_direction=True))
    return pen.glyph()


def build(font):
    names = [".notdef", "space"] + NAMES + ["colon"] + [e[0] for e in EXTRA]
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
    shapes.append(("colon", font.colon(), 0, font.colon_adv))
    for name, chars, ratio in EXTRA:
        g = font.sized(ratio)
        shapes.append((name, g.glyph(chars[0]), g.SIDE, g.adv))
    for name, shape, dx, adv in shapes:
        glyphs[name] = tt_glyph(shape, dx)
        # Левый отступ — от левого края самого рисунка (так требует TrueType).
        metrics[name] = (adv, round(shape.bounds[0] + dx))

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(names)
    cmap = {ord(" "): "space", ord(":"): "colon"}
    cmap.update({ord(c): n for c, n in zip(DIGITS, NAMES)})
    cmap.update({ord(c): name for name, chars, _ in EXTRA for c in chars})
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
        m = {k: round(v, 3) + 0.0 for k, v in m.items()}  # без «-0.000f»
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
