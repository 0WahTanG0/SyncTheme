"""Баннер SyncTheme для меню Google TV / Android TV: 320x180 dp (xhdpi — 640x360 px)."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 640, 360
ACCENT = (94, 242, 198)
out = sys.argv[1] if len(sys.argv) > 1 else "mobile/src/main/res/drawable-xhdpi/tv_banner.png"

img = Image.new("RGB", (W, H))
px = img.load()
for y in range(H):
    for x in range(W):
        t = (x / W) * 0.6 + (y / H) * 0.4
        px[x, y] = (int(11 + 10 * t), int(23 + 40 * t), int(20 + 30 * t))

# Неоновое кольцо с разрывом и точкой — как значок приложения; свечение — размытой копией.
ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(ring)
cx, cy, r = 118, H // 2, 66
d.arc((cx - r, cy - r, cx + r, cy + r), start=-60, end=240, fill=ACCENT + (255,), width=16)
d.ellipse((cx - 19, cy - 19, cx + 19, cy + 19), fill=ACCENT + (255,))
glow = ring.filter(ImageFilter.GaussianBlur(14))
img.paste(glow, (0, 0), glow)
img.paste(ring, (0, 0), ring)

draw = ImageDraw.Draw(img)
font = ImageFont.truetype("mobile/src/main/res/font/unbounded_800.ttf", 46)
draw.text((214, H // 2 - 4), "Sync", font=font, fill=(255, 255, 255), anchor="lm")
sync_w = draw.textlength("Sync", font=font)
draw.text((214 + sync_w, H // 2 - 4), "Theme", font=font, fill=ACCENT, anchor="lm")
small = ImageFont.truetype("mobile/src/main/res/font/manrope_600.ttf", 22)
draw.text((216, H // 2 + 42), "тема с телефона — на экране", font=small, fill=(190, 214, 206), anchor="lm")
img.save(out, optimize=True)
print(out)
