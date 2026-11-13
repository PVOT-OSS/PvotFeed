#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
"""Draw the store icon (512x512) and feature graphic (1024x500) from the launcher icon's shapes.

Usage: tools/store-graphics.py [out-dir]   (default fastlane/metadata/android/en-US/images)
Needs Pillow and the Noto Sans fonts.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "fastlane/metadata/android/en-US/images")
TOP, BOTTOM = (0x8E, 0x7C, 0xFF), (0x5B, 0x47, 0xE0)
FONT = "/usr/share/fonts/truetype/noto/NotoSans-{}.ttf"
SS = 4  # supersampling


def gradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(int(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
    return img


def glyph(draw, cx, cy, unit, color=(255, 255, 255)):
    """The feed mark from ic_launcher_foreground, in its 108-unit space centred on (54, 54)."""
    def p(x, y):
        return cx + (x - 54) * unit, cy + (y - 54) * unit
    r = 5 * unit
    x, y = p(42, 66)
    draw.ellipse([x - r, y - r, x + r, y + r], fill=color)
    w = round(6.5 * unit)
    for radius in (20, 34):
        # arcs centred on (37, 71), from straight up to straight right
        # Pillow draws the stroke inside the box, so grow it by half a stroke to centre the line
        ax, ay = p(37 - radius, 71 - radius)
        bx, by = p(37 + radius, 71 + radius)
        draw.arc([ax - w / 2, ay - w / 2, bx + w / 2, by + w / 2], start=270, end=360, fill=color, width=w)
        for ex, ey in ((37, 71 - radius), (37 + radius, 71)):
            qx, qy = p(ex, ey)
            draw.ellipse([qx - w / 2, qy - w / 2, qx + w / 2, qy + w / 2], fill=color)


def icon():
    size = 512 * SS
    img = gradient(size, size)
    # the central 72 of 108 units fill the icon, as launchers show it
    glyph(ImageDraw.Draw(img), size / 2, size / 2, size / 72)
    return img.resize((512, 512), Image.LANCZOS)


def feature():
    w, h = 1024 * SS, 500 * SS
    img = gradient(w, h)
    d = ImageDraw.Draw(img)
    tile = 260 * SS
    tx, ty = 110 * SS, (h - tile) // 2
    mark = gradient(tile, tile)
    glyph(ImageDraw.Draw(mark), tile / 2, tile / 2, tile / 72)
    mask = Image.new("L", (tile, tile), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, tile - 1, tile - 1], radius=64 * SS, fill=255)
    d.rounded_rectangle([tx - 5 * SS, ty - 5 * SS, tx + tile + 5 * SS, ty + tile + 5 * SS], radius=68 * SS, fill=(255, 255, 255))
    img.paste(mark, (tx, ty), mask)
    title = ImageFont.truetype(FONT.format("Bold"), 92 * SS)
    tag = ImageFont.truetype(FONT.format("Regular"), 38 * SS)
    x = tx + tile + 70 * SS
    d.text((x, 150 * SS), "Pvot Feed", font=title, fill=(255, 255, 255))
    d.text((x, 275 * SS), "The day's news, grouped.", font=tag, fill=(240, 236, 255))
    d.text((x, 325 * SS), "A feed that ends.", font=tag, fill=(240, 236, 255))
    return img.resize((1024, 500), Image.LANCZOS)


OUT.mkdir(parents=True, exist_ok=True)
icon().save(OUT / "icon.png")
feature().save(OUT / "featureGraphic.png")
print("wrote", OUT / "icon.png", OUT / "featureGraphic.png")
