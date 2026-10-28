#!/usr/bin/env python3
"""Генерирует ресурсы виджетов SyncTheme: разметку всех стилей и размеров, фигуры, циферблаты,
иконки погоды и Kotlin-таблицы идентификаторов. Запуск: python3 scripts/gen_widgets.py

RemoteViews не умеют менять шрифт и состав элементов на лету, поэтому на каждое сочетание
«вид × стиль × размер» нужна своя разметка — её проще генерировать, чем писать руками.

Соглашение об идентификаторах: «роль_слот». Роль задаёт цвет, который ставит WidgetRenderer:
  m_ — основной текст, s_ — приглушённый, f_ — бледный, a_ — акцент,
  o_ — текст на акцентной фигуре, n_ — приглушённый текст на фигуре;
  im_/is_/if_/ia_/io_ — значки и линии в тех же цветах;
  bg_s — подложка (цвет поверхности стиля), bg_g — подложка со своими цветами (стекло, бумага, табло),
  у неё меняется только прозрачность; tile_ — плашки «Табло» (не перекрашиваются);
  fx_g — свечение «Неона», wx_ — иконки погоды, pb_ — индикаторы,
  bo_ — текст на акцентной плашке (фон-плашка красится в акцент),
  ap_ — значки приложений, row_/cell_ — строки, которые можно скрыть.
Слот — часть после роли: один слот в разных стилях может быть в разных ролях (m_temp и o_temp).
"""
import math
import os
import re
from collections import OrderedDict


ROOT = os.path.join(os.path.dirname(__file__), "..", "mobile", "src", "main")
RES = os.path.join(ROOT, "res")
KT = os.path.join(ROOT, "java", "app", "synctheme", "mobile", "widget")
HEADER = "<!-- Сгенерировано scripts/gen_widgets.py — не редактировать вручную. -->"
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

# Пять стилей, непохожих друг на друга: «Стекло» — матовое стекло, «Плакат» — сплошной цвет акцента
# и огромные цифры, «Неон» — светящийся контур на чёрном, «Бумага» — лист бумаги с засечками,
# «Табло» — графитовая панель, цифры на перекидных плашках.
FAMILIES = ["glass", "poster", "neon", "paper", "board"]
CLOCK_FAMILIES = FAMILIES
# Раскладка, от которой стиль отталкивается: «Плакат» и «Табло» — крупные цифры, «Бумага» — тонкие линии.
BASE = {"glass": "glass", "poster": "bold", "neon": "neon", "paper": "minimal", "board": "bold"}


def B(f):
    return BASE[f]



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

# Подписи и числа — системными шрифтами: шрифты из ресурсов приложения лаунчер в виджетах не загружает
# (цифры часов рисуются картинкой, см. TIME_IMAGES). «семейство:начертание».
FONTS = {
    "glass": dict(main="sans-serif-light", sub="sans-serif", label="sans-serif-medium", body="sans-serif"),
    "poster": dict(main="sans-serif-condensed:bold", sub="sans-serif-condensed", label="sans-serif-condensed:bold",
                   body="sans-serif-condensed"),
    "neon": dict(main="sans-serif-thin", sub="sans-serif-light", label="sans-serif-light", body="sans-serif-light"),
    "paper": dict(main="serif", sub="serif", label="serif:italic", body="serif"),
    "board": dict(main="monospace:bold", sub="monospace", label="monospace:bold", body="monospace"),
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


def flat(children):
    for c in children:
        if isinstance(c, (list, tuple)):
            yield from flat(c)
        elif c is not None:
            yield c


class E:
    def __init__(self, tag, attrs, children=()):
        self.tag, self.attrs, self.children = tag, attrs, list(flat(children))

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
PREVIEW_TINT = {"bg_s": "#E61B1B24", "sh_a": "#FF8B6CF6", "fx_": "#FF8B6CF6"}


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
         lines=1, spacing=None, shadow=False, txt=None, ellipsize=True, **kw):
    tag = "TextClock" if clock else "TextView"
    kw.update(id=id_, w=w, h=h, g=g)
    h = kw["h"]
    if clock:
        kw["format12Hour"], kw["format24Hour"] = clock
    if font:
        family, _, style = font.partition(":")
        kw["fontFamily"] = family
        if style:
            kw["textStyle"] = style
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
        if ellipsize:
            kw["ellipsize"] = "end"
    if shadow:
        kw.update(shadowColor="#80FFFFFF", shadowRadius="14", shadowDx="0", shadowDy="0")
    kw["textColor"] = "#FFFFFFFF"
    if txt:
        kw["text"] = txt
    images = time_images(id_, clock, font, kw)
    node = E(tag, attrs_of(kw))
    return [node] + images if images else node


# Лаунчер собирает виджет в «ограниченном» контексте, где TextView не загружает шрифты из ресурсов
# приложения: цифры часов показывались бы системным шрифтом. Поэтому рядом с каждыми цифрами — картинка
# на то же место (те же размеры и отступы); WidgetRenderer рисует в неё время выбранным шрифтом
# и обновляет раз в минуту, а TextClock прячет.
TIME_CODES = {"H:mm": "t", "HH": "h", "mm": "m", "HH\\nmm": "w", "HH mm": "s"}
DIGIT_FAMILIES = {d["family"] for d in DIGIT_FONTS.values() if d["family"].startswith("@font/")}
TIME_IMAGES = OrderedDict()  # id картинки -> (id цифр, код формата, вариант выравнивания)
LAYOUT_KEYS = ("w", "h", "weight", "lg", "m", "mt", "mb", "ms", "me", "mh", "mv",
               "pad", "padH", "padV", "padT", "padB", "padS", "padE")


def time_images(id_, clock, font, kw):
    code = TIME_CODES.get(clock[1]) if clock else None
    if not code or font not in DIGIT_FAMILIES:
        return []
    base = {k: kw[k] for k in LAYOUT_KEYS if k in kw}
    g = kw.get("g", "center")
    fixed = "fitStart" if ("start" in g or "top" in g) else "fitEnd" if ("end" in g or "bottom" in g) else "fitCenter"
    variants = [("_s", "s", "fitStart"), ("_c", "c", "fitCenter"), ("_e", "e", "fitEnd")] if id_.endswith("_al") else [("", ".", fixed)]
    out = []
    for suffix, align, scale in variants:
        image = f"ti{code}_{id_}{suffix}"
        TIME_IMAGES[image] = (id_, code, align)
        out.append(E("ImageView", attrs_of(dict(base, id=image, scale=scale, vis="gone", importantForAccessibility="no"))))
    return out


LAYOUTS = OrderedDict()  # имя -> список id в порядке появления

# Старые сгенерированные разметки и рисунки удаляем: набор стилей и шрифтов мог измениться.
for old in os.listdir(os.path.join(RES, "layout")):
    if old.startswith("w_") and old.endswith(".xml"):
        os.remove(os.path.join(RES, "layout", old))
for old in os.listdir(os.path.join(RES, "drawable")):
    path = os.path.join(RES, "drawable", old)
    if old.endswith(".xml") and HEADER in open(path, encoding="utf-8").read():
        os.remove(path)


def layout(name, *layers):
    if name.startswith(("w_wx_", "w_clock_")):
        layers = with_sky(layers)
    root = frame(*layers, id="widget_root")
    xml = root.render(0).replace("<FrameLayout", f"<FrameLayout\n    {NS}", 1)
    write(os.path.join(RES, "layout", f"{name}.xml"), f'<?xml version="1.0" encoding="utf-8"?>\n{HEADER}\n' + xml + "\n")
    LAYOUTS[name] = list(OrderedDict.fromkeys(re.findall(r'@\+id/(\w+)', xml)))


# ---------------------------------------------------------------- подложки стилей


def bg(family, shaped=None):
    """Слои подложки. shaped — у «Неона» в маленьких размерах круг со светящимся кольцом."""
    if family == "glass":
        return [img("bg_g", "wbg_glass", scale="fitXY")]
    if family == "neon":
        if shaped == "ring_only":
            return [img("bg_s", "wsh_circle")]
        if shaped:
            return [img("bg_s", "wsh_circle"), img("fx_g", "wfx_ring")]
        return [img("bg_s", "wbg_r24", scale="fitXY"), img("fx_g", "wfx_neon", scale="fitXY")]
    if family == "poster":
        # Сплошной цвет акцента (его ставит WidgetRenderer) и почти прямые углы — как лист плаката.
        return [img("bg_s", "wbg_r10", scale="fitXY")]
    if family == "paper":
        return [img("bg_g", "wbg_paper", scale="fitXY")]
    return [img("bg_g", "wbg_board", scale="fitXY")]


def tile(id_, node, square=False, **kw):
    """«Табло»: число на перекидной плашке — тёмная плашка с щелью посередине, цифры поверх."""
    return frame(img(id_, "wtile_sq" if square else "wtile", scale="fitXY"), node, **kw)


def T(f, id_, node, pad="4dp"):
    """У «Табло» главное значение — на плашке: место и вес элемента переходят к плашке, текст заполняет её."""
    if f != "board":
        return node
    outer = OrderedDict((k, node.attrs.pop(k)) for k in list(node.attrs) if k.startswith("android:layout_"))
    node.attrs["android:layout_width"] = "match_parent"
    node.attrs["android:layout_height"] = "match_parent"
    node.attrs.setdefault("android:padding", pad)
    return E("FrameLayout", outer, [img(id_, "wtile", scale="fitXY"), node])


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


def shaped_bg(f):
    """Подложка вытянутых размеров: у «Неона» — прямоугольник со свечением, а не круг."""
    return bg(f)


# Небо: у погоды — под текущую погоду (днём и ночью), у часов — под время суток. Лежит под
# подложкой стиля и показывается вместо неё; скругление — как у подложки, оно зашито в имя
# элемента (sc_sky28). Добавляется ко всем разметкам часов и погоды с прямоугольной подложкой.
SCENES = OrderedDict([
    ("clear_day", ("#FF2F7FE6", "#FF3E9BEA", "#FF6CC4F0")),
    ("clear_night", ("#FF070B22", "#FF141E45", "#FF2C3B6E")),
    ("cloudy_day", ("#FF4D5E74", "#FF66798F", "#FF8C9DB0")),
    ("cloudy_night", ("#FF161C27", "#FF232B39", "#FF3A4556")),
    ("rain", ("#FF1E2A38", "#FF2D4053", "#FF46617A")),
    ("storm", ("#FF140F26", "#FF2A2145", "#FF4A3F6B")),
    ("snow", ("#FF3F5873", "#FF5A7591", "#FF8098B2")),
    ("fog", ("#FF4E555E", "#FF656D77", "#FF858D96")),
    # Время суток — для часов.
    ("dawn", ("#FF2B2F6B", "#FF8E5A8C", "#FFE2906F")),
    ("day", ("#FF2B78DE", "#FF4597E8", "#FF7DBDF0")),
    ("sunset", ("#FF2A1B4F", "#FFB33F68", "#FFE88A55")),
    ("evening", ("#FF0E1738", "#FF27295E", "#FF443A72")),
    ("night", ("#FF04060F", "#FF0B1230", "#FF19244C")),
])
SKY_RADII = [28]
# Подложка стиля → скругление неба: небо есть только под «Стеклом» (у остальных стилей свой фон).
SKY_FOR = {"wbg_glass": 28}


def wx_bg(f):
    return bg(f)


def with_sky(layers):
    """Небо под прямоугольной подложкой стиля (если она есть и это не «Неон»)."""
    srcs = {l.attrs.get("android:id"): l.attrs.get("android:src", "") for l in layers if isinstance(l, E)}
    if any(i and i.startswith("@+id/fx_") for i in srcs):
        return layers
    for i, src in srcs.items():
        if i in ("@+id/bg_s", "@+id/bg_g"):
            rad = SKY_FOR.get(src.replace("@drawable/", ""))
            if rad:
                return (img(f"sc_sky{rad}", f"wsc_day_r{rad}", scale="fitXY", vis="gone"),) + tuple(layers)
    return layers


# ---------------------------------------------------------------- вертикальные формы
#
# Столбики (narrow, column) и высокие (tall) — зеркало полосок: там главное значение во всю высоту,
# а подписи рядом; здесь главное значение во всю ширину, а подписи и строки под ним — одной группой
# по центру. Кегль главного значения и отступы, срезающие пустые поля шрифта над и под цифрами,
# ставит VerticalFit по настоящему размеру виджета; он же прячет строки (часы прогноза, события,
# значки), которые не помещаются целиком. Размеры элементов — в VSPEC, их же получает Kotlin.

VERTICAL = ("narrow", "column", "tall")
HOURS_V = 6

# Отступы, промежутки, значки и полоса заряда — dp; высоты подписей и строк (…Box, …Sp) — sp:
# они растут вместе с размером шрифта телефона.
VSPEC = {
    "column": dict(padH=5, padV=12, gap=6, div=10, label=10, labelBox=14, small=9, smallBox=12, icon=26,
                   hourSp=25, hourDp=26, eventSp=12, eventDp=14, hintSp=0, alarmSp=12, alarmDp=0,
                   bar=8, appIcon=30, appGap=10, titleBox=0, cityBox=0, descBox=0, stateBox=24, extraBox=0, main=28),
    "tall": dict(padH=12, padV=16, gap=6, div=10, label=12, labelBox=17, small=11, smallBox=15, icon=40,
                 hourSp=26, hourDp=0, eventSp=30, eventDp=8, hintSp=44, alarmSp=16, alarmDp=0,
                 bar=12, appIcon=38, appGap=14, titleBox=17, cityBox=18, descBox=28, stateBox=17, extraBox=14, main=52),
}
VSPEC["narrow"] = VSPEC["column"]
VSPEC_KEYS = [k for k in VSPEC["tall"] if k not in ("label", "small", "main")]


def vtext(id_, font, sp, box, clock=None, caps=False, spacing=None, lines=1, **kw):
    """Подпись в строке постоянной высоты (sp): автоподбор только уменьшает кегль, если не влезает по ширине."""
    return text(id_, clock=clock, font=font, auto=(6, sp), h=f"{box}sp", caps=caps, spacing=spacing, lines=lines, **kw)


def vmain(id_, font, v, clock=None, shadow=False, **kw):
    """Главное значение: кегль и отрицательные отступы по полям шрифта ставит VerticalFit."""
    return text(id_, clock=clock, font=font, h="wrap", lines=1, ellipsize=False, shadow=shadow,
                textSize=f"{v['main']}dp", includeFontPadding="false", **kw)


def vdivider(v, size):
    return img("if_line", "wline", w="40dp" if size == "tall" else "20dp", h="1dp", scale="fitXY", mv=f"{v['div']}dp")


def vbody(v, *children):
    return col(spacer(), *children, spacer(), id="box_v", padH=f"{v['padH']}dp", padV=f"{v['padV']}dp")


def clock_vertical(f, size, F, name):
    v = VSPEC[size]
    tall = size == "tall"
    sh = f == "neon"
    # Как в полосках: у «Стекла» и «Минимала» время и дата разделены линией, у остальных — промежутком.
    divider = vdivider(v, size) if B(f) in ("glass", "minimal") else None
    alarm = row(img("is_alarm", "wic_alarm", w=f"{v['small'] + 2}dp", h=f"{v['small'] + 2}dp"),
                text("s_alarm", font=F["body"], size=v["small"], w="wrap", g="start", ms="3dp"),
                id="row_alarm", w="wrap", h=f"{v['alarmSp']}sp", mt=f"{v['gap']}dp")
    hours = vmain("m_hours", F["time"], v, clock=HOURS, shadow=sh, mt=f"{v['gap']}dp")
    minutes = vmain("m_minutes", F["time"], v, clock=MINS, shadow=sh)
    if f == "board":
        # Плашки по высоте цифр: кегль и отступы цифр внутри ставит VerticalFit.
        hours = tile("tile_hours", hours, h="wrap")
        minutes = tile("tile_minutes", minutes, h="wrap", mt="3dp")
    body = vbody(v,
                 vtext("a_weekday", F["label"], v["label"], v["labelBox"], clock=WEEKDAY if tall else WEEKDAY_SHORT, caps=True, spacing=0.06),
                 hours,
                 minutes,
                 divider,
                 vtext("s_date", F["sub"], v["label"], v["labelBox"], clock=DAY_MONTH if tall else ("d MMM", "d MMM"),
                       mt=None if divider else f"{v['div']}dp"),
                 alarm)
    return layout(name, *shaped_bg(f), body)


def calendar_vertical(f, size, F, name):
    v = VSPEC[size]
    tall = size == "tall"
    rows = []
    for i in range(EVENTS):
        if tall:
            rows.append(row(img(f"ed_{i}", "wpill", w="3dp", h="26dp", scale="fitXY"),
                            col(text(f"m_ev{i}_title", font=F["body"], size=12, g="start", h="16sp"),
                                text(f"s_ev{i}_time", font=F["body"], size=10, g="start", h="14sp"),
                                w="0dp", weight=1, h="wrap", ms="6dp"),
                            id=f"row_ev{i}", mv="4dp"))
        else:
            rows.append(col(img(f"ed_{i}", "wdot", w="6dp", h="6dp"),
                            text(f"s_ev{i}_time", font=F["body"], auto=(6, v["small"]), h=f"{v['smallBox']}sp", mt="2dp"),
                            id=f"row_ev{i}", h="wrap", mv="3dp"))
    hint = text("s_ev_empty", font=F["body"], auto=(7, 11), lines=3, h=f"{v['hintSp']}sp", txt="Событий нет", vis="gone") if tall else None
    body = vbody(v,
                 vtext("a_weekday", F["label"], v["label"], v["labelBox"], clock=WEEKDAY if tall else WEEKDAY_SHORT, caps=True, spacing=0.06),
                 tile("tile_day", vmain("m_day", F["main"], v, clock=DAY, mt=f"{v['gap']}dp"), h="wrap") if f == "board" else
                 vmain("m_day", F["main"], v, clock=DAY, shadow=f == "neon", mt=f"{v['gap']}dp"),
                 vtext("s_month", F["sub"], v["label"], v["labelBox"], clock=MONTH if tall else ("LLL", "LLL"), caps=B(f) == "minimal" or not tall),
                 vdivider(v, size),
                 *rows,
                 hint)
    return layout(name, *shaped_bg(f), body)


def weather_vertical(f, size, F, name):
    v = VSPEC[size]
    tall = size == "tall"
    hours = []
    for i in range(HOURS_V):
        if tall:
            hours.append(row(text(f"s_h{i}_time", font=F["body"], auto=(7, 11), w="0dp", h="match", weight=1, g="start|center_vertical"),
                             img(f"wx_h{i}", "wx_clear_day", w="16dp", h="16dp", mh="3dp"),
                             text(f"m_h{i}_temp", font=F["sub"], size=12, w="wrap", g="end|center_vertical"),
                             id=f"cell_h{i}", h=f"{v['hourSp']}sp"))
        else:
            hours.append(col(text(f"s_h{i}_time", font=F["body"], auto=(6, 9), h="12sp"),
                             img(f"wx_h{i}", "wx_clear_day", w="16dp", h="16dp", mv="2dp"),
                             text(f"m_h{i}_temp", font=F["sub"], auto=(6, 10), h="13sp"),
                             id=f"cell_h{i}", h="wrap", mv="3dp"))
    body = vbody(v,
                 img("wx_now", "wx_partly_day", w=f"{v['icon']}dp", h=f"{v['icon']}dp"),
                 tile("tile_temp", vmain("m_temp", F["main"], v, mt=f"{v['gap']}dp"), h="wrap") if f == "board" else
                 vmain("m_temp", F["main"], v, shadow=f == "neon", mt=f"{v['gap']}dp"),
                 vtext("m_city", F["sub"], 13, v["cityBox"]) if tall else None,
                 text("a_desc", font=F["body"], auto=(7, 11), lines=2, h=f"{v['descBox']}sp") if tall else None,
                 vtext("s_range", F["body"], v["small"], v["smallBox"]),
                 vdivider(v, size),
                 *hours)
    return layout(name, *wx_bg(f), body)


def battery_vertical(f, size, F, name):
    v = VSPEC[size]
    tall = size == "tall"
    # Полоса заряда растёт снизу вверх и занимает всю свободную высоту — как полоска в 4×1 по ширине.
    vbar = E("ProgressBar", attrs_of(dict(id="pb_bar", style="?android:attr/progressBarStyleHorizontal", indeterminate="false",
                                          max="100", progress="75", progressDrawable="@drawable/wbar_vprogress",
                                          w=f"{v['bar']}dp", h="match", lg="center_horizontal", minHeight="0dp", maxHeight="4000dp")))
    if tall:
        state = row(img("is_bolt", "wic_bolt", w="12dp", h="12dp"),
                    text("s_state", font=F["body"], auto=(7, 12), w="0dp", weight=1, h="match"),
                    h=f"{v['stateBox']}sp")
        extras = [vtext("s_remain", F["body"], 10, v["extraBox"]), vtext("s_temp", F["body"], 10, v["extraBox"])]
    else:
        state = text("s_state", font=F["body"], auto=(6, v["small"]), lines=2, h=f"{v['stateBox']}sp")
        extras = []
    body = col(img("ia_bolt", "wic_bolt", w="12dp", h="12dp", mb="2dp") if not tall else None,
               tile("tile_pct", vmain("m_pct", F["main"], v), h="wrap") if f == "board" else vmain("m_pct", F["main"], v, shadow=f == "neon"),
               frame(vbar, id="box_vbar", h="0dp", weight=1, mv=f"{v['div']}dp"),
               state,
               *extras,
               id="box_v", padH=f"{v['padH']}dp", padV=f"{v['padV']}dp")
    return layout(name, *shaped_bg(f), body)


def apps_vertical(f, size, name, layers):
    F = FONTS[f]
    v = VSPEC[size]
    icon = v["appIcon"]

    def cell(i, **kw):
        ring_ = img(f"fx_ap{i}", "wfx_ring_small", w=f"{icon + 8}dp", h=f"{icon + 8}dp", lg="center") if f == "neon" else None
        return frame(ring_, img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp", lg="center"), id=f"cell_ap{i}", **kw)
    # Значки делят высоту поровну, как в полоске — ширину; лишние (не влезают целиком) прячет VerticalFit.
    if size == "tall":
        rows = [row(*[cell(r * 2 + c, w="0dp", h="match", weight=1) for c in range(2)], id=f"row_vap{r}", h="0dp", weight=1)
                for r in range(APPS_MAX // 2)]
        title = text("m_title", font=F["label"], size=12, h=f"{v['titleBox']}sp", vis="gone", caps=f != "glass")
        body = col(title, *rows, id="box_v", padH=f"{v['padH']}dp", padV=f"{v['padV']}dp")
    else:
        body = col(*[cell(i, w="match", h="0dp", weight=1) for i in range(APPS_MAX)], id="box_v",
                   padH=f"{v['padH']}dp", padV=f"{v['padV']}dp")
    return layout(name, *layers, body)


# ---------------------------------------------------------------- часы: цифровые


def clock_digital(f, size, digit_key):
    F = dict(FONTS[f])
    F["digits"] = DIGIT_FONTS[digit_key]
    F["time"] = F["digits"]["family"]
    name = f"w_clock_{f}_{size}_{digit_key}"
    sh = f == "neon"
    if f == "board" and size not in VERTICAL:
        return clock_board(f, size, F, name)
    if size in SHAPED:
        return clock_shaped(f, size, F, name)
    if B(f) == "bold":
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

    if B(f) == "glass":
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


def clock_board(f, size, F, name):
    """«Табло»: часы и минуты — на двух перекидных плашках, подписи — моноширинным шрифтом, как на вокзале."""
    def digits(id_, fmt, auto, **kw):
        return tile(f"tile_{id_[2:]}", text(id_, clock=fmt, font=F["time"], auto=auto, h="match", padH="5dp", padV="3dp"), **kw)

    def pair(auto, gap, **kw):
        return row(digits("m_hours", HOURS, auto, w="0dp", h="match", weight=1),
                   digits("m_minutes", MINS, auto, w="0dp", h="match", weight=1, ms=gap), **kw)

    if size == "s":
        body = col(digits("m_hours", HOURS, (12, 200), h="0dp", weight=1),
                   digits("m_minutes", MINS, (12, 200), h="0dp", weight=1, mt="3dp"),
                   pad="6dp")
    elif size == "bar":
        body = pair((12, 200), "4dp", h="match", padH="8dp", padV="6dp")
    elif size == "strip":
        body = row(pair((14, 200), "4dp", w="0dp", h="match", weight=1.3),
                   col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.08),
                       text("s_date", clock=DAY_MONTH, font=F["sub"], size=13, g="start", caps=True),
                       w="0dp", weight=1, g="center_vertical|start", ms="12dp"),
                   h="match", padH="10dp", padV="8dp")
    elif size == "m":
        body = col(pair((16, 300), "5dp", h="0dp", weight=1),
                   text("a_date", clock=("EEE d MMM", "EEE d MMM"), font=F["label"], size=12, caps=True, spacing=0.08, mt="8dp"),
                   pad="12dp")
    elif size == "wide":
        body = row(pair((20, 300), "6dp", w="0dp", h="match", weight=1.4),
                   col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=13, caps=True, g="start", spacing=0.08),
                       text("s_date", clock=DAY_MONTH, font=F["sub"], size=15, g="start", caps=True, mt="2dp"),
                       alarm_row(f, mt="8dp", lg="start"),
                       w="0dp", weight=1, g="center_vertical|start", ms="14dp"),
                   h="match", pad="14dp")
    else:
        body = col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=14, caps=True, spacing=0.12),
                   pair((24, 400), "8dp", h="0dp", weight=1, mv="10dp"),
                   text("s_date", clock=DATE_LONG, font=F["sub"], size=15, caps=True, spacing=0.04),
                   alarm_row(f, mt="4dp"),
                   pad="16dp")
    return layout(name, *bg(f), body)


def clock_shaped(f, size, F, name):
    """Цифровые часы в вытянутых размерах: полоска 2×1; столбики 1×N и высокий 2×N — в clock_vertical."""
    sh = f == "neon"
    if size in VERTICAL:
        return clock_vertical(f, size, F, name)
    if size == "bar":
        body = col(text("m_time", clock=TIME, font=F["time"], auto=(12, 200), h="0dp", weight=1, shadow=sh),
                   text("s_date", clock=DATE_SHORT, font=F["sub"], auto=(7, 12), caps=B(f) in ("neon", "minimal")),
                   padH="10dp", padV="4dp")
    return layout(name, *shaped_bg(f), body)


# ---------------------------------------------------------------- календарь

EVENTS = 3


def event_rows(f, n, size=13, compact=False, inline=False):
    """События: название и время под ним, только название (compact) или время справа в той же строке (inline)."""
    F = FONTS[f]
    rows = []
    for i in range(n):
        if inline:
            rows.append(row(img(f"ed_{i}", "wpill", w="3dp", h="16dp", scale="fitXY"),
                            text(f"m_ev{i}_title", font=F["body"], size=size, g="start", w="0dp", weight=1, ms="8dp"),
                            text(f"s_ev{i}_time", font=F["body"], size=size - 2, g="end", w="wrap", ms="6dp"),
                            id=f"row_ev{i}", mv="2dp"))
            continue
        rows.append(row(img(f"ed_{i}", "wpill", w="3dp", h="16dp" if compact else "30dp", scale="fitXY"),
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
    today = {"neon": "wfx_ring_small", "paper": "wbar", "poster": "wsq", "board": "wtile_sq"}.get(f, "wdot")
    for r in range(6):
        cells = []
        for c in range(7):
            i = r * 7 + c
            cells.append(frame(
                img(f"tb_{i}", today, w="26dp" if today != "wtile_sq" else "22dp", h="26dp" if today != "wbar" else "2dp",
                    lg="center" if today != "wbar" else "bottom|center_horizontal", scale="fitXY", vis="gone"),
                text(f"m_cd{i}", font=font, size=12, h="match"),
                w="0dp", h="match", weight=1))
        rows.append(row(*cells, h="0dp", weight=1, g="center"))
    return rows


def calendar_shaped(f, size, name):
    F = FONTS[f]
    sh = f == "neon"
    if size in VERTICAL:
        return calendar_vertical(f, size, F, name)
    if size == "bar":
        body = row(T(f, "tile_day", text("m_day", clock=DAY, font=F["main"], auto=(12, 120), w="0dp", h="match", weight=0.8, shadow=sh)),
                   col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 13), caps=True, g="start"),
                       text("s_month", clock=("LLL", "LLL"), font=F["sub"], auto=(7, 13), caps=True, g="start"),
                       w="0dp", weight=1, g="center_vertical|start", ms="6dp"),
                   h="match", padH="10dp", padV="4dp")
    return layout(name, *shaped_bg(f), body)


def calendar(f, size):
    F = FONTS[f]
    name = f"w_cal_{f}_{size}"
    neon = f == "neon"
    if size in SHAPED:
        return calendar_shaped(f, size, name)
    if size == "s":
        if neon:
            return layout(name, *bg(f, "ring"),
                          col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 13), caps=True),
                              text("m_day", clock=DAY, font=F["main"], auto=(12, 120), h="0dp", weight=1, shadow=True),
                              pad="18dp"))
        return layout(name, *bg(f),
                      col(text("a_weekday_al", clock=WEEKDAY_SHORT, font=F["label"], auto=(8, 16), caps=True, spacing=0.08),
                          T(f, "tile_day", text("m_day_al", clock=DAY, font=F["main"], auto=(16, 300), h="0dp", weight=1)),
                          pad="10dp" if f == "glass" else "8dp"))
    if size == "strip":
        body = row(T(f, "tile_day", text("m_day", clock=DAY, font=F["main"], auto=(16, 120), w="64dp", h="match", shadow=neon)),
                   col(text("a_weekday", clock=("EEEE, LLLL", "EEEE, LLLL"), font=F["label"], size=12, caps=True, g="start", spacing=0.05),
                       text("m_ev0_title", font=F["body"], size=15, g="start"),
                       w="0dp", weight=1, g="center_vertical|start", ms="12dp"),
                   h="match", padH="14dp", padV="8dp")
        return layout(name, *bg(f), body)
    if size == "m":
        if neon:
            head = frame(img("fx_g2", "wfx_ring"),
                         col(text("a_weekday", clock=WEEKDAY_SHORT, font=F["label"], auto=(7, 12), caps=True),
                             text("m_day", clock=DAY, font=F["main"], auto=(12, 80), h="0dp", weight=1, shadow=True), pad="18dp"),
                         h="0dp", weight=1)
        else:
            head = col(row(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.06, w="0dp", weight=1)),
                       T(f, "tile_day", text("m_day", clock=DAY, font=F["main"], auto=(20, 200), h="0dp", weight=1,
                                              g="center" if f == "board" else "start|center_vertical", mt="4dp" if f == "board" else None)),
                       h="0dp", weight=1)
        body = col(head,
                   text("s_month", clock=MONTH, font=F["sub"], size=13, g="center" if neon else "start", caps=f in ("paper", "board")),
                   hline("if_line", mv="6dp") if not neon else None,
                   text("m_ev0_title", font=F["body"], size=13, g="center" if neon else "start"),
                   pad="14dp")
        return layout(name, *bg(f), body)
    if size == "wide":
        left = col(text("a_weekday", clock=WEEKDAY, font=F["label"], size=12, caps=True, g="start", spacing=0.06),
                   T(f, "tile_day", text("m_day", clock=DAY, font=F["main"], auto=(20, 200), h="0dp", weight=1,
                                          g="center" if f == "board" else "start|center_vertical", shadow=neon, mv="4dp" if f == "board" else None)),
                   text("s_month", clock=MONTH, font=F["sub"], size=14, g="start"),
                   w="0dp", weight=0.8)
        body = row(left,
                   vline("if_vline", mh="12dp", mv="4dp"),
                   col(*event_rows(f, EVENTS, inline=True), w="0dp", weight=1.4, g="center_vertical"),
                   h="match", pad="14dp")
        return layout(name, *bg(f), body)
    # l: месяц + события
    body = col(row(text("a_month", clock=MONTH_YEAR, font=F["label"], size=14, caps=f != "glass", g="start", w="0dp", weight=1, spacing=0.04)),
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
        rows.append(row(text(f"s_d{i}_day", font=F["body"], size=size, w="0dp", weight=0.9, g="start", caps=True),
                        img(f"wx_d{i}", "wx_clear_day", w=f"{icon}dp", h=f"{icon}dp"),
                        text(f"s_d{i}_min", font=F["body"], size=size, w="38dp", g="end"),
                        img(f"rb_d{i}", "wpill", w="0dp", h="6dp", weight=1.3, scale="fitXY", mh="8dp"),
                        text(f"m_d{i}_max", font=F["sub"], size=size, w="32dp", g="start"),
                        id=f"cell_d{i}", h="0dp", weight=1))
    return rows


def weather_shaped(f, size, name):
    F = FONTS[f]
    sh = f == "neon"
    if size in VERTICAL:
        return weather_vertical(f, size, F, name)
    # Полоска 2×1: значок и температура.
    if size == "bar":
        body = row(img("wx_now", "wx_partly_day", w="0dp", h="match", weight=0.7),
                   T(f, "tile_temp", text("m_temp", font=F["main"], auto=(12, 80), w="0dp", h="match", weight=1, shadow=sh)),
                   h="match", padH="8dp", padV="4dp")
    return layout(name, *wx_bg(f), body)


def weather(f, size):
    F = FONTS[f]
    name = f"w_wx_{f}_{size}"
    neon = f == "neon"
    if size in SHAPED:
        return weather_shaped(f, size, name)
    if size == "s":
        if neon:
            return layout(name, *bg(f, "ring"),
                          col(img("wx_now", "wx_partly_day", w="match", h="0dp", weight=0.8),
                              text("m_temp", font=F["main"], auto=(10, 60), h="0dp", weight=1, shadow=True),
                              pad="20dp"))
        return layout(name, *wx_bg(f),
                      col(img("wx_now", "wx_partly_day", w="match", h="0dp", weight=0.9),
                          T(f, "tile_temp", text("m_temp", font=F["main"], auto=(14, 120), h="0dp", weight=1, mt="4dp" if f == "board" else None)),
                          pad="10dp"))
    if size == "strip":
        body = row(img("wx_now", "wx_partly_day", w="40dp", h="match"),
                   T(f, "tile_temp", text("m_temp", font=F["main"], auto=(14, 40), w="76dp", h="match", ms="6dp", shadow=neon)),
                   col(text("m_city", font=F["sub"], size=14, g="start"),
                       text("a_desc", font=F["body"], size=12, g="start"),
                       w="0dp", weight=1, ms="10dp", g="center_vertical|start"),
                   text("s_range", font=F["body"], size=12, w="wrap", g="end"),
                   h="match", padH="14dp", padV="6dp")
        return layout(name, *wx_bg(f), body)
    if size == "m":
        if B(f) == "bold":
            body = col(row(text("a_city", font=F["label"], size=12, caps=True, spacing=0.08, w="0dp", weight=1, g="start"),
                           img("wx_now", "wx_partly_day", w="30dp", h="30dp")),
                       T(f, "tile_temp", text("m_temp", font=F["main"], auto=(30, 220), h="0dp", weight=1,
                                               g="center" if f == "board" else "start|center_vertical", mv="6dp" if f == "board" else None)),
                       text("s_desc", font=F["body"], size=12, g="start"),
                       pad="14dp")
        elif neon:
            body = col(img("wx_now", "wx_partly_day", w="34dp", h="34dp"),
                       text("m_temp", font=F["main"], auto=(16, 70), h="0dp", weight=1, shadow=True),
                       text("a_city", font=F["label"], size=11, caps=True, spacing=0.1),
                       text("s_desc", font=F["body"], size=11, mt="2dp"),
                       pad="16dp")
        elif B(f) == "minimal":
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
        return layout(name, *wx_bg(f), body)
    head = row(col(text("m_city", font=F["sub"], size=15, g="start"),
                   text("a_desc", font=F["body"], size=12, g="start"),
                   w="0dp", weight=1, g="start"),
               img("wx_now", "wx_partly_day", w="40dp", h="40dp"),
               T(f, "tile_temp", text("m_temp", font=F["main"], size=34 if not neon else 26, w="wrap", ms="6dp", shadow=neon,
                                      includeFontPadding="false"), pad="6dp"),
               )
    if size == "wide":
        # Слева — сейчас, справа — ближайшие часы: помещается и в невысокий широкий виджет.
        now = col(row(img("wx_now", "wx_partly_day", w="34dp", h="34dp"),
                      T(f, "tile_temp", text("m_temp", font=F["main"], auto=(16, 40), w="0dp", h="44dp", weight=1, ms="4dp",
                                             g="center" if f == "board" else "start|center_vertical", shadow=neon))),
                  text("m_city", font=F["sub"], size=13, g="start"),
                  text("s_range", font=F["body"], size=11, g="start"),
                  w="0dp", weight=1, g="center_vertical|start")
        hours = row(*[col(text(f"s_h{i}_time", font=F["body"], size=10),
                          img(f"wx_h{i}", "wx_clear_day", w="20dp", h="20dp", mv="2dp"),
                          text(f"m_h{i}_temp", font=F["sub"], size=12),
                          id=f"cell_h{i}", w="0dp", h="wrap", weight=1) for i in range(4)],
                    w="0dp", weight=1.5, h="match", g="center")
        body = row(now, vline("if_vline", mv="6dp", mh="10dp"), hours, h="match", padH="14dp", padV="10dp")
        return layout(name, *wx_bg(f), body)
    # Большой: сейчас, по часам и на 5 дней с полосками «мин–макс» (макс. и мин. на сегодня — в первой строке).
    body = col(head,
               hourly(f),
               hline("if_line", mv="8dp"),
               col(*daily(f), h="0dp", weight=1),
               pad="14dp")
    return layout(name, *wx_bg(f), body)


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
    if size in VERTICAL:
        return battery_vertical(f, size, F, name)
    # Полоска 2×1: заряд и полоса под ним.
    body = col(row(img("ia_bolt", "wic_bolt", w="14dp", h="14dp"),
                   T(f, "tile_pct", text("m_pct", font=F["main"], auto=(12, 80), w="0dp", h="match", weight=1, shadow=f == "neon")),
                   h="0dp", weight=1),
               bar(h="5dp", mt="3dp"),
               padH="10dp", padV="6dp")
    return layout(name, *shaped_bg(f), body)


def battery(f, size):
    F = FONTS[f]
    name = f"w_bat_{f}_{size}"
    neon = f == "neon"
    ring_style = f in ("glass", "neon", "paper")
    if size in SHAPED:
        return battery_shaped(f, size, name)

    def state(role="a", sz=12, **kw):
        return row(img(f"i{role}_bolt", "wic_bolt", w=f"{sz}dp", h=f"{sz}dp"),
                   text(f"{role}_state", font=F["body"], size=sz, w="wrap", ms="3dp"),
                   w="wrap", **kw)

    def big_pct(auto, **kw):
        return T(f, "tile_pct", text("m_pct", font=F["main"], auto=auto, **kw))
    if size == "s":
        if neon:
            return layout(name, *bg(f, "ring_only"), ring_pct(f, 52, shadow=True))
        if ring_style:
            return layout(name, *bg(f), ring_pct(f, 52, font=F["sub"] if f == "paper" else F["main"]))
        return layout(name, *bg(f), col(big_pct((16, 160), h="0dp", weight=1), bar(h="6dp", mt="5dp"), pad="12dp"))
    if size == "strip":
        body = row(big_pct((14, 40), w="84dp", h="match", shadow=neon),
                   col(bar(h="8dp"), state("s", 12, mt="4dp", lg="start"), w="0dp", weight=1, ms="14dp"),
                   h="match", padH="16dp", padV="8dp")
        return layout(name, *bg(f), body)
    if size == "m":
        if ring_style:
            inner = frame(ring_pct(f, 92, shadow=neon), h="0dp", weight=1)
        else:
            inner = col(big_pct((20, 200), h="0dp", weight=1), bar(h="8dp", mt="6dp"), h="0dp", weight=1)
        body = col(inner, state("a", 12, mt="6dp"), text("s_remain", font=F["body"], size=11), pad="12dp")
        return layout(name, *bg(f), body)
    details = row(text("s_temp", font=F["body"], size=12, w="0dp", weight=1, g="start"),
                  text("s_health", font=F["body"], size=12, w="0dp", weight=1, g="end"))
    if size == "wide":
        left = (frame(ring_pct(f, 88, shadow=neon), w="0dp", weight=0.8) if f in ("glass", "neon") else
                big_pct((20, 200), w="0dp", h="match", weight=0.9, g="center" if f == "board" else "center_vertical|start"))
        body = row(left,
                   col(state("m", 15, lg="start"),
                       text("s_remain", font=F["body"], size=12, g="start"),
                       bar(h="8dp", mv="6dp"),
                       details,
                       w="0dp", weight=1.2, g="center_vertical", ms="14dp"),
                   h="match", pad="12dp")
        return layout(name, *bg(f), body)
    big = frame(ring_pct(f, 160, shadow=neon), h="0dp", weight=1) if ring_style else big_pct((30, 300), h="0dp", weight=1, mv="4dp")
    body = col(text("a_device", font=F["label"], size=13, caps=True, spacing=0.06, txt="Телефон"),
               big,
               state("m", 15, mt="8dp"),
               text("s_remain", font=F["body"], size=12),
               bar(h="8dp", mv="8dp") if not ring_style else hline("if_line", mv="8dp"),
               details,
               pad="16dp")
    return layout(name, *bg(f), body)


# ---------------------------------------------------------------- приложения (папка)

APP_STYLES = FAMILIES
APPS_MAX = 8


def app_icon(i, f, icon):
    """Значок: у «Неона» — в светящемся кольце, у «Табло» — на квадратной плашке."""
    if f == "neon":
        return frame(img(f"fx_ap{i}", "wfx_ring_small", w=f"{icon + 10}dp", h=f"{icon + 10}dp", lg="center"),
                     img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp", lg="center"),
                     w=f"{icon + 10}dp", h=f"{icon + 10}dp")
    if f == "board":
        return frame(img(f"tile_ap{i}", "wtile_sq", w=f"{icon + 12}dp", h=f"{icon + 12}dp", lg="center", scale="fitXY"),
                     img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp", lg="center"),
                     w=f"{icon + 12}dp", h=f"{icon + 12}dp")
    return img(f"ap_{i}", "wapp_placeholder", w=f"{icon}dp", h=f"{icon}dp")


def app_cell(i, f, icon, label, lsize=11):
    F = FONTS[f]
    return col(app_icon(i, f, icon),
               text(f"m_ap{i}_label", font=F["body"], size=lsize, mt="4dp", shadow=f == "neon") if label else None,
               id=f"cell_ap{i}", w="0dp", h="wrap", weight=1)


def apps_shaped(f, size, name):
    if size in VERTICAL:
        return apps_vertical(f, size, name, bg(f))
    body = row(*[app_cell(i, f, 28 if f != "board" else 24, False) for i in range(3)], h="match", padH="8dp")
    return layout(name, *bg(f), body)


def apps(f, size):
    F = FONTS[f]
    name = f"w_apps_{f}_{size}"
    if size in SHAPED:
        return apps_shaped(f, size, name)
    small = 0 if f != "board" else 6
    if size in ("s", "m"):
        icon = (22 if size == "s" else 46) - small
        grid = col(row(app_cell(0, f, icon, False), app_cell(1, f, icon, False), g="center"),
                   row(app_cell(2, f, icon, False), app_cell(3, f, icon, False), g="center", mt="6dp" if size == "m" else "3dp"),
                   pad="16dp" if size == "s" else "22dp")
        title = text("m_title", font=F["label"], size=12, mb="2dp", vis="gone", caps=f != "glass") if size == "m" else None
        return layout(name, *bg(f), col(title, grid) if title else grid)
    if size == "strip":
        cells = [app_cell(i, f, 34 - small, False) for i in range(5)]
        return layout(name, *bg(f), row(*cells, h="match", padH="12dp"))
    # В широком виджете (от 100 dp в высоту) подписи под значками не помещаются — только значки.
    icon = (34 if size == "wide" else 48) - small
    rows = [row(*[app_cell(r * 4 + c, f, icon, size != "wide") for c in range(4)], id=f"row_apps{r}", g="center", h="0dp", weight=1)
            for r in range(2)]
    title = text("m_title", font=F["label"], size=13 if size == "wide" else 15, g="start", mb="4dp", vis="gone",
                 caps=f != "glass", spacing=0.04)
    return layout(name, *bg(f), col(title, *rows, pad="14dp"))


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


for name, radius in [("wbg_r10", "10dp"), ("wbg_r24", "24dp")]:
    shape(name, rrect(radius))
shape("wpill", rrect("99dp"))
for cond, (top, mid, bottom) in SCENES.items():
    for rad in SKY_RADII:
        shape(f"wsc_{cond}_r{rad}", f'''<shape {NS} android:shape="rectangle">
    <gradient android:angle="270" android:startColor="{top}" android:centerColor="{mid}" android:endColor="{bottom}" android:type="linear" />
    <corners android:radius="{rad}dp" />
</shape>''')
# Стекло — как матовое стекло: светлая заливка, к низу прозрачнее; блик в верхней части; яркая тонкая
# кромка и еле заметная тень снизу. Цвет не меняется, только прозрачность.
shape("wbg_glass", f'''<layer-list {NS}>
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="270" android:startColor="#5CFFFFFF" android:centerColor="#38FFFFFF" android:endColor="#29FFFFFF" />
            <corners android:radius="28dp" />
        </shape>
    </item>
    <item android:bottom="45dp" android:left="1dp" android:right="1dp" android:top="1dp">
        <shape android:shape="rectangle">
            <gradient android:angle="270" android:startColor="#33FFFFFF" android:endColor="#00FFFFFF" />
            <corners android:topLeftRadius="27dp" android:topRightRadius="27dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#00000000" />
            <stroke android:width="1.2dp" android:color="#B3FFFFFF" />
            <corners android:radius="28dp" />
        </shape>
    </item>
    <item android:left="1.5dp" android:right="1.5dp" android:top="1.5dp" android:bottom="1.5dp">
        <shape android:shape="rectangle">
            <solid android:color="#00000000" />
            <stroke android:width="1dp" android:color="#1F000000" />
            <corners android:radius="26.5dp" />
        </shape>
    </item>
</layer-list>''')
# Бумага: кремовый лист с тенью снизу и тонкой полосой поля сверху — как карточка из блокнота.
shape("wbg_paper", f'''<layer-list {NS}>
    <item android:top="3dp">
        <shape android:shape="rectangle">
            <solid android:color="#2E000000" />
            <corners android:radius="8dp" />
        </shape>
    </item>
    <item android:bottom="3dp">
        <shape android:shape="rectangle">
            <gradient android:angle="270" android:startColor="#FFFAF6EC" android:endColor="#FFF1EADB" />
            <corners android:radius="8dp" />
        </shape>
    </item>
    <item android:bottom="3dp" android:left="0.8dp" android:right="0.8dp" android:top="0.8dp">
        <shape android:shape="rectangle">
            <solid android:color="#00000000" />
            <stroke android:width="0.8dp" android:color="#1A5A4630" />
            <corners android:radius="7.5dp" />
        </shape>
    </item>
</layer-list>''')
# Табло: графитовая панель с тонкой рамкой и «винтами» по углам.
screws = "\n".join(f'''    <item android:gravity="{g}" android:width="4dp" android:height="4dp" android:{h}="7dp" android:{v}="7dp">
        <shape android:shape="oval"><solid android:color="#FF3A3A40" /></shape>
    </item>''' for g, h, v in [("top|left", "left", "top"), ("top|right", "right", "top"),
                                 ("bottom|left", "left", "bottom"), ("bottom|right", "right", "bottom")])
shape("wbg_board", f'''<layer-list {NS}>
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="270" android:startColor="#FF1E1E22" android:endColor="#FF141417" />
            <corners android:radius="14dp" />
            <stroke android:width="1dp" android:color="#FF2E2E34" />
        </shape>
    </item>
{screws}
</layer-list>''')


def flap(name, radius):
    """Перекидная плашка табло: две половины чуть разного тона, щель посередине и петли по краям щели."""
    shape(name, f'''<layer-list {NS}>
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="270" android:startColor="#FF34343A" android:centerColor="#FF2A2A30" android:endColor="#FF222227" />
            <corners android:radius="{radius}" />
        </shape>
    </item>
    <item android:gravity="center_vertical" android:height="1.5dp">
        <shape android:shape="rectangle"><solid android:color="#FF0C0C0E" /></shape>
    </item>
    <item android:gravity="center_vertical|left" android:width="2.5dp" android:height="7dp">
        <shape android:shape="rectangle"><solid android:color="#FF0C0C0E" /></shape>
    </item>
    <item android:gravity="center_vertical|right" android:width="2.5dp" android:height="7dp">
        <shape android:shape="rectangle"><solid android:color="#FF0C0C0E" /></shape>
    </item>
</layer-list>''')


flap("wtile", "7dp")
flap("wtile_sq", "6dp")
# «Плакат»: квадратная отметка (сегодня в календаре).
shape("wsq", rrect("3dp"))
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
# Та же полоса, но растёт снизу вверх — для столбиков.
shape("wbar_vprogress", f'''<layer-list {NS}>
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <solid android:color="#FFFFFFFF" />
            <corners android:radius="99dp" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip android:clipOrientation="vertical" android:gravity="bottom">
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


def fill(path, color="#FFFFFFFF"):
    return f'    <path android:fillColor="{color}" android:pathData="{path}" />'


def circle_path(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0z"


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

def line(x1, y1, x2, y2, w, color="#FFFFFFFF"):
    return (f'    <path android:pathData="M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}" android:strokeWidth="{w}" '
            f'android:strokeColor="{color}" android:strokeLineCap="round" />')


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
    ("ti", "T"), ("bo_", "P"), ("bg_s", "b"), ("bg_a", "B"), ("bg_g", "g"), ("sh_a", "h"), ("fx_", "x"), ("pb_", "p"),
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
      "    /** Ключ — «вид_стиль_размер», например «wx_paper_m». */",
      "    @Synchronized",
      "    operator fun get(key: String): WidgetLayout? = cache.getOrPut(key) {",
      "        " + " ?: ".join(f"{k}(key)" for k in ["clock", "cal", "wx", "bat", "apps"]),
      "    }",
      ]
for kind in ["clock", "cal", "wx", "bat", "apps"]:
    groups = [kind] if kind != "clock" else [f"clock_{f}" for f in CLOCK_FAMILIES]
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
kt += ["", "/**",
       " * Картинка с цифрами часов вместо TextClock (шрифты из ресурсов приложения лаунчер не загружает):",
       " * [clock] — цифры, место которых она занимает, [format] — h (часы), m (минуты), t («ч:мм»),",
       " * w («чч» над «мм»), s («чч мм»), [align] — для выравниваемых цифр s/c/e (начало, центр, конец), иначе «.».",
       " */",
       "class TimeImage(val image: Int, val clock: Int, val format: Char, val align: Char)", "",
       "object TimeImages {",
       "    private val map: Map<Int, TimeImage> = listOf("]
for image, (clock_id, code, align) in TIME_IMAGES.items():
    kt.append(f"        TimeImage(R.id.{image}, R.id.{clock_id}, '{code}', '{align}'),")
kt += ["    ).associateBy { it.image }", "", "    operator fun get(id: Int): TimeImage? = map[id]", "}"]
kt += ["", "/** Небо (по погоде или времени суток): градиент со скруглением подложки стиля. */", "object SkyScenes {",
       "    val radii = intArrayOf(" + ", ".join(str(r) for r in SKY_RADII) + ")", "",
       "    fun of(scene: String, radius: Int): Int = when (\"${scene}_$radius\") {"]
for cond in SCENES:
    for rad in SKY_RADII:
        kt.append(f'        "{cond}_{rad}" -> R.drawable.wsc_{cond}_r{rad}')
kt += ["        else -> R.drawable.wsc_cloudy_day_r28", "    }", "}"]
write(os.path.join(KT, "WidgetIds.kt"), "\n".join(kt) + "\n")

vs = ["package app.synctheme.mobile.widget", "", "// Сгенерировано scripts/gen_widgets.py — не редактировать вручную.", "",
      "/**",
      " * Размеры элементов вертикальных разметок (столбики и высокие виджеты): отступы, промежутки, значки",
      " * и полоса заряда — в dp, высоты подписей и строк (…Box, …Sp) — в sp. По ним [VerticalFit] подбирает",
      " * кегль главного значения и решает, какие строки поместятся целиком.",
      " */",
      "internal class VSpec(" + ", ".join(f"val {k}: Float" for k in VSPEC_KEYS) + ")",
      "",
      "internal object VSpecs {"]
for size in ("column", "tall"):
    vs.append(f"    val {size} = VSpec(" + ", ".join(f"{VSPEC[size][k]}f" for k in VSPEC_KEYS) + ")")
vs += ["", "    fun of(size: WidgetSize): VSpec = if (size == WidgetSize.TALL) tall else column", "}"]
write(os.path.join(KT, "WidgetVSpec.kt"), "\n".join(vs) + "\n")
print(f"layouts: {len(LAYOUTS)}, ids: {len(ALL_IDS)}, slots: {len(slots)}")
