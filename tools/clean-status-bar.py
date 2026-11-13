#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
"""Hide notification icons right of the clock in a store screenshot.

Usage: tools/clean-status-bar.py <in.png> <out.png>
Each row of the icon area is painted with that row's median colour, so it also works where the
app's translucent top bar lets content show through.
"""
import sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
bar = int(min(w, h) * 0.085)
px = im.load()
# the clock is the first run of bright pixels from the left
cols = [any(sum(px[x, y]) > 600 for y in range(bar // 4, bar)) for x in range(w // 2)]
x = cols.index(True)
gap = 0
while x < w // 2 and gap < max(8, int(bar * 0.12)):
    gap = 0 if cols[x] else gap + 1
    x += 1
clock_end = x - gap
# the notification icons: bright columns between the clock and the middle of the screen
icons = [xx for xx in range(clock_end + 6, w // 2) if any(sum(px[xx, y]) > 450 for y in range(bar // 4, bar))]
if icons:
    left, right = icons[0] - 6, icons[-1] + 8
    top, bottom = bar // 5, bar + 4
    for y in range(top, bottom):
        # the row's median colour outside the icons: the bar's own colour, text showing through ignored
        row = [px[xx, y] for xx in list(range(right, min(w, right + 200))) + list(range(max(0, left - 40), left))]
        fill = tuple(sorted(c[i] for c in row)[len(row) // 2] for i in range(3))
        for xx in range(left, right):
            px[xx, y] = fill
im.save(dst)
