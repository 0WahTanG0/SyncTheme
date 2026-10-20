#!/usr/bin/env python3
"""Шрифты цифр для часов: скачивает их из github.com/google/fonts (лицензия OFL), оставляет
нужную насыщенность и только цифры с двоеточием, кладёт в модуль :faces и в шаблон циферблата
и записывает метрики в core/DigitFont.kt (по ним виджеты и циферблаты вписывают цифры в рамку).

Запуск: python3 scripts/gen_fonts.py  (нужен интернет и fonttools)
"""
import io
import os
import urllib.request

from fontTools import subset
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.join(os.path.dirname(__file__), "..")
# Шрифты циферблатов лежат в общем модуле :faces (их видят и телефон, и приложение для часов).
MOBILE_FONTS = os.path.join(ROOT, "faces", "src", "main", "res", "font")
WATCH_FONTS = os.path.join(ROOT, "watchface", "src", "main", "res", "font")
LICENSES = os.path.join(ROOT, "mobile", "src", "main", "assets", "licenses", "fonts-digits.txt")
KT = os.path.join(ROOT, "core", "src", "main", "kotlin", "app", "synctheme", "core", "DigitFont.kt")
GF = "https://raw.githubusercontent.com/google/fonts/main/ofl/"

# имя константы, ресурс, подпись, путь в google/fonts, оси
NEW = [
    ("BLOCK", "bungee", "Блоки", "bungee/Bungee-Regular.ttf", {}),
    ("OUTLINE", "bungee_outline", "Контур", "bungeeoutline/BungeeOutline-Regular.ttf", {}),
    ("DOTS", "doto", "Точки", "doto/Doto%5BROND,wght%5D.ttf", {"wght": 800, "ROND": 100}),
    ("TUBE", "tilt_neon", "Неон", "tiltneon/TiltNeon%5BXROT,YROT%5D.ttf", {}),
    ("PIXEL", "pixelify", "Пиксели", "pixelifysans/PixelifySans%5Bwght%5D.ttf", {"wght": 700}),
    ("SERIF", "dm_serif", "Антиква", "dmserifdisplay/DMSerifDisplay-Regular.ttf", {}),
    ("ITALIC", "instrument_italic", "Курсив", "instrumentserif/InstrumentSerif-Italic.ttf", {}),
    ("BUBBLE", "dynapuff", "Пузыри", "dynapuff/DynaPuff%5Bwdth,wght%5D.ttf", {"wght": 700, "wdth": 100}),
    ("MONO", "space_mono", "Моно", "spacemono/SpaceMono-Bold.ttf", {}),
    ("STENCIL", "stencil", "Трафарет", "bigshouldersstencil/BigShouldersStencil%5Bopsz,wght%5D.ttf", {"wght": 800, "opsz": 72}),
    ("SPACE", "orbitron", "Космос", "orbitron/Orbitron%5Bwght%5D.ttf", {"wght": 800}),
    ("RETRO", "monoton", "Ретро", "monoton/Monoton-Regular.ttf", {}),
    ("HAND", "caveat", "Рукопись", "caveat/Caveat%5Bwght%5D.ttf", {"wght": 700}),
]
# Шрифты, которые уже есть в приложении (полные, с кириллицей).
EXISTING = [
    ("NARROW", "oswald_700", "Узкий"),
    ("WIDE", "unbounded_700", "Широкий"),
    ("ROUND", "nunito_900", "Округлый"),
    ("GROTESK", "manrope_800", "Гротеск"),
]
TEXT = "0123456789: AMPamp"


def fetch(url):
    with urllib.request.urlopen(url) as r:
        return r.read()


def metrics(font):
    upm = font["head"].unitsPerEm
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    ymin, ymax, adv = 10 ** 9, -10 ** 9, 0
    for ch in "0123456789":
        g = cmap[ord(ch)]
        pen = BoundsPen(gs)
        gs[g].draw(pen)
        if pen.bounds:
            ymin, ymax = min(ymin, pen.bounds[1]), max(ymax, pen.bounds[3])
        adv = max(adv, font["hmtx"][g][0])
    hhea = font["hhea"]
    return dict(ascent=hhea.ascent / upm, descent=-hhea.descent / upm, top=ymax / upm, bottom=ymin / upm,
                digit=adv / upm, colon=font["hmtx"][cmap[ord(":")]][0] / upm)


rows = []
licenses = ["Шрифты цифр SyncTheme распространяются по лицензии SIL Open Font License 1.1.",
            "Из них оставлены только цифры, двоеточие и буквы AM/PM."]
for const, res, title, path, axes in NEW:
    font = TTFont(io.BytesIO(fetch(GF + path)))
    if "fvar" in font:
        values = {}
        for a in font["fvar"].axes:
            values[a.axisTag] = max(a.minValue, min(a.maxValue, axes.get(a.axisTag, a.defaultValue)))
        font = instancer.instantiateVariableFont(font, values, updateFontNames=False)
    opts = subset.Options()
    opts.layout_features = ["kern", "tnum", "lnum", "liga", "calt"]
    opts.notdef_outline = True
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=sorted({ord(c) for c in TEXT}))
    sub.subset(font)
    buf = io.BytesIO()
    font.save(buf)
    for d in (MOBILE_FONTS, WATCH_FONTS):
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, f"{res}.ttf"), "wb") as f:
            f.write(buf.getvalue())
    rows.append((const, res, title, metrics(TTFont(io.BytesIO(buf.getvalue())))))
    folder = path.split("/")[0]
    licenses += ["", f"==== {title}: {folder} ====", fetch(GF + folder + "/OFL.txt").decode("utf-8")]
for const, res, title in EXISTING:
    rows.append((const, res, title, metrics(TTFont(os.path.join(MOBILE_FONTS, f"{res}.ttf")))))

with open(LICENSES, "w", encoding="utf-8") as f:
    f.write("\n".join(licenses))

kt = ["package app.synctheme.core", "", "import kotlinx.serialization.Serializable", "",
      "// Сгенерировано scripts/gen_fonts.py — не редактировать вручную.", "",
      "/**",
      " * Шрифт цифр часов (виджеты и циферблат). res — имя ресурса в res/font приложения и шаблона",
      " * циферблата; null — системный тонкий шрифт. Метрики — в долях кегля: ascent/descent строки,",
      " * верх и низ цифр над базовой линией, ширина цифры и двоеточия.",
      " */",
      "@Serializable",
      "enum class DigitFont(",
      "    val res: String?, val title: String,",
      "    val ascent: Float, val descent: Float, val top: Float, val bottom: Float, val digit: Float, val colon: Float,",
      ") {"]
order = ["NARROW", "WIDE", "ROUND", "GROTESK"] + [r[0] for r in NEW]
by = {r[0]: r for r in rows}
for const in order:
    _, res, title, m = by[const]
    kt.append(f'    {const}("{res}", "{title}", {m["ascent"]:.3f}f, {m["descent"]:.3f}f, {m["top"]:.3f}f, {m["bottom"]:.3f}f, '
              f'{m["digit"]:.3f}f, {m["colon"]:.3f}f),')
# Системный Roboto Thin: метрики Roboto.
kt.append('    THIN(null, "Тонкий", 0.928f, 0.244f, 0.711f, -0.010f, 0.562f, 0.240f);')
kt += ["",
       "    /** Высота строки в долях кегля. */",
       "    val line get() = ascent + descent",
       "",
       "    /** Высота цифр в долях кегля. */",
       "    val digitHeight get() = top - bottom",
       "",
       "    /** Ширина текста из цифр и двоеточий в долях кегля. */",
       "    fun width(text: String) = text.sumOf { (if (it == ':') colon else digit).toDouble() }.toFloat()",
       "}", ""]
with open(KT, "w", encoding="utf-8") as f:
    f.write("\n".join(kt))
print("fonts:", len(rows))
