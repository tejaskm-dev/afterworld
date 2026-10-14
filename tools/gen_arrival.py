#!/usr/bin/env python3
"""Art for the arrival screen: rings of light on the floor, a pool of glow, a spotlight and a shaft of light."""
import math, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'gui', 'arrival')


def ring():
    """One thin ring of light on the floor, seen from a little above: a crisp line with a soft halo.
    The screen draws the far half behind the figure and the near half in front of it."""
    W, H = 256, 64
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = img.load()
    cx, cy, rx, ry = W / 2 - 0.5, H / 2 - 0.5, 120.0, 26.0
    for y in range(H):
        for x in range(W):
            dx, dy = x - cx, y - cy
            f = (dx / rx) ** 2 + (dy / ry) ** 2 - 1
            g = 2 * math.hypot(dx / rx ** 2, dy / ry ** 2)
            d = abs(f / g) if g > 0 else 99
            # Nearer (lower) side a little brighter.
            near = 0.75 + 0.25 * max(0.0, dy / ry)
            a = (math.exp(-(d / 0.7) ** 2) + 0.35 * math.exp(-(d / 3.5) ** 2)) * near
            if a > 0.01:
                px[x, y] = (255, 255, 255, min(255, int(255 * a)))
    return img


def floor():
    W, H = 128, 32
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            r = math.hypot((x - W / 2 + 0.5) / (W / 2), (y - H / 2 + 0.5) / (H / 2))
            if r < 1:
                px[x, y] = (255, 255, 255, int(255 * (1 - r) ** 1.8))
    return img


def spotlight():
    S = 128
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - S / 2 + 0.5, y - S / 2 + 0.5) / (S / 2)
            if r < 1:
                a = (1 - r) ** 2.2
                px[x, y] = (255, 255, 255, int(255 * a))
    return img


def rays():
    W, H = 128, 160
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        t = y / H
        half = 10 + 50 * t
        for x in range(W):
            d = abs(x - W / 2 + 0.5) / half
            if d < 1:
                a = (1 - d) ** 1.5 * (0.15 + 0.85 * t) * (1 - t) ** 0.4
                px[x, y] = (255, 240, 210, int(200 * a))
    return img


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    ring().save(os.path.join(OUT, 'ring.png'))
    floor().save(os.path.join(OUT, 'floor.png'))
    spotlight().save(os.path.join(OUT, 'spotlight.png'))
    rays().save(os.path.join(OUT, 'rays.png'))
    print('arrival art written')
