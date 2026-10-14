#!/usr/bin/env python3
"""Sprites for Afterworld's particles: white or near-white so the colour option tints them."""
import json, math, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'particle')
DEF = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'particles')
W = (255, 255, 255)

RUNES = [
    ['..X..', '.XXX.', 'X.X.X', '..X..', '..X..'],
    ['X...X', '.X.X.', '..X..', '.X.X.', 'X...X'],
    ['XXXXX', 'X....', 'XXX..', 'X....', 'X....'],
    ['..X..', '.X.X.', 'X...X', '.X.X.', '..X..'],
    ['X.X.X', 'X.X.X', 'XXXXX', '..X..', '..X..'],
    ['XXX..', '..X..', '..XXX', '..X..', 'XXX..'],
    ['.XXX.', 'X...X', '..XX.', '.X...', 'XXXXX'],
    ['X....', 'XX...', 'X.X..', 'X..X.', 'XXXXX'],
]


def save(name, img):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, name + '.png'))


def glyph(rows):
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == 'X':
                px[1 + i, 1 + j] = W + (255,)
    # Soft halo around the strokes.
    out = img.copy()
    op = out.load()
    for y in range(8):
        for x in range(8):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 8 and 0 <= ny < 8 and px[nx, ny][3]:
                        op[x, y] = W + (90,)
                        break
    return out


def blob(size, falloff, core=1.0):
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    c = 3.5
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - c, y - c) / size
            if d < 1:
                px[x, y] = W + (int(255 * core * (1 - d) ** falloff),)
    return img


def shard(frame):
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    pts = [((3, 0), (4, 0)), ((5, 1), (2, 6)), ((6, 3), (1, 4)), ((4, 6), (3, 1))][frame]
    for y in range(8):
        for x in range(8):
            if abs(x - 3.5) + abs(y - 3.5) * (0.6 + 0.2 * frame) < 3.0:
                px[x, y] = W + (220,)
    px[3, 2] = W + (255,)
    px[4, 3] = W + (255,)
    return img


def gear(frame):
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            a = math.atan2(y - 3.5, x - 3.5) + frame * math.pi / 8
            tooth = math.cos(a * 6) > 0.3
            if (d < 3.6 if tooth else d < 2.8) and d > 1.0:
                px[x, y] = W + (255,)
    return img


def ink(frame):
    r = random.Random(frame)
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5) + r.uniform(-0.8, 0.8)
            if d < 3.2:
                px[x, y] = (40, 34, 60, 230 if d < 2.2 else 140)
    return img


def write_def(name, textures):
    os.makedirs(DEF, exist_ok=True)
    with open(os.path.join(DEF, name + '.json'), 'w') as f:
        json.dump({'textures': [f'afterworld:{t}' for t in textures]}, f, indent=2)


if __name__ == '__main__':
    for i, rows in enumerate(RUNES):
        save(f'rune_{i}', glyph(rows))
    write_def('rune', [f'rune_{i}' for i in range(len(RUNES))])
    for i, (s, f) in enumerate(((1.0, 1.4), (0.8, 1.2), (0.6, 1.0), (0.45, 0.8))):
        save(f'mote_{i}', blob(s * 4.0, f))
    write_def('mote', [f'mote_{i}' for i in range(4)])
    save('ember_0', blob(1.6, 0.6))
    save('ember_1', blob(1.2, 0.5))
    write_def('ember', ['ember_0', 'ember_1'])
    for i in range(4):
        save(f'shard_{i}', shard(i))
    write_def('shard', [f'shard_{i}' for i in range(4)])
    for i in range(2):
        save(f'gear_{i}', gear(i))
    write_def('gear', ['gear_0', 'gear_1'])
    for i in range(3):
        save(f'ink_{i}', ink(i))
    write_def('ink', [f'ink_{i}' for i in range(3)])
    print('particles written')
