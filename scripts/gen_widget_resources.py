#!/usr/bin/env python3
"""Генерирует ресурсы виджетов SyncTheme: разметку всех стилей, циферблаты, иконки погоды
и Kotlin-файлы с идентификаторами. Запуск: python3 scripts/gen_widget_resources.py

RemoteViews не умеют менять шрифт и состав элементов на лету, поэтому на каждый стиль
(и начертание шрифта часов) нужна своя разметка — её проще генерировать, чем писать руками.
"""
import math
import os

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "mobile", "src", "main")
RES = os.path.join(ROOT, "res")
KT = os.path.join(ROOT, "java", "app", "synctheme", "mobile", "widget")
HEADER = "<!-- Сгенерировано scripts/gen_widget_resources.py — не редактировать вручную. -->"
A = 'xmlns:android="http://schemas.android.com/apk/res/android"'

WEIGHTS = {
    "thin": ('android:fontFamily="sans-serif-thin"', ""),
    "light": ('android:fontFamily="sans-serif-light"', ""),
    "regular": ('android:fontFamily="sans-serif"', ""),
    "bold": ('android:fontFamily="sans-serif"', 'android:textStyle="bold"'),
}


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def layout(name, content):
    write(os.path.join(RES, "layout", f"{name}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
{HEADER}
<FrameLayout {A}
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageView
        android:id="@+id/widget_bg"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:importantForAccessibility="no"
        android:scaleType="fitXY"
        android:src="@drawable/widget_bg_round"
        android:tint="#D91B1B24" />
{content}
</FrameLayout>
''')


def text_clock(id_, fmt12, fmt24, extra="", autosize=None, size=None, w="match_parent", h="wrap_content",
               weight=None, gravity="center", caps=False):
    attrs = [f'android:id="@+id/{id_}"', f'android:layout_width="{w}"', f'android:layout_height="{h}"']
    if weight:
        attrs.append(f'android:layout_weight="{weight}"')
    if autosize:
        lo, hi = autosize
        attrs += ['android:autoSizeTextType="uniform"', f'android:autoSizeMinTextSize="{lo}sp"',
                  f'android:autoSizeMaxTextSize="{hi}sp"', 'android:includeFontPadding="false"']
    if size:
        attrs.append(f'android:textSize="{size}sp"')
    if caps:
        attrs.append('android:textAllCaps="true"')
    attrs += [f'android:format12Hour="{fmt12}"', f'android:format24Hour="{fmt24}"', f'android:gravity="{gravity}"',
              'android:maxLines="1"', 'android:ellipsize="end"', 'android:textColor="#FFFFFFFF"']
    if extra:
        attrs.append(extra)
    return "<TextClock\n            " + "\n            ".join(attrs) + " />"


def text_view(id_, size=13, w="match_parent", h="wrap_content", weight=None, gravity="center", extra="", text=""):
    attrs = [f'android:id="@+id/{id_}"', f'android:layout_width="{w}"', f'android:layout_height="{h}"']
    if weight:
        attrs.append(f'android:layout_weight="{weight}"')
    attrs += [f'android:textSize="{size}sp"', f'android:gravity="{gravity}"', 'android:maxLines="1"',
              'android:ellipsize="end"', 'android:textColor="#FFFFFFFF"']
    if text:
        attrs.append(f'android:text="{text}"')
    if extra:
        attrs.append(extra)
    return "<TextView\n            " + "\n            ".join(attrs) + " />"


def image(id_, w, h, src, extra=""):
    return f'''<ImageView
            android:id="@+id/{id_}"
            android:layout_width="{w}"
            android:layout_height="{h}"
            android:importantForAccessibility="no"
            android:scaleType="fitCenter"
            android:src="@drawable/{src}" {extra} />'''


def column(content, id_="content", padding="12dp", gravity="center", w="match_parent", h="match_parent", weight=None):
    wt = f'\n        android:layout_weight="{weight}"' if weight else ""
    return f'''
    <LinearLayout
        android:id="@+id/{id_}"
        android:layout_width="{w}"
        android:layout_height="{h}"{wt}
        android:gravity="{gravity}"
        android:orientation="vertical"
        android:padding="{padding}">
        {content}
    </LinearLayout>'''


def row(content, id_=None, h="wrap_content", w="match_parent", weight=None, gravity="center_vertical", extra=""):
    idattr = f'android:id="@+id/{id_}"\n        ' if id_ else ""
    wt = f'\n        android:layout_weight="{weight}"' if weight else ""
    return f'''<LinearLayout
        {idattr}android:layout_width="{w}"
        android:layout_height="{h}"{wt}
        android:gravity="{gravity}"
        android:orientation="horizontal" {extra}>
        {content}
    </LinearLayout>'''


ids = {}  # имя массива Kotlin -> список id


def remember(name, values):
    ids[name] = values


# ---------------------------------------------------------------- часы (цифровые)

CLOCK_STYLES = ["classic", "big", "stacked", "side", "seconds"]
for style in CLOCK_STYLES:
    for wname, (family, bold) in WEIGHTS.items():
        f = family + (" " + bold if bold else "")
        if style == "classic":
            body = column(
                text_clock("clock_time", "h:mm", "HH:mm", f, autosize=(20, 160), h="0dp", weight=1) + "\n        " +
                text_clock("clock_date", "EEEE, d MMMM", "EEEE, d MMMM", size=15))
        elif style == "seconds":
            body = column(
                text_clock("clock_time", "h:mm:ss", "HH:mm:ss", f, autosize=(16, 140), h="0dp", weight=1) + "\n        " +
                text_clock("clock_date", "EEEE, d MMMM", "EEEE, d MMMM", size=15))
        elif style == "big":
            body = column(text_clock("clock_time", "h:mm", "HH:mm", f, autosize=(20, 220), h="match_parent"),
                          padding="4dp")
        elif style == "stacked":
            body = column(
                text_clock("clock_hours", "hh", "HH", f, autosize=(20, 160), h="0dp", weight=1) + "\n        " +
                text_clock("clock_minutes", "mm", "mm", f, autosize=(20, 160), h="0dp", weight=1) + "\n        " +
                text_clock("clock_date", "EEE, d MMM", "EEE, d MMM", size=13))
        else:  # side
            body = "\n    " + row(
                text_clock("clock_time", "h:mm", "HH:mm", f, autosize=(18, 120), w="0dp", h="match_parent", weight="1.5") +
                '''
        <ImageView
            android:id="@+id/clock_divider"
            android:layout_width="3dp"
            android:layout_height="match_parent"
            android:layout_marginStart="10dp"
            android:layout_marginEnd="12dp"
            android:layout_marginVertical="14dp"
            android:importantForAccessibility="no"
            android:scaleType="fitXY"
            android:src="@drawable/dot" />
        ''' + column(
                    text_clock("clock_weekday", "EEEE", "EEEE", 'android:fontFamily="sans-serif-medium"', size=14, caps=True,
                               gravity="start") + "\n        " +
                    text_clock("clock_date", "d MMMM", "d MMMM", size=18, gravity="start"),
                    id_="side_text", padding="0dp", gravity="center_vertical|start", w="0dp", weight="1"),
                id_="content", h="match_parent", extra='android:padding="12dp"')
        layout(f"widget_clock_{style}_{wname}", body)

# ---------------------------------------------------------------- часы (стрелки)

for dial in ["ticks", "numbers", "minimal"]:
    for sec in [True, False]:
        hand = '\n        android:hand_second="@drawable/analog_second"' if sec else ""
        layout(f"widget_clock_analog_{dial}" + ("" if sec else "_nosec"), f'''
    <AnalogClock
        android:id="@+id/analog"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="8dp"
        android:dial="@drawable/dial_{dial}"
        android:hand_hour="@drawable/analog_hour"
        android:hand_minute="@drawable/analog_minute"{hand} />''')

# ---------------------------------------------------------------- дата

layout("widget_date_big_day", column(
    text_clock("date_weekday", "EEEE", "EEEE", 'android:fontFamily="sans-serif-medium"', size=13, caps=True) + "\n        " +
    text_clock("date_day", "d", "d", 'android:fontFamily="sans-serif-light"', autosize=(20, 110), h="0dp", weight=1) + "\n        " +
    text_clock("date_month", "LLLL yyyy", "LLLL yyyy", size=13)))
layout("widget_date_line", column(
    text_clock("date_weekday", "EEEE", "EEEE", 'android:fontFamily="sans-serif-light"', autosize=(14, 48), h="0dp", weight=1) + "\n        " +
    text_clock("date_month", "d MMMM yyyy", "d MMMM yyyy", size=15)))
layout("widget_date_compact", "\n    " + row(
    text_clock("date_weekday", "EEE", "EEE", 'android:fontFamily="sans-serif-medium"', autosize=(12, 40), w="0dp", h="match_parent",
               weight=1, caps=True) + "\n        " +
    text_clock("date_day", "d", "d", 'android:fontFamily="sans-serif-light"', autosize=(14, 80), w="0dp", h="match_parent", weight="1.3"),
    id_="content", h="match_parent", gravity="center", extra='android:padding="10dp"'))
layout("widget_date_tear", '''
    <LinearLayout
        android:id="@+id/content"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="0.3">

            <ImageView
                android:id="@+id/date_header_bg"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:importantForAccessibility="no"
                android:scaleType="fitXY"
                android:src="@drawable/header_bg" />

            ''' + text_clock("date_month", "LLLL", "LLLL", 'android:fontFamily="sans-serif-medium" android:padding="6dp"',
                             autosize=(10, 22), h="match_parent", caps=True) + '''
        </FrameLayout>

        ''' + text_clock("date_day", "d", "d", 'android:fontFamily="sans-serif-light"', autosize=(20, 100), h="0dp", weight="0.52") + '''

        ''' + text_clock("date_weekday", "EEEE", "EEEE", size=13, h="0dp", weight="0.18") + '''
    </LinearLayout>''')

# ---------------------------------------------------------------- календарь


def month_grid():
    days = [f"cal_d{i}" for i in range(42)]
    bgs = [f"cal_bg{i}" for i in range(42)]
    wds = [f"cal_wd{i}" for i in range(7)]
    remember("CAL_DAYS", days)
    remember("CAL_DAY_BGS", bgs)
    remember("CAL_WEEKDAYS", wds)
    weekday_row = row("\n        ".join(text_view(w, size=11, w="0dp", weight=1) for w in wds), h="wrap_content")
    rows = []
    for r in range(6):
        cells = []
        for c in range(7):
            i = r * 7 + c
            cells.append(f'''<FrameLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1">
            <ImageView
                android:id="@+id/{bgs[i]}"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:layout_gravity="center"
                android:importantForAccessibility="no"
                android:src="@drawable/dot"
                android:visibility="gone" />
            <TextView
                android:id="@+id/{days[i]}"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:textColor="#FFFFFFFF"
                android:textSize="12sp" />
        </FrameLayout>''')
        rows.append(row("\n        ".join(cells), h="0dp", weight=1, gravity="center"))
    return text_view("cal_title", size=15, gravity="start", extra='android:fontFamily="sans-serif-medium" android:paddingBottom="4dp"') + \
        "\n        " + weekday_row + "\n        " + "\n        ".join(rows)


def agenda(n):
    rows_ids = [f"ev_row{i}" for i in range(n)]
    remember("EV_ROWS", rows_ids)
    remember("EV_DOTS", [f"ev_dot{i}" for i in range(n)])
    remember("EV_TIMES", [f"ev_time{i}" for i in range(n)])
    remember("EV_TITLES", [f"ev_title{i}" for i in range(n)])
    out = []
    for i in range(n):
        out.append(row(
            image(f"ev_dot{i}", "8dp", "8dp", "dot") + "\n        " +
            text_view(f"ev_time{i}", size=12, w="64dp", gravity="start", extra='android:layout_marginStart="8dp"') + "\n        " +
            text_view(f"ev_title{i}", size=14, w="0dp", weight=1, gravity="start"),
            id_=f"ev_row{i}", h="0dp", weight=1))
    out.append(text_view("ev_empty", size=13, text="Событий нет", extra='android:visibility="gone" android:alpha="0.7"'))
    return "\n        ".join(out)


layout("widget_calendar_month", column(month_grid(), padding="10dp", gravity="top"))
layout("widget_calendar_agenda", column(
    text_view("cal_title", size=15, gravity="start", extra='android:fontFamily="sans-serif-medium" android:paddingBottom="6dp"') +
    "\n        " + agenda(5), padding="12dp", gravity="top"))
# Месяц + 3 события: отдельные id строк, чтобы не путать с agenda(5).
grid = month_grid()
ev = []
for i in range(3):
    ev.append(row(
        image(f"ev_dot{i}", "8dp", "8dp", "dot") + "\n        " +
        text_view(f"ev_time{i}", size=12, w="64dp", gravity="start", extra='android:layout_marginStart="8dp"') + "\n        " +
        text_view(f"ev_title{i}", size=14, w="0dp", weight=1, gravity="start"),
        id_=f"ev_row{i}", h="26dp"))
ev.append(text_view("ev_empty", size=13, text="Событий нет", extra='android:visibility="gone" android:alpha="0.7"'))
layout("widget_calendar_month_agenda", f'''
    <LinearLayout
        android:id="@+id/content"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="10dp">
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:orientation="vertical">
            {grid}
        </LinearLayout>
        {"".join(ev)}
    </LinearLayout>''')

# ---------------------------------------------------------------- погода

HOURS = 6
DAYS = 5
remember("H_TIMES", [f"h{i}_time" for i in range(HOURS)])
remember("H_ICONS", [f"h{i}_icon" for i in range(HOURS)])
remember("H_TEMPS", [f"h{i}_temp" for i in range(HOURS)])
remember("D_DAYS", [f"d{i}_day" for i in range(DAYS)])
remember("D_ICONS", [f"d{i}_icon" for i in range(DAYS)])
remember("D_MAXS", [f"d{i}_max" for i in range(DAYS)])
remember("D_MINS", [f"d{i}_min" for i in range(DAYS)])


def hourly_row():
    cols = []
    for i in range(HOURS):
        cols.append(column(
            text_view(f"h{i}_time", size=11) + "\n        " + image(f"h{i}_icon", "26dp", "26dp", "wx_clear_day") +
            "\n        " + text_view(f"h{i}_temp", size=13),
            id_=f"h{i}_col", padding="0dp", w="0dp", h="wrap_content", weight=1))
    return row("".join(cols), h="wrap_content", gravity="center")


def daily_row():
    cols = []
    for i in range(DAYS):
        cols.append(column(
            text_view(f"d{i}_day", size=11, extra='android:textAllCaps="true"') + "\n        " +
            image(f"d{i}_icon", "26dp", "26dp", "wx_clear_day") + "\n        " +
            text_view(f"d{i}_max", size=13) + "\n        " + text_view(f"d{i}_min", size=11, extra='android:alpha="0.7"'),
            id_=f"d{i}_col", padding="0dp", w="0dp", h="wrap_content", weight=1))
    return row("".join(cols), h="wrap_content", gravity="center")


def current_block(icon="56dp", temp=40):
    return row(
        image("wx_icon", icon, icon, "wx_partly_day") + "\n        " + column(
            text_view("wx_temp", size=temp, gravity="start", extra='android:fontFamily="sans-serif-light" android:includeFontPadding="false"') +
            "\n        " + text_view("wx_desc", size=13, gravity="start") + "\n        " +
            text_view("wx_city", size=12, gravity="start", extra='android:alpha="0.75"'),
            id_="wx_current_text", padding="0dp", gravity="center_vertical|start", w="0dp", h="wrap_content", weight=1),
        extra='android:layout_marginBottom="4dp"')


def header_line():
    return row(
        text_view("wx_city", size=13, w="0dp", weight=1, gravity="start", extra='android:fontFamily="sans-serif-medium"') + "\n        " +
        text_view("wx_temp", size=20, w="wrap_content", extra='android:fontFamily="sans-serif-light"') + "\n        " +
        image("wx_icon", "28dp", "28dp", "wx_partly_day", 'android:layout_marginStart="6dp"'),
        extra='android:layout_marginBottom="6dp"')


layout("widget_weather_compact", column(current_block(), gravity="center_vertical|start"))
layout("widget_weather_details", column(
    current_block() + "\n        " + row(
        text_view("wx_feels", size=12, w="0dp", weight=1, gravity="start") + "\n        " +
        text_view("wx_wind", size=12, w="0dp", weight=1) + "\n        " +
        text_view("wx_humidity", size=12, w="0dp", weight=1, gravity="end")) + "\n        " +
    row(text_view("wx_range", size=12, w="0dp", weight=1, gravity="start") + "\n        " +
        text_view("wx_updated", size=11, w="0dp", weight=1, gravity="end", extra='android:alpha="0.6"'),
        extra='android:layout_marginTop="4dp"'),
    gravity="center_vertical"))
layout("widget_weather_hourly", column(header_line() + "\n        " + hourly_row(), gravity="center_vertical"))
layout("widget_weather_daily", column(header_line() + "\n        " + daily_row(), gravity="center_vertical"))
layout("widget_weather_full", column(
    current_block(icon="52dp", temp=36) + "\n        " +
    text_view("wx_range", size=12, gravity="start", extra='android:layout_marginBottom="6dp"') + "\n        " +
    hourly_row() + '''
        <ImageView
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginVertical="8dp"
            android:alpha="0.2"
            android:importantForAccessibility="no"
            android:scaleType="fitXY"
            android:src="@drawable/dot" />
        ''' + daily_row(), gravity="center_vertical"))

# ---------------------------------------------------------------- батарея

layout("widget_battery_ring", '''
    <ProgressBar
        android:id="@+id/battery_ring"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_margin="10dp"
        android:indeterminate="false"
        android:max="100"
        android:progress="75"
        android:progressDrawable="@drawable/battery_ring" />
''' + column(
    text_view("battery_percent", size=26, w="wrap_content", extra='android:fontFamily="sans-serif-light" android:includeFontPadding="false" android:text="75%"') +
    "\n        " + text_view("battery_state", size=11, w="wrap_content")))
layout("widget_battery_bar", column(
    row(text_view("battery_percent", size=28, w="0dp", weight=1, gravity="start",
                  extra='android:fontFamily="sans-serif-light" android:text="75%"') + "\n        " +
        text_view("battery_state", size=12, w="wrap_content")) + '''
        <ProgressBar
            android:id="@+id/battery_bar"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="12dp"
            android:layout_marginTop="8dp"
            android:indeterminate="false"
            android:max="100"
            android:progress="75"
            android:progressDrawable="@drawable/battery_bar" />''', padding="14dp"))
layout("widget_battery_big", column(
    text_view("battery_percent", size=40, h="0dp", weight=1,
              extra='android:fontFamily="sans-serif-thin" android:autoSizeTextType="uniform" android:autoSizeMinTextSize="16sp" android:autoSizeMaxTextSize="96sp" android:includeFontPadding="false" android:text="75%"') +
    "\n        " + text_view("battery_state", size=12)))

# ---------------------------------------------------------------- рисунки


def vector(name, paths, size=48, dp=48):
    write(os.path.join(RES, "drawable", f"{name}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
{HEADER}
<vector {A}
    android:width="{dp}dp" android:height="{dp}dp"
    android:viewportWidth="{size}" android:viewportHeight="{size}">
{paths}
</vector>
''')


def shape(name, body):
    write(os.path.join(RES, "drawable", f"{name}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
{HEADER}
{body}
''')


for name, radius in [("widget_bg_round", "28dp"), ("widget_bg_soft", "14dp"), ("widget_bg_square", "3dp"),
                     ("widget_bg_pill", "500dp")]:
    shape(name, f'''<shape {A} android:shape="rectangle">
    <solid android:color="#FFFFFFFF" />
    <corners android:radius="{radius}" />
</shape>''')
shape("dot", f'''<shape {A} android:shape="oval">
    <solid android:color="#FFFFFFFF" />
</shape>''')
shape("header_bg", f'''<shape {A} android:shape="rectangle">
    <solid android:color="#FFFFFFFF" />
    <corners android:topLeftRadius="28dp" android:topRightRadius="28dp" />
</shape>''')
shape("battery_bar", f'''<layer-list {A}>
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <solid android:color="#FFFFFFFF" />
            <corners android:radius="6dp" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <solid android:color="#FFFFFFFF" />
                <corners android:radius="6dp" />
            </shape>
        </clip>
    </item>
</layer-list>''')


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

# Цифры на циферблате — контуры глифов DejaVu Sans (векторы не умеют рисовать текст).
font = TTFont("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
units = font["head"].unitsPerEm


def number_path(text, cx, cy, height):
    scale = height / (units * 0.73)  # высота цифр ≈ 0.73 em
    widths = [glyphs[cmap[ord(ch)]].width * scale for ch in text]
    x = cx - sum(widths) / 2
    out = []
    for ch, wdt in zip(text, widths):
        pen = SVGPathPen(glyphs)
        glyphs[cmap[ord(ch)]].draw(TransformPen(pen, (scale, 0, 0, -scale, x, cy + height / 2)))
        out.append(pen.getCommands())
        x += wdt
    return " ".join(out)


numbers = dial_ticks(minor=True)[:0]  # только мелкие деления у края
for i in range(60):
    a = math.radians(i * 6)
    ln, w = (5, 2.2) if i % 5 == 0 else (2.5, 1.2)
    numbers.append(line(100 + 97 * math.sin(a), 100 - 97 * math.cos(a),
                        100 + (97 - ln) * math.sin(a), 100 - (97 - ln) * math.cos(a), w, "#B3FFFFFF"))
for h in range(1, 13):
    a = math.radians(h * 30)
    numbers.append(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{number_path(str(h), 100 + 78 * math.sin(a), 100 - 78 * math.cos(a), 15)}" />')
vector200("dial_numbers", numbers)


# Иконки погоды: цветные, но в «монохромном» режиме перекрашиваются целиком через colorFilter.
def circle(cx, cy, r, color):
    return (f'    <path android:fillColor="{color}" android:pathData="M{cx - r:.2f},{cy:.2f}'
            f'a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0z" />')


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
        else:  # snow
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

kt = ["package app.synctheme.mobile.widget", "", "import app.synctheme.R", "",
      "// Сгенерировано scripts/gen_widget_resources.py — не редактировать вручную.", "",
      "/** Идентификаторы повторяющихся элементов разметки виджетов. */", "object WidgetIds {"]
for name, values in ids.items():
    kt.append(f"    val {name} = intArrayOf({', '.join('R.id.' + v for v in values)})")
kt.append("}")
kt += ["", "/** Разметка стиля часов с нужным начертанием шрифта. */", "object ClockLayouts {",
       "    fun digital(style: String, weight: String): Int = when (\"${style}_$weight\") {"]
for style in CLOCK_STYLES:
    for w in WEIGHTS:
        kt.append(f'        "{style}_{w}" -> R.layout.widget_clock_{style}_{w}')
kt.append("        else -> R.layout.widget_clock_classic_light")
kt.append("    }")
kt.append("")
kt.append("    fun analog(dial: String, seconds: Boolean): Int = when (dial) {")
for dial in ["ticks", "numbers", "minimal"]:
    kt.append(f'        "{dial}" -> if (seconds) R.layout.widget_clock_analog_{dial} else R.layout.widget_clock_analog_{dial}_nosec')
kt.append("        else -> R.layout.widget_clock_analog_ticks")
kt.append("    }")
kt.append("}")
write(os.path.join(KT, "WidgetIds.kt"), "\n".join(kt) + "\n")
print("ok")
