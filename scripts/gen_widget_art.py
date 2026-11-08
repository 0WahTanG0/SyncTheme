#!/usr/bin/env python3
"""
Рисунки стиля «Мята»: подложка виджетов и капсула подписей — растягиваемые картинки (9-patch)
в drawable-xxhdpi. Простыми фигурами (shape) не нарисовать то, что делает стиль: мягкую тень,
толщину плитки снизу и блик по верхней кромке.

«Мята» — по образцу «Product UI Styleguide»: матовая тёплая светлая плитка, снизу видна её толщина,
под ней — мягкая тень. Капсула подписи — мятная плитка поменьше, тоже с толщиной.

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


# ---------------------------------------------------------------- «Мята»


def smooth(x0, x1, v):
    t = np.clip((v - x0) / (x1 - x0), 0, 1)
    return t * t * (3 - 2 * t)


def clay(c, box, r, side, face, edge, shadow=(0.30, 5, 7), rim=0.85):
    """Плитка: тень, торец высотой side (px) под лицевой стороной, лицевая сторона с градиентом и блик."""
    w, h = c.w, c.h
    body = rr(w, h, box, r)
    base = rr(w, h, shift(box, 0, side), r)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    fade = smooth(0, 2.5 * S, np.minimum.reduce([xx, w - 1 - xx, yy, h - 1 - yy]))  # тень гаснет к краю картинки
    soft = blur(rr(w, h, shift(inset(box, 2 * S), 0, side + shadow[1] * S), r), shadow[2] * S) * shadow[0]
    contact = blur(rr(w, h, shift(box, 0, side + 0.8 * S), r), 1.2 * S) * shadow[0] * 0.8
    c.over((74, 64, 54), np.clip(soft + contact, 0, 1) * (1 - base) * fade)
    # Торец: снизу темнее.
    thick = np.clip(base - body, 0, 1)
    c.over(edge, thick)
    c.over(tuple(v * 0.88 for v in edge), thick * vgrad(w, h, box[3] - r, box[3] + side, 0.0, 0.6))
    # Лицевая сторона: светлее вверху, к низу чуть темнее.
    top = tuple(min(255, v + (255 - v) * 0.45) for v in face)
    low = tuple(v * 0.975 for v in face)
    c.over(top, body)
    c.over(face, body * vgrad(w, h, box[1], box[1] + (box[3] - box[1]) * 0.45, 0.0, 1.0))
    c.over(low, body * vgrad(w, h, box[1] + (box[3] - box[1]) * 0.55, box[3], 0.0, 1.0))
    # Блик по верхней кромке и тонкая граница лица с торцом.
    ring = np.clip(body - rr(w, h, inset(box, 1.1 * S), r - 1.1 * S), 0, 1)
    c.over((255, 255, 255), ring * vgrad(w, h, box[1], box[1] + 1.6 * r, rim, 0.0))
    c.over(edge, ring * vgrad(w, h, box[3] - 0.8 * r, box[3], 0.0, 0.45))


SURFACE = (243, 241, 237)
SURFACE_EDGE = (207, 201, 193)
MINT = (168, 220, 195)
MINT_EDGE = (127, 187, 159)


def mint_slab():
    r, ml, mt, mb, st = 24 * S, 4 * S, 2 * S, 9 * S, 20 * S
    side = 3 * S
    w, h = 2 * ml + 2 * r + st, mt + mb + 2 * r + st
    box = (ml, mt, w - ml - 1, h - mb - 1)
    c = Canvas(w, h)
    clay(c, box, r, side, SURFACE, SURFACE_EDGE, shadow=(0.30, 2.5, 3.5))
    c.save9("wbg_mint", (ml + r, ml + r + st), (mt + r, mt + r + st))


def mint_pill():
    r, ml, mt, mb, st = 12 * S, 3 * S, 1 * S, 6 * S, 6 * S
    side = int(2.5 * S)
    w, h = 2 * ml + 2 * r + st, mt + mb + 2 * r + st
    box = (ml, mt, w - ml - 1, h - mb - 1)
    c = Canvas(w, h)
    clay(c, box, r, side, MINT, MINT_EDGE, shadow=(0.26, 2, 2.5), rim=0.7)
    # Поля текста: 9 dp по бокам, 4–5 dp сверху и снизу лицевой стороны.
    c.save9("wpill_mint", (ml + r, ml + r + st), (mt + r, mt + r + st),
            pad_x=(ml + 9 * S, w - ml - 9 * S), pad_y=(mt + 4 * S, h - mb - 4 * S))


if __name__ == "__main__":
    mint_slab()
    mint_pill()
    print("ok:", sorted(f for f in os.listdir(OUT) if f.endswith(".9.png")))
