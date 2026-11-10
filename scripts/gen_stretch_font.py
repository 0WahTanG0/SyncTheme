#!/usr/bin/env python3
"""«Растянутый» шрифт цифр: высокие узкие цифры одной тонкой линией, как часы на экране
блокировки с растянутым шрифтом. Шрифт свой: каждая цифра задана осевой линией из отрезков и дуг,
линия обводится пером постоянной толщины (skia-pathops) и обрезается по высоте цифр — концы штрихов
получаются ровными. Кладёт stretch.ttf в модуль :faces и в шаблон циферблата; метрики потом
записывает scripts/gen_fonts.py.

Запуск: python3 scripts/gen_stretch_font.py [--preview out.png]  (нужны fonttools и skia-pathops)
"""
import io
import os
import sys

import pathops
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = [os.path.join(ROOT, "faces", "src", "main", "res", "font", "stretch.ttf"),
       os.path.join(ROOT, "watchface", "src", "main", "res", "font", "stretch.ttf")]

UPM = 1000
H = 1000          # высота цифр (в долях кегля — 1.0)
W = 270           # ширина цифры по внешним краям линий
S = 18            # толщина линии
SIDE = 46         # поля слева и справа: цифры моноширинные, время не «прыгает»
ADV = W + 2 * SIDE
COLON_ADV = 150
DOT = 21          # радиус точек двоеточия
K = 0.5523        # дуга четверти эллипса кубической кривой

L, R, B, T = S / 2, W - S / 2, S / 2, H - S / 2
CX = W / 2
RX = (R - L) / 2


class Line:
    """Осевая линия цифры: отрезки и четверти эллипсов, каждая следующая часть — от конца прежней."""

    def __init__(self, x, y):
        self.p = pathops.Path()
        self.p.moveTo(x, y)
        self.x, self.y = x, y

    def to(self, x, y):
        self.p.lineTo(x, y)
        self.x, self.y = x, y
        return self

    def arc(self, x, y, vertical_first):
        """Четверть эллипса до (x, y); vertical_first — сначала идёт вертикально (касательная по y)."""
        x0, y0 = self.x, self.y
        if vertical_first:
            c1 = (x0, y0 + K * (y - y0))
            c2 = (x - K * (x - x0), y)
        else:
            c1 = (x0 + K * (x - x0), y0)
            c2 = (x, y - K * (y - y0))
        self.p.cubicTo(*c1, *c2, x, y)
        self.x, self.y = x, y
        return self

    def curve(self, c1, c2, x, y):
        self.p.cubicTo(*c1, *c2, x, y)
        self.x, self.y = x, y
        return self


def stadium(y0, y1, ry, l=L, r=R):
    """Замкнутый овал со прямыми боками: нижняя дуга, правый бок, верхняя дуга, левый бок."""
    cx = (l + r) / 2
    s = Line(l, y0 + ry)
    s.arc(cx, y0, True).arc(r, y0 + ry, False).to(r, y1 - ry).arc(cx, y1, True).arc(l, y1 - ry, False).to(l, y0 + ry)
    s.p.close()
    return s


def glyph_lines(ch):
    ry = 1.5 * RX                 # дуги высоких овалов
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
        ym = 0.555 * H
        ryt = 0.155 * H
        ryb = 0.19 * H
        top = Line(L, T - ryt * 0.95).arc(CX, T, True).arc(R, T - ryt, False).to(R, ym + 0.11 * H).arc(CX - 0.06 * W, ym, True).to(0.28 * W, ym)
        bottom = Line(0.28 * W, ym).to(CX - 0.06 * W, ym).arc(R, ym - ryb, False).to(R, B + ryb).arc(CX, B, True).arc(L, B + ryb * 0.95, False)
        return [top, bottom]
    if ch == "4":
        sx = 0.74 * W
        yc = 0.30 * H
        cross = Line(W + S, yc).to(L, yc).to(sx, H + S)
        stem = Line(sx, H + S).to(sx, -S)
        return [cross, stem]
    if ch == "5":
        yb = 0.60 * H                # верх чаши
        ry = 0.19 * H
        # Одной линией: стык черты и чаши — острый угол, без зазубрины.
        s = Line(W + S, T).to(L, T).to(L, yb - 0.035 * H).curve((L + 0.22 * W, yb), (CX - 0.12 * W, yb), CX, yb)
        s.arc(R, yb - ry, False).to(R, B + ry).arc(CX, B, True).arc(L, B + ry * 0.92, False)
        return [s]
    if ch == "6":
        yb = 0.60 * H
        ryb = 0.20 * H
        s = Line(R, T - 0.13 * H).arc(CX, T, True).arc(L, T - ry, False).to(L, B + ryb).arc(CX, B, True).arc(R, B + ryb, False)
        s.to(R, yb - ryb).arc(CX, yb, True).arc(L, yb - ryb, False)
        return [s]
    if ch == "9":
        # «6», повёрнутая на пол-оборота.
        return [_rotate(line) for line in glyph_lines("6")]
    if ch == "7":
        return [Line(-S, T).to(R, T).curve((R - 0.02 * W, 0.62 * H), (0.36 * W, 0.30 * H), 0.30 * W, -S)]
    if ch == "8":
        ym = 0.545 * H
        upper = stadium(ym, T, 0.17 * H, L + 0.035 * W, R - 0.035 * W)
        # Петли касаются друг друга в одной точке — в талии линия той же толщины.
        lower = stadium(B, ym, 0.20 * H)
        return [upper, lower]
    raise KeyError(ch)


def _rotate(line):
    p = pathops.Path()
    pen = p.getPen()
    line.p.draw(_RotPen(pen))
    out = Line(0, 0)
    out.p = p
    return out


class _RotPen:
    """Поворот на 180° вокруг центра цифры."""

    def __init__(self, pen):
        self.pen = pen

    def _t(self, pts):
        return [(W - x, H - y) for x, y in pts]

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


def outline(lines, clip=True):
    """Обводит осевые линии пером S, сливает в одну фигуру и обрезает по высоте цифр."""
    shape = pathops.Path()
    for line in lines:
        stroked = pathops.Path()
        stroked.addPath(line.p)
        stroked.stroke(S, pathops.LineCap.BUTT_CAP, pathops.LineJoin.MITER_JOIN, 4)
        shape = pathops.op(shape, stroked, pathops.PathOp.UNION)
    if clip:
        box = pathops.Path()
        box.moveTo(-S * 2, 0)
        box.lineTo(W + S * 2, 0)
        box.lineTo(W + S * 2, H)
        box.lineTo(-S * 2, H)
        box.close()
        shape = pathops.op(shape, box, pathops.PathOp.INTERSECTION)
        # По ширине — тоже в рамку цифры, чтобы концы штрихов «2», «4», «5» и «7» были срезаны ровно.
        side = pathops.Path()
        side.moveTo(0, -S * 2)
        side.lineTo(W, -S * 2)
        side.lineTo(W, H + S * 2)
        side.lineTo(0, H + S * 2)
        side.close()
        shape = pathops.op(shape, side, pathops.PathOp.INTERSECTION)
    return shape


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


def tt_glyph(path, dx):
    """Фигура skia → глиф TrueType (квадратичные кривые), со сдвигом на поле dx."""
    pen = TTGlyphPen(None)
    moved = path.transform(1, 0, 0, 1, dx, 0)
    moved.draw(Cu2QuPen(pen, 1.0, reverse_direction=True))
    return pen.glyph()


def build():
    names = [".notdef", "space"] + ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"] + ["colon"]
    glyphs, metrics = {}, {}
    empty = TTGlyphPen(None).glyph()
    # .notdef — пустой прямоугольник: внешний контур и вырез внутри.
    nd = pathops.Path()
    for (x0, y0, x1, y1), cw in [((40, 0, 260, 700), True), ((60, 20, 240, 680), False)]:
        pts = [(x0, y0), (x0, y1), (x1, y1), (x1, y0)] if cw else [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
        nd.moveTo(*pts[0])
        for pt in pts[1:]:
            nd.lineTo(*pt)
        nd.close()
    glyphs[".notdef"] = tt_glyph(nd, 0)
    metrics[".notdef"] = (300, 40)
    glyphs["space"] = empty
    metrics["space"] = (ADV // 2, 0)
    for ch, name in zip("0123456789", names[2:12]):
        shape = outline(glyph_lines(ch))
        glyphs[name] = tt_glyph(shape, SIDE)
        metrics[name] = (ADV, SIDE)
    colon = pathops.op(circle(COLON_ADV / 2, 0.34 * H, DOT), circle(COLON_ADV / 2, 0.66 * H, DOT), pathops.PathOp.UNION)
    glyphs["colon"] = tt_glyph(colon, 0)
    metrics["colon"] = (COLON_ADV, int(COLON_ADV / 2 - DOT))
    # Точный левый отступ = xMin глифа (так требует TrueType).
    for name, g in glyphs.items():
        if hasattr(g, "xMin") and g.numberOfContours:
            metrics[name] = (metrics[name][0], g.xMin)

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(names)
    cmap = {ord(" "): "space", ord(":"): "colon"}
    cmap.update({ord(c): n for c, n in zip("0123456789", names[2:12])})
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    ascent, descent = 1060, 200
    fb.setupHorizontalHeader(ascent=ascent, descent=-descent)
    fb.setupNameTable({"familyName": "SyncTheme Stretch", "styleName": "Thin"})
    fb.setupOS2(sTypoAscender=ascent, sTypoDescender=-descent, sTypoLineGap=0, usWinAscent=ascent, usWinDescent=descent,
                usWeightClass=100, sCapHeight=H, sxHeight=int(H * 0.7))
    fb.setupPost()
    buf = io.BytesIO()
    fb.save(buf)
    return buf.getvalue()


def preview(data, path):
    from PIL import Image, ImageDraw, ImageFont
    font = ImageFont.truetype(io.BytesIO(data), 420)
    small = ImageFont.truetype(io.BytesIO(data), 120)
    im = Image.new("RGB", (1300, 1240), (112, 132, 158))
    d = ImageDraw.Draw(im)
    d.text((80, 40), "15:46", font=font, fill=(18, 22, 30))
    d.text((80, 560), "0123456789", font=small, fill=(18, 22, 30))
    d.text((80, 760), "10:08 23:59", font=small, fill=(240, 240, 240))
    d.text((80, 960), "07:21", font=ImageFont.truetype(io.BytesIO(data), 200), fill=(18, 22, 30))
    im.save(path)


if __name__ == "__main__":
    data = build()
    if "--preview" in sys.argv:
        preview(data, sys.argv[sys.argv.index("--preview") + 1])
    else:
        for p in OUT:
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "wb") as f:
                f.write(data)
        print("stretch.ttf:", len(data), "bytes")
