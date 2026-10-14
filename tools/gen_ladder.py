#!/usr/bin/env python3
"""The Warden's Ladder's stones: the gate in the world and the rungs up and out on each floor.
Drawn bold and bright (thick outlines, a few flat tones, one glow colour per stone), the way the
game's own art is going. Writes the textures, models, blockstate and item."""
import json
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld')


def hx(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


# A dusk-violet stone: outline, shadow, body, light, highlight.
STONE = [hx('#1C1528'), hx('#352A48'), hx('#4B3D63'), hx('#62527E'), hx('#7C6C99')]
GOLD = [hx('#5A3A0E'), hx('#B07A1E'), hx('#F0C048'), hx('#FFF0A8')]
CYAN = [hx('#0E3C48'), hx('#1E8CA0'), hx('#5ED8E8'), hx('#D8FFFF')]
COLD = [hx('#241C34'), hx('#3A2F50'), hx('#55487A')]


def blocks():
    """The stone face: four chunky blocks with dark mortar and a lit top-left edge on each."""
    img = Image.new('RGBA', (16, 16), STONE[2])
    px = img.load()
    rows = [(0, 7, 0), (8, 15, 4)]
    for (y0, y1, off) in rows:
        for bx in range(-1, 2):
            x0 = bx * 8 + off
            for y in range(y0, y1 + 1):
                for x in range(x0, x0 + 8):
                    if not 0 <= x < 16:
                        continue
                    u, v = x - x0, y - y0
                    if u == 7 or v == y1 - y0:
                        px[x, y] = STONE[0]
                    elif u == 0 or v == 0:
                        px[x, y] = STONE[3]
                    elif u == 6 or v == y1 - y0 - 1:
                        px[x, y] = STONE[1]
                    elif (x * 7 + y * 13) % 11 == 0:
                        px[x, y] = STONE[4]
    return img


def side():
    """The sides: the stone, with a ladder carved up the middle in gold."""
    img = blocks()
    px = img.load()
    for y in range(16):
        for x in (4, 11):
            px[x, y] = GOLD[0]
            px[x + 1 if x == 4 else x - 1, y] = GOLD[2] if y % 4 else GOLD[3]
    for y in (2, 6, 10, 14):
        for x in range(5, 11):
            px[x, y] = GOLD[2]
            px[x, y + 1] = GOLD[0]
    return img


def plate(glow, emblem, lit=True):
    """A top face: a round plate ringed in the glow colour with an emblem in the middle."""
    img = blocks()
    px = img.load()
    ring = glow if lit else COLD
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 6.0 < d <= 7.3:
                px[x, y] = ring[1] if lit else ring[0]
            elif 5.2 < d <= 6.0:
                px[x, y] = ring[2] if lit else ring[1]
            elif d <= 5.2:
                px[x, y] = STONE[1] if lit else COLD[0]
    for (x, y, tone) in emblem:
        px[x, y] = (glow if lit else COLD)[min(tone, len(glow if lit else COLD) - 1)]
    return img


def chevrons():
    """Two chevrons pointing up (north): the way on."""
    out = []
    for k, y0 in enumerate((4, 8)):
        for i in range(5):
            out.append((7 - i, y0 + i, 3 if i == 0 else 2))
            out.append((8 + i, y0 + i, 3 if i == 0 else 2))
            out.append((7 - i, y0 + i + 1, 1))
            out.append((8 + i, y0 + i + 1, 1))
    return [(x, y, t) for (x, y, t) in out if 2 <= x <= 13 and 2 <= y <= 13]


def door():
    """A round-topped doorway: the way home."""
    out = []
    for y in range(5, 12):
        out.append((5, y, 2))
        out.append((10, y, 2))
    for x in range(6, 10):
        out.append((x, 4, 3))
    out.append((5, 4, 2))
    out.append((10, 4, 2))
    for y in range(5, 12):
        for x in range(6, 10):
            out.append((x, y, 1))
    out.append((8, 8, 3))
    return out


def ladder_emblem():
    """The gate's emblem: a ladder of five rungs."""
    out = []
    for y in range(3, 13):
        out.append((5, y, 2))
        out.append((10, y, 2))
    for y in (4, 6, 8, 10, 12):
        for x in range(6, 10):
            out.append((x, y, 3))
    return out


def main():
    tex = os.path.join(A, 'textures', 'block')
    side().save(os.path.join(tex, 'ladder_side.png'))
    plate(GOLD, ladder_emblem()).save(os.path.join(tex, 'ladder_gate_top.png'))
    plate(CYAN, chevrons(), lit=False).save(os.path.join(tex, 'ladder_up_top.png'))
    plate(CYAN, chevrons()).save(os.path.join(tex, 'ladder_up_top_active.png'))
    plate(GOLD, door(), lit=False).save(os.path.join(tex, 'ladder_out_top.png'))
    plate(GOLD, door()).save(os.path.join(tex, 'ladder_out_top_active.png'))
    variants = {}
    for kind in ('gate', 'up', 'out'):
        for active in (False, True):
            top = 'ladder_gate_top' if kind == 'gate' else f'ladder_{kind}_top' + ('_active' if active else '')
            name = f'ladder_stone_{kind}' + ('_active' if active and kind != 'gate' else '')
            write(os.path.join(A, 'models', 'block', name + '.json'), {
                'parent': 'minecraft:block/cube_bottom_top',
                'textures': {'top': f'afterworld:block/{top}', 'side': 'afterworld:block/ladder_side', 'bottom': 'afterworld:block/ladder_side'}})
            variants[f'active={"true" if active else "false"},kind={kind}'] = {'model': f'afterworld:block/{name}'}
    write(os.path.join(A, 'blockstates', 'ladder_stone.json'), {'variants': variants})
    write(os.path.join(A, 'items', 'ladder_stone.json'), {'model': {'type': 'minecraft:model', 'model': 'afterworld:block/ladder_stone_gate'}})
    print('ladder stones written')


if __name__ == '__main__':
    main()
