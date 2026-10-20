#!/usr/bin/env python3
"""Генерирует ресурсы виджетов SyncTheme: разметку всех стилей и размеров, фигуры, циферблаты,
иконки погоды и Kotlin-таблицы идентификаторов. Запуск: python3 scripts/gen_widgets.py

RemoteViews не умеют менять шрифт и состав элементов на лету, поэтому на каждое сочетание
«вид × стиль × размер» нужна своя разметка — её проще генерировать, чем писать руками.

Соглашение об идентификаторах: «роль_слот». Роль задаёт цвет, который ставит WidgetRenderer:
  m_ — основной текст, s_ — приглушённый, f_ — бледный, a_ — акцент,
  o_ — текст на акцентной фигуре, n_ — приглушённый текст на фигуре;
  im_/is_/if_/ia_/io_ — значки и линии в тех же цветах;
  bg_s — подложка (цвет поверхности стиля), bg_a — подложка-фигура акцентного цвета, bg_g — стекло;
  sh_a — акцентная фигура, fx_g — свечение «Неона», wx_ — иконки погоды, pb_ — индикаторы,
  bo_ — текст на акцентной плашке (фон-плашка красится в акцент),
  ap_ — значки приложений, row_/cell_ — строки, которые можно скрыть.
Слот — часть после роли: один слот в разных стилях может быть в разных ролях (m_temp и o_temp).
"""
import math
import os
import re
from collections import OrderedDict

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "mobile", "src", "main")
RES = os.path.join(ROOT, "res")
KT = os.path.join(ROOT, "java", "app", "synctheme", "mobile", "widget")
HEADER = "<!-- Сгенерировано scripts/gen_widgets.py — не редактировать вручную. -->"
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

FAMILIES = ["bold", "blob", "glass", "neon", "minimal"]
# «Диагональ» — только у часов.
CLOCK_FAMILIES = FAMILIES + ["diag"]


def load_digit_fonts():
    """Шрифты цифр и их метрики — из core/DigitFont.kt (его пишет scripts/gen_fonts.py)."""
    path = os.path.join(os.path.dirname(__file__), "..", "core", "src", "main", "kotlin", "app", "synctheme", "core", "DigitFont.kt")
    out = OrderedDict()
    pattern = r'^\s+(\w+)\((?:"(\w+)"|null), "[^"]+", ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f\)'
    for m in re.finditer(pattern, open(path, encoding="utf-8").read(), re.M):
        key, res = m.group(1).lower(), m.group(2)
        asc, desc, top, bottom, digit, colon = (float(m.group(i)) for i in range(3, 9))
        out[key] = dict(family=f"@font/{res}" if res else "sans-serif-thin", ascent=asc, descent=desc, top=top,
                        bottom=bottom, digit=digit)
    return out


DIGIT_FONTS = load_digit_fonts()
# Размеры: квадратные (s, m, l), вытянутые по горизонтали (bar, strip, wide) и по вертикали
# (narrow, column, tall). Какой показать, решает WidgetSize.classify по пропорциям виджета.
SIZES = ["s", "bar", "strip", "narrow", "column", "m", "tall", "wide", "l"]
# Вытянутые размеры: у них общая для стилей раскладка в шрифтах и подложке стиля.
SHAPED = {"bar", "narrow", "column", "tall"}

FONTS = {
    "bold": dict(main="@font/oswald_700", sub="@font/oswald_500", label="@font/onest_600", body="@font/onest_500"),
    "blob": dict(main="@font/nunito_900", sub="@font/nunito_700", label="@font/nunito_900", body="@font/nunito_700"),
    "glass": dict(main="@font/manrope_800", sub="@font/manrope_600", label="@font/manrope_700", body="@font/manrope_600"),
    "neon": dict(main="@font/unbounded_700", sub="@font/onest_500", label="@font/unbounded_500", body="@font/onest_500"),
    "minimal": dict(main="sans-serif-thin", sub="sans-serif-light", label="monospace", body="sans-serif-light"),
    "diag": dict(main="@font/oswald_700", sub="@font/onest_500", label="@font/onest_600", body="@font/onest_500"),
}


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


# ---------------------------------------------------------------- мини-DSL разметки

SHORT = {
    "w": "android:layout_width", "h": "android:layout_height", "weight": "android:layout_weight",
    "lg": "android:layout_gravity", "g": "android:gravity", "pad": "android:padding",
    "padH": "android:paddingHorizontal", "padV": "android:paddingVertical",
    "padT": "android:paddingTop", "padB": "android:paddingBottom", "padS": "android:paddingStart", "padE": "android:paddingEnd",
    "m": "android:layout_margin", "mt": "android:layout_marginTop", "mb": "android:layout_marginBottom",
    "ms": "android:layout_marginStart", "me": "android:layout_marginEnd", "mh": "android:layout_marginHorizontal",
    "mv": "android:layout_marginVertical", "scale": "android:scaleType", "vis": "android:visibility",
    "alpha": "android:alpha", "orientation": "android:orientation",
}
DIM = {"match": "match_parent", "wrap": "wrap_content"}


class E:
    def __init__(self, tag, attrs, children=()):
        self.tag, self.attrs, self.children = tag, attrs, [c for c in children if c is not None]

    def render(self, depth=1):
        pad = "    " * depth
        lines = [f"{pad}<{self.tag}"]
        for k, v in self.attrs.items():
            lines.append(f'{pad}    {k}="{v}"')
        if not self.children:
            lines[-1] += " />"
            return "\n".join(lines)
        lines[-1] += ">"
        out = "\n".join(lines) + "\n\n" + "\n\n".join(c.render(depth + 1) for c in self.children) + f"\n{pad}</{self.tag}>"
        return out


ALL_IDS = OrderedDict()  # id -> True (порядок появления)


def attrs_of(kw):
    out = OrderedDict()
    if "id" in kw:
        i = kw.pop("id")
        if i:
            out["android:id"] = f"@+id/{i}"
            ALL_IDS[i] = True
    for k, v in kw.items():
        if v is None or v is False:
            continue
        name = SHORT.get(k, k if ":" in k or k == "style" else f"android:{k}")
        if k in ("w", "h"):
            v = DIM.get(v, v)
        out[name] = v
    return out


def frame(*children, **kw):
    kw.setdefault("w", "match")
    kw.setdefault("h", "match")
    return E("FrameLayout", attrs_of(kw), children)


def col(*children, **kw):
    kw.setdefault("w", "match")
    kw.setdefault("h", "match")
    kw["orientation"] = "vertical"
    kw.setdefault("g", "center")
    return E("LinearLayout", attrs_of(kw), children)


def row(*children, **kw):
    kw.setdefault("w", "match")
    kw.setdefault("h", "wrap")
    kw["orientation"] = "horizontal"
    kw.setdefault("g", "center_vertical")
    return E("LinearLayout", attrs_of(kw), children)


# Цвета «по умолчанию» для списка виджетов в лаунчере: там код приложения не выполняется.
PREVIEW_TINT = {"bg_s": "#E61B1B24", "bg_a": "#FF8B6CF6", "sh_a": "#FF8B6CF6", "fx_": "#FF8B6CF6"}


def img(id_, src, w="match", h="match", scale="fitCenter", **kw):
    kw.update(id=id_, w=w, h=h, scale=scale, src=f"@drawable/{src}", importantForAccessibility="no")
    tint = next((c for prefix, c in PREVIEW_TINT.items() if id_ and id_.startswith(prefix)), None)
    if tint:
        kw["tint"] = tint
    return E("ImageView", attrs_of(kw))


def spacer(weight=1, vertical=True):
    """Пустое место: в RemoteViews нельзя Space, поэтому пустой FrameLayout."""
    return E("FrameLayout", attrs_of(dict(w="0dp" if not vertical else "match", h="0dp" if vertical else "match",
                                           weight=str(weight))))


def text(id_, *, clock=None, font=None, size=None, auto=None, w="match", h="wrap", g="center", caps=False,
         lines=1, spacing=None, shadow=False, txt=None, **kw):
    tag = "TextClock" if clock else "TextView"
    kw.update(id=id_, w=w, h=h, g=g)
    h = kw["h"]
    if clock:
        kw["format12Hour"], kw["format24Hour"] = clock
    if font:
        kw["fontFamily"] = font
    if auto:
        # Автоподбор размера работает только в заданных границах: wrap_content даёт крошечный или обрезанный текст.
        # Крупный текст занимает всё место, мелкая подпись — строку высотой по максимальному кеглю.
        if h == "wrap":
            h = "match" if auto[1] >= 40 else f"{round(auto[1] * 1.4)}dp"
            kw["h"] = h
        assert w != "wrap", f"{id_}: autosize needs a fixed width"
        lo, hi = auto
        kw.update(autoSizeTextType="uniform", autoSizeMinTextSize=f"{lo}sp", autoSizeMaxTextSize=f"{hi}sp",
                  autoSizeStepGranularity="1sp")
        kw.setdefault("includeFontPadding", "false")
    if size:
        kw["textSize"] = f"{size}sp"
    if caps:
        kw["textAllCaps"] = "true"
    if spacing:
        kw["letterSpacing"] = str(spacing)
    if lines:
        kw["maxLines"] = str(lines)
        kw["ellipsize"] = "end"
    if shadow:
        kw.update(shadowColor="#80FFFFFF", shadowRadius="14", shadowDx="0", shadowDy="0")
    kw["textColor"] = "#FFFFFFFF"
    if txt:
        kw["text"] = txt
    return E(tag, attrs_of(kw))


LAYOUTS = OrderedDict()  # имя -> список id в порядке появления

# Старые сгенерированные разметки удаляем: набор стилей и шрифтов мог измениться.
for old in os.listdir(os.path.join(RES, "layout")):
    if old.startswith("w_") and old.endswith(".xml"):
        os.remove(os.path.join(RES, "layout", old))


def layout(name, *layers):
    root = frame(*layers, id="widget_root")
    xml = root.render(0).replace("<FrameLayout", f"<FrameLayout\n    {NS}", 1)
    write(os.path.join(RES, "layout", f"{name}.xml"), f'<?xml version="1.0" encoding="utf-8"?>\n{HEADER}\n' + xml + "\n")
    LAYOUTS[name] = list(OrderedDict.fromkeys(re.findall(r'@\+id/(\w+)', xml)))


# ---------------------------------------------------------------- подложки стилей


def bg(family, shaped=None, kind=""):
    """Слои подложки. shaped — имя фигуры для маленьких размеров «Облака»/«Неона»."""
    if family == "glass":
        return [img("bg_g", "wbg_glass", scale="fitXY")]
    if family == "neon":
        if shaped == "ring_only":
            return [img("bg_s", "wsh_circle")]
        if shaped:
            return [img("bg_s", "wsh_circle"), img("fx_g", "wfx_ring")]
        return [img("bg_s", "wbg_r24", scale="fitXY"), img("fx_g", "wfx_neon", scale="fitXY")]
    if family == "blob":
        if shaped:
            return [img("bg_a", f"wsh_{shaped}")]
        return [img("bg_s", "wbg_r36", scale="fitXY")]
    if family == "minimal":
        return [img("bg_s", "wbg_r18", scale="fitXY")]
    return [img("bg_s", "wbg_r28", scale="fitXY")]


def hline(id_="if_line", **kw):
    return img(id_, "wline", w="match", h="1dp", scale="fitXY", **kw)


def vline(id_="if_vline", **kw):
    return img(id_, "wline", w="1dp", h="match", scale="fitXY", **kw)


# Форматы TextClock (12 ч, 24 ч).
TIME = ("h:mm", "H:mm")
HOURS = ("hh", "HH")
MINS = ("mm", "mm")
AMPM_OR_DAY = ("a", "EEE")
DATE_LONG = ("EEEE, d MMMM", "EEEE, d MMMM")
DATE_MID = ("EEE, d MMMM", "EEE, d MMMM")
DATE_SHORT = ("EEE, d MMM", "EEE, d MMM")
WEEKDAY = ("EEEE", "EEEE")
WEEKDAY_SHORT = ("EEE", "EEE")
DAY = ("d", "d")
MONTH = ("LLLL", "LLLL")
MONTH_YEAR = ("LLLL yyyy", "LLLL yyyy")
DAY_MONTH = ("d MMMM", "d MMMM")


def stacked_digits(F, band, auto, width, gravity=("center", "center")):
    """Часы и минуты друг под другом. У шрифтов над и под цифрами пустые поля (у Oswald — треть
    кегля): текст в рамке делаем выше самой рамки (отрицательные отступы), поля уходят за её края,
    а цифры занимают почти всю высоту. band и width — примерные высота и ширина рамки, dp;
    отступы считаются по метрикам шрифта цифр, чтобы цифры не обрезались и не наезжали."""
    m = F["digits"]
    digit_h = m["top"] - m["bottom"]
    em = min(0.9 * band / digit_h, 0.95 * width / (2 * m["digit"]))  # кегль, при котором цифры влезают
    visible = digit_h * em
    pad = (band - visible) / 2
    top = max(0, round((m["ascent"] - m["top"]) * em - pad))
    line = (m["ascent"] + m["descent"]) * em
    bottom = max(0, round(line - band - top))

    def part(id_, fmt, g):
        return frame(text(id_, clock=fmt, font=F["time"], auto=auto, h="match", g=g, mt=f"-{top}dp", mb=f"-{bottom}dp"),
                     h="0dp", weight=1, clipChildren="false")
    return [part("m_hours_al" if gravity[0] == "center" else "m_hours", HOURS, gravity[0]),
            part("a_minutes_al" if gravity[1] == "center" else "a_minutes", MINS, gravity[1])]


def alarm_row(f, role="s", size=12, **kw):
    F = FONTS[f]
    return row(
        img(f"i{role}_alarm", "wic_alarm", w=f"{size + 2}dp", h=f"{size + 2}dp"),
        text(f"{role}_alarm", font=F["body"], size=size, w="wrap", g="start", ms="5dp"),
        id="row_alarm", w="wrap", **kw)


# Автоподбор кегля не уменьшает по ширине значение без переносов («1», «0%»), если высота позволяет больше:
# в высоких узких виджетах у чисел свой предел кегля по ширине разметки.
NUM_NARROW = (10, 34)
NUM_TALL = (18, 72)

# Часы над минутами одной строкой текста «чч\nмм»: автоподбор кегля вписывает обе строки по ширине и высоте,
# поэтому в узком высоком виджете цифры крупные и стоят плотно, без пустот между часами и минутами.
TIME2 = ("hh\\nmm", "HH\\nmm")


def time2(id_, F, weight=1, auto=(12, 400), **kw):
    m = F["digits"]
    line = m["ascent"] + m["descent"]
    lsm = max(0.6, min(1.0, 1.08 * (m["top"] - m["bottom"]) / line))
    return text(id_, clock=TIME2, font=F["time"], auto=auto, h="0dp", weight=weight, lines=2,
                lineSpacingMultiplier=f"{lsm:.2f}", **kw)


def alarm_col(f, role="s"):
    """Будильник столбиком (значок над временем) — для узких виджетов."""
    F = FONTS[f]
    return col(img(f"i{role}_alarm", "wic_alarm", w="12dp", h="12dp"),
               text(f"{role}_alarm", font=F["body"], auto=(7, 11), mt="2dp"),
               id="row_alarm", w="match", h="wrap", mt="4dp")


def shaped_bg(f):
    """Подложка вытянутых размеров: у «Облака» — скруглённый прямоугольник вместо фигуры."""
    return bg(f)


# ---------------------------------------------------------------- часы: цифровые


def clock_digital(f, size, digit_key):
    F = dict(FONTS[f])
    F["digits"] = DIGIT_FONTS[digit_key]
    F["time"] = F["digits"]["family"]
    name = f"w_clock_{f}_{size}_{digit_key}"
    sh = f == "neon"
    if size in SHAPED:
        return clock_shaped(f, size, F, name)
    if f == "diag":
        # Часы — в левом верхнем углу, минуты — в правом нижнем; дата и будильник — в свободных углах.
        corner_top = text("a_weekday", clock=WEEKDAY, font=F["label"], size=11 if size != "l" else 14, caps=True, spacing=0.08,
                          w="wrap", lg="top|end", m="12dp" if size != "s" else "6dp")
        corner_bottom = col(text("s_date", clock=DAY_MONTH, font=F["sub"], size=12 if size != "l" else 16, w="wrap", g="start"),
                            alarm_row(f) if size in ("wide", "l") else None,
                            w="wrap", h="wrap", lg="bottom|start", m="14dp", g="start")
        if size == "strip":
            body = row(text("m_hours", clock=HOURS, font=F["time"], auto=(16, 200), w="0dp", h="match", weight=1, g="top|start"),
                       col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=11, caps=True, spacing=0.08),
                           text("s_date", clock=DAY_MONTH, font=F["sub"], size=13), w="wrap", h="match"),
                       text("a_minutes", clock=MINS, font=F["time"], auto=(16, 200), w="0dp", h="match", weight=1, g="bottom|end"),
                       h="match", padH="14dp", padV="4dp")
            return layout(name, *bg(f), body)
        band, width = {"s": (42, 84), "m": (64, 146), "wide": (64, 300), "l": (125, 294)}[size]
        digits = col(*stacked_digits(F, band, (16, 500), width * 0.62, gravity=("start", "end")),
                     pad="8dp" if size == "s" else "14dp", clipChildren="false")
        layers = [digits] if size == "s" else [digits, corner_top, corner_bottom]
        return layout(name, *bg(f), *layers)

    if f == "bold":
        if size == "s":
            body = col(*stacked_digits(F, 42, (16, 300), 84), id="box_al", pad="8dp", clipChildren="false")
        elif size == "strip":
            body = row(text("m_time", clock=("hh mm", "HH mm"), font=F["time"], auto=(18, 200), w="0dp", h="match", weight=1.4, g="center_vertical|start"),
                       col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.08),
                           text("m_date", clock=DAY_MONTH, font=F["sub"], auto=(12, 26), h="0dp", weight=1, g="start|center_vertical"),
                           w="0dp", weight=1, g="center_vertical", ms="10dp"),
                       h="match", padH="16dp", padV="8dp")
        elif size == "m":
            body = col(*stacked_digits(F, 64, (20, 300), 146),
                       text("s_date_al", clock=DATE_SHORT, font=F["label"], size=12, caps=True, spacing=0.08, mt="4dp"),
                       id="box_al", pad="12dp", clipChildren="false")
        elif size == "wide":
            body = col(
                row(text("m_time", clock=("hh mm", "HH mm"), font=F["time"], auto=(28, 400), w="0dp", h="match", weight=1, g="center_vertical|start"),
                    text("a_ampm", clock=AMPM_OR_DAY, font=F["main"], auto=(12, 40), w="56dp", h="match", g="top|start", caps=True, padT="6dp"),
                    h="0dp", weight=1),
                row(text("s_date", clock=DATE_LONG, font=F["label"], size=13, caps=True, spacing=0.06, w="0dp", weight=1, g="start"),
                    alarm_row(f)),
                padH="16dp", padV="12dp")
        else:
            body = col(*stacked_digits(F, 125, (30, 500), 294),
                       text("m_date_al", clock=DATE_LONG, font=F["sub"], size=18, caps=True, spacing=0.04, mt="6dp"),
                       alarm_row(f, mt="4dp"),
                       id="box_al", padH="18dp", padV="14dp", clipChildren="false")
        return layout(name, *bg(f), body)

    if f == "blob":
        if size in ("s", "m"):
            inner = [text("o_time", clock=TIME, font=F["time"], auto=(14, 200), h="0dp", weight=1)]
            if size == "m":
                inner.append(text("n_date", clock=DATE_SHORT, font=F["sub"], auto=(9, 16), h="wrap", mb="2dp"))
            return layout(name, *bg(f, "cookie"), col(*inner, pad="18dp" if size == "m" else "12dp"))
        if size == "strip":
            body = row(text("m_time", clock=TIME, font=F["time"], auto=(18, 160), w="0dp", h="match", weight=1, g="center_vertical|start"),
                       text("bo_date", clock=DATE_SHORT, font=F["sub"], size=14, w="wrap", h="wrap", padH="14dp", padV="8dp",
                            background="@drawable/wsh_pill", backgroundTint="#FF8B6CF6"),
                       h="match", padH="16dp", padV="8dp")
            return layout(name, *bg(f), body)
        if size == "wide":
            body = row(frame(img("sh_a", "wsh_cookie"),
                             text("o_time", clock=TIME, font=F["time"], auto=(14, 120), pad="16dp"),
                             w="0dp", weight=1),
                       col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=14, g="start"),
                           text("m_date", clock=DAY_MONTH, font=F["main"], auto=(14, 34), h="wrap", g="start"),
                           alarm_row(f, mt="6dp"),
                           w="0dp", weight=1.2, g="center_vertical|start", ms="14dp"),
                       h="match", pad="12dp")
            return layout(name, *bg(f), body)
        body = col(frame(img("sh_a", "wsh_cookie"),
                         text("o_time", clock=TIME, font=F["time"], auto=(20, 200), pad="30dp", h="match"),
                         w="180dp", h="180dp"),
                   spacer(),
                   text("m_date", clock=DATE_LONG, font=F["main"], size=17, mt="8dp"),
                   alarm_row(f, mt="2dp"),
                   pad="14dp")
        return layout(name, *bg(f), body)

    if f == "glass":
        head = row(img("ia_dot", "wdot", w="7dp", h="7dp"),
                   text("s_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, spacing=0.06, w="wrap", g="start", ms="6dp"),
                   id="box_al", w="match")
        if size == "s":
            body = col(text("m_time_al", clock=TIME, font=F["time"], auto=(14, 160), h="0dp", weight=1),
                       text("s_date_al", clock=DATE_SHORT, font=F["sub"], auto=(8, 13), h="wrap"),
                       pad="10dp")
        elif size == "strip":
            body = row(text("m_time", clock=TIME, font=F["time"], auto=(16, 160), w="0dp", h="match", weight=1, g="center_vertical|start"),
                       vline("if_vline", mv="10dp", mh="12dp"),
                       col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.06),
                           text("m_date", clock=DAY_MONTH, font=F["sub"], size=16, g="start"),
                           w="wrap", g="center_vertical|start"),
                       h="match", padH="16dp", padV="6dp")
        elif size == "m":
            body = col(head,
                       text("m_time_al", clock=TIME, font=F["time"], auto=(20, 200), h="0dp", weight=1),
                       text("s_date_al", clock=DAY_MONTH, font=F["sub"], size=14),
                       pad="14dp")
        elif size == "wide":
            body = row(col(head,
                           text("m_time_al", clock=TIME, font=F["time"], auto=(24, 260), h="0dp", weight=1),
                           w="0dp", weight=1.4),
                       vline("if_vline", mv="6dp", mh="14dp"),
                       col(text("m_date", clock=DAY_MONTH, font=F["main"], auto=(14, 30), h="wrap", g="start"),
                           text("s_month", clock=("yyyy", "yyyy"), font=F["sub"], size=13, g="start"),
                           alarm_row(f, mt="8dp"),
                           w="0dp", weight=1, g="center_vertical|start"),
                       h="match", pad="16dp")
        else:
            body = col(head,
                       text("m_time_al", clock=TIME, font=F["time"], auto=(30, 320), h="0dp", weight=1),
                       text("m_date_al", clock=DATE_LONG, font=F["sub"], size=17),
                       hline("if_line", mv="10dp"),
                       alarm_row(f, size=14, lg="start"),
                       pad="18dp")
        return layout(name, *bg(f), body)

    if f == "neon":
        if size in ("s", "m"):
            inner = [text("m_time", clock=TIME, font=F["time"], auto=(10, 120), h="0dp", weight=1, shadow=True)]
            if size == "m":
                inner.append(text("a_date", clock=DATE_SHORT, font=F["sub"], auto=(8, 14), caps=True, mb="4dp"))
            return layout(name, *bg(f, "ring"), col(*inner, pad="26dp" if size == "m" else "16dp"))
        if size == "strip":
            body = row(text("m_time", clock=TIME, font=F["time"], auto=(16, 120), w="0dp", h="match", weight=1, g="center_vertical|start", shadow=True),
                       text("a_date", clock=DATE_SHORT, font=F["label"], size=12, caps=True, spacing=0.06, w="wrap", g="end|center_vertical"),
                       h="match", padH="18dp", padV="8dp")
        elif size == "wide":
            body = col(row(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, spacing=0.1, w="0dp", weight=1, g="start"),
                           alarm_row(f, role="a", size=11)),
                       text("m_time", clock=TIME, font=F["time"], auto=(24, 260), h="0dp", weight=1, g="center_vertical|start", shadow=True),
                       text("s_date", clock=DAY_MONTH, font=F["sub"], size=15, g="start", caps=True, spacing=0.06),
                       padH="20dp", padV="16dp")
        else:
            body = col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=13, caps=True, spacing=0.12),
                       spacer(),
                       frame(img("fx_g2", "wfx_ring"),
                             text("m_time", clock=TIME, font=F["time"], auto=(14, 160), pad="30dp", shadow=True, h="match"),
                             w="170dp", h="170dp", mv="6dp"),
                       spacer(),
                       text("s_date", clock=DAY_MONTH, font=F["sub"], size=15, caps=True, spacing=0.06),
                       alarm_row(f, role="a", size=12, mt="4dp"),
                       pad="16dp")
        return layout(name, *bg(f), body)

    # minimal
    if size == "s":
        body = col(text("m_time_al", clock=TIME, font=F["time"], auto=(14, 160), h="0dp", weight=1),
                   text("a_date_al", clock=("EEE d", "EEE d"), font=F["label"], auto=(8, 12), caps=True),
                   pad="8dp")
    elif size == "strip":
        body = row(text("m_time", clock=TIME, font=F["time"], auto=(16, 160), w="0dp", h="match", weight=1, g="center_vertical|start"),
                   vline("if_vline", mv="12dp", mh="12dp"),
                   text("a_date", clock=("EEE d MMM", "EEE d MMM"), font=F["label"], size=12, caps=True, w="wrap", g="center_vertical|start"),
                   h="match", padH="12dp")
    elif size == "m":
        body = col(text("m_time_al", clock=TIME, font=F["time"], auto=(20, 200), h="0dp", weight=1),
                   hline("if_line", mv="6dp"),
                   text("a_date_al", clock=("EEE d MMM", "EEE d MMM"), font=F["label"], size=12, caps=True),
                   pad="12dp")
    elif size == "wide":
        body = row(text("m_time", clock=TIME, font=F["time"], auto=(24, 300), w="0dp", h="match", weight=1.5, g="center_vertical|start"),
                   vline("if_vline", mv="10dp", mh="14dp"),
                   col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start"),
                       text("m_date", clock=DAY_MONTH, font=F["sub"], size=18, g="start", mt="2dp"),
                       alarm_row(f, role="s", mt="8dp", lg="start"),
                       w="0dp", weight=1, g="center_vertical|start"),
                   h="match", padH="14dp", padV="10dp")
    else:
        body = col(text("m_time_al", clock=TIME, font=F["time"], auto=(30, 360), h="0dp", weight=1),
                   hline("if_line", mv="10dp"),
                   row(text("a_date", clock=("EEEE d MMMM", "EEEE d MMMM"), font=F["label"], size=12, caps=True, w="0dp", weight=1, g="start"),
                       alarm_row(f, role="s")),
                   pad="16dp")
    return layout(name, *bg(f), body)


def clock_shaped(f, size, F, name):
    """Цифровые часы в вытянутых размерах: полоска 2×1, столбики 1×N, высокий 2×N."""
    sh = f == "neon"
    if f == "diag":
        # Часы — в верхнем левом углу, минуты — в нижнем правом, как в остальных размерах «Диагонали».
        if size == "bar":
            body = row(text("m_hours", clock=HOURS, font=F["time"], auto=(12, 200), w="0dp", h="match", weight=1, g="top|start"),
                       text("a_minutes", clock=MINS, font=F["time"], auto=(12, 200), w="0dp", h="match", weight=1, g="bottom|end"),
                       h="match", padH="10dp", padV="4dp")
        else:
            middle = None if size == "narrow" else text("s_date", clock=DAY_MONTH if size == "tall" else ("d MMM", "d MMM"),
                                                       font=F["sub"], auto=(7, 16), mv="4dp")
            body = col(text("m_hours", clock=HOURS, font=F["time"], auto=(12, 400), h="0dp", weight=1, g="top|start"),
                       middle,
                       text("a_minutes", clock=MINS, font=F["time"], auto=(12, 400), h="0dp", weight=1, g="bottom|end"),
                       pad="4dp" if size != "tall" else "14dp")
        return layout(name, *shaped_bg(f), body)
    if size == "bar":
        body = col(text("m_time", clock=TIME, font=F["time"], auto=(12, 200), h="0dp", weight=1, shadow=sh),
                   text("s_date", clock=DATE_SHORT, font=F["sub"], auto=(7, 12), caps=f in ("neon", "minimal")),
                   padH="10dp", padV="4dp")
    elif size == "narrow":
        body = col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 14), caps=True, spacing=0.06),
                   time2("m_time", F, shadow=sh),
                   text("s_date", clock=("d MMM", "d MMM"), font=F["sub"], auto=(7, 13)),
                   pad="4dp")
    elif size == "column":
        body = col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 14), caps=True, spacing=0.06),
                   time2("m_time", F, weight=1.7, shadow=sh),
                   hline("if_line", mv="6dp"),
                   text("m_day", clock=DAY, font=F["main"], auto=NUM_NARROW, h="0dp", weight=0.55),
                   text("s_month", clock=("LLL", "LLL"), font=F["sub"], auto=(7, 13), caps=True),
                   alarm_col(f),
                   pad="4dp")
    else:
        body = col(text("a_weekday", clock=WEEKDAY, font=F["label"], auto=(9, 16), caps=True, spacing=0.06),
                   time2("m_time", F, shadow=sh),
                   text("s_date", clock=DAY_MONTH, font=F["sub"], auto=(10, 18)),
                   alarm_row(f, mt="4dp"),
                   pad="12dp")
    return layout(name, *shaped_bg(f), body)


# ---------------------------------------------------------------- часы: стрелки

ANALOG = {
    # стиль: (циферблат, часовая, минутная, секундная, фон для маленьких размеров)
    "analog_blob": ("dial_cookie", "hand_hour_round", "hand_minute_round", "analog_second", "blob"),
    "analog_neon": ("dial_neon", "analog_hour", "analog_minute", "analog_second", "neon"),
    "analog_numbers": ("dial_numbers", "analog_hour", "analog_minute", "analog_second", "glass"),
    "analog_minimal": ("dial_minimal", "hand_hour_thin", "hand_minute_thin", "analog_second", "minimal"),
}


def analog_view(style, seconds, **kw):
    dial, hh, mh, sh, _ = ANALOG[style]
    kw.update(id="analog", dial=f"@drawable/{dial}", hand_hour=f"@drawable/{hh}", hand_minute=f"@drawable/{mh}")
    if seconds:
        kw["hand_second"] = f"@drawable/{sh}"
    kw.setdefault("w", "match")
    kw.setdefault("h", "match")
    return E("AnalogClock", attrs_of(kw))


def clock_analog(style, size, seconds):
    fam = ANALOG[style][4]
    F = FONTS[fam]
    name = f"w_clock_{style}_{size}" + ("" if seconds else "_ns")
    layers = [] if style in ("analog_blob", "analog_neon") and size in ("s", "m") else bg(fam)
    if size == "bar":
        body = row(analog_view(style, seconds, w="0dp", weight=0.5, mv="2dp"),
                   col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 13), caps=True),
                       text("m_day", clock=DAY, font=F["main"], auto=(10, 30), h="0dp", weight=1),
                       w="0dp", weight=0.5),
                   h="match", padH="8dp", padV="4dp")
        return layout(name, *layers, body)
    if size in ("narrow", "column"):
        body = col(analog_view(style, seconds, h="0dp", weight=1),
                   text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 13), caps=True, mt="4dp"),
                   text("m_day", clock=DAY, font=F["main"], auto=NUM_NARROW, h="0dp", weight=0.45 if size == "column" else 0.35),
                   text("s_month", clock=("LLL", "LLL"), font=F["sub"], auto=(7, 12), caps=True) if size == "column" else None,
                   pad="6dp")
        return layout(name, *layers, body)
    if size == "tall":
        body = col(analog_view(style, seconds, h="0dp", weight=1),
                   text("m_date", clock=DATE_MID, font=F["sub"], auto=(10, 16), mt="8dp"),
                   alarm_row(fam, mt="2dp"),
                   pad="12dp")
        return layout(name, *layers, body)
    if size in ("s", "m"):
        return layout(name, *layers, analog_view(style, seconds, m="4dp" if size == "s" else "8dp"))
    if size == "strip":
        body = row(analog_view(style, seconds, w="0dp", weight=0.45, mv="4dp"),
                   col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start"),
                       text("m_date", clock=DAY_MONTH, font=F["sub"], size=16, g="start"),
                       w="0dp", weight=1, g="center_vertical|start", ms="12dp"),
                   h="match", padH="10dp", padV="6dp")
    elif size == "wide":
        body = row(analog_view(style, seconds, w="0dp", weight=1),
                   col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=13, caps=True, g="start"),
                       text("m_date", clock=DAY_MONTH, font=F["main"], auto=(14, 30), h="wrap", g="start"),
                       text("s_time", clock=TIME, font=F["sub"], size=14, g="start"),
                       alarm_row(fam, mt="6dp"),
                       w="0dp", weight=1.1, g="center_vertical|start", ms="12dp"),
                   h="match", pad="12dp")
    else:
        body = col(analog_view(style, seconds, h="0dp", weight=1),
                   text("m_date", clock=DATE_LONG, font=F["sub"], size=16, mt="8dp"),
                   alarm_row(fam, mt="2dp"),
                   pad="14dp")
    return layout(name, *layers, body)


# ---------------------------------------------------------------- календарь

EVENTS = 3


def event_rows(f, n, size=13, compact=False, inline=False):
    """События: название и время под ним, только название (compact) или время справа в той же строке (inline)."""
    F = FONTS[f]
    rows = []
    for i in range(n):
        if inline:
            rows.append(row(img(f"ed_{i}", "wdot", w="7dp", h="7dp"),
                            text(f"m_ev{i}_title", font=F["body"], size=size, g="start", w="0dp", weight=1, ms="8dp"),
                            text(f"s_ev{i}_time", font=F["body"], size=size - 2, g="end", w="wrap", ms="6dp"),
                            id=f"row_ev{i}", mv="2dp"))
            continue
        rows.append(row(img(f"ed_{i}", "wdot", w="7dp", h="7dp"),
                        col(text(f"m_ev{i}_title", font=F["body"], size=size, g="start"),
                            text(f"s_ev{i}_time", font=F["body"], size=size - 2, g="start") if not compact else None,
                            w="0dp", weight=1, h="wrap", ms="8dp", g="start"),
                        id=f"row_ev{i}", mv="3dp"))
    rows.append(text("s_ev_empty", font=F["body"], size=size - 1, txt="Событий нет", g="start", vis="gone"))
    return rows


def month_grid(f):
    F = FONTS[f]
    font = F["body"]
    wds = [text(f"s_wd{i}", font=F["label"], size=10, w="0dp", weight=1, caps=True) for i in range(7)]
    rows = [row(*wds, mb="2dp")]
    today = {"blob": "wsh_cookie", "neon": "wfx_ring_small", "minimal": "wbar"}.get(f, "wdot")
    for r in range(6):
        cells = []
        for c in range(7):
            i = r * 7 + c
            cells.append(frame(
                img(f"tb_{i}", today, w="26dp", h="26dp" if today != "wbar" else "2dp", lg="center" if today != "wbar" else "bottom|center_horizontal", vis="gone"),
                text(f"m_cd{i}", font=font, size=12),
                w="0dp", h="match", weight=1))
        rows.append(row(*cells, h="0dp", weight=1, g="center"))
    return rows


def event_dots(f, n):
    """Ближайшие события столбиком для узкого виджета: цветная точка и время."""
    F = FONTS[f]
    rows = [col(img(f"ed_{i}", "wdot", w="6dp", h="6dp"),
                text(f"s_ev{i}_time", font=F["body"], auto=(6, 10), mt="2dp"),
                id=f"row_ev{i}", w="match", h="wrap", mv="3dp") for i in range(n)]
    rows.append(text("s_ev_empty", font=F["body"], auto=(6, 10), lines=2, txt="Событий нет", vis="gone"))
    return rows


def calendar_shaped(f, size, name):
    F = FONTS[f]
    sh = f == "neon"
    if size == "bar":
        body = row(text("m_day", clock=DAY, font=F["main"], auto=(12, 120), w="0dp", h="match", weight=0.8, shadow=sh),
                   col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 13), caps=True, g="start"),
                       text("s_month", clock=("LLL", "LLL"), font=F["sub"], auto=(7, 13), caps=True, g="start"),
                       w="0dp", weight=1, g="center_vertical|start", ms="6dp"),
                   h="match", padH="10dp", padV="4dp")
    elif size == "narrow":
        body = col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 14), caps=True, spacing=0.06),
                   text("m_day", clock=DAY, font=F["main"], auto=NUM_NARROW, h="0dp", weight=1, shadow=sh),
                   text("s_month", clock=("LLL", "LLL"), font=F["sub"], auto=(7, 13), caps=True),
                   pad="4dp")
    elif size == "column":
        body = col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 14), caps=True, spacing=0.06),
                   text("m_day", clock=DAY, font=F["main"], auto=NUM_NARROW, h="0dp", weight=1, shadow=sh),
                   text("s_month", clock=("LLL", "LLL"), font=F["sub"], auto=(7, 13), caps=True),
                   hline("if_line", mv="6dp"),
                   *event_dots(f, 3),
                   pad="4dp")
    else:
        body = col(text("a_weekday", clock=WEEKDAY, font=F["label"], auto=(9, 15), caps=True, spacing=0.06),
                   text("m_day", clock=DAY, font=F["main"], auto=NUM_TALL, h="0dp", weight=1, shadow=sh),
                   text("s_month", clock=MONTH, font=F["sub"], size=13, caps=f == "minimal"),
                   hline("if_line", mv="6dp"),
                   *event_rows(f, EVENTS, size=12, compact=True),
                   pad="12dp")
    return layout(name, *shaped_bg(f), body)


def calendar(f, size):
    F = FONTS[f]
    name = f"w_cal_{f}_{size}"
    if size in SHAPED:
        return calendar_shaped(f, size, name)
    if size == "s":
        if f == "blob":
            return layout(name, *bg(f, "scallop"),
                          col(text("n_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(8, 16), caps=True),
                              text("o_day", clock=DAY, font=F["main"], auto=(14, 200), h="0dp", weight=1),
                              pad="14dp"))
        if f == "neon":
            return layout(name, *bg(f, "ring"),
                          col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 13), caps=True),
                              text("m_day", clock=DAY, font=F["main"], auto=(12, 120), h="0dp", weight=1, shadow=True),
                              pad="18dp"))
        return layout(name, *bg(f),
                      col(text("a_weekday_al", clock=WEEKDAY_SHORT, font=F["label"], auto=(8, 16), caps=True, spacing=0.08),
                          text("m_day_al", clock=DAY, font=F["main"], auto=(16, 300), h="0dp", weight=1),
                          pad="8dp" if f != "glass" else "10dp"))
    if size == "strip":
        day = (frame(img("sh_a", "wsh_cookie"), text("o_day", clock=DAY, font=F["main"], auto=(10, 60), pad="6dp"), w="56dp", h="match")
               if f == "blob" else text("m_day", clock=DAY, font=F["main"], auto=(16, 120), w="64dp", h="match", shadow=f == "neon"))
        body = row(day,
                   col(text("a_weekday", clock=("EEEE, LLLL", "EEEE, LLLL"), font=F["label"], size=12, caps=True, g="start", spacing=0.05),
                       text("m_ev0_title", font=F["body"], size=15, g="start"),
                       w="0dp", weight=1, g="center_vertical|start", ms="12dp"),
                   h="match", padH="14dp", padV="8dp")
        return layout(name, *bg(f), body)
    if size == "m":
        if f == "blob":
            head = frame(img("sh_a", "wsh_scallop"),
                         col(text("n_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(8, 14), caps=True),
                             text("o_day", clock=DAY, font=F["main"], auto=(14, 90), h="0dp", weight=1), pad="10dp"),
                         h="0dp", weight=1)
        elif f == "neon":
            head = frame(img("fx_g2", "wfx_ring"),
                         col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 12), caps=True),
                             text("m_day", clock=DAY, font=F["main"], auto=(12, 80), h="0dp", weight=1, shadow=True), pad="18dp"),
                         h="0dp", weight=1)
        else:
            head = col(row(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.06, w="0dp", weight=1)),
                       text("m_day", clock=DAY, font=F["main"], auto=(20, 200), h="0dp", weight=1, g="start|center_vertical"),
                       h="0dp", weight=1)
        body = col(head,
                   text("s_month", clock=MONTH, font=F["sub"], size=13, g="start" if f not in ("blob", "neon") else "center", caps=f == "minimal"),
                   hline("if_line", mv="6dp") if f in ("glass", "minimal", "bold") else None,
                   text("m_ev0_title", font=F["body"], size=13, g="start" if f not in ("blob", "neon") else "center"),
                   pad="14dp")
        return layout(name, *bg(f), body)
    if size == "wide":
        if f == "blob":
            left = col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=13),
                       frame(img("sh_a", "wsh_scallop"),
                             text("o_day", clock=DAY, font=F["main"], auto=(16, 90), h="match", pad="14dp"),
                             h="0dp", weight=1, mv="4dp"),
                       text("s_month", clock=MONTH, font=F["sub"], size=13),
                       w="0dp", weight=0.85)
        else:
            left = col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.06),
                       text("m_day", clock=DAY, font=F["main"], auto=(20, 200), h="0dp", weight=1, g="start|center_vertical", shadow=f == "neon"),
                       text("s_month", clock=MONTH, font=F["sub"], size=14, g="start"),
                       w="0dp", weight=0.8)
        body = row(left,
                   vline("if_vline", mh="12dp", mv="4dp") if f != "blob" else None,
                   col(*event_rows(f, EVENTS, inline=True), w="0dp", weight=1.4, g="center_vertical", ms="12dp" if f == "blob" else None),
                   h="match", pad="14dp")
        return layout(name, *bg(f), body)
    # l: месяц + события
    body = col(row(text("a_month", clock=MONTH_YEAR, font=F["label"], size=14, caps=f in ("bold", "neon", "minimal"), g="start", w="0dp", weight=1, spacing=0.04)),
               col(*month_grid(f), h="0dp", weight=1, mt="4dp"),
               hline("if_line", mv="6dp"),
               *event_rows(f, 2, size=12, compact=True),
               pad="14dp")
    return layout(name, *bg(f), body)


# ---------------------------------------------------------------- погода

HOURS_N = 5
DAYS_N = 5


def hourly(f, size=12, icon=24):
    F = FONTS[f]
    cols = []
    for i in range(HOURS_N):
        cols.append(col(text(f"s_h{i}_time", font=F["body"], size=size - 1),
                        img(f"wx_h{i}", "wx_clear_day", w=f"{icon}dp", h=f"{icon}dp", mv="3dp"),
                        text(f"m_h{i}_temp", font=F["sub"], size=size + 1),
                        id=f"cell_h{i}", w="0dp", h="wrap", weight=1))
    return row(*cols, g="center")


def daily(f, size=12, icon=22):
    F = FONTS[f]
    rows = []
    for i in range(DAYS_N):
        rows.append(row(text(f"s_d{i}_day", font=F["body"], size=size, w="0dp", weight=1, g="start", caps=True),
                        img(f"wx_d{i}", "wx_clear_day", w=f"{icon}dp", h=f"{icon}dp"),
                        text(f"s_d{i}_min", font=F["body"], size=size, w="44dp", g="end"),
                        text(f"m_d{i}_max", font=F["sub"], size=size, w="44dp", g="end"),
                        id=f"cell_d{i}", h="0dp", weight=1))
    return rows


def weather_shaped(f, size, name):
    F = FONTS[f]
    sh = f == "neon"
    if size == "bar":
        body = row(img("wx_now", "wx_partly_day", w="0dp", h="match", weight=0.7),
                   text("m_temp", font=F["main"], auto=(12, 80), w="0dp", h="match", weight=1, shadow=sh),
                   h="match", padH="8dp", padV="4dp")
    elif size == "narrow":
        body = col(img("wx_now", "wx_partly_day", w="match", h="0dp", weight=0.9),
                   text("m_temp", font=F["main"], auto=NUM_NARROW, h="0dp", weight=1, shadow=sh),
                   text("s_range", font=F["body"], auto=(6, 11)),
                   pad="4dp")
    elif size == "column":
        hours = [col(text(f"s_h{i}_time", font=F["body"], auto=(6, 10)),
                     img(f"wx_h{i}", "wx_clear_day", w="18dp", h="18dp", mv="2dp"),
                     text(f"m_h{i}_temp", font=F["sub"], auto=(7, 12)),
                     id=f"cell_h{i}", w="match", h="0dp", weight=1) for i in range(3)]
        body = col(img("wx_now", "wx_partly_day", w="match", h="0dp", weight=0.8),
                   text("m_temp", font=F["main"], auto=NUM_NARROW, h="0dp", weight=0.9, shadow=sh),
                   text("s_range", font=F["body"], auto=(6, 11)),
                   hline("if_line", mv="6dp"),
                   *hours,
                   pad="4dp")
    else:
        hours = [row(text(f"s_h{i}_time", font=F["body"], auto=(8, 12), w="0dp", weight=1.2, g="start|center_vertical"),
                     img(f"wx_h{i}", "wx_clear_day", w="20dp", h="20dp"),
                     text(f"m_h{i}_temp", font=F["sub"], auto=(9, 14), w="0dp", weight=1, g="end|center_vertical"),
                     id=f"cell_h{i}", h="0dp", weight=1) for i in range(4)]
        body = col(row(img("wx_now", "wx_partly_day", w="36dp", h="36dp"),
                       text("m_temp", font=F["main"], auto=(16, 44), w="0dp", h="52dp", weight=1, ms="6dp", shadow=sh)),
                   text("m_city", font=F["sub"], size=14),
                   text("a_desc", font=F["body"], size=11),
                   hline("if_line", mv="8dp"),
                   *hours,
                   pad="12dp")
    return layout(name, *shaped_bg(f), body)


def weather(f, size):
    F = FONTS[f]
    name = f"w_wx_{f}_{size}"
    shadow = f == "neon"
    if size in SHAPED:
        return weather_shaped(f, size, name)
    if size == "s":
        if f == "blob":
            return layout(name, *bg(f, "cloud"),
                          col(img("wxo_now", "wx_partly_day", w="match", h="0dp", weight=0.8),
                              text("o_temp", font=F["main"], auto=(12, 80), h="0dp", weight=1),
                              padH="16dp", padT="16dp", padB="10dp"))
        if f == "neon":
            return layout(name, *bg(f, "ring"),
                          col(img("wx_now", "wx_partly_day", w="match", h="0dp", weight=0.8),
                              text("m_temp", font=F["main"], auto=(10, 60), h="0dp", weight=1, shadow=True),
                              pad="20dp"))
        return layout(name, *bg(f),
                      col(img("wx_now", "wx_partly_day", w="match", h="0dp", weight=0.9),
                          text("m_temp", font=F["main"], auto=(14, 120), h="0dp", weight=1),
                          pad="10dp"))
    if size == "strip":
        body = row(img("wx_now", "wx_partly_day", w="40dp", h="match"),
                   text("m_temp", font=F["main"], auto=(14, 40), w="76dp", h="match", ms="6dp", shadow=shadow),
                   col(text("m_city", font=F["sub"], size=14, g="start"),
                       text("a_desc", font=F["body"], size=12, g="start"),
                       w="0dp", weight=1, ms="10dp", g="center_vertical|start"),
                   text("s_range", font=F["body"], size=12, w="wrap", g="end"),
                   h="match", padH="14dp", padV="6dp")
        return layout(name, *bg(f), body)
    if size == "m":
        if f == "bold":
            body = col(row(text("a_city", font=F["label"], size=12, caps=True, spacing=0.08, w="0dp", weight=1, g="start"),
                           img("wx_now", "wx_partly_day", w="30dp", h="30dp")),
                       text("m_temp", font=F["main"], auto=(30, 220), h="0dp", weight=1, g="start|center_vertical"),
                       text("s_desc", font=F["body"], size=12, g="start"),
                       pad="14dp")
        elif f == "blob":
            body = col(frame(img("sh_a", "wsh_cloud"),
                             col(img("wxo_now", "wx_partly_day", w="match", h="0dp", weight=0.75),
                                 text("o_temp", font=F["main"], auto=(14, 80), h="0dp", weight=1),
                                 padH="22dp", padT="18dp", padB="8dp"),
                             h="0dp", weight=1),
                       text("m_city", font=F["sub"], size=15, mt="6dp"),
                       text("a_desc", font=F["body"], size=12),
                       pad="12dp")
        elif f == "neon":
            body = col(img("wx_now", "wx_partly_day", w="34dp", h="34dp"),
                       text("m_temp", font=F["main"], auto=(16, 70), h="0dp", weight=1, shadow=True),
                       text("a_city", font=F["label"], size=11, caps=True, spacing=0.1),
                       text("s_desc", font=F["body"], size=11, mt="2dp"),
                       pad="16dp")
        elif f == "minimal":
            body = col(text("m_temp", font=F["main"], auto=(24, 200), h="0dp", weight=1, g="start|center_vertical"),
                       hline("if_line", mv="6dp"),
                       row(text("a_city", font=F["label"], size=11, caps=True, w="0dp", weight=1, g="start"),
                           img("wx_now", "wx_partly_day", w="22dp", h="22dp")),
                       pad="14dp")
        else:
            body = col(row(img("wx_now", "wx_partly_day", w="0dp", h="match", weight=1),
                           text("m_temp", font=F["main"], auto=(16, 80), w="0dp", h="match", weight=1.2),
                           h="0dp", weight=1),
                       text("m_city", font=F["sub"], size=15, mt="6dp"),
                       text("a_desc", font=F["body"], size=12),
                       pad="14dp")
        return layout(name, *bg(f), body)
    head = row(col(text("m_city", font=F["sub"], size=15, g="start"),
                   text("a_desc", font=F["body"], size=12, g="start"),
                   w="0dp", weight=1, g="start"),
               img("wx_now", "wx_partly_day", w="40dp", h="40dp"),
               text("m_temp", font=F["main"], size=34 if f != "neon" else 26, w="wrap", ms="6dp", shadow=shadow, includeFontPadding="false"),
               )
    if size == "wide":
        # Слева — сейчас, справа — ближайшие часы: помещается и в невысокий широкий виджет.
        now = col(row(img("wx_now", "wx_partly_day", w="34dp", h="34dp"),
                      text("m_temp", font=F["main"], auto=(16, 40), w="0dp", h="44dp", weight=1, ms="4dp", g="start|center_vertical", shadow=shadow)),
                  text("m_city", font=F["sub"], size=13, g="start"),
                  text("s_range", font=F["body"], size=11, g="start"),
                  w="0dp", weight=1, g="center_vertical|start")
        hours = row(*[col(text(f"s_h{i}_time", font=F["body"], size=10),
                          img(f"wx_h{i}", "wx_clear_day", w="20dp", h="20dp", mv="2dp"),
                          text(f"m_h{i}_temp", font=F["sub"], size=12),
                          id=f"cell_h{i}", w="0dp", h="wrap", weight=1) for i in range(4)],
                    w="0dp", weight=1.5, h="match", g="center")
        body = row(now, vline("if_vline", mv="6dp", mh="10dp"), hours, h="match", padH="14dp", padV="10dp")
        return layout(name, *bg(f), body)
    body = col(head,
               text("s_range", font=F["body"], size=12, g="start", mb="8dp"),
               hourly(f),
               hline("if_line", mv="8dp"),
               col(*daily(f), h="0dp", weight=1),
               pad="14dp")
    return layout(name, *bg(f), body)


# ---------------------------------------------------------------- батарея


def ring(size, id_="pb_ring", **kw):
    """Кольцо заряда. Кольцо рисуется по ширине, поэтому размер задаётся квадратом."""
    kw.update(id=id_, style="?android:attr/progressBarStyleHorizontal", indeterminate="false", max="100",
              progress="75", progressDrawable="@drawable/wring_progress", w=f"{size}dp", h=f"{size}dp", lg="center")
    return E("ProgressBar", attrs_of(kw))


def ring_pct(f, size, shadow=False, font=None):
    F = FONTS[f]
    return frame(ring(size),
                 text("m_pct", font=font or F["main"], auto=(8, max(12, size // 3)), w=f"{size}dp", h=f"{size}dp", lg="center",
                      pad=f"{size // 5}dp", shadow=shadow),
                 w="match", h="match")


def bar(id_="pb_bar", h="10dp", **kw):
    kw.update(id=id_, style="?android:attr/progressBarStyleHorizontal", indeterminate="false", max="100",
              progress="75", progressDrawable="@drawable/wbar_progress", w="match", h=h)
    return E("ProgressBar", attrs_of(kw))


def battery_shaped(f, size, name):
    F = FONTS[f]
    sh = f == "neon"
    bolt = img("ia_bolt", "wic_bolt", w="14dp", h="14dp")
    if size == "bar":
        body = col(row(bolt, text("m_pct", font=F["main"], auto=(12, 80), w="0dp", h="match", weight=1, shadow=sh), h="0dp", weight=1),
                   bar(h="5dp", mt="3dp"),
                   padH="10dp", padV="6dp")
    elif size == "narrow":
        body = col(bolt,
                   text("m_pct", font=F["main"], auto=NUM_NARROW, h="0dp", weight=1, shadow=sh),
                   bar(h="5dp", mt="4dp"),
                   pad="4dp")
    elif size == "column":
        body = col(bolt,
                   text("m_pct", font=F["main"], auto=NUM_NARROW, h="0dp", weight=1, shadow=sh),
                   bar(h="5dp", mv="6dp"),
                   text("s_state", font=F["body"], auto=(6, 10), lines=2),
                   text("s_temp", font=F["body"], auto=(6, 10), mt="2dp"),
                   pad="4dp")
    else:
        big = (frame(img("sh_a", "wsh_flower"), text("o_pct", font=F["main"], auto=(10, 30), pad="12dp"), h="0dp", weight=1) if f == "blob" else
               frame(ring_pct(f, 88, shadow=sh), h="0dp", weight=1) if f in ("glass", "neon", "minimal") else
               text("m_pct", font=F["main"], auto=NUM_TALL, h="0dp", weight=1))
        body = col(big,
                   row(img("ia_bolt", "wic_bolt", w="14dp", h="14dp"), text("m_state", font=F["body"], size=14, w="wrap", ms="4dp"), w="wrap", mt="6dp"),
                   text("s_remain", font=F["body"], size=11),
                   bar(h="6dp", mv="6dp") if f == "bold" else hline("if_line", mv="6dp"),
                   text("s_temp", font=F["body"], size=12),
                   text("s_health", font=F["body"], size=11),
                   pad="12dp")
    return layout(name, *shaped_bg(f), body)


def battery(f, size):
    F = FONTS[f]
    name = f"w_bat_{f}_{size}"
    shadow = f == "neon"
    if size in SHAPED:
        return battery_shaped(f, size, name)
    def state(role="a", sz=12, **kw):
        return row(img(f"i{role}_bolt", "wic_bolt", w=f"{sz}dp", h=f"{sz}dp"),
                   text(f"{role}_state", font=F["body"], size=sz, w="wrap", ms="3dp"),
                   w="wrap", **kw)
    if size == "s":
        if f == "blob":
            return layout(name, *bg(f, "flower"),
                          col(text("o_pct", font=F["main"], auto=(12, 80), h="0dp", weight=1), state("n", 10, mb="2dp"), pad="16dp"))
        if f == "bold":
            return layout(name, *bg(f),
                          col(text("m_pct", font=F["main"], auto=(16, 160), h="0dp", weight=1), bar(h="6dp", mt="4dp"), pad="12dp"))
        if f == "neon":
            return layout(name, *bg(f, "ring_only"), ring_pct(f, 52, shadow=True))
        return layout(name, *bg(f), ring_pct(f, 52, font=F["main"] if f != "minimal" else F["sub"]))
    if size == "strip":
        body = row(text("m_pct", font=F["main"], auto=(14, 40), w="84dp", h="match", shadow=shadow),
                   col(bar(h="8dp"), state("s", 12, mt="4dp", lg="start"), w="0dp", weight=1, ms="14dp"),
                   h="match", padH="16dp", padV="8dp")
        return layout(name, *bg(f), body)
    if size == "m":
        if f == "blob":
            inner = frame(img("sh_a", "wsh_flower"), text("o_pct", font=F["main"], auto=(12, 64), pad="22dp"), h="0dp", weight=1)
        elif f == "bold":
            inner = col(text("m_pct", font=F["main"], auto=(20, 200), h="0dp", weight=1), bar(h="8dp", mt="4dp"), h="0dp", weight=1)
        else:
            inner = frame(ring_pct(f, 92, shadow=shadow), h="0dp", weight=1)
        body = col(inner, state("a", 12, mt="6dp"), text("s_remain", font=F["body"], size=11), pad="12dp")
        return layout(name, *bg(f), body)
    details = row(text("s_temp", font=F["body"], size=12, w="0dp", weight=1, g="start"),
                  text("s_health", font=F["body"], size=12, w="0dp", weight=1, g="end"))
    if size == "wide":
        left = (frame(img("sh_a", "wsh_flower"), text("o_pct", font=F["main"], auto=(12, 64), pad="20dp"), w="0dp", weight=0.8) if f == "blob" else
                frame(ring_pct(f, 88, shadow=shadow), w="0dp", weight=0.8) if f in ("glass", "neon") else
                text("m_pct", font=F["main"], auto=(20, 200), w="0dp", h="match", weight=0.9, g="center_vertical|start"))
        body = row(left,
                   col(state("m", 15, lg="start"),
                       text("s_remain", font=F["body"], size=12, g="start"),
                       bar(h="8dp", mv="6dp"),
                       details,
                       w="0dp", weight=1.2, g="center_vertical", ms="14dp"),
                   h="match", pad="12dp")
        return layout(name, *bg(f), body)
    big = (frame(img("sh_a", "wsh_flower"), text("o_pct", font=F["main"], auto=(16, 120), pad="34dp"), h="0dp", weight=1) if f == "blob" else
           frame(ring_pct(f, 160, shadow=shadow), h="0dp", weight=1) if f in ("glass", "neon", "minimal") else
           text("m_pct", font=F["main"], auto=(30, 300), h="0dp", weight=1))
    body = col(text("a_title", font=F["label"], size=13, caps=True, spacing=0.06, txt="Батарея"),
               big,
               state("m", 15, mt="8dp"),
               text("s_remain", font=F["body"], size=12),
               bar(h="8dp", mv="8dp") if f == "bold" else hline("if_line", mv="8dp"),
               details,
               pad="16dp")
    return layout(name, *bg(f), body)


# ---------------------------------------------------------------- приложения (папка)

APP_STYLES = ["flower", "cookie", "glass", "neon", "capsule", "minimal"]
APP_FAMILY = {"flower": "blob", "cookie": "blob", "glass": "glass", "neon": "neon", "capsule": "blob", "minimal": "minimal"}
APPS_MAX = 8


def app_cell(i, f, icon, label, lsize=11):
    F = FONTS[f]
    ring_ = img(f"fx_ap{i}", "wfx_ring_small", w=f"{icon + 10}dp", h=f"{icon + 10}dp", lg="center") if f == "neon" else None
    icon_box = frame(ring_, img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp", lg="center"),
                     w=f"{icon + 10}dp", h=f"{icon + 10}dp") if f == "neon" else img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp")
    return col(icon_box,
               text(f"m_ap{i}_label", font=F["body"], size=lsize, mt="4dp", shadow=f == "neon") if label else None,
               id=f"cell_ap{i}", w="0dp", h="wrap", weight=1)


def app_vcell(i, f, icon):
    """Значок приложения в столбике (без подписи — в узком виджете она не помещается)."""
    ring_ = img(f"fx_ap{i}", "wfx_ring_small", w=f"{icon + 8}dp", h=f"{icon + 8}dp", lg="center") if f == "neon" else None
    return frame(ring_, img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp", lg="center"),
                 id=f"cell_ap{i}", w="match", h="0dp", weight=1)


def apps_shaped(style, size, name):
    f = APP_FAMILY[style]
    F = FONTS[f]
    layers = [img("bg_s", "wbg_r36", scale="fitXY")] if style in ("capsule", "flower", "cookie") else bg(f)
    if size == "bar":
        body = row(*[app_cell(i, f, 28, False) for i in range(3)], h="match", padH="8dp")
    elif size in ("narrow", "column"):
        n = 3 if size == "narrow" else 5
        body = col(*[app_vcell(i, f, 28) for i in range(n)], pad="4dp")
    else:
        rows = [row(*[app_cell(r * 2 + c, f, 30, False) for c in range(2)], g="center", h="0dp", weight=1) for r in range(3)]
        title = text("m_title", font=F["label"], size=13, mb="4dp", vis="gone", caps=f in ("neon", "minimal"))
        body = col(title, *rows, pad="12dp")
    return layout(name, *layers, body)


def apps(style, size):
    f = APP_FAMILY[style]
    F = FONTS[f]
    name = f"w_apps_{style}_{size}"
    if size in SHAPED:
        return apps_shaped(style, size, name)
    if size in ("s", "m"):
        icon = 22 if size == "s" else 46
        grid = col(row(app_cell(0, f, icon, False), app_cell(1, f, icon, False), g="center"),
                   row(app_cell(2, f, icon, False), app_cell(3, f, icon, False), g="center", mt="6dp" if size == "m" else "3dp"),
                   pad="16dp" if size == "s" else "22dp")
        if style == "flower":
            layers = [img("bg_a", "wsh_clover")]
        elif style == "cookie":
            layers = [img("bg_a", "wsh_cookie")]
        elif style == "capsule":
            layers = [img("bg_s", "wbg_r36", scale="fitXY")]
        elif style == "neon":
            layers = bg("neon")
        else:
            layers = bg(f)
        title = text("m_title", font=F["label"], size=12, mb="2dp", vis="gone") if size == "m" else None
        return layout(name, *layers, col(title, grid) if title else grid)
    if size == "strip":
        cells = [app_cell(i, f, 34, False) for i in range(5)]
        layers = [img("bg_s", "wbg_pill", scale="fitXY")] if style in ("capsule", "flower", "cookie") else bg(f)
        return layout(name, *layers, row(*cells, h="match", padH="12dp"))
    rows_n = 2
    # В широком виджете (от 100 dp в высоту) подписи под значками не помещаются — только значки.
    icon = 34 if size == "wide" else 48
    rows = []
    for r in range(rows_n):
        rows.append(row(*[app_cell(r * 4 + c, f, icon, size != "wide") for c in range(4)], id=f"row_apps{r}", g="center", h="0dp", weight=1))
    title = text("m_title", font=F["label"], size=13 if size == "wide" else 15, g="start", mb="4dp", vis="gone",
                 caps=f in ("neon", "minimal"), spacing=0.04)
    layers = [img("bg_s", "wbg_r36", scale="fitXY")] if style in ("capsule", "flower", "cookie") else bg(f)
    if style in ("flower", "cookie") and size == "l":
        layers.append(img("sh_a2", f"wsh_{'clover' if style == 'flower' else 'cookie'}_soft", w="140dp", h="140dp", lg="top|end", me="-30dp", mt="-30dp"))
    return layout(name, *layers, col(title, *rows, pad="14dp"))


# ---------------------------------------------------------------- генерация разметки

for f in CLOCK_FAMILIES:
    for s in SIZES:
        for dk in DIGIT_FONTS:
            clock_digital(f, s, dk)
for f in FAMILIES:
    for s in SIZES:
        calendar(f, s)
        weather(f, s)
        battery(f, s)
for st in ANALOG:
    for s in SIZES:
        for sec in (True, False):
            clock_analog(st, s, sec)
for st in APP_STYLES:
    for s in SIZES:
        apps(st, s)

# ---------------------------------------------------------------- рисунки


def vector(name, paths, size=48, dp=48, w=None, h=None):
    vw, vh = (w or size), (h or size)
    write(os.path.join(RES, "drawable", f"{name}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
{HEADER}
<vector {NS}
    android:width="{dp * vw // max(vw, vh)}dp" android:height="{dp * vh // max(vw, vh)}dp"
    android:viewportWidth="{vw}" android:viewportHeight="{vh}">
{paths}
</vector>
''')


def shape(name, body):
    write(os.path.join(RES, "drawable", f"{name}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
{HEADER}
{body}
''')


def rrect(radius, color="#FFFFFFFF", stroke=None):
    s = f'\n    <stroke android:width="{stroke[0]}" android:color="{stroke[1]}" />' if stroke else ""
    return f'''<shape {NS} android:shape="rectangle">
    <solid android:color="{color}" />
    <corners android:radius="{radius}" />{s}
</shape>'''


for name, radius in [("wbg_r18", "18dp"), ("wbg_r24", "24dp"), ("wbg_r28", "28dp"), ("wbg_r36", "36dp"), ("wbg_pill", "999dp")]:
    shape(name, rrect(radius))
shape("wsh_pill", rrect("999dp"))
# Стекло: светлая полупрозрачная заливка и тонкая светлая рамка; цвет не меняется, только прозрачность.
shape("wbg_glass", f'''<layer-list {NS}>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#47FFFFFF" />
            <corners android:radius="28dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#00000000" />
            <stroke android:width="1dp" android:color="#8CFFFFFF" />
            <corners android:radius="28dp" />
        </shape>
    </item>
</layer-list>''')
# Неон: несколько рамок с убывающей яркостью — «свечение»; цвет задаётся тинтом.
glow = []
for inset, width, alpha in [(0, 6, "1A"), (2, 4, "33"), (3.5, 2.5, "66"), (4.5, 1.5, "FF")]:
    glow.append(f'''    <item android:left="{inset}dp" android:top="{inset}dp" android:right="{inset}dp" android:bottom="{inset}dp">
        <shape android:shape="rectangle">
            <solid android:color="#00000000" />
            <stroke android:width="{width}dp" android:color="#{alpha}FFFFFF" />
            <corners android:radius="{24 - inset}dp" />
        </shape>
    </item>''')
shape("wfx_neon", f"<layer-list {NS}>\n" + "\n".join(glow) + "\n</layer-list>")
shape("wdot", f'''<shape {NS} android:shape="oval">
    <solid android:color="#FFFFFFFF" />
</shape>''')
shape("wline", rrect("1dp"))
shape("wbar", rrect("1dp"))
shape("wsh_circle", f'''<shape {NS} android:shape="oval">
    <solid android:color="#FFFFFFFF" />
</shape>''')
shape("wbar_progress", f'''<layer-list {NS}>
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <solid android:color="#FFFFFFFF" />
            <corners android:radius="99dp" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <solid android:color="#FFFFFFFF" />
                <corners android:radius="99dp" />
            </shape>
        </clip>
    </item>
</layer-list>''')
shape("wring_progress", f'''<layer-list {NS}>
    <item android:id="@android:id/background">
        <shape android:innerRadiusRatio="2.5" android:shape="ring" android:thicknessRatio="11" android:useLevel="false">
            <solid android:color="#FFFFFFFF" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <rotate android:fromDegrees="270" android:pivotX="50%" android:pivotY="50%" android:toDegrees="270">
            <shape android:innerRadiusRatio="2.5" android:shape="ring" android:thicknessRatio="11" android:useLevel="true">
                <solid android:color="#FFFFFFFF" />
            </shape>
        </rotate>
    </item>
</layer-list>''')


def wavy(lobes, depth, r=48.0, cx=50.0, cy=50.0, steps=192, rot=0.0):
    pts = []
    amp = r * depth
    for i in range(steps):
        t = 2 * math.pi * i / steps
        rr = r - amp + amp * math.cos(lobes * (t + rot))
        pts.append((cx + rr * math.sin(t), cy - rr * math.cos(t)))
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z"


def fill(path, color="#FFFFFFFF"):
    return f'    <path android:fillColor="{color}" android:pathData="{path}" />'


vector("wsh_cookie", fill(wavy(9, 0.07)), size=100, dp=100)
vector("wsh_flower", fill(wavy(6, 0.12)), size=100, dp=100)
vector("wsh_clover", fill(wavy(4, 0.17, rot=math.pi / 4)), size=100, dp=100)
vector("wsh_scallop", fill(wavy(12, 0.045)), size=100, dp=100)
vector("wsh_cookie_soft", fill(wavy(9, 0.07), "#33FFFFFF"), size=100, dp=100)
vector("wsh_clover_soft", fill(wavy(4, 0.17, rot=math.pi / 4), "#33FFFFFF"), size=100, dp=100)


def circle_path(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0z"


# Облако: несколько кругов и основание — одна заливка.
cloud_shape = "\n".join(fill(circle_path(*c)) for c in [(31, 62, 21), (52, 44, 27), (74, 58, 22), (86, 70, 13), (16, 74, 13)]) + "\n" + \
    fill("M16,62 L86,62 Q99,62 99,74 Q99,86 86,86 L16,86 Q3,86 3,74 Q3,62 16,62 Z")
vector("wsh_cloud", cloud_shape, w=102, h=90, dp=102)


def stroke_circle(cx, cy, r, width, color):
    return (f'    <path android:fillColor="#00000000" android:strokeWidth="{width}" android:strokeColor="{color}" '
            f'android:pathData="{circle_path(cx, cy, r)}" />')


ring_glow = [stroke_circle(50, 50, 44, w, c) for w, c in [(9, "#14FFFFFF"), (6, "#33FFFFFF"), (3.5, "#80FFFFFF"), (1.8, "#FFFFFFFF")]]
vector("wfx_ring", "\n".join(ring_glow), size=100, dp=100)
vector("wfx_ring_small", "\n".join(stroke_circle(50, 50, 44, w * 1.6, c) for w, c in [(6, "#26FFFFFF"), (3, "#66FFFFFF"), (1.6, "#FFFFFFFF")]), size=100, dp=100)

# Значки
vector("wic_alarm", "\n".join([
    stroke_circle(12, 13, 7.5, 2, "#FFFFFFFF"),
    '    <path android:fillColor="#00000000" android:strokeWidth="2" android:strokeColor="#FFFFFFFF" android:strokeLineCap="round" android:pathData="M12,9.5v4l2.5,1.5" />',
    '    <path android:fillColor="#00000000" android:strokeWidth="2" android:strokeColor="#FFFFFFFF" android:strokeLineCap="round" android:pathData="M4,5.5l3,-2.5M20,5.5l-3,-2.5" />',
]), size=24, dp=24)
vector("wic_bolt", fill("M13.5,2 L5,13.5 L11,13.5 L10,22 L19,10 L13,10 Z"), size=24, dp=24)
vector("wapp_placeholder", fill(circle_path(24, 24, 22), "#66FFFFFF"))

# Стрелки
def hand(name, y1, y2, width, cap="round"):
    vector(name, f'    <path android:pathData="M100,{y1}L100,{y2}" android:strokeWidth="{width}" android:strokeColor="#FFFFFFFF" android:strokeLineCap="{cap}" />',
           size=200, dp=200)


hand("hand_hour_round", 108, 54, 14)
hand("hand_minute_round", 112, 30, 10)
hand("hand_hour_thin", 104, 52, 4)
hand("hand_minute_thin", 106, 22, 2.5)


def line(x1, y1, x2, y2, w, color="#FFFFFFFF"):
    return (f'    <path android:pathData="M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}" android:strokeWidth="{w}" '
            f'android:strokeColor="{color}" android:strokeLineCap="round" />')


def dial_ticks(only_main=False, minor=True):
    out = []
    for i in range(60):
        a = math.radians(i * 6)
        main, hour = i % 15 == 0, i % 5 == 0
        if only_main and not main:
            continue
        if not minor and not hour:
            continue
        outer = 97
        ln, w, alpha = (14, 6, "FF") if main else ((9, 4, "FF") if hour else (3, 1.6, "99"))
        if only_main:
            ln, w = 13, 7
        out.append(line(100 + outer * math.sin(a), 100 - outer * math.cos(a),
                        100 + (outer - ln) * math.sin(a), 100 - (outer - ln) * math.cos(a), w, f"#{alpha}FFFFFF"))
    return out


def vector200(name, paths):
    vector(name, "\n".join(paths), size=200, dp=200)


vector200("dial_ticks", dial_ticks())
vector200("dial_minimal", dial_ticks(only_main=True))
# «Облако»: циферблат-печенье с точками на часах (точки вырезаны — сквозь них видно обои).
cookie_dial = [f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{wavy(9, 0.07, r=98, cx=100, cy=100)} ' +
               " ".join(circle_path(100 + 80 * math.sin(math.radians(h * 30)), 100 - 80 * math.cos(math.radians(h * 30)), 5 if h % 3 == 0 else 3)
                        for h in range(12)) + '" />']
vector200("dial_cookie", cookie_dial)
# «Неон»: светящееся кольцо и 12 точек.
neon_dial = [stroke_circle(100, 100, 92, w, c) for w, c in [(14, "#14FFFFFF"), (9, "#33FFFFFF"), (5, "#80FFFFFF"), (2.6, "#FFFFFFFF")]]
for h in range(12):
    a = math.radians(h * 30)
    neon_dial.append(fill(circle_path(100 + 76 * math.sin(a), 100 - 76 * math.cos(a), 4 if h % 3 == 0 else 2.2), "#FFFFFFFF"))
vector200("dial_neon", neon_dial)

# Цифры на циферблате — контуры глифов (векторы не умеют рисовать текст).
font = TTFont(os.path.join(RES, "font", "manrope_700.ttf"))
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
units = font["head"].unitsPerEm


def number_path(txt, cx, cy, height):
    scale = height / (units * 0.72)
    widths = [glyphs[cmap[ord(ch)]].width * scale for ch in txt]
    x = cx - sum(widths) / 2
    out = []
    for ch, wdt in zip(txt, widths):
        pen = SVGPathPen(glyphs)
        glyphs[cmap[ord(ch)]].draw(TransformPen(pen, (scale, 0, 0, -scale, x, cy + height / 2)))
        out.append(pen.getCommands())
        x += wdt
    return " ".join(out)


numbers = []
for i in range(60):
    a = math.radians(i * 6)
    ln, w = (5, 2.2) if i % 5 == 0 else (2.5, 1.2)
    numbers.append(line(100 + 97 * math.sin(a), 100 - 97 * math.cos(a),
                        100 + (97 - ln) * math.sin(a), 100 - (97 - ln) * math.cos(a), w, "#B3FFFFFF"))
for hh in range(1, 13):
    a = math.radians(hh * 30)
    numbers.append(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{number_path(str(hh), 100 + 78 * math.sin(a), 100 - 78 * math.cos(a), 16)}" />')
vector200("dial_numbers", numbers)


# Иконки погоды: цветные, но в «монохромном» режиме перекрашиваются целиком через colorFilter.
def circle(cx, cy, r, color):
    return f'    <path android:fillColor="{color}" android:pathData="{circle_path(cx, cy, r)}" />'


def rect(x, y, w, h, color):
    return f'    <path android:fillColor="{color}" android:pathData="M{x},{y}h{w}v{h}h{-w}z" />'


def cloud(dx=0, dy=0, s=1.0, color="#FFE6ECF5"):
    def t(x, y):
        return dx + x * s, dy + y * s
    parts = []
    for cx, cy, r in [(15, 29, 7), (24, 22, 10), (34, 28, 8)]:
        x, y = t(cx, cy)
        parts.append(circle(x, y, r * s, color))
    x, y = t(15, 24)
    parts.append(rect(round(x, 2), round(y, 2), round(19 * s, 2), round(12 * s, 2), color))
    return parts


def sun(cx, cy, r, color="#FFFFC83D"):
    parts = [circle(cx, cy, r, color)]
    for i in range(8):
        a = math.radians(i * 45)
        parts.append(line(cx + (r + 3) * math.cos(a), cy + (r + 3) * math.sin(a),
                          cx + (r + 7) * math.cos(a), cy + (r + 7) * math.sin(a), 3, color))
    return parts


def moon(cx, cy, r, color="#FFF2E6B8"):
    return [f'    <path android:fillColor="{color}" android:pathData="M{cx + r * 0.2:.2f},{cy - r:.2f}'
            f'A{r},{r} 0 1,0 {cx + r:.2f},{cy + r * 0.45:.2f}A{r * 0.8:.2f},{r * 0.8:.2f} 0 0,1 {cx + r * 0.2:.2f},{cy - r:.2f}z" />']


def drops(color, kind):
    out = []
    for i, x in enumerate([17, 25, 33]):
        if kind == "rain":
            out.append(line(x, 38, x - 3, 45, 3, color))
        elif kind == "drizzle":
            out.append(line(x, 39, x - 1.5, 42.5, 3, color))
        else:
            out.append(circle(x - 1, 41, 2.6, color))
    return out


ICONS = {
    "wx_clear_day": sun(24, 24, 9),
    "wx_clear_night": moon(22, 24, 13),
    "wx_partly_day": sun(18, 17, 6.5) + cloud(5, 7, 0.95),
    "wx_partly_night": moon(16, 15, 8) + cloud(5, 7, 0.95),
    "wx_cloudy": cloud(9, -1, 0.85, "#FFAEB8C6") + cloud(1, 6, 0.95),
    "wx_fog": cloud(2, -2, 0.95, "#FFC9D1DC") + [line(10, 40, 38, 40, 3, "#FFC9D1DC"), line(14, 45, 34, 45, 3, "#FFC9D1DC")],
    "wx_drizzle": cloud(2, -3, 0.95) + drops("#FF5AB2FF", "drizzle"),
    "wx_rain": cloud(2, -3, 0.95) + drops("#FF3D9BFF", "rain"),
    "wx_snow": cloud(2, -3, 0.95) + drops("#FFFFFFFF", "snow"),
    "wx_thunder": cloud(2, -4, 0.95, "#FF9AA5B4") + [
        '    <path android:fillColor="#FFFFC83D" android:pathData="M26,30 L18,40 L23,40 L20,47 L30,36 L25,36 L28,30 Z" />'],
}
for name, parts in ICONS.items():
    vector(name, "\n".join(parts))

# ---------------------------------------------------------------- Kotlin

# Код роли элемента: какой цвет или действие ему положено (см. WidgetRenderer.paint).
ROLE_CODES = [
    ("bo_", "P"), ("bg_s", "b"), ("bg_a", "B"), ("bg_g", "g"), ("sh_a", "h"), ("fx_", "x"), ("pb_", "p"),
    ("im_", "M"), ("is_", "S"), ("if_", "F"), ("ia_", "A"), ("io_", "O"), ("in_", "N"),
    ("m_", "m"), ("s_", "s"), ("f_", "f"), ("a_", "a"), ("o_", "o"), ("n_", "n"),
]


def role_of(i):
    for prefix, code in ROLE_CODES:
        if i.startswith(prefix):
            return code
    return "."


def slot_of(i):
    base = i[:-3] if i.endswith("_al") else i
    m = re.match(r"^(m|s|f|a|o|n|bo)_(.+)$", base)
    if m:  # текст: слот без роли, чтобы «m_temp» и «o_temp» получили одно значение
        return m.group(2)
    t = re.match(r"^(im|is|if|ia|io|in)_(.+)$", base)
    if t:  # значки — отдельное пространство имён, чтобы не поставить текст в ImageView
        return "i_" + t.group(2)
    if base.startswith(("bg_", "sh_", "fx_", "box", "widget_")):
        return None
    return base


slots = OrderedDict()
for i in ALL_IDS:
    sl = slot_of(i)
    if sl:
        slots.setdefault(sl, []).append(i)

kt = ["package app.synctheme.mobile.widget", "", "import app.synctheme.R", "",
      "// Сгенерировано scripts/gen_widgets.py — не редактировать вручную.", "",
      "/**",
      " * Разметка виджета: ресурс, есть ли подложка-фигура акцентного цвета и все элементы с ролями.",
      " * roles — по два символа на элемент: роль (m — основной текст, b — подложка …) и признак",
      " * выравнивания (l — текст, L — контейнер, . — нет).",
      " */",
      "class WidgetLayout(val res: Int, val accentBackground: Boolean, val roles: String, val ids: IntArray) {",
      "    private val present = ids.toHashSet()",
      "",
      "    fun has(id: Int) = id in present",
      "}",
      "",
      "object WidgetLayouts {",
      "    private val cache = HashMap<String, WidgetLayout?>()",
      "",
      "    /** Ключ — «вид_стиль_размер», например «wx_blob_m». */",
      "    @Synchronized",
      "    operator fun get(key: String): WidgetLayout? = cache.getOrPut(key) {",
      "        " + " ?: ".join(f"{k}(key)" for k in ["clock", "cal", "wx", "bat", "apps"]),
      "    }",
      ]
for kind in ["clock", "cal", "wx", "bat", "apps"]:
    groups = [kind] if kind != "clock" else [f"clock_{f}" for f in CLOCK_FAMILIES] + ["clock_analog"]
    if kind == "clock":
        kt += ["", "    private fun clock(key: String): WidgetLayout? = " + " ?: ".join(f"{g}(key)" for g in groups)]
    for group in groups:
      kt += ["", f"    private fun {group}(key: String): WidgetLayout? = when (key) {{"]
      for name, idl in LAYOUTS.items():
        if not name.startswith(f"w_{group}_"):
            continue
        codes = "".join(role_of(i) + ("L" if i.startswith("box") and i.endswith("_al") else "l" if i.endswith("_al") else ".") for i in idl)
        accent_bg = "true" if "bg_a" in idl else "false"
        kt.append(f'        "{name[2:]}" -> WidgetLayout(R.layout.{name}, {accent_bg}, "{codes}", intArrayOf({", ".join("R.id." + i for i in idl)}))')
      kt += ["        else -> null", "    }"]
kt += ["}", "", "/** Слот данных → все элементы, которые его показывают (в разных стилях — в разных ролях). */", "object WidgetSlots {",
       "    private val map: Map<String, IntArray> = mapOf("]
for slot, values in slots.items():
    kt.append(f'        "{slot}" to intArrayOf({", ".join("R.id." + v for v in values)}),')
kt += ["    )", "", "    operator fun get(slot: String): IntArray = map[slot] ?: IntArray(0)", "}"]
write(os.path.join(KT, "WidgetIds.kt"), "\n".join(kt) + "\n")
print(f"layouts: {len(LAYOUTS)}, ids: {len(ALL_IDS)}, slots: {len(slots)}")
