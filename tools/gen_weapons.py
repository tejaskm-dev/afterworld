#!/usr/bin/env python3
"""Item sprites for Afterworld's arms. Each weapon is painted along the diagonal, the way vanilla
holds its swords: a profile function says, for every pixel, how far along the weapon it is (t, from
pommel 0 to tip 1) and how far off the centre line (u), and returns a colour or nothing."""
import math, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'item')
OUTLINE = (24, 20, 26, 255)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def paint(name, profile, outline=True):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    x0, y0, x1, y1 = 1.5, 14.5, 14.5, 1.5
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    ax, ay = dx / length, dy / length
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            rel = ((cx - x0) * ax + (cy - y0) * ay)
            t = rel / length
            u = (cx - x0) * -ay + (cy - y0) * ax   # positive = above-left of the line
            c = profile(t, u)
            if c:
                px[x, y] = c
    if outline:
        filled = {(x, y) for y in range(16) for x in range(16) if px[x, y][3]}
        for (x, y) in list(filled):
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in filled and not px[nx, ny][3]:
                    px[nx, ny] = OUTLINE
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + '.png'))


def grip(t, u, t0, t1, w, a, b):
    if t0 <= t < t1 and abs(u) < w:
        return a if int(t * 18) % 2 else b
    return None


def emberbrand(t, u):
    if t < 0.07 and abs(u) < 1.3:
        return hexc('#E8C060')
    g = grip(t, u, 0.07, 0.3, 0.75, hexc('#4A2E1A'), hexc('#6A4428'))
    if g:
        return g
    if 0.3 <= t < 0.37 and abs(u) < 3.0:
        return hexc('#FF7A2A') if abs(u) < 0.8 else hexc('#3A3A44')
    if 0.37 <= t <= 1.0:
        w = 1.9 * (1 - max(0, t - 0.82) / 0.18)
        if abs(u) < w:
            if abs(u) < 0.55:
                return mix(hexc('#FF7A2A'), hexc('#FFD27A'), (t - 0.4) / 0.6)
            return hexc('#9A9AAA') if u > 0 else hexc('#2E2E38')
    return None


def shardsong_glaive(t, u):
    if t < 0.64 and abs(u) < 0.7:
        if int(t * 20) % 5 == 0:
            return hexc('#C8A8FF')
        return hexc('#6A4A2A') if u > 0 else hexc('#4A321C')
    if 0.62 <= t <= 1.0:
        w = 2.5 * math.sin(math.pi * (t - 0.62) / 0.38)
        if abs(u) < max(0.6, w):
            return mix(hexc('#F0E0FF'), hexc('#8A5CD0'), abs(u) / 2.5 + (t - 0.62))
    return None


def lurkers_hook(t, u):
    g = grip(t, u, 0.0, 0.45, 0.75, hexc('#3A4A22'), hexc('#2A3418'))
    if g:
        return g
    if 0.42 <= t < 0.48 and abs(u) < 1.6:
        return hexc('#5A5A5A')
    # The hook: a crescent curling back from the head of the haft.
    cx, cu = 0.74, 1.8
    d = math.hypot((t - cx) * 18.4, u - cu)
    if 2.0 < d < 3.6 and (t > 0.5) and not (u > cu and t < cx):
        return mix(hexc('#F0E8D0'), hexc('#8A7A5A'), (d - 2.0) / 1.6)
    return None


def echo_edge(t, u):
    if t < 0.05 and abs(u) < 1.0:
        return hexc('#B79CE8')
    g = grip(t, u, 0.05, 0.3, 0.7, hexc('#1A1424'), hexc('#2A2244'))
    if g:
        return g
    if 0.3 <= t < 0.35 and abs(u) < 2.0:
        return hexc('#6A4AB0')
    if 0.35 <= t <= 0.88:
        w = 1.25 * (1 - max(0, t - 0.7) / 0.18)
        if abs(u) < w:
            return hexc('#D8C0FF') if u > 0.45 else (hexc('#6A4AB0') if abs(u) < 0.45 else hexc('#141220'))
    return None


def gearspring_maul(t, u):
    if t < 0.68 and abs(u) < 0.7:
        return hexc('#5A5E66') if int(t * 16) % 3 else hexc('#B8892E')
    if 0.66 <= t <= 0.98 and abs(u) < 3.4:
        edge = abs(u) > 2.6 or t > 0.93 or t < 0.7
        c = hexc('#6A4E1A') if edge else hexc('#B8892E')
        if not edge and (int(t * 30) + int(u + 4)) % 3 == 0:
            c = hexc('#E8C060')
        return c
    return None


def rimefang_spear(t, u):
    if t < 0.68 and abs(u) < 0.6:
        return hexc('#D8C8A8') if u > 0 else hexc('#A8987A')
    if 0.66 <= t < 0.7 and abs(u) < 1.4:
        return hexc('#6AC8F0')
    if 0.7 <= t <= 1.0:
        w = 1.9 * (1 - abs(t - 0.8) / 0.2)
        if abs(u) < max(0.5, w):
            return mix(hexc('#FFFFFF'), hexc('#4AA8E0'), abs(u) / 1.9 + (t - 0.7) * 0.6)
    return None


def bellhammer(t, u):
    if t < 0.62 and abs(u) < 0.7:
        return hexc('#E8C060') if int(t * 20) % 6 == 0 else hexc('#4A2E1A')
    if 0.6 <= t <= 0.98:
        # A bell, mouth toward the tip.
        k = (t - 0.6) / 0.38
        w = 1.6 + 1.8 * k * k
        if abs(u) < w:
            if k > 0.86:
                return hexc('#8A6420')
            return hexc('#F2C14E') if u > w * 0.3 else hexc('#C8962E')
    return None


def second_hand(t, u):
    if t < 0.05 and abs(u) < 1.0:
        return hexc('#E8C060')
    g = grip(t, u, 0.05, 0.26, 0.6, hexc('#2A2018'), hexc('#3A2E22'))
    if g:
        return g
    if 0.26 <= t < 0.31 and abs(u) < 1.9:
        return hexc('#E8C060')
    if 0.26 <= t < 0.88 and abs(u) < 0.55:
        return hexc('#E8E8F0') if u > 0 else hexc('#9A9AAA')
    if 0.86 <= t <= 1.0:
        w = 1.8 * (1 - (t - 0.86) / 0.14)
        if abs(u) < w:
            return hexc('#E8C060')
    return None


def pilgrims_staff(t, u):
    if t < 0.78 and abs(u) < 0.6:
        return hexc('#E8DCC0') if u > 0 else hexc('#B8A888')
    d = math.hypot((t - 0.9) * 18.4, u)
    if d < 2.2:
        return mix(hexc('#FFFFFF'), hexc('#FFE070'), d / 2.2)
    if 2.2 <= d < 2.9 and int((math.atan2(u, (t - 0.9) * 18.4) + 4) * 2) % 2 == 0:
        return hexc('#C8A050')
    return None


WEAPONS = {
    'emberbrand': emberbrand, 'shardsong_glaive': shardsong_glaive, 'lurkers_hook': lurkers_hook, 'echo_edge': echo_edge,
    'gearspring_maul': gearspring_maul, 'rimefang_spear': rimefang_spear, 'bellhammer': bellhammer, 'second_hand': second_hand,
    'pilgrims_staff': pilgrims_staff,
}

if __name__ == '__main__':
    for name, fn in WEAPONS.items():
        paint(name, fn)
    # The Stopped effect's icon: a clock face with its hands at rest.
    img = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    px = img.load()
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d < 8:
                px[x, y] = hexc('#E8D7A0') if d < 6.6 else hexc('#8A6420')
    for i in range(1, 6):
        px[8, 8 - i] = hexc('#2A2018')
    for i in range(1, 4):
        px[8 + i, 8] = hexc('#2A2018')
    eff = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'mob_effect')
    os.makedirs(eff, exist_ok=True)
    img.save(os.path.join(eff, 'stopped.png'))
    print(len(WEAPONS), 'weapon sprites written')
