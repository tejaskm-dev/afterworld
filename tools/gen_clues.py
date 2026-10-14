#!/usr/bin/env python3
"""Site props. Clues: flat things lying where they fell (a cut wagon wheel, a caravan's manifest,
tracks in the dust, a warm glyph, a note), as decals on plate models with a random turn per spot.
The seal stone that locks a site's vault (dark, then gold once its riddle is solved), and the
Crimson Lich's phylactery (a blood-red heart in a gilt cage). Writes textures, models, blockstates,
items and the phylactery's loot."""
import json
import math
import os
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld')
D = os.path.join(HERE, '..', 'src', 'main', 'resources', 'data', 'afterworld')
KINDS = ['wagon', 'manifest', 'tracks', 'glyph', 'note']


def hx(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


WOOD = [hx('#3A2618'), hx('#553822'), hx('#714C2E'), hx('#8E663F'), hx('#AD8457')]
IRON = [hx('#3C3D44'), hx('#5A5C64'), hx('#80838C')]
PAPER = [hx('#B9A67F'), hx('#D3C39D'), hx('#E6D9B8'), hx('#F3EAD0')]
INK = [hx('#6A5866'), hx('#4A3C48')]
WAX = [hx('#6E1E1A'), hx('#A0302A'), hx('#CF5A44')]
GOLD = [hx('#7A4A10', 200), hx('#C07A1C', 230), hx('#F0B840'), hx('#FFE9A0')]


def wagon():
    """A wheel on its side: an iron-shod rim broken at one point, spokes cut clean (not snapped)."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 7.5
    gap = (math.radians(98), math.radians(128))
    cut = {2, 5}
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx) % (2 * math.pi)
            light = (-dx - dy) / 10.0
            if 5.4 <= d <= 7.4 and not gap[0] <= a <= gap[1]:
                if d > 6.7:
                    px[x, y] = IRON[1 + (light > 0.1)]
                else:
                    px[x, y] = WOOD[2 + (light > 0.15) - (d < 5.8)]
            elif d <= 1.7:
                px[x, y] = WOOD[3] if d < 0.9 else WOOD[1]
            elif d < 5.6:
                for k in range(8):
                    t = k * math.pi / 4 + 0.2
                    along = dx * math.cos(t) + dy * math.sin(t)
                    across = -dx * math.sin(t) + dy * math.cos(t)
                    reach = 2.6 if k in cut else 5.6
                    if 0 < along < reach and abs(across) < 0.62:
                        px[x, y] = WOOD[3] if along < reach - 0.8 or k not in cut else WOOD[4]
    # Splinters at the break and two loose spoke ends on the ground.
    for (x, y) in [(5, 14), (4, 13), (10, 1)]:
        px[x, y] = WOOD[2]
    for (x, y) in [(13, 13), (14, 13), (1, 3), (2, 3)]:
        px[x, y] = WOOD[3]
    return img


def sheet(img, cx, cy, w, h, angle, seed, lines=True, seal=False, fold=False, size=16):
    """A rotated sheet of paper with ink lines, a shaded edge and (sometimes) a wax seal."""
    r = random.Random(seed)
    px = img.load()
    # Handwriting: short words with gaps, one line of them every other row.
    words = []
    for _ in range(h + 1):
        row, on = [], r.random() < 0.5
        while len(row) < w + 1:
            n = r.randint(1, 3) if on else 1
            row += [on] * n
            on = not on
        words.append(row)
    ca, sa = math.cos(angle), math.sin(angle)
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u = dx * ca + dy * sa + w / 2
            v = -dx * sa + dy * ca + h / 2
            if 0 <= u < w and 0 <= v < h:
                edge = u < 1 or v < 1 or u >= w - 1 or v >= h - 1
                c = PAPER[1] if edge else PAPER[2 + (r.random() < 0.15)]
                if fold and abs(v - h / 2) < 0.5:
                    c = PAPER[0]
                if lines and not edge and 1.5 < u < w - 1.5 and int(v) % 2 == 0 and 1 < v < h - 1.5 and words[int(v)][int(u)]:
                    c = INK[int(v) == 2]
                if seal and (u - (w - 2.2)) ** 2 + (v - (h - 2.2)) ** 2 < 2.2:
                    c = WAX[1] if (u - (w - 2.6)) + (v - (h - 2.6)) > 0 else WAX[2]
                px[x, y] = c


def manifest():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    sheet(img, 5.5, 6.0, 7, 9, 0.35, 1)
    sheet(img, 10.0, 10.5, 7, 9, -0.25, 2, seal=True)
    sheet(img, 4.5, 12.5, 5, 4, 1.1, 3, lines=False)
    return img


def note():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    sheet(img, 8.0, 8.0, 8, 10, 0.4, 7, fold=True)
    # A water stain across one corner.
    px = img.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] and (x - 11) ** 2 + (y - 11) ** 2 < 9:
                r_, g, b, a = px[x, y]
                px[x, y] = (int(r_ * 0.86), int(g * 0.84), int(b * 0.8), a)
    return img


def tracks():
    """Boot prints walking off to the top-right, and something larger following them."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    boot = (64, 46, 28, 150)
    heel = (50, 35, 21, 175)
    # Toe at the top, a gap at the arch, the heel below.
    sole = ['.XX', 'XXX', 'XX.', '...', 'HH.']
    for (x, y) in [(0, 11), (3, 8), (5, 4), (8, 1)]:
        for oy, row in enumerate(sole):
            for ox, ch in enumerate(row):
                if ch != '.' and 0 <= x + ox < 16 and 0 <= y + oy < 16:
                    px[x + ox, y + oy] = heel if ch == 'H' else boot
    claw = (34, 23, 16, 200)
    pad = (46, 32, 21, 180)
    paw = ['C.C.C', 'C.C.C', '.....', '.PPP.', 'PPPPP', '.PPP.']
    for (x, y) in [(10, 9)]:
        for oy, row in enumerate(paw):
            for ox, ch in enumerate(row):
                if ch != '.':
                    px[x + ox, y + oy] = claw if ch == 'C' else pad
    return img


def glyph():
    """A ring with a crossed eye at its heart, cut into the floor and still glowing."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            c = None
            if 5.6 <= d <= 6.6:
                c = GOLD[1]
            elif d <= 6.6 and (abs(dx) < 0.6 or abs(dy) < 0.6) and d > 3.2:
                c = GOLD[2]
            elif 1.6 <= d <= 2.6 and abs(dy) < 1.8:
                c = GOLD[2]
            elif d < 1.0:
                c = GOLD[3]
            if c:
                px[x, y] = c
    for (x, y) in [(7, 1), (8, 14), (1, 8), (14, 7)]:
        px[x, y] = GOLD[3]
    return img


def seal_side():
    """Dark dressed stone with a carved frame and a rune channel down the middle."""
    r = random.Random(5)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 0.5 + (r.random() - 0.5) * 0.25
            if x in (0, 15) or y in (0, 15):
                v -= 0.18
            elif x in (1, 14) or y in (1, 14):
                v += 0.12
            if x in (7, 8) and 3 <= y <= 12:
                v -= 0.22
            c = int(52 + 60 * v)
            px[x, y] = (c - 4, c - 2, c + 6, 255)
    for y in (4, 8, 11):
        px[7, y] = (120, 140, 150, 255)
    return img


def seal_top(solved):
    """A ring of runes around a sealed eye: cold grey-teal, or burning gold once solved."""
    img = seal_side()
    px = img.load()
    line = (230, 184, 72, 255) if solved else (88, 120, 128, 255)
    hot = (255, 238, 170, 255) if solved else (120, 156, 164, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d <= 6.2 or (d <= 2.2 and abs(y - 7.5) < 1.6):
                px[x, y] = line
            elif d < 0.9:
                px[x, y] = hot
    for a in range(8):
        t = a * math.pi / 4
        px[int(round(7.5 + math.cos(t) * 4.0)), int(round(7.5 + math.sin(t) * 4.0))] = hot
    return img


def phylactery():
    """A heart-shaped crystal of old blood in a gilt cage, on a little stand."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    red = [hx('#3E0810'), hx('#7A1420'), hx('#B01E2A'), hx('#E2505A'), hx('#FFB0A8')]
    gold = [hx('#6A4A14'), hx('#B08A2E'), hx('#E8C060')]
    for y in range(2, 13):
        half = [1, 2, 3, 4, 4, 4, 4, 3, 2, 1, 0][y - 2]
        for x in range(8 - half, 8 + half):
            edge = x in (8 - half, 7 + half)
            shade = 1 if edge else (3 if x < 8 and y < 7 else 2)
            px[x, y] = red[shade]
    px[6, 4], px[6, 5], px[7, 4] = red[4], red[3], red[3]
    for y in range(1, 14):
        px[3, y] = gold[1]
        px[12, y] = gold[1]
    for x in range(3, 13):
        px[x, 1] = gold[2]
        px[x, 13] = gold[1]
    px[7, 0], px[8, 0] = gold[2], gold[2]
    for x in range(5, 11):
        px[x, 14] = gold[1]
        px[x, 15] = gold[0]
    return img


def phylactery_model():
    """The cage as a cross of two planes, with a gilt plinth under it; the blood glows."""
    plane = lambda frm, to, faces: {'from': frm, 'to': to, 'shade': False, 'light_emission': 10,
                                     'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True},
                                     'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#cross'} for f in faces}}
    return {'ambientocclusion': False,
            'textures': {'particle': 'afterworld:block/phylactery', 'cross': 'afterworld:block/phylactery', 'gold': 'minecraft:block/gold_block'},
            'elements': [
                {'from': [5, 0, 5], 'to': [11, 1, 11], 'faces': {f: {'texture': '#gold'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
                plane([0.8, 0, 8], [15.2, 16, 8], ('north', 'south')),
                plane([8, 0, 0.8], [8, 16, 15.2], ('east', 'west')),
            ]}


def plate(k):
    element = {'from': [0, 0.25, 0], 'to': [16, 0.25, 16], 'shade': False,
               'faces': {'up': {'uv': [0, 0, 16, 16], 'texture': '#decal'}, 'down': {'uv': [0, 16, 16, 0], 'texture': '#decal'}}}
    if k == 'glyph':
        element['light_emission'] = 12
    return {'ambientocclusion': False, 'textures': {'particle': f'afterworld:block/clue_{k}', 'decal': f'afterworld:block/clue_{k}'},
            'elements': [element]}


def board_notes():
    """The day's notices on a Warden's Board, drawn at 32x32: a bounty with a red seal and a crude
    sketch of its quarry, two contract sheets and a torn map scrap, each pinned with a brass tack."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    sheet(img, 8.5, 9.5, 12, 15, 0.08, 21, seal=True, size=32)
    sheet(img, 23.0, 8.5, 11, 12, -0.12, 22, size=32)
    sheet(img, 21.5, 23.5, 13, 10, 0.1, 23, fold=True, size=32)
    sheet(img, 8.0, 25.0, 10, 8, -0.2, 24, lines=False, size=32)
    px = img.load()
    # A quarry sketched on the bounty: a hunched beast in charcoal.
    for x, y in [(6, 7), (7, 6), (8, 6), (9, 6), (10, 7), (11, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (5, 9), (6, 10), (10, 10), (11, 10), (8, 5), (9, 5)]:
        px[x, y] = INK[1]
    # Map scrap: a coastline and a cross.
    for x, y in [(4, 23), (5, 23), (6, 24), (7, 24), (8, 25), (9, 26), (10, 26), (11, 27)]:
        px[x, y] = hx('#5B7FA0')
    for d in (-1, 0, 1):
        px[9 + d, 23 + d] = WAX[1]
        px[9 + d, 23 - d] = WAX[1]
    # Brass tacks.
    for x, y in [(8, 2), (23, 3), (21, 19), (8, 21)]:
        px[x, y] = GOLD[2]
        px[x + 1, y] = GOLD[1]
        px[x, y + 1] = GOLD[1]
    return img


def board_notes_more(variant):
    """More of the board's notices, for the panels of a great notice board (each panel picks one):
    different sheets at different slants, so a wall of them never repeats in a grid."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    if variant == 2:
        sheet(img, 9.5, 8.0, 13, 11, -0.1, 31, size=32)
        sheet(img, 23.5, 10.5, 10, 14, 0.14, 32, seal=True, size=32)
        sheet(img, 10.0, 23.5, 14, 11, 0.06, 33, fold=True, size=32)
        sheet(img, 24.5, 25.5, 9, 9, -0.18, 34, lines=False, size=32)
        tacks = [(9, 2), (23, 3), (10, 17), (24, 20)]
    else:
        sheet(img, 16.0, 10.5, 20, 13, 0.04, 41, seal=True, size=32)
        sheet(img, 8.5, 24.0, 12, 11, -0.16, 42, size=32)
        sheet(img, 23.0, 24.5, 12, 10, 0.12, 43, lines=False, size=32)
        tacks = [(15, 3), (8, 18), (23, 19)]
    px = img.load()
    for x, y in tacks:
        px[x, y] = GOLD[2]
        px[x + 1, y] = GOLD[1]
        px[x, y + 1] = GOLD[1]
    return img


def panel_model(notes):
    """One panel of a great notice board: a slab of planks against the wall behind it, framed in dark
    wood, notices pinned on its face. Panels side by side make one board."""
    def box(f, t, tex):
        return {'from': f, 'to': t, 'faces': {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    return {
        'parent': 'minecraft:block/block',
        'ambientocclusion': False,
        'textures': {'particle': 'minecraft:block/spruce_planks', 'board': 'minecraft:block/spruce_planks',
                     'frame': 'minecraft:block/dark_oak_planks', 'notes': f'afterworld:block/{notes}'},
        'elements': [
            box([0, 0, 13], [16, 16, 16], '#board'),
            box([0, 0, 12.5], [16, 1, 13], '#frame'),
            box([0, 15, 12.5], [16, 16, 13], '#frame'),
            {'from': [0.5, 0.5, 12.9], 'to': [15.5, 15.5, 12.9], 'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#notes'}}},
        ],
    }


def board_model():
    """Two posts, a backboard and a little roof in the game's own woods (so a texture pack restyles
    them), with the notices as a decal on both faces."""
    def box(f, t, tex, uv=None):
        faces = {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
        return {'from': f, 'to': t, 'faces': faces}
    notes_front = {'from': [2, 4.5, 7.4], 'to': [14, 14, 7.4], 'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#notes'}}}
    notes_back = {'from': [2, 4.5, 8.6], 'to': [14, 14, 8.6], 'faces': {'south': {'uv': [16, 0, 0, 16], 'texture': '#notes'}}}
    return {
        'parent': 'minecraft:block/block',
        'ambientocclusion': False,
        'textures': {'particle': 'minecraft:block/spruce_planks', 'post': 'minecraft:block/dark_oak_log', 'board': 'minecraft:block/spruce_planks',
                     'roof': 'minecraft:block/dark_oak_planks', 'notes': 'afterworld:block/board_notes'},
        'elements': [
            box([0, 0, 7], [2, 16, 9], '#post'),
            box([14, 0, 7], [16, 16, 9], '#post'),
            box([1.5, 4, 7.5], [14.5, 14.5, 8.5], '#board'),
            box([-1, 14.5, 6], [17, 16, 10], '#roof'),
            notes_front,
            notes_back,
        ],
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        },
    }


def main():
    tex = os.path.join(A, 'textures', 'block')
    os.makedirs(tex, exist_ok=True)
    draw = {'wagon': wagon, 'manifest': manifest, 'tracks': tracks, 'glyph': glyph, 'note': note}
    for k in KINDS:
        draw[k]().save(os.path.join(tex, f'clue_{k}.png'))
        write(os.path.join(A, 'models', 'block', f'clue_{k}.json'), plate(k))
    write(os.path.join(A, 'blockstates', 'clue.json'), {'variants': {
        f'kind={k}': [{'model': f'afterworld:block/clue_{k}', 'y': r} for r in (0, 90, 180, 270)] for k in KINDS}})
    write(os.path.join(A, 'models', 'item', 'clue.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'afterworld:block/clue_manifest'}})
    write(os.path.join(A, 'items', 'clue.json'), {'model': {'type': 'minecraft:model', 'model': 'afterworld:item/clue'}})
    # The seal stone.
    seal_side().save(os.path.join(tex, 'seal_side.png'))
    seal_top(False).save(os.path.join(tex, 'seal_top.png'))
    seal_top(True).save(os.path.join(tex, 'seal_top_solved.png'))
    for solved in (False, True):
        name = 'seal_solved' if solved else 'seal'
        write(os.path.join(A, 'models', 'block', f'{name}.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'top': f'afterworld:block/seal_top{"_solved" if solved else ""}', 'side': 'afterworld:block/seal_side', 'bottom': 'afterworld:block/seal_side'}})
    write(os.path.join(A, 'blockstates', 'seal.json'), {'variants': {'solved=false': {'model': 'afterworld:block/seal'},
                                                                    'solved=true': {'model': 'afterworld:block/seal_solved'}}})
    write(os.path.join(A, 'items', 'seal.json'), {'model': {'type': 'minecraft:model', 'model': 'afterworld:block/seal'}})
    # The phylactery.
    phylactery().save(os.path.join(tex, 'phylactery.png'))
    write(os.path.join(A, 'models', 'block', 'phylactery.json'), phylactery_model())
    write(os.path.join(A, 'blockstates', 'phylactery.json'), {'variants': {'': {'model': 'afterworld:block/phylactery'}}})
    write(os.path.join(A, 'models', 'item', 'phylactery.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'afterworld:block/phylactery'}})
    write(os.path.join(A, 'items', 'phylactery.json'), {'model': {'type': 'minecraft:model', 'model': 'afterworld:item/phylactery'}})
    write(os.path.join(D, 'loot_table', 'blocks', 'phylactery.json'), {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'afterworld:phylactery_shard'}]}]})
    # The Warden's Board.
    board_notes().save(os.path.join(tex, 'board_notes.png'))
    board_notes_more(2).save(os.path.join(tex, 'board_notes_2.png'))
    board_notes_more(3).save(os.path.join(tex, 'board_notes_3.png'))
    write(os.path.join(A, 'models', 'block', 'wardens_board.json'), board_model())
    notes = ['board_notes', 'board_notes_2', 'board_notes_3']
    for n in notes:
        write(os.path.join(A, 'models', 'block', f'wardens_board_panel_{n[12:] or "1"}.json'), panel_model(n))
    variants = {}
    for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        # On its own, the old board on posts; as a panel of a great board, one of the notice sheets.
        variants[f'facing={f},panel=false'] = {'model': 'afterworld:block/wardens_board', 'y': r}
        variants[f'facing={f},panel=true'] = [{'model': f'afterworld:block/wardens_board_panel_{n[12:] or "1"}', 'y': r} for n in notes]
    write(os.path.join(A, 'blockstates', 'wardens_board.json'), {'variants': variants})
    write(os.path.join(A, 'items', 'wardens_board.json'), {'model': {'type': 'minecraft:model', 'model': 'afterworld:block/wardens_board'}})
    write(os.path.join(D, 'loot_table', 'blocks', 'wardens_board.json'), {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'afterworld:wardens_board'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    print('site props written')


if __name__ == '__main__':
    main()
