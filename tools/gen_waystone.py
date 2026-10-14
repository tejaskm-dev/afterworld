#!/usr/bin/env python3
"""The waystone: a carved pillar on a two-step plinth, a band of glowing runes, a capstone and a
crystal turning above it. Writes the block model (elements reach above the block, the crystal
and runes glow), the blockstate, the item (a painted sprite), loot, recipe and textures."""
import json, math, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
A = os.path.join(RES, 'assets', 'afterworld')
D = os.path.join(RES, 'data', 'afterworld')


def hx(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


STONE = [hx('#26282E'), hx('#33363D'), hx('#41454D'), hx('#50555E'), hx('#636872'), hx('#7A808A')]
RUNE = [hx('#0E3A3A'), hx('#1F6E68'), hx('#3FB8A8'), hx('#7FE3D2'), hx('#D8FFF6')]
CRYSTAL = [hx('#1C4A60'), hx('#2F7F9A'), hx('#4FB8D0'), hx('#8FE6F2'), hx('#E8FFFF')]


def stone_tile(seed, bevel=True, cracks=0):
    """Dressed stone: a value-noise surface with chisel marks, bevelled edges lit from the top-left."""
    r = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 2.2 + (r.random() - 0.5) * 0.9 + 0.35 * math.sin(x * 0.9 + seed) * math.cos(y * 0.7)
            if bevel:
                if x == 0 or y == 0:
                    v += 1.3
                elif x == 15 or y == 15:
                    v -= 1.4
                elif x == 1 or y == 1:
                    v += 0.5
                elif x == 14 or y == 14:
                    v -= 0.5
            px[x, y] = STONE[max(0, min(5, int(round(v))))]
    for _ in range(cracks):
        x, y = r.randrange(3, 13), r.randrange(3, 13)
        for _ in range(r.randrange(3, 6)):
            px[x, y] = STONE[0]
            x += r.choice((-1, 0, 1))
            y += 1
            if not (1 <= x <= 14 and 1 <= y <= 14):
                break
    return img


def shaft():
    """The shaft: stone with two carved vertical channels and a chevron between them."""
    img = stone_tile(5, bevel=False)
    px = img.load()
    for y in range(16):
        for x in (3, 12):
            px[x, y] = STONE[0]
            px[x + 1, y] = STONE[1]
    for y in range(2, 14, 4):
        for k in range(3):
            px[6 + k, y + k] = STONE[1]
            px[9 - k, y + k] = STONE[1]
            px[6 + k, y + k - 1] = STONE[4]
    # Edge highlights so the pillar reads as a solid.
    for y in range(16):
        px[0, y] = STONE[4]
        px[15, y] = STONE[1]
    return img


def runes():
    """The rune band: dark stone with glowing teal glyphs (the element is drawn full-bright)."""
    img = Image.new('RGBA', (16, 16), STONE[1])
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = STONE[1] if (x + y) % 3 else STONE[0]
    glyphs = [
        ['X.X', '.X.', 'X.X'], ['XXX', 'X..', 'XXX'], ['.X.', 'XXX', '.X.'], ['X..', 'XX.', 'X.X'],
    ]
    for i, gph in enumerate(glyphs):
        ox = 1 + i * 4
        for gy, row in enumerate(gph):
            for gx, c in enumerate(row):
                if c == 'X':
                    px[ox + gx, 6 + gy] = RUNE[3]
                    if gy == 0:
                        px[ox + gx, 6 + gy] = RUNE[4]
    for x in range(16):
        px[x, 3] = RUNE[1]
        px[x, 12] = RUNE[1]
        px[x, 2] = STONE[3]
        px[x, 13] = STONE[0]
    return img


def crystal():
    """A cut crystal: bands of blue-teal with a hard highlight down one facet."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 2 + (1 if x < 6 else 0) - (1 if x > 11 else 0) + (1 if (x + y) % 7 == 0 else 0)
            if x in (4, 5):
                v = 4
            px[x, y] = CRYSTAL[max(0, min(4, v))]
    return img


def item_sprite():
    """The inventory icon: the whole waystone in miniature with a dark outline."""
    S = {
        'o': hx('#141418'), 'a': STONE[1], 'b': STONE[2], 'c': STONE[3], 'd': STONE[4], 'e': STONE[5],
        'r': RUNE[3], 'R': RUNE[4], 'k': CRYSTAL[1], 'K': CRYSTAL[3], 'w': CRYSTAL[4],
    }
    rows = [
        '.......oo.......',
        '......okwo......',
        '......oKko......',
        '.......oo.......',
        '.....oooooo.....',
        '.....odccbo.....',
        '......odbo......',
        '......odbo......',
        '.....oRrrRo.....',
        '......odbo......',
        '......odbo......',
        '......odao......',
        '....oddccbbo....',
        '...oeddcccbao...',
        '...oaaaaaaaao...',
        '....oooooooo....',
    ]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = S[ch]
    return img


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def model():
    t = lambda name: 'afterworld:block/' + name

    def box(frm, to, tex, top=None, light=0, rot=None, uv_side=None):
        faces = {}
        for face in ('north', 'south', 'east', 'west'):
            f = {'texture': '#' + tex}
            if uv_side:
                f['uv'] = uv_side
            faces[face] = f
        faces['up'] = {'texture': '#' + (top or tex)}
        faces['down'] = {'texture': '#' + (top or tex)}
        e = {'from': frm, 'to': to, 'faces': faces}
        if light:
            e['light_emission'] = light
        if rot:
            e['rotation'] = rot
        return e
    return {
        'parent': 'minecraft:block/block',
        'ambientocclusion': False,
        'textures': {'particle': t('waystone_shaft'), 'base': t('waystone_base'), 'shaft': t('waystone_shaft'),
                     'runes': t('waystone_runes'), 'crystal': t('waystone_crystal'), 'cap': t('waystone_cap')},
        'elements': [
            box([1, 0, 1], [15, 3, 15], 'base', uv_side=[1, 0, 15, 3]),
            box([2, 3, 2], [14, 5, 14], 'base', uv_side=[2, 4, 14, 6]),
            box([4, 5, 4], [12, 10, 12], 'shaft', top='cap', uv_side=[4, 0, 12, 5]),
            box([3.5, 10, 3.5], [12.5, 14, 12.5], 'runes', top='cap', light=13, uv_side=[3, 2, 12, 14]),
            box([4, 14, 4], [12, 20, 12], 'shaft', top='cap', uv_side=[4, 6, 12, 12]),
            box([3, 20, 3], [13, 22, 13], 'cap', uv_side=[3, 0, 13, 2]),
            box([5, 22, 5], [11, 23, 11], 'cap', uv_side=[5, 2, 11, 3]),
            box([6, 24.5, 6], [10, 30.5, 10], 'crystal', light=15, rot={'angle': 45, 'axis': 'y', 'origin': [8, 27, 8]},
                uv_side=[4, 2, 8, 14]),
        ],
    }


def main():
    tex = os.path.join(A, 'textures', 'block')
    os.makedirs(tex, exist_ok=True)
    stone_tile(11, cracks=1).save(os.path.join(tex, 'waystone_base.png'))
    stone_tile(13, bevel=True).save(os.path.join(tex, 'waystone_cap.png'))
    shaft().save(os.path.join(tex, 'waystone_shaft.png'))
    runes().save(os.path.join(tex, 'waystone_runes.png'))
    crystal().save(os.path.join(tex, 'waystone_crystal.png'))
    os.makedirs(os.path.join(A, 'textures', 'item'), exist_ok=True)
    item_sprite().save(os.path.join(A, 'textures', 'item', 'waystone.png'))
    write(os.path.join(A, 'models', 'block', 'waystone.json'), model())
    write(os.path.join(A, 'blockstates', 'waystone.json'), {'variants': {'': {'model': 'afterworld:block/waystone'}}})
    write(os.path.join(A, 'models', 'item', 'waystone.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'afterworld:item/waystone'}})
    write(os.path.join(A, 'items', 'waystone.json'), {'model': {'type': 'minecraft:model', 'model': 'afterworld:item/waystone'}})
    write(os.path.join(D, 'loot_table', 'blocks', 'waystone.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'afterworld:waystone'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': 'afterworld:blocks/waystone'})
    write(os.path.join(D, 'recipe', 'waystone.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'pattern': [' A ', 'SLS', 'SSS'],
        'key': {'A': 'minecraft:amethyst_shard', 'S': 'minecraft:chiseled_stone_bricks', 'L': 'minecraft:lodestone'},
        'result': {'id': 'afterworld:waystone', 'count': 1}})
    print('waystone written')


if __name__ == '__main__':
    main()
