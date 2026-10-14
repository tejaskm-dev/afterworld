#!/usr/bin/env python3
"""Afterworld Resources: the texture pack that goes with Afterworld Shaders. It gives water a living
surface, draws a new sun, moon and clouds, and dresses the menus in parchment and slate. The world's
blocks come from Patrix 32x in the modpack; with --blocks this pack also repaints the most-seen
blocks itself (grass, dirt, stones, sand, logs, planks, leaves, plants, snow and ice) and warms the
biome colours, for playing without Patrix.

    python3 tools/gen_resourcepack.py [vanilla-assets-dir] [--blocks]

The vanilla assets are only read to keep the layout of container screens (their slots stay where
the game expects them); every texture here is drawn by this script."""
import json
import math
import os
import random
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'pack', 'resourcepack')
T = os.path.join(ROOT, 'assets', 'minecraft', 'textures')
OUT = os.path.join(HERE, '..', 'pack', 'overrides', 'resourcepacks', 'Afterworld-Resources.zip')
ARGS = [a for a in sys.argv[1:] if not a.startswith('--')]
BLOCKS = '--blocks' in sys.argv
VANILLA = ARGS[0] if ARGS else os.environ.get('AW_VANILLA_TEXTURES', '')


def hx(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(img, *path):
    p = os.path.join(T, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def write_json(obj, *path):
    p = os.path.join(T, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)


# ------------------------------------------------------------------ noise and colour

class Noise:
    """Tileable value noise: lattice points wrap with the texture, so tiles meet seamlessly."""

    def __init__(self, seed, size=16):
        self.r = random.Random(seed)
        self.size = size
        self.cache = {}

    def lattice(self, period):
        if period not in self.cache:
            self.cache[period] = [[self.r.random() for _ in range(period)] for _ in range(period)]
        return self.cache[period]

    def at(self, x, y, period):
        g = self.lattice(period)
        s = self.size / period
        fx, fy = x / s, y / s
        x0, y0 = int(math.floor(fx)) % period, int(math.floor(fy)) % period
        x1, y1 = (x0 + 1) % period, (y0 + 1) % period
        tx, ty = fx - math.floor(fx), fy - math.floor(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
        b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
        return a + (b - a) * ty

    def fbm(self, x, y, periods=(4, 8, 16), weights=(0.55, 0.3, 0.15)):
        return sum(self.at(x, y, p) * w for p, w in zip(periods, weights)) / sum(weights)


def ramp(pal, t):
    t = min(0.999, max(0.0, t))
    return pal[int(t * len(pal))]


def tex(fn, size=16, seed=0, mode='RGBA'):
    img = Image.new('RGBA', (size, size))
    px = img.load()
    n = Noise(seed, size)
    r = random.Random(seed + 99)
    for y in range(size):
        for x in range(size):
            px[x, y] = fn(x, y, n, r)
    return img


def grey(v, a=255):
    v = int(max(0, min(255, v * 255)))
    return (v, v, v, a)


# ------------------------------------------------------------------ ground

DIRT = [hx('#4A3122'), hx('#5C3D29'), hx('#6E4A31'), hx('#80583A'), hx('#926848'), hx('#A67C58')]


def dirt(seed=1, pebbles=0.06, pal=DIRT):
    def f(x, y, n, r):
        v = n.fbm(x, y)
        c = ramp(pal, v * 0.9 + 0.05)
        if r.random() < pebbles:
            c = pal[-1] if r.random() < 0.6 else pal[0]
        return c
    return tex(f, seed=seed)


def grass_top():
    def f(x, y, n, r):
        v = n.fbm(x, y, (4, 8, 16), (0.4, 0.35, 0.25))
        blade = 0.08 if r.random() < 0.18 else (-0.06 if r.random() < 0.1 else 0)
        return grey(0.60 + v * 0.26 + blade)
    return tex(f, seed=3)


def grass_side_overlay():
    n = Noise(7)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        depth = 3 + int(n.at(x, 0, 8) * 3.2) + (1 if x % 5 == 2 else 0)
        for y in range(depth):
            shade = 0.84 - y * 0.06 + (n.at(x, y, 16) - 0.5) * 0.12
            px[x, y] = grey(shade)
    return img


STONE = [hx('#5A5D63'), hx('#686B71'), hx('#76797F'), hx('#84878D'), hx('#92959B'), hx('#A0A3A8')]


def stone(seed=11, pal=STONE, strata=True):
    def f(x, y, n, r):
        v = n.fbm(x, y, (2, 4, 8, 16), (0.3, 0.35, 0.2, 0.15))
        if strata:
            v += 0.08 * math.sin((y + n.at(x, y, 4) * 5) * 0.9)
        if r.random() < 0.03:
            v -= 0.25
        return ramp(pal, v)
    return tex(f, seed=seed)


def speckled(base, specks, seed, rate=0.14):
    def f(x, y, n, r):
        v = n.fbm(x, y)
        c = ramp(base, v)
        if r.random() < rate:
            c = specks[r.randrange(len(specks))]
        return c
    return tex(f, seed=seed)


def cobble(seed=13, pal=STONE, moss=None):
    """Rounded stones in dark mortar, lit from the top left; moss creeping over some."""
    r = random.Random(seed)
    cells = [(r.uniform(0, 16), r.uniform(0, 16)) for _ in range(9)]
    n = Noise(seed)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            ds = []
            for (cx, cy) in cells:
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        ds.append(math.hypot(x + 0.5 - cx - ox, y + 0.5 - cy - oy))
            ds.sort()
            edge = ds[1] - ds[0]
            if edge < 1.0:
                c = hx('#3A3B40')
            else:
                # Each stone is lit from the top-left: brighter on that side of its centre.
                nearest = min(((math.hypot(x + 0.5 - cx - ox, y + 0.5 - cy - oy), cx + ox, cy + oy)
                               for (cx, cy) in cells for ox in (-16, 0, 16) for oy in (-16, 0, 16)))
                _, cx, cy = nearest
                lit = ((cx - (x + 0.5)) + (cy - (y + 0.5))) / 12.0
                v = 0.45 + lit + (n.at(x, y, 16) - 0.5) * 0.25 + min(edge, 3) * 0.05
                c = ramp(pal, v)
            if moss and n.fbm(x, y, (4, 8), (0.6, 0.4)) > 0.58:
                c = ramp(moss, n.at(x, y, 16))
            px[x, y] = c
    return img


def gravel():
    pal = [hx('#4E4A48'), hx('#625D59'), hx('#76716B'), hx('#8A847D'), hx('#9E978F'), hx('#B2AA9F')]
    return cobble(seed=17, pal=pal)


SAND = [hx('#C9B07A'), hx('#D4BC86'), hx('#DEC792'), hx('#E6D19E'), hx('#EEDBAA'), hx('#F5E4B8')]
RED_SAND = [hx('#9A4A22'), hx('#AA5628'), hx('#B8632F'), hx('#C67038'), hx('#D27E44'), hx('#DC8C52')]


def sand(pal=SAND, seed=19):
    def f(x, y, n, r):
        v = n.fbm(x, y, (4, 8, 16), (0.3, 0.3, 0.4)) * 0.8 + 0.1
        v += 0.07 * math.sin((x * 0.6 + y * 1.4) + n.at(x, y, 4) * 4)
        return ramp(pal, v + (r.random() - 0.5) * 0.12)
    return tex(f, seed=seed)


def sandstone(part, pal=SAND, seed=23):
    def f(x, y, n, r):
        v = n.fbm(x, y) * 0.5 + 0.25
        if part == 'side':
            band = y in (0, 5, 10, 15)
            v += -0.22 if band else (0.08 if y in (1, 6, 11) else 0)
        elif part == 'top':
            v += 0.1
            if x in (0, 15) or y in (0, 15):
                v -= 0.18
        else:
            v -= 0.05
        return ramp(pal, v)
    return tex(f, seed=seed)


DEEP = [hx('#2E2E36'), hx('#383841'), hx('#42424C'), hx('#4D4D57'), hx('#585862'), hx('#64646E')]


def deepslate(top=False):
    def f(x, y, n, r):
        if top:
            v = n.fbm(x, y) * 0.8 + 0.1
        else:
            v = n.fbm(x * 0.5, y * 2, (4, 8, 16), (0.5, 0.3, 0.2)) + 0.1 * math.sin(x * 1.3 + n.at(x, y, 4) * 3)
        return ramp(DEEP, v)
    return tex(f, seed=29 if top else 31)


# ------------------------------------------------------------------ wood

WOODS = {
    'oak': dict(bark=['#3E2E1E', '#4C3824', '#5A432B', '#684F33', '#765B3C'], wood=['#8A6438', '#9A7244', '#AA8050', '#B88E5C', '#C49C68', '#CFAA76']),
    'spruce': dict(bark=['#2A1E14', '#33251A', '#3D2C1F', '#473425', '#523C2B'], wood=['#5A3E24', '#674A2C', '#745534', '#81603C', '#8D6B45', '#99774F']),
    'birch': dict(bark=['#D8D2C4', '#E2DCCE', '#ECE6D8', '#F4F0E4', '#FAF7EE'], wood=['#B89E6C', '#C4AA78', '#D0B684', '#DAC190', '#E2CA9A', '#EAD4A6']),
}


def log_side(kind):
    w = WOODS[kind]
    bark = [hx(c) for c in w['bark']]

    def f(x, y, n, r):
        if kind == 'birch':
            v = 0.62 + (n.fbm(x, y, (2, 4, 16), (0.4, 0.4, 0.2)) - 0.5) * 0.5 + (0.08 if x in (0, 1) else 0) - (0.1 if x in (14, 15) else 0)
            c = ramp(bark, v)
            # Birch's dark marks: short horizontal scars and the odd knot.
            band = n.at(x, y, 4)
            if (y % 4 == 1 and band > 0.46) or (y % 7 == 4 and band > 0.6 and x % 6 < 4):
                c = hx('#3A3632') if band > 0.66 else hx('#6E6862')
            return c
        groove = 0.5 + 0.5 * math.sin(x * 1.9 + n.at(x, y, 4) * 3.0)
        v = 0.25 + groove * 0.55 + (n.at(x, y, 16) - 0.5) * 0.2
        return ramp(bark, v)
    return tex(f, seed=37 + len(kind))


def log_top(kind):
    w = WOODS[kind]
    bark = [hx(c) for c in w['bark']]
    wood = [hx(c) for c in w['wood']]

    def f(x, y, n, r):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 6.6 or x in (0, 15) or y in (0, 15):
            return ramp(bark, 0.3 + n.at(x, y, 8) * 0.4)
        ring = 0.5 + 0.5 * math.sin(d * 2.1 + n.at(x, y, 4) * 1.2)
        return ramp(wood, 0.25 + ring * 0.6)
    return tex(f, seed=41 + len(kind))


def planks(kind):
    wood = [hx(c) for c in WOODS[kind]['wood']]

    def f(x, y, n, r):
        board = y // 4
        seam = y % 4 == 3
        offset = (board * 7) % 16
        v = 0.45 + (n.at(x + offset, board * 4, 16) - 0.5) * 0.3
        v += 0.12 * math.sin((x + offset) * 0.8 + board * 2.0) * 0.5
        if seam:
            v -= 0.32
        elif y % 4 == 0:
            v += 0.08
        if (x + offset) % 16 == 0 and not seam:
            v -= 0.25
        return ramp(wood, v)
    return tex(f, seed=43 + len(kind))


def leaves(seed, density=0.78, alpha_cut=True):
    """Clustered leaves: soft clumps lit from above, a few gaps to see through."""
    def f(x, y, n, r):
        v = n.fbm(x, y, (4, 8, 16), (0.5, 0.3, 0.2))
        if alpha_cut and v < 1 - density:
            return (0, 0, 0, 0)
        light = 0.55 + v * 0.35 - y * 0.008
        if r.random() < 0.12:
            light += 0.12
        return grey(light)
    return tex(f, seed=seed)


def plant(kind, seed):
    """Grass: tapering blades that curve; fern: arching fronds with leaflets down each side."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    r = random.Random(seed)

    def dot(x, y, shade):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = grey(shade)

    if kind == 'fern':
        for f in range(4):
            base = 4 + f * 2.5 + r.uniform(-0.5, 0.5)
            lean = (-1 if f % 2 else 1) * r.uniform(0.15, 0.35)
            h = r.randint(10, 14)
            for k in range(h):
                t = k / h
                x = base + lean * k + lean * t * t * 6
                y = 15 - k
                shade = 0.5 + 0.38 * t
                dot(x, y, shade)
                if 1 < k < h - 1 and k % 2 == 0:
                    span = int(3 * (1 - t) + 1)
                    for s_ in range(1, span + 1):
                        dot(x - s_, y + s_ * 0.5 - 0.5, shade - 0.06)
                        dot(x + s_, y + s_ * 0.5 - 0.5, shade - 0.04)
        return img
    blades = 9 if kind == 'grass' else 7
    for b_ in range(blades):
        x0 = 0.8 + b_ * (14.4 / blades) + r.uniform(-0.6, 0.6)
        h = r.randint(7, 15) if kind != 'tall_bottom' else 16
        bend = r.uniform(-0.45, 0.45)
        for k in range(h):
            t = k / h
            x = x0 + bend * t * t * 6
            y = 15 - k
            shade = 0.48 + 0.42 * t + (0.06 if b_ % 3 == 0 else 0)
            dot(x, y, shade)
            if t < 0.35:
                dot(x + 1, y, shade - 0.08)
    return img


def snow():
    def f(x, y, n, r):
        v = 0.86 + n.fbm(x, y) * 0.12 + (0.04 if r.random() < 0.08 else 0)
        c = grey(min(1.0, v))
        return (c[0] - 6, c[1] - 2, min(255, c[2] + 4), 255)
    return tex(f, seed=53)


def ice(alpha=190, seed=59, packed=False):
    pal = [hx('#7FA8E0', alpha), hx('#8CB4E8', alpha), hx('#9CC2EE', alpha), hx('#AED0F4', alpha), hx('#C4E0F8', alpha), hx('#DCEEFC', alpha)]

    def f(x, y, n, r):
        v = n.fbm(x, y)
        if packed:
            v = v * 0.6 + 0.2
        crack = abs(math.sin((x - y * 0.7) * 0.9 + n.at(x, y, 4) * 2)) < 0.12
        c = ramp(pal, v + (0.25 if crack else 0))
        return c
    return tex(f, seed=seed)


def clay():
    pal = [hx('#8A92A4'), hx('#96A0B2'), hx('#A2ACBE'), hx('#AEB8C8'), hx('#BAC4D2')]
    return tex(lambda x, y, n, r: ramp(pal, n.fbm(x, y) * 0.9), seed=61)


def mud():
    pal = [hx('#2E2824'), hx('#38312C'), hx('#423A34'), hx('#4C433C'), hx('#564C44')]
    return tex(lambda x, y, n, r: ramp(pal, n.fbm(x, y) * 0.9 + (0.15 if r.random() < 0.05 else 0)), seed=67)


def podzol_top():
    pal = [hx('#3E2814'), hx('#4E331A'), hx('#5E3E20'), hx('#6E4A28'), hx('#7E5630'), hx('#8E6238')]

    def f(x, y, n, r):
        v = n.fbm(x, y)
        if r.random() < 0.12:
            return hx('#8A6A2E')
        return ramp(pal, v)
    return tex(f, seed=71)


# ------------------------------------------------------------------ water

def water_still():
    frames = 32
    img = Image.new('RGBA', (16, 16 * frames))
    px = img.load()
    n = Noise(73)
    for f in range(frames):
        t = f / frames * math.pi * 2
        for y in range(16):
            for x in range(16):
                a = math.sin(x * 0.785 + t) + math.sin(y * 0.785 + t * 1.0 + 1.3) + math.sin((x + y) * 0.39 + t + n.at(x, y, 4) * 6)
                caustic = 1.0 - abs(a) / 3.0
                v = 0.72 + caustic ** 3 * 0.26
                px[x, y + f * 16] = grey(v, 196)
    return img


def water_flow():
    frames = 32
    img = Image.new('RGBA', (32, 32 * frames))
    px = img.load()
    n = Noise(79, 32)
    for f in range(frames):
        for y in range(32):
            for x in range(32):
                yy = (y - f) % 32
                streak = n.at(x, yy, 8) * 0.6 + n.at(x, yy, 16) * 0.4
                v = 0.70 + streak ** 2 * 0.28
                px[x, y + f * 32] = grey(v, 200)
    return img


# ------------------------------------------------------------------ the sky

def sun():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < 7:
                warm = 1.0 - d / 7 * 0.15
                px[x, y] = (255, int(244 * warm), int(206 * warm), 255)
            elif d < 15:
                a = int(255 * (1 - (d - 7) / 8) ** 2.2)
                px[x, y] = (255, 214, 150, a)
    return img


def moon(phase):
    """Phases 0-7: full, waning gibbous, third quarter, waning crescent, new, waxing crescent,
    first quarter, waxing gibbous (the order the game uses)."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    n = Noise(83, 32)
    lit_frac = [1.0, 0.75, 0.5, 0.25, 0.0, 0.25, 0.5, 0.75][phase]
    waning = phase in (1, 2, 3)
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            d = math.hypot(dx, dy)
            if d >= 9:
                if d < 13 and lit_frac > 0:
                    px[x, y] = (200, 214, 255, int(70 * (1 - (d - 9) / 4) * lit_frac))
                continue
            # The terminator: a curved line across the disc.
            k = 1 - 2 * lit_frac
            xt = k * math.sqrt(max(0, 81 - dy * dy))
            lit = (dx > xt) if not waning else (-dx > xt)
            crater = n.fbm(x, y, (4, 8, 16, 32), (0.3, 0.3, 0.2, 0.2))
            v = 0.82 + (crater - 0.5) * 0.35
            if lit:
                px[x, y] = (int(228 * v), int(234 * v), int(255 * v), 255)
            else:
                px[x, y] = (30, 34, 52, 255 if lit_frac < 1 else 0)
    return img


def clouds():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    px = img.load()
    n = Noise(89, 256)
    for y in range(256):
        for x in range(256):
            v = n.fbm(x, y, (8, 16, 32), (0.6, 0.3, 0.1))
            if v > 0.6:
                px[x, y] = (255, 255, 255, 255)
    return img


# ------------------------------------------------------------------ colours of the land

def colormap(kind):
    """The game picks a colour by temperature (x) and rainfall (y). Warmer, softer greens; golden
    in the dry heat; teal and deep in the wet; frosted blue-green in the cold."""
    img = Image.new('RGB', (256, 256))
    px = img.load()
    if kind == 'grass':
        hot_dry, hot_wet, cold = (198, 178, 92), (92, 168, 64), (118, 158, 132)
    elif kind == 'foliage':
        hot_dry, hot_wet, cold = (176, 160, 68), (52, 140, 46), (96, 138, 116)
    else:
        hot_dry, hot_wet, cold = (168, 120, 70), (150, 108, 64), (130, 118, 96)
    for y in range(256):
        for x in range(256):
            temp = 1 - x / 255.0
            rain = (1 - y / 255.0) * temp
            c = []
            for i in range(3):
                warm = hot_dry[i] + (hot_wet[i] - hot_dry[i]) * min(1, rain * 1.4)
                c.append(int(cold[i] + (warm - cold[i]) * temp ** 0.8))
            px[x, y] = tuple(c)
    return img


# ------------------------------------------------------------------ the menus

SLATE = hx('#1E262D')
SLATE_HI = hx('#2C3842')
GOLD = hx('#C8A048')
GOLD_HI = hx('#F0D27A')
INK = hx('#0E1114')


def button(state):
    img = Image.new('RGBA', (200, 20))
    px = img.load()
    fill = {'normal': SLATE, 'highlighted': SLATE_HI, 'disabled': hx('#25282B')}[state]
    edge = {'normal': hx('#4A5A66'), 'highlighted': GOLD, 'disabled': hx('#3A3D40')}[state]
    for y in range(20):
        for x in range(200):
            if x == 0 or y == 0 or x == 199 or y == 19:
                c = INK
            elif x == 1 or y == 1 or x == 198 or y == 18:
                c = edge
            else:
                t = (y - 2) / 15.0
                k = 1.08 - 0.16 * t
                c = (int(fill[0] * k), int(fill[1] * k), int(fill[2] * k), 255)
                if y == 2 and state != 'disabled':
                    c = tuple(min(255, int(v * 1.35)) for v in fill[:3]) + (255,)
            px[x, y] = c
    return img


def hotbar():
    img = Image.new('RGBA', (182, 22), (0, 0, 0, 0))
    px = img.load()
    for y in range(22):
        for x in range(182):
            if x == 0 or y == 0 or x == 181 or y == 21:
                px[x, y] = (14, 17, 20, 230)
            elif (x - 1) % 20 == 0 or (x - 1) % 20 == 19 or y in (1, 20):
                px[x, y] = (200, 160, 72, 150)
            else:
                px[x, y] = (24, 30, 36, 170)
    return img


def hotbar_selection():
    img = Image.new('RGBA', (24, 23), (0, 0, 0, 0))
    px = img.load()
    for y in range(23):
        for x in range(24):
            outer = x in (0, 23) or y in (0, 22)
            inner = x in (1, 22) or y in (1, 21)
            if outer:
                px[x, y] = INK
            elif inner:
                px[x, y] = GOLD_HI
            elif x in (2, 21) or y in (2, 20):
                px[x, y] = GOLD
    for (x, y) in ((0, 0), (23, 0), (0, 22), (23, 22)):
        px[x, y] = (0, 0, 0, 0)
    return img


def crosshair():
    img = Image.new('RGBA', (15, 15), (0, 0, 0, 0))
    px = img.load()
    for k in (2, 3, 4, 10, 11, 12):
        px[7, k] = (255, 255, 255, 255)
        px[k, 7] = (255, 255, 255, 255)
    px[7, 7] = (255, 255, 255, 255)
    return img


# Vanilla's container greys, mapped onto parchment and ink.
PARCHMENT = {
    (198, 198, 198): hx('#E6D8B8'), (255, 255, 255): hx('#FFF6DE'), (85, 85, 85): hx('#8C7A5C'),
    (0, 0, 0): hx('#2B2118'), (139, 139, 139): hx('#B9A684'), (55, 55, 55): hx('#6B5A40'),
}


def parchment_ramp(l):
    stops = [(0, hx('#2B2118')), (55, hx('#6B5A40')), (85, hx('#8C7A5C')), (139, hx('#B9A684')), (198, hx('#E6D8B8')), (255, hx('#FFF6DE'))]
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if a <= l <= b:
            t = (l - a) / max(1, b - a)
            return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)
    return stops[-1][1]


def retheme(path):
    """A container screen in parchment: every grey keeps its place (so slots line up) and takes
    its parchment tone; coloured details (arrows, flames, bubbles) keep theirs."""
    src = Image.open(path).convert('RGBA')
    px = src.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if abs(r - g) < 6 and abs(g - b) < 6:
                c = PARCHMENT.get((r, g, b)) or parchment_ramp(r)
                px[x, y] = c[:3] + (a,)
    return src


CONTAINERS = ['inventory', 'generic_54', 'crafting_table', 'furnace', 'blast_furnace', 'smoker', 'dispenser', 'hopper', 'shulker_box',
              'brewing_stand', 'anvil', 'enchanting_table', 'grindstone', 'stonecutter', 'loom', 'cartography_table', 'smithing', 'beacon',
              'villager', 'horse', 'crafter']


def icon():
    img = Image.new('RGBA', (128, 128))
    px = img.load()
    for y in range(128):
        for x in range(128):
            d = math.hypot(x - 63.5, y - 63.5) / 90
            px[x, y] = (int(18 + 20 * (1 - d)), int(24 + 26 * (1 - d)), int(32 + 30 * (1 - d)), 255)
    # A turning waystone crystal over a gold ring.
    for y in range(128):
        for x in range(128):
            d = math.hypot(x - 63.5, y - 70)
            if 34 <= d <= 38:
                px[x, y] = GOLD
            q = abs(x - 63.5) / 18 + abs(y - 56) / 34
            if q <= 1:
                light = 0.6 + 0.4 * (1 - q) + (0.2 if x < 63 else -0.1)
                px[x, y] = (int(79 * light + 40), int(227 * light), int(210 * light), 255)
    return img


def main():
    import shutil
    shutil.rmtree(os.path.join(ROOT, 'assets'), ignore_errors=True)
    blocks = {
        'grass_block_top': grass_top(), 'grass_block_side_overlay': grass_side_overlay(), 'grass_block_side': dirt(2),
        'dirt': dirt(1), 'coarse_dirt': dirt(5, pebbles=0.25), 'podzol_top': podzol_top(), 'podzol_side': dirt(6),
        'stone': stone(), 'cobblestone': cobble(), 'mossy_cobblestone': cobble(14, moss=[hx('#4A6A30'), hx('#567A38'), hx('#628A40'), hx('#709848')]),
        'andesite': speckled([hx('#7A7C80'), hx('#86888C'), hx('#929498'), hx('#9EA0A4')], [hx('#6A6C70'), hx('#B0B2B6')], 101),
        'diorite': speckled([hx('#C8C6C2'), hx('#D2D0CC'), hx('#DCDAD6'), hx('#E6E4E0')], [hx('#8E8C88'), hx('#A8A6A2')], 103),
        'granite': speckled([hx('#946A58'), hx('#A07462'), hx('#AC7E6C'), hx('#B88A76')], [hx('#6E4A3E'), hx('#D4A694'), hx('#C8B0A6')], 107),
        'tuff': speckled([hx('#5E6058'), hx('#686A62'), hx('#72746C'), hx('#7C7E76')], [hx('#4C4E48'), hx('#8E9088')], 109),
        'deepslate': deepslate(), 'deepslate_top': deepslate(True),
        'gravel': gravel(), 'sand': sand(), 'red_sand': sand(RED_SAND, 21),
        'sandstone': sandstone('side'), 'sandstone_top': sandstone('top'), 'sandstone_bottom': sandstone('bottom'),
        'red_sandstone': sandstone('side', RED_SAND, 25), 'red_sandstone_top': sandstone('top', RED_SAND, 25), 'red_sandstone_bottom': sandstone('bottom', RED_SAND, 25),
        'oak_leaves': leaves(91), 'spruce_leaves': leaves(93, 0.84), 'birch_leaves': leaves(95, 0.74), 'jungle_leaves': leaves(97, 0.82),
        'short_grass': plant('grass', 111), 'fern': plant('fern', 113), 'tall_grass_bottom': plant('tall_bottom', 115), 'tall_grass_top': plant('grass', 117),
        'snow': snow(), 'ice': ice(), 'packed_ice': ice(255, 61, packed=True), 'clay': clay(), 'mud': mud(),
    }
    for kind in WOODS:
        blocks[f'{kind}_log'] = log_side(kind)
        blocks[f'{kind}_log_top'] = log_top(kind)
        blocks[f'{kind}_planks'] = planks(kind)
    if not BLOCKS:
        blocks = {}
    for name, img in blocks.items():
        save(img, 'block', name + '.png')
    save(water_still(), 'block', 'water_still.png')
    write_json({'animation': {'frametime': 2, 'interpolate': True}}, 'block', 'water_still.png.mcmeta')
    save(water_flow(), 'block', 'water_flow.png')
    write_json({'animation': {'frametime': 1}}, 'block', 'water_flow.png.mcmeta')
    save(sun(), 'environment', 'celestial', 'sun.png')
    for i, ph in enumerate(['full_moon', 'waning_gibbous', 'third_quarter', 'waning_crescent', 'new_moon', 'waxing_crescent', 'first_quarter', 'waxing_gibbous']):
        save(moon(i), 'environment', 'celestial', 'moon', ph + '.png')
    save(clouds(), 'environment', 'clouds.png')
    if BLOCKS:
        for k in ('grass', 'foliage', 'dry_foliage'):
            save(colormap(k), 'colormap', k + '.png')
    for state, name in (('normal', 'button'), ('highlighted', 'button_highlighted'), ('disabled', 'button_disabled')):
        save(button(state), 'gui', 'sprites', 'widget', name + '.png')
        write_json({'gui': {'scaling': {'type': 'nine_slice', 'width': 200, 'height': 20, 'border': 3}}}, 'gui', 'sprites', 'widget', name + '.png.mcmeta')
    save(hotbar(), 'gui', 'sprites', 'hud', 'hotbar.png')
    save(hotbar_selection(), 'gui', 'sprites', 'hud', 'hotbar_selection.png')
    save(crosshair(), 'gui', 'sprites', 'hud', 'crosshair.png')
    themed = 0
    if VANILLA and os.path.isdir(VANILLA):
        for c in CONTAINERS:
            p = os.path.join(VANILLA, 'gui', 'container', c + '.png')
            if os.path.exists(p):
                save(retheme(p), 'gui', 'container', c + '.png')
                themed += 1
    os.makedirs(ROOT, exist_ok=True)
    icon().save(os.path.join(ROOT, 'pack.png'))
    with open(os.path.join(ROOT, 'pack.mcmeta'), 'w') as f:
        json.dump({'pack': {'description': 'Afterworld Resources: sky, water and menus to match Afterworld Shaders',
                            'min_format': [97, 0], 'max_format': [97, 1]}}, f, indent=2)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(ROOT):
            for fn in sorted(files):
                p = os.path.join(root, fn)
                z.write(p, os.path.relpath(p, ROOT))
    print(f'{len(blocks)} blocks, {themed} containers themed ->', OUT, os.path.getsize(OUT) // 1024, 'KB')


if __name__ == '__main__':
    main()
