#!/usr/bin/env python3
"""
Рисунки стилей «Лёд» и «Сияние»: подложки виджетов и капсулы подписей — растягиваемые картинки
(9-patch) в drawable-xxhdpi. Простыми фигурами (shape) не нарисовать то, что делает эти стили:
размытые тени и свечение, толстую объёмную кромку стекла, толщину панели, сквозь которую светит свет.

«Лёд» — прозрачное акриловое стекло: чуть матовое, толстая светлая кромка, блик сверху, тонкая тёмная
грань снизу (толщина) и мягкая тень. Капсула подписи — голубое стекло.
«Сияние» — тёмная дымчатая панель с видимой толщиной снизу: сквозь неё пробивается фиолетовый свет,
в левом нижнем углу — тёплое оранжевое свечение. Капсула подписи — светящаяся, фиолетовый в розовый.

Запуск: python3 scripts/gen_widget_art.py (разметки к ним пишет scripts/gen_widgets.py).
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "mobile", "src", "main", "res", "drawable-xxhdpi")
S = 3  # xxhdpi: 1 dp = 3 px


class Canvas:
    """Слои с обычным наложением «поверх» (прямая альфа)."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3), np.float32)
        self.a = np.zeros((h, w), np.float32)

    def over(self, color, alpha):
        alpha = np.clip(alpha, 0, 1).astype(np.float32)
        col = np.broadcast_to(np.asarray(color, np.float32), (self.h, self.w, 3))
        out_a = alpha + self.a * (1 - alpha)
        safe = np.where(out_a > 1e-6, out_a, 1)
        self.rgb = (col * alpha[..., None] + self.rgb * (self.a * (1 - alpha))[..., None]) / safe[..., None]
        self.a = out_a

    def save9(self, name, stretch_x, stretch_y, pad_x=None, pad_y=None):
        """9-patch: чёрные метки сверху и слева — что растягивать, снизу и справа — поля для текста."""
        img = np.zeros((self.h + 2, self.w + 2, 4), np.uint8)
        img[1:-1, 1:-1, :3] = np.clip(self.rgb, 0, 255).astype(np.uint8)
        img[1:-1, 1:-1, 3] = np.clip(self.a * 255, 0, 255).astype(np.uint8)
        black = (0, 0, 0, 255)
        img[0, 1 + stretch_x[0]:1 + stretch_x[1]] = black
        img[1 + stretch_y[0]:1 + stretch_y[1], 0] = black
        if pad_x:
            img[self.h + 1, 1 + pad_x[0]:1 + pad_x[1]] = black
        if pad_y:
            img[1 + pad_y[0]:1 + pad_y[1], self.w + 1] = black
        os.makedirs(OUT, exist_ok=True)
        Image.fromarray(img, "RGBA").save(os.path.join(OUT, f"{name}.9.png"), optimize=True)


def rr(w, h, box, r):
    """Маска скруглённого прямоугольника 0..1; box — (x0, y0, x1, y1) включительно."""
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=max(r, 0), fill=255)
    return np.asarray(m, np.float32) / 255


def inset(box, d):
    return box[0] + d, box[1] + d, box[2] - d, box[3] - d


def shift(box, dx, dy):
    return box[0] + dx, box[1] + dy, box[2] + dx, box[3] + dy


def blur(a, radius):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(radius)), np.float32) / 255


def vgrad(w, h, y0, y1, v0, v1):
    t = np.clip((np.arange(h, dtype=np.float32) - y0) / max(y1 - y0, 1), 0, 1)
    return np.repeat((v0 + (v1 - v0) * t)[:, None], w, axis=1)


def hgrad(w, h, x0, x1, v0, v1):
    t = np.clip((np.arange(w, dtype=np.float32) - x0) / max(x1 - x0, 1), 0, 1)
    return np.repeat((v0 + (v1 - v0) * t)[None, :], h, axis=0)


def hcolors(w, h, x0, x1, stops):
    """Горизонтальный градиент цвета по точкам (доля, (r, g, b))."""
    t = np.clip((np.arange(w, dtype=np.float32) - x0) / max(x1 - x0, 1), 0, 1)
    pos = [p for p, _ in stops]
    row = np.stack([np.interp(t, pos, [c[i] for _, c in stops]) for i in range(3)], axis=-1)
    return np.repeat(row[None, :, :], h, axis=0)


def radial(w, h, cx, cy, radius):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / radius
    return np.clip(1 - d, 0, 1) ** 1.6


# ---------------------------------------------------------------- «Лёд»
#
# Стекло считается как толстая линза со скруглённой кромкой: для каждой точки известно, насколько она
# далеко от края и куда «смотрит» кромка (расстояние до скруглённого прямоугольника и его градиент).
# Свет падает сверху: верх кромки даёт яркий блик и под ним мягкую тень, низ — светлый отсвет света,
# прошедшего сквозь толщину, и тёмную грань. Середина почти прозрачная — как на образце.


def sdf(w, h, box, r):
    """Расстояние до края скруглённого прямоугольника (внутри отрицательное) и единичная нормаль наружу."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    hx, hy = (box[2] - box[0]) / 2 - r, (box[3] - box[1]) / 2 - r
    px, py = x - cx, y - cy
    qx, qy = np.abs(px) - hx, np.abs(py) - hy
    ox, oy = np.maximum(qx, 0), np.maximum(qy, 0)
    outside = np.sqrt(ox ** 2 + oy ** 2)
    d = outside + np.minimum(np.maximum(qx, qy), 0) - r
    # Нормаль: в скруглении — от центра дуги, на прямых участках — перпендикулярно стороне.
    corner = (qx > 0) & (qy > 0)
    nx = np.where(corner, ox / np.maximum(outside, 1e-6), np.where(qx > qy, 1.0, 0.0)) * np.sign(px)
    ny = np.where(corner, oy / np.maximum(outside, 1e-6), np.where(qx > qy, 0.0, 1.0)) * np.sign(py)
    return d, nx, ny


def smooth(x0, x1, v):
    t = np.clip((v - x0) / (x1 - x0), 0, 1)
    return t * t * (3 - 2 * t)


def glass(c, box, r, bevel, tint, body_alpha, rim_scale=1.0, shadow=(0.24, 5, 8), depth_tint=0.0):
    """Толстое стекло: tint — цвет стекла, body_alpha — плотность середины, bevel — ширина кромки, px."""
    w, h = c.w, c.h
    d, nx, ny = sdf(w, h, box, r)
    inside = smooth(0.8, -0.8, d)  # мягкий край маски
    t = np.maximum(-d, 0)  # глубина от края
    lx, ly = -0.32, -0.95  # к источнику света: сверху, чуть слева
    facing = nx * lx + ny * ly  # +1 — кромка смотрит на свет (верх), −1 — от света (низ)
    up, down = np.clip(facing, 0, 1), np.clip(-facing, 0, 1)
    raw = t / bevel
    u = np.clip(raw, 0, 1)
    slope = (1 - u) ** 2  # кромка круче у края, к середине — плоская
    # Тень снаружи: мягкая и плотная у касания.
    body = rr(w, h, box, r)
    soft = blur(rr(w, h, shift(inset(box, 2 * S), 0, shadow[1] * S), r), shadow[2] * S) * shadow[0]
    contact = blur(rr(w, h, shift(box, 0, 1.2 * S), r), 1.4 * S) * shadow[0] * 0.6
    # Тень гаснет к краю картинки — иначе её обрез виден прямоугольником.
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    edge = np.minimum.reduce([xx, w - 1 - xx, yy, h - 1 - yy])
    c.over((40, 48, 68), np.clip(soft + contact, 0, 1) * (1 - body) * smooth(0, 2.5 * S, edge))
    # Тело: почти прозрачное, у кромки стекло толще — гуще цвет.
    thick = slope * depth_tint
    c.over(tint, inside * np.clip(body_alpha + thick, 0, 1))
    # Внутренняя тень под верхней кромкой — объём линзы.
    band = np.exp(-((raw - 0.85) / 0.32) ** 2)
    c.over((70, 80, 102), inside * band * up * 0.22 * rim_scale)
    # Свет снизу, прошедший сквозь толщину: светлая полоса чуть внутри нижней кромки.
    caustic = np.exp(-((raw - 0.38) / 0.22) ** 2)
    c.over((255, 255, 255), inside * caustic * down * 0.55 * rim_scale)
    # Блик на кромке, обращённой к свету.
    c.over((255, 255, 255), inside * slope * (0.15 + 0.85 * up) * 0.8 * rim_scale)
    # Тёмная грань толщины у нижнего края.
    grade = smooth(1.2 * S, 2.0 * S, t) * smooth(3.4 * S, 2.4 * S, t)
    c.over((60, 70, 96), inside * grade * down * 0.35 * rim_scale)
    # Тонкая яркая линия по самому краю: сверху ярче.
    line = smooth(1.4 * S, 0.3 * S, t) * inside
    c.over((255, 255, 255), line * (0.62 + 0.38 * up))
    return d


def ice_slab():
    r, ml, mt, mb, st = 30 * S, 6 * S, 3 * S, 11 * S, 20 * S
    w, h = 2 * ml + 2 * r + st, mt + mb + 2 * r + st
    box = (ml, mt, w - ml - 1, h - mb - 1)
    c = Canvas(w, h)
    glass(c, box, r, bevel=11 * S, tint=(246, 249, 255), body_alpha=0.12, depth_tint=0.14)
    c.save9("wbg_ice", (ml + r, ml + r + st), (mt + r, mt + r + st))


def ice_pill():
    r, ml, mt, mb, st = 13 * S, 3 * S, 1 * S, 4 * S, 6 * S
    w, h = 2 * ml + 2 * r + st, mt + mb + 2 * r + st
    box = (ml, mt, w - ml - 1, h - mb - 1)
    c = Canvas(w, h)
    glass(c, box, r, bevel=6 * S, tint=(104, 150, 240), body_alpha=0.62, depth_tint=0.3, shadow=(0.30, 2, 3))
    # Поля текста: 8 dp по бокам, 6–7 dp сверху и снизу (вместе с местом под тень).
    c.save9("wpill_ice", (ml + r, ml + r + st), (mt + r, mt + r + st),
            pad_x=(ml + 8 * S, w - ml - 8 * S), pad_y=(mt + 5 * S, h - mb - 4 * S))


# ---------------------------------------------------------------- «Сияние»

PURPLE = (172, 92, 255)
ORANGE = (255, 150, 72)


def glow_slab():
    r, ml, mt, mb, st = 26 * S, 4 * S, 2 * S, 12 * S, 20 * S
    side = 5 * S  # толщина панели снизу
    w, h = 2 * ml + 2 * r + st, mt + mb + 2 * r + st
    box = (ml, mt, w - ml - 1, h - mb - 1)
    c = Canvas(w, h)
    body = rr(w, h, box, r)
    base = rr(w, h, shift(box, 0, side), r)
    # Свет, который выходит из-под панели: фиолетовый снизу и тёплый оранжевый слева.
    c.over(PURPLE, blur(rr(w, h, shift(inset(box, 6 * S), 0, side + 4 * S), r), 6 * S) * 0.75 * (1 - body))
    c.over(ORANGE, blur(radial(w, h, box[0] + 14 * S, box[3] + side, 24 * S), 4 * S) * 0.85 * (1 - body))
    # Толщина: тёмная к краям и светящаяся фиолетовым, слева — оранжевым.
    thick = np.clip(base - body, 0, 1)
    c.over((30, 24, 44), thick * 0.96)
    c.over(PURPLE, thick * hgrad(w, h, box[0], box[2], 0.35, 0.95))
    c.over(ORANGE, thick * hgrad(w, h, box[0], box[0] + 34 * S, 0.9, 0.0))
    c.over((235, 200, 255), np.clip(base - rr(w, h, shift(box, 0, side - S), r), 0, 1) * 0.55)
    # Верх панели: дымчатое стекло, снизу подсвечено фиолетовым и в углу — оранжевым.
    c.over((24, 24, 30), body)
    c.over((48, 48, 58), body * vgrad(w, h, box[1], box[3], 0.95, 0.0))
    c.over(PURPLE, body * vgrad(w, h, box[3] - 42 * S, box[3], 0.0, 0.32))
    c.over(ORANGE, body * radial(w, h, box[0] + 6 * S, box[3], 30 * S) * 0.42)
    # Стеклянная кромка: светлая сверху, у низа почти не видна.
    c.over((255, 255, 255), np.clip(body - rr(w, h, inset(box, 0.8 * S), r - 0.8 * S), 0, 1) * vgrad(w, h, box[1], box[3], 0.36, 0.06))
    c.over((255, 255, 255), rr(w, h, inset(box, S), r - S) * vgrad(w, h, box[1], box[1] + 22 * S, 0.07, 0.0))
    c.save9("wbg_glow", (ml + r, ml + r + st), (mt + r, mt + r + st))


def glow_pill():
    r, m, mb, st = 13 * S, 3 * S, 4 * S, 6 * S
    w, h = 2 * m + 2 * r + st, m + mb + 2 * r + st
    box = (m, m, w - m - 1, h - mb - 1)
    c = Canvas(w, h)
    body = rr(w, h, box, r)
    colors = hcolors(w, h, box[0], box[2], [(0.0, (112, 58, 232)), (0.55, (186, 88, 255)), (1.0, (246, 120, 232))])
    halo = blur(body, 3 * S)
    c.rgb[:] = colors
    c.a[:] = np.clip(halo * 0.75 * (1 - body), 0, 1)
    c.over(colors, body)
    inner = rr(w, h, inset(box, S), r - S)
    c.over((255, 255, 255), inner * vgrad(w, h, box[1], box[1] + 10 * S, 0.34, 0.0))
    c.over((255, 230, 255), np.clip(body - inner, 0, 1) * vgrad(w, h, box[1], box[3], 0.5, 0.15))
    c.save9("wpill_glow", (m + r, m + r + st), (m + r, m + r + st),
            pad_x=(m + 8 * S, w - m - 8 * S), pad_y=(m + 4 * S, h - mb - 4 * S))


if __name__ == "__main__":
    ice_slab()
    ice_pill()
    glow_slab()
    glow_pill()
    print("ok:", sorted(f for f in os.listdir(OUT) if f.endswith(".9.png")))
