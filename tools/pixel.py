"""Tiny pixel-art toolkit used by gen_assets.py. Sprites are ASCII grids mapped through palettes."""
import random
from PIL import Image

def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))

def shade(c, f):
    """f < 1 darkens, f > 1 lightens (toward white)."""
    if f <= 1:
        return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])
    t = f - 1
    return mix(c, (255, 255, 255, c[3]), min(1, t))

def ramp(base, steps=(0.45, 0.65, 0.85, 1.0, 1.25, 1.5)):
    c = hexc(base)
    return [shade(c, s) for s in steps]

def sprite(rows, palette, size=None):
    h = len(rows)
    w = max(len(r) for r in rows)
    img = Image.new('RGBA', (size or w, size or h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ('.', ' '):
                continue
            col = palette.get(ch)
            if col is None:
                raise KeyError(f"palette missing '{ch}'")
            if isinstance(col, str):
                col = hexc(col)
            px[x, y] = col
    return img

def noise_tile(w, h, base, var=10, seed=0, speck=None, speck_rate=0.0):
    r = random.Random(seed)
    img = Image.new('RGBA', (w, h))
    px = img.load()
    b = hexc(base) if isinstance(base, str) else base
    for y in range(h):
        for x in range(w):
            d = r.randint(-var, var)
            c = (max(0, min(255, b[0] + d)), max(0, min(255, b[1] + d)), max(0, min(255, b[2] + d)), 255)
            if speck and r.random() < speck_rate:
                c = hexc(speck) if isinstance(speck, str) else speck
            px[x, y] = c
    return img

def outline(img, color=(26, 22, 30, 255)):
    """Adds a 1px outline around opaque pixels (inside the canvas)."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 0:
                        dst[x, y] = color
                        break
    return out

def save(img, path):
    import os
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
