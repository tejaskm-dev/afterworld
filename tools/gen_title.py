#!/usr/bin/env python3
"""Title screen art: the AFTERWORLD logo and the parallax layers of the night scene from the concept."""
import math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(__file__))
from pixel import hexc, mix, shade, save

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'gui', 'title')

GLYPHS = {
    'A': ["..XXXX..", ".XXXXXX.", "XXX..XXX", "XX....XX", "XX....XX", "XXXXXXXX", "XXXXXXXX", "XX....XX", "XX....XX", "XX....XX"],
    'F': ["XXXXXXXX", "XXXXXXXX", "XX......", "XX......", "XXXXXX..", "XXXXXX..", "XX......", "XX......", "XX......", "XX......"],
    'T': ["XXXXXXXX", "XXXXXXXX", "...XX...", "...XX...", "...XX...", "...XX...", "...XX...", "...XX...", "...XX...", "...XX..."],
    'E': ["XXXXXXXX", "XXXXXXXX", "XX......", "XX......", "XXXXXX..", "XXXXXX..", "XX......", "XX......", "XXXXXXXX", "XXXXXXXX"],
    'R': ["XXXXXXX.", "XXXXXXXX", "XX....XX", "XX....XX", "XXXXXXXX", "XXXXXXX.", "XX..XX..", "XX...XX.", "XX....XX", "XX....XX"],
    'W': ["XX......XX", "XX......XX", "XX......XX", "XX......XX", "XX..XX..XX", "XX..XX..XX", "XX.XXXX.XX", "XXXX..XXXX", "XXX....XXX", "XX......XX"],
    'O': [".XXXXXX.", "XXXXXXXX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XXXXXXXX", ".XXXXXX."],
    'L': ["XX......", "XX......", "XX......", "XX......", "XX......", "XX......", "XX......", "XX......", "XXXXXXXX", "XXXXXXXX"],
    'D': ["XXXXXX..", "XXXXXXX.", "XX...XXX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX...XXX", "XXXXXXX.", "XXXXXX.."],
}

def logo():
    word = "AFTERWORLD"
    gap = 2
    width = sum(len(GLYPHS[c][0]) for c in word) + gap * (len(word) - 1)
    height = 10
    mask = Image.new('L', (width, height), 0)
    x = 0
    for c in word:
        g = GLYPHS[c]
        for gy, row in enumerate(g):
            for gx, ch in enumerate(row):
                if ch == 'X':
                    mask.putpixel((x + gx, gy), 255)
        x += len(g[0]) + gap
    S = 4
    pad = 8
    big = mask.resize((width * S, height * S), Image.NEAREST)
    W, H = width * S + pad * 2, height * S + pad * 2 + 10
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    face_top, face_bottom = hexc('#FFF6DC'), hexc('#E8C88A')
    extrude = hexc('#8C5A22')
    extrude_dark = hexc('#4A2E12')
    outline_c = hexc('#120E14')
    # Glow behind everything.
    glow = Image.new('L', (W, H), 0)
    glow.paste(big, (pad, pad))
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    gpx = glow.load()
    px = img.load()
    for yy in range(H):
        for xx in range(W):
            a = gpx[xx, yy]
            if a > 0:
                px[xx, yy] = (232, 163, 61, int(a * 0.55))
    bpx = big.load()
    depth = 6
    # Extrusion: the letters pushed down and back, like an old carved sign.
    for d in range(depth, 0, -1):
        col = mix(extrude, extrude_dark, d / depth)
        for yy in range(big.height):
            for xx in range(big.width):
                if bpx[xx, yy]:
                    px[pad + xx, pad + yy + d] = col
    for yy in range(big.height):
        t = yy / (big.height - 1)
        for xx in range(big.width):
            if bpx[xx, yy]:
                c = mix(face_top, face_bottom, t)
                if (yy // S) % 2 == 0 and yy % S == 0:
                    c = shade(c, 1.06)
                px[pad + xx, pad + yy] = c
    # Outline around face + extrusion.
    solid = Image.new('L', (W, H), 0)
    sp = solid.load()
    for yy in range(H):
        for xx in range(W):
            if px[xx, yy][3] == 255:
                sp[xx, yy] = 255
    out = img.copy()
    op = out.load()
    for yy in range(H):
        for xx in range(W):
            if sp[xx, yy] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = xx + dx, yy + dy
                    if 0 <= nx < W and 0 <= ny < H and sp[nx, ny] == 255:
                        op[xx, yy] = outline_c
                        break
    # A small edict shard gleam above the O.
    save(out, os.path.join(OUT, 'logo.png'))
    return out

def moon():
    S = 96
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = S / 2
    r = 26
    for yy in range(S):
        for xx in range(S):
            dd = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
            if dd <= r:
                t = dd / r
                c = mix(hexc('#F4E7BE'), hexc('#D9C48C'), t ** 1.6)
                img.putpixel((xx, yy), c)
    rng = random.Random(4)
    for _ in range(9):
        ang = rng.random() * math.pi * 2
        rr = rng.random() * r * 0.75
        x0, y0 = cx + math.cos(ang) * rr, cy + math.sin(ang) * rr
        cr = rng.randint(2, 5)
        d.ellipse((x0 - cr, y0 - cr, x0 + cr, y0 + cr), fill=hexc('#CBB57E'))
        d.ellipse((x0 - cr + 1, y0 - cr + 1, x0 + cr - 1, y0 + cr - 1), fill=hexc('#D7C291'))
    # The faint chevron from the concept art.
    d.line((cx - 12, cy, cx + 2, cy - 10), fill=hexc('#BFA874'), width=2)
    d.line((cx - 12, cy, cx + 2, cy + 10), fill=hexc('#BFA874'), width=2)
    save(img, os.path.join(OUT, 'moon.png'))

def halo():
    S = 256
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    cx = cy = S / 2
    for yy in range(S):
        for xx in range(S):
            dd = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
            if dd <= 126:
                c = hexc('#1B2731', 255) if dd > 92 else hexc('#24343F', 255)
                img.putpixel((xx, yy), c)
    save(img, os.path.join(OUT, 'halo.png'))

def ridge(w, h, seed, base_y, amp, octaves, color, top_color=None, pines=0, pine_color=None):
    rng = random.Random(seed)
    phases = [(rng.random() * 6.28, rng.random() * 6.28) for _ in range(octaves)]
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = img.load()
    heights = []
    for x in range(w):
        y = base_y
        for i, (p1, p2) in enumerate(phases):
            f = (i + 1) * 1.7
            y += math.sin(x / w * math.pi * 2 * f + p1) * amp / (i + 1) + math.sin(x / w * math.pi * 2 * f * 2.3 + p2) * amp / (2 * (i + 1))
        y = int(y)
        heights.append(y)
        for yy in range(max(0, y), h):
            c = color if top_color is None else mix(top_color, color, min(1, (yy - y) / 8))
            px[x, yy] = c
    if pines:
        d = ImageDraw.Draw(img)
        for _ in range(pines):
            x = rng.randrange(w)
            y = heights[x]
            ph = rng.randint(6, 14)
            d.polygon([(x, y - ph), (x - ph // 3, y + 1), (x + ph // 3, y + 1)], fill=pine_color or color)
    return img

def layers():
    W = 512
    far = ridge(W, 140, 11, 70, 18, 3, hexc('#1E2A33'), top_color=hexc('#2A3944'))
    save(far, os.path.join(OUT, 'mountains.png'))
    mid = ridge(W, 140, 23, 80, 12, 3, hexc('#26342C'), top_color=hexc('#2F4236'), pines=70, pine_color=hexc('#1C2822'))
    save(mid, os.path.join(OUT, 'hills.png'))
    fg = ridge(W, 120, 37, 60, 8, 2, hexc('#34483A'), top_color=hexc('#3E5645'))
    d = ImageDraw.Draw(fg)
    # The winding amber road.
    pts = []
    for i in range(80):
        t = i / 79
        x = 60 + t * 300
        y = 100 - t * 34 + math.sin(t * 7) * 6
        pts.append((x, y))
    d.line(pts, fill=hexc('#C98A2E'), width=2)
    for i in range(0, 80, 9):
        x, y = pts[i]
        d.point((x, y - 3), fill=hexc('#FFD27A'))
    # The traveller with a lantern.
    tx, ty = 170, 74
    d.ellipse((tx - 3, ty - 18, tx + 3, ty - 12), fill=hexc('#0E1216'))
    d.rectangle((tx - 2, ty - 12, tx + 2, ty), fill=hexc('#0E1216'))
    d.point((tx + 4, ty - 7), fill=hexc('#FFD27A'))
    d.point((tx + 4, ty - 6), fill=hexc('#E8A33D'))
    save(fg, os.path.join(OUT, 'foreground.png'))
    # Castle silhouette with lit windows.
    cw, ch = 160, 170
    castle = Image.new('RGBA', (cw, ch), (0, 0, 0, 0))
    d = ImageDraw.Draw(castle)
    dark = hexc('#0E1216')
    d.rectangle((30, 70, 130, 170), fill=dark)
    d.rectangle((50, 40, 110, 70), fill=dark)
    d.rectangle((66, 10, 94, 40), fill=dark)
    d.polygon([(126, 70), (136, 22), (146, 70)], fill=dark)
    d.rectangle((126, 70, 146, 170), fill=dark)
    d.polygon([(22, 90), (30, 50), (38, 90)], fill=dark)
    for (x, y) in ((76, 22), (76, 50), (90, 50), (58, 92), (98, 92), (78, 120), (58, 140), (98, 140)):
        d.rectangle((x, y, x + 5, y + 8), fill=hexc('#E8A33D'))
        d.point((x + 2, y + 2), fill=hexc('#FFE6A8'))
    for x in range(30, 131, 8):
        d.rectangle((x, 64, x + 4, 70), fill=dark)
    save(castle, os.path.join(OUT, 'castle.png'))

def panel():
    w, h = 64, 64
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=4, fill=(14, 18, 22, 190), outline=hexc('#5A4420'))
    d.rounded_rectangle((2, 2, w - 3, h - 3), radius=3, outline=(232, 163, 61, 70))
    os.makedirs(os.path.join(OUT, '..', 'sprites', 'title'), exist_ok=True)
    img.save(os.path.join(OUT, '..', 'sprites', 'title', 'panel.png'))
    with open(os.path.join(OUT, '..', 'sprites', 'title', 'panel.png.mcmeta'), 'w') as f:
        f.write('{"gui": {"scaling": {"type": "nine_slice", "width": 64, "height": 64, "border": 6}}}\n')

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    logo()
    moon()
    halo()
    layers()
    panel()
    print('title art written')
