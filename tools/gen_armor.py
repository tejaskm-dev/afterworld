#!/usr/bin/env python3
"""Paints the six armour sets as worn on the body (equipment layers), writes their equipment
assets and repair tags. The vanilla iron layer is used only as a map of where each face sits;
every pixel is repainted from the set's own ramp, then trimmed and decorated per set.

Usage: python3 tools/gen_armor.py <vanilla assets dir>  (the folder holding minecraft/textures)
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'afterworld')
DATA = os.path.join(RES, 'data', 'afterworld')


def hexc(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# Five-tone ramps (dark to light), an accent (trim), and a glow colour where the set has one.
SETS = {
    'wardens_regalia': dict(ramp=['#1d2a33', '#2e4652', '#46707a', '#6fa3a5', '#a8d8d0'], trim='#d8b45a', trim_dark='#8a6a2a',
                            glow='#7fe3d2', repair=['afterworld:glyph_gold', 'minecraft:iron_ingot']),
    'duskpine_ranger': dict(ramp=['#1e1a14', '#33291d', '#4f4029', '#6c5a39', '#8e7a52'], trim='#3f5a33', trim_dark='#28381f',
                            glow=None, repair=['minecraft:leather', 'afterworld:glimmer_silk']),
    'emberforged': dict(ramp=['#1a1414', '#2b2222', '#3f3331', '#5a4943', '#7a6359'], trim='#e8a33d', trim_dark='#a8432e',
                        glow='#ff8a3a', repair=['afterworld:ember_core', 'minecraft:netherite_scrap']),
    'rimeguard': dict(ramp=['#3c5468', '#5d7f96', '#8fb1c6', '#c3dbe8', '#eef8ff'], trim='#ffffff', trim_dark='#7fa8c8',
                      glow='#8ae0ff', repair=['afterworld:rime_pearl', 'minecraft:iron_ingot']),
    'starweave': dict(ramp=['#120f26', '#1e1940', '#2e2760', '#463c86', '#6a5cb0'], trim='#c9b8ff', trim_dark='#7a68c8',
                      glow='#fff4c8', repair=['afterworld:star_fragment', 'afterworld:glimmer_silk']),
    'gilded_glyphmail': dict(ramp=['#4a3112', '#7a5320', '#b4842e', '#e0b44a', '#ffe38a'], trim='#2a1c0c', trim_dark='#5a3e18',
                             glow='#fff0a0', repair=['afterworld:glyph_gold', 'minecraft:gold_ingot']),
}

# Faces of the 64x32 humanoid layer (x0, y0, x1, y1) by name.
HEAD_FRONT = (8, 8, 16, 16)
HAT_FRONT = (40, 8, 48, 16)
HEAD_SIDES = [(0, 8, 8, 16), (16, 8, 24, 16), (24, 8, 32, 16)]
BODY_FRONT = (20, 20, 28, 32)
BODY_BACK = (32, 20, 40, 32)
ARM_FRONT = (44, 20, 48, 32)
LEG_FRONT = (4, 20, 8, 32)


def in_rect(x, y, r):
    return r[0] <= x < r[2] and r[1] <= y < r[3]


def paint(base, spec, leggings, seed):
    rnd = random.Random(seed)
    ramp = [hexc(c) for c in spec['ramp']]
    trim = hexc(spec['trim'])
    trim_dark = hexc(spec['trim_dark'])
    glow = hexc(spec['glow']) if spec['glow'] else None
    w, h = base.size
    src = base.load()
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = out.load()
    lums = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a > 0:
                lums.append(0.299 * r + 0.587 * g + 0.114 * b)
    lo, hi = min(lums), max(lums)
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / max(1, hi - lo)
            # Five tones, a touch of noise so plates don't look flat.
            t = min(0.999, max(0.0, t + rnd.uniform(-0.05, 0.05)))
            i = t * 4
            c = lerp(ramp[int(i)], ramp[min(4, int(i) + 1)], i - int(i))
            px[x, y] = (c[0], c[1], c[2], a)

    def opaque(x, y):
        return 0 <= x < w and 0 <= y < h and src[x, y][3] > 0

    def put(x, y, c):
        if opaque(x, y):
            px[x, y] = c

    def border(rect, c, dark):
        x0, y0, x1, y1 = rect
        for x in range(x0, x1):
            put(x, y0, c)
            put(x, y1 - 1, dark)
        for y in range(y0, y1):
            put(x0, y, c)
            put(x1 - 1, y, dark)

    name = spec['name']
    if not leggings:
        # A trimmed brow band on the helmet and trimmed edges on the cuirass.
        for x in range(HEAD_FRONT[0], HEAD_FRONT[2]):
            put(x, HEAD_FRONT[1], trim)
            put(x, HEAD_FRONT[1] + 1, trim_dark)
        border(BODY_FRONT, trim, trim_dark)
        for r in (ARM_FRONT,):
            for x in range(r[0], r[2]):
                put(x, r[1], trim)
    else:
        for x in range(16, 40):
            put(x, 20, trim)
            put(x, 21, trim_dark)

    if name == 'wardens_regalia' and not leggings:
        # The waystone glyph on the chest, and a teal gem on the brow.
        cx, cy = 24, 25
        for dx, dy in [(0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2), (0, 0)]:
            put(cx + dx, cy + dy, glow)
        put(11, 10, glow)
        put(12, 10, glow)
    elif name == 'duskpine_ranger':
        # Stitching and a hood feather.
        for y in range(20, 32, 2):
            put(BODY_FRONT[0] + 3 if not leggings else 5, y, trim)
        if not leggings:
            for i, (x, y) in enumerate([(1, 9), (2, 10), (2, 11), (3, 12), (3, 13)]):
                put(x, y, hexc('#c8b070') if i % 2 == 0 else hexc('#8a7040'))
    elif name == 'emberforged':
        # Glowing seams that run like cracks in cooling iron.
        for _ in range(26 if not leggings else 16):
            x, y = rnd.randrange(w), rnd.randrange(16, 32) if leggings else rnd.randrange(h)
            length = rnd.randrange(2, 5)
            for k in range(length):
                xx, yy = x + k * rnd.choice([0, 1]), y + k
                put(xx, yy, glow if k % 2 == 0 else trim)
    elif name == 'rimeguard':
        # Frost along the top of every plate, and icy points on the helmet.
        for x in range(w):
            for y in range(h):
                if opaque(x, y) and not opaque(x, y - 1) and rnd.random() < 0.7:
                    px[x, y] = hexc('#ffffff')
        if not leggings:
            for x in (9, 11, 13, 15):
                put(x, 8, glow)
    elif name == 'starweave':
        # Stars woven through the cloth.
        for _ in range(40 if not leggings else 22):
            x, y = rnd.randrange(w), rnd.randrange(h)
            put(x, y, glow if rnd.random() < 0.6 else trim)
    elif name == 'gilded_glyphmail':
        # Engraved glyph lines across the gold.
        for y in range(1, h, 3):
            for x in range(w):
                if opaque(x, y) and (x + y) % 5 == 0:
                    px[x, y] = trim
        if not leggings:
            cx, cy = 24, 26
            for dx, dy in [(0, -3), (0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (0, 0), (0, 1), (0, 2)]:
                put(cx + dx, cy + dy, glow)
    return out


def main():
    vanilla = sys.argv[1] if len(sys.argv) > 1 else None
    if not vanilla:
        sys.exit('pass the vanilla assets folder (holding minecraft/textures)')
    eq = os.path.join(vanilla, 'minecraft', 'textures', 'entity', 'equipment')
    for layer in ('humanoid', 'humanoid_leggings'):
        os.makedirs(os.path.join(ASSETS, 'textures', 'entity', 'equipment', layer), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'equipment'), exist_ok=True)
    os.makedirs(os.path.join(DATA, 'tags', 'item'), exist_ok=True)
    for i, (name, spec) in enumerate(SETS.items()):
        spec['name'] = name
        for layer, leggings in (('humanoid', False), ('humanoid_leggings', True)):
            base = Image.open(os.path.join(eq, layer, 'iron.png')).convert('RGBA')
            img = paint(base, spec, leggings, seed=i * 7 + (1 if leggings else 0))
            img.save(os.path.join(ASSETS, 'textures', 'entity', 'equipment', layer, f'{name}.png'))
        with open(os.path.join(ASSETS, 'equipment', f'{name}.json'), 'w') as f:
            json.dump({'layers': {'humanoid': [{'texture': f'afterworld:{name}'}],
                                  'humanoid_leggings': [{'texture': f'afterworld:{name}'}]}}, f, indent=2)
            f.write('\n')
        with open(os.path.join(DATA, 'tags', 'item', f'repairs_{name}.json'), 'w') as f:
            json.dump({'values': spec['repair']}, f, indent=2)
            f.write('\n')
    print(len(SETS), 'armour sets painted')


if __name__ == '__main__':
    main()
