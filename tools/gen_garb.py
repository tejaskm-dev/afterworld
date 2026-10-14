#!/usr/bin/env python3
"""Traveller's garb: geometry hung on the player skeleton, packed UVs, and four matching textures.

cloth.png and trim.png are greyscale and get dyed at render time (primary and secondary colour),
fixed.png keeps its own colours (metal, leather, glass) and glow.png is drawn full-bright.
heart.png is the Edict shard on the chest: full-bright, tinted with the Edict's colour.
Writes client/model/gen/GarbMesh.java and textures/entity/garb/*.png.
"""
import math, os, random, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from gen_models import Box, fmt, uv_size

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_OUT = os.path.join(HERE, '..', 'src', 'client', 'java', 'dev', 'afterworld', 'client', 'model', 'gen')
TEX_OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'entity', 'garb')
TEX_W = 128
PI = math.pi


class GPart:
    """A garb part: hangs off a vanilla player part (or another garb part). Arm/leg pieces may
    carry separate slim-arm boxes, which get their own UVs."""
    def __init__(self, name, parent, boxes=(), slim=None, pivot=(0, 0, 0), rot=(0, 0, 0)):
        self.name, self.parent, self.boxes, self.slim, self.pivot, self.rot = name, parent, list(boxes), slim, pivot, rot


EMBLEMS = {
    1: ['X.XX.X', 'X.XX.X', 'XXXXXX', 'XoXXoX', 'XXXXXX', '......'],   # crown
    2: ['..XX..', '..oX..', 'XXXXXX', 'XXXXoX', '..XX..', '..XX..'],   # guild compass
    3: ['X....X', 'XX..XX', 'XoXXoX', '.XXXX.', 'XX..XX', 'X....X'],   # court moth
    4: ['..XX..', '.XooX.', 'XX..XX', 'XX..XX', '.XooX.', '..XX..'],   # bureau gear
    5: ['..XX..', '.XoXX.', 'XoXXXX', '.XXXX.', '..XX..', '......'],   # shard
    6: ['......', '.XXXX.', 'XXooXX', 'XXooXX', '.XXXX.', '......'],   # eye
}
BADGES = {
    1: ['X..X', 'XXXX', 'XooX', 'XXXX'],
    2: ['.X..', 'XXXX', '.oX.', '.X..'],
    3: ['X..X', 'XooX', '.XX.', 'X..X'],
    4: ['.XX.', 'XooX', 'XooX', '.XX.'],
    5: ['.X..', 'XoX.', 'XXXX', '.XX.'],
    6: ['....', '.XX.', 'XooX', '.XX.'],
}


def spec():
    p = []
    # ---- head
    p.append(GPart('garb_hood', 'head', [Box((-4, -8, -4), (8, 8, 8), 'hood', 0.75)]))
    p.append(GPart('garb_hood_tip', 'head', [Box((-1, -9.6, 1.5), (2, 2, 3), 'hood_tip', 0.3)], rot=(0.35, 0, 0)))
    p.append(GPart('garb_mask', 'head', [Box((-4, -4, -4), (8, 4, 1), 'mask', 0.85)]))
    p.append(GPart('garb_hat', 'head', [
        Box((-8, -7.5, -8), (16, 1, 16), 'hat_brim'),
        Box((-4.7, -12, -4.7), (9.4, 4.5, 9.4), 'hat_crown'),
        Box((-4.7, -8.7, -4.7), (9.4, 1.2, 9.4), 'hat_band', 0.12),
        Box((4.4, -13.5, -1), (0.6, 5, 3), 'hat_feather')]))
    p.append(GPart('garb_circlet', 'head', [
        Box((-4, -6.6, -4), (8, 1, 8), 'circlet', 0.65),
        Box((-0.5, -7.3, -4.9), (1, 1.4, 0.4), 'gem')]))
    # ---- body
    p.append(GPart('garb_hood_collar', 'body', [Box((-4.5, -0.6, -2.5), (9, 2.5, 5), 'hood_collar', 0.3)]))
    # The cloak swings with the vanilla cape physics; its variants and emblems ride on it.
    p.append(GPart('garb_cloak', 'body', [], pivot=(0, 0, 2), rot=(0, PI, 0)))
    p.append(GPart('garb_cloak_short', 'garb_cloak', [Box((-5, 0, -1), (10, 11, 1), 'cloak_short')]))
    p.append(GPart('garb_cloak_long', 'garb_cloak', [Box((-5.5, 0, -1), (11, 20, 1), 'cloak_long')]))
    p.append(GPart('garb_cloak_tattered', 'garb_cloak', [Box((-5.5, 0, -1), (11, 20, 1), 'cloak_tattered')]))
    for n in range(1, 7):
        p.append(GPart(f'garb_cloak_emblem_{n}', 'garb_cloak', [Box((-3, 3, -1.12), (6, 6, 0), f'emblem_{n}')]))
    p.append(GPart('garb_mantle', 'body', [
        Box((-6, -0.6, -3.2), (12, 3.6, 6.4), 'mantle', 0.1),
        Box((-4.6, -1.7, -3), (9.2, 2, 6), 'fur', 0.3)]))
    p.append(GPart('garb_belt', 'body', [
        Box((-4, 9.6, -2), (8, 1.6, 4), 'belt', 0.4),
        Box((-1, 9.4, -2.7), (2, 2, 0.5), 'buckle')]))
    p.append(GPart('garb_satchel', 'body', [Box((3.9, 8.6, -1.6), (2.2, 3.6, 3.2), 'satchel')]))
    p.append(GPart('garb_sash', 'body', [Box((-4, 0, -2), (8, 12, 4), 'sash', 0.32)]))
    p.append(GPart('garb_bandolier', 'body', [Box((-4, 0, -2), (8, 12, 4), 'bandolier', 0.34)]))
    p.append(GPart('garb_lantern', 'body', [
        Box((-1.5, 0, -1.5), (3, 4, 3), 'lantern'),
        Box((-0.5, -1.4, -0.5), (1, 1.4, 1), 'lantern_handle')], pivot=(-5.6, 10.4, 0)))
    for n in range(1, 7):
        p.append(GPart(f'garb_badge_{n}', 'body', [Box((-3.7, 1.8, -2.55), (3.2, 3.2, 0), f'badge_{n}')]))
    # The heart: a small shard just below the mantle line, with a soft halo behind it.
    p.append(GPart('garb_heart', 'body', [
        Box((-2.5, 2.9, -2.62), (5, 6, 0), 'heart_halo'),
        Box((-1.5, 3.9, -3.05), (3, 4, 0.6), 'heart')]))
    # ---- arms (wide boxes, and slim boxes with their own UVs)
    p.append(GPart('garb_bracer', 'right_arm', [Box((-3, 5.6, -2), (4, 3.6, 4), 'bracer', 0.42)],
                   slim=[Box((-2, 5.6, -2), (3, 3.6, 4), 'bracer', 0.42)]))
    p.append(GPart('garb_bracer', 'left_arm', [Box((-1, 5.6, -2), (4, 3.6, 4), 'bracer', 0.42)],
                   slim=[Box((-1, 5.6, -2), (3, 3.6, 4), 'bracer', 0.42)]))
    p.append(GPart('garb_wrap', 'right_arm', [Box((-3, 2.5, -2), (4, 7.5, 4), 'armwrap', 0.32)],
                   slim=[Box((-2, 2.5, -2), (3, 7.5, 4), 'armwrap', 0.32)]))
    p.append(GPart('garb_wrap', 'left_arm', [Box((-1, 2.5, -2), (4, 7.5, 4), 'armwrap', 0.32)],
                   slim=[Box((-1, 2.5, -2), (3, 7.5, 4), 'armwrap', 0.32)]))
    # ---- legs
    for leg in ('right_leg', 'left_leg'):
        p.append(GPart('garb_boot', leg, [Box((-2, 8.4, -2), (4, 3.6, 4), 'boot', 0.42)]))
        p.append(GPart('garb_legwrap', leg, [Box((-2, 3.5, -2), (4, 7.5, 4), 'legwrap', 0.32)]))
    return p


def pack(parts):
    boxes = []
    for part in parts:
        boxes += part.boxes
        if part.slim:
            boxes += part.slim
    boxes.sort(key=lambda b: (-uv_size(b)[1], -uv_size(b)[0]))
    x = y = shelf = 0
    for b in boxes:
        bw, bh = uv_size(b)
        if x + bw > TEX_W:
            x = 0
            y += shelf
            shelf = 0
        b.u, b.v = x, y
        x += bw
        shelf = max(shelf, bh)
    h = 16
    while h < y + shelf:
        h *= 2
    return h


# ---------------------------------------------------------------- java

def cubes(boxes):
    s = 'CubeListBuilder.create()'
    for b in boxes:
        w, h, d = b.size
        deform = f', new CubeDeformation({fmt(b.inflate)})' if b.inflate else ''
        s += f'.texOffs({b.u}, {b.v}).addBox({fmt(b.origin[0])}, {fmt(b.origin[1])}, {fmt(b.origin[2])}, {fmt(w)}, {fmt(h)}, {fmt(d)}{deform})'
    return s


def write_java(parts, th):
    lines = [
        'package dev.afterworld.client.model.gen;', '',
        'import net.minecraft.client.model.geom.PartPose;',
        'import net.minecraft.client.model.geom.builders.CubeDeformation;',
        'import net.minecraft.client.model.geom.builders.CubeListBuilder;',
        'import net.minecraft.client.model.geom.builders.LayerDefinition;',
        'import net.minecraft.client.model.geom.builders.MeshDefinition;',
        'import net.minecraft.client.model.geom.builders.PartDefinition;',
        'import net.minecraft.client.model.player.PlayerModel;', '',
        '/** Generated by tools/gen_garb.py. Do not edit by hand. */',
        'public final class GarbMesh {',
        '\tprivate GarbMesh() {', '\t}', '',
        '\tpublic static LayerDefinition create(boolean slim) {',
        '\t\tMeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);',
        '\t\tPartDefinition root = mesh.getRoot().clearRecursively();',
    ]
    for name in ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):
        lines.append(f'\t\tPartDefinition {name} = root.getChild("{name}");')
    declared = set()
    for part in parts:
        px, py, pz = part.pivot
        rx, ry, rz = part.rot
        pose = f'PartPose.offsetAndRotation({fmt(px)}, {fmt(py)}, {fmt(pz)}, {fmt(rx)}, {fmt(ry)}, {fmt(rz)})'
        c = f'slim ? {cubes(part.slim)} : {cubes(part.boxes)}' if part.slim else cubes(part.boxes)
        has_children = any(q.parent == part.name for q in parts)
        if has_children:
            lines.append(f'\t\tPartDefinition {part.name} = {part.parent}.addOrReplaceChild("{part.name}", {c}, {pose});')
            declared.add(part.name)
        else:
            lines.append(f'\t\t{part.parent}.addOrReplaceChild("{part.name}", {c}, {pose});')
    lines += [f'\t\treturn LayerDefinition.create(mesh, {TEX_W}, {th});', '\t}', '}', '']
    os.makedirs(JAVA_OUT, exist_ok=True)
    with open(os.path.join(JAVA_OUT, 'GarbMesh.java'), 'w') as f:
        f.write('\n'.join(lines))


# ---------------------------------------------------------------- painting

def grey(v, a=255):
    v = max(0, min(255, int(v)))
    return (v, v, v, a)


def rgb(h, a=255, k=1.0):
    h = h.lstrip('#')
    return (min(255, int(int(h[0:2], 16) * k)), min(255, int(int(h[2:4], 16) * k)), min(255, int(int(h[4:6], 16) * k)), a)


class Canvas:
    def __init__(self, th):
        self.img = {k: Image.new('RGBA', (TEX_W, th), (0, 0, 0, 0)) for k in ('cloth', 'trim', 'fixed', 'glow', 'heart')}
        self.px = {k: v.load() for k, v in self.img.items()}

    def put(self, layer, x, y, c):
        # Every texel belongs to exactly one dyed/fixed layer, so passes never z-fight.
        for k in ('cloth', 'trim', 'fixed'):
            if k != layer:
                self.px[k][x, y] = (0, 0, 0, 0)
        self.px[layer][x, y] = c

    def glow(self, x, y, c):
        self.px['glow'][x, y] = c

    def heart(self, x, y, c):
        self.px['heart'][x, y] = c


def weave(rng, base, var=7):
    return base + rng.randint(-var, var)


def face_rects(b):
    w, h, d = (int(math.ceil(s)) for s in b.size)
    u, v = b.u, b.v
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
    }


def fill(cv, rng, layer, rect, base, var=7, edge=None):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = weave(rng, base, var)
            if edge is not None and (x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)):
                v = edge + rng.randint(-4, 4)
            cv.put(layer, x, y, grey(v))


def stripes(cv, rng, layer, rect, a, b, period=3):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            band = ((x - x0) + (y - y0)) % period == 0
            cv.put(layer, x, y, grey(weave(rng, b if band else a, 4)))


def pattern(cv, layer, rect, rows, color, hi):
    x0, y0, w, h = rect
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if i < w and j < h and ch != '.':
                cv.put(layer, x0 + i, y0 + j, hi if ch == 'o' else color)


def paint_box(cv, rng, b):
    m = b.mat
    F = face_rects(b)
    if m == 'hood':
        for f, r in F.items():
            if f == 'down':
                continue
            if f == 'north':
                x0, y0, w, h = r
                for y in range(y0, y0 + h):
                    for x in range(x0, x0 + w):
                        if y == y0:
                            cv.put('cloth', x, y, grey(weave(rng, 196)))
                        elif y == y0 + 1:
                            cv.put('trim', x, y, grey(weave(rng, 222, 4)))
                        elif x in (x0, x0 + w - 1):
                            cv.put('trim', x, y, grey(weave(rng, 215, 4)))
                continue
            fill(cv, rng, 'cloth', r, 208 if f == 'up' else 200, 7)
            if f in ('east', 'west', 'south'):
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('cloth', x, y0 + h - 1, grey(weave(rng, 176, 4)))
    elif m in ('hood_tip',):
        for r in F.values():
            fill(cv, rng, 'cloth', r, 196)
    elif m == 'hood_collar':
        for f, r in F.items():
            fill(cv, rng, 'cloth', r, 198)
            if f in ('north', 'south', 'east', 'west'):
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('trim', x, y0 + h - 1, grey(weave(rng, 218, 4)))
    elif m == 'mask':
        for f, r in F.items():
            fill(cv, rng, 'trim', r, 205, 6)
            if f == 'north':
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('trim', x, y0 + 1, grey(weave(rng, 178, 3)))
    elif m == 'hat_brim':
        for f, r in F.items():
            fill(cv, rng, 'cloth', r, {'up': 196, 'down': 160}.get(f, 180), 5, edge=172 if f == 'up' else None)
    elif m == 'hat_crown':
        for f, r in F.items():
            fill(cv, rng, 'cloth', r, 204 if f == 'up' else 192, 5)
        x0, y0, w, h = F['up']
        for x in range(x0 + 2, x0 + w - 2):
            cv.put('cloth', x, y0 + h // 2, grey(weave(rng, 172, 3)))
    elif m == 'hat_band':
        for f, r in F.items():
            fill(cv, rng, 'trim', r, 214, 4)
    elif m == 'hat_feather':
        for f, r in F.items():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    t = (y - y0) / max(1, h - 1)
                    cv.put('trim', x, y, grey(235 - 40 * t + rng.randint(-5, 5)))
    elif m == 'circlet':
        for f, r in F.items():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    k = 1.0 + rng.uniform(-0.06, 0.06)
                    c = rgb('#D9B45A', k=k * (0.82 if (x - x0) % 3 == 0 and f != 'up' and f != 'down' else 1.0))
                    cv.put('fixed', x, y, c)
    elif m == 'gem':
        for f, r in F.items():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    cv.put('fixed', x, y, rgb('#5FD3C8', k=1.0 + rng.uniform(-0.1, 0.1)))
                    cv.glow(x, y, rgb('#7FF0E0', a=150))
    elif m.startswith('cloak'):
        tattered = m == 'cloak_tattered'
        x0, y0, w, h = F['north']
        cuts = [rng.randint(0, 5) for _ in range(w)] if tattered else [0] * w
        holes = {(x0 + rng.randrange(1, w - 1), y0 + rng.randrange(h // 2, h - 4)) for _ in range(3)} if tattered else set()
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                col = x - x0
                if tattered and (y >= y0 + h - cuts[col] or (x, y) in holes):
                    continue
                fold = (col % 3 == 0) * -20 + (col % 3 == 1) * 12
                if not tattered and y >= y0 + h - 2:
                    cv.put('trim', x, y, grey(weave(rng, 222, 4)))
                elif y == y0:
                    cv.put('cloth', x, y, grey(weave(rng, 172, 3)))
                else:
                    cv.put('cloth', x, y, grey(weave(rng, 202 + fold, 5)))
        # Lining (inner face) shows the trim colour, mirrored to line up with the outer cuts.
        sx0, sy0, sw, sh = F['south']
        for y in range(sy0, sy0 + sh):
            for x in range(sx0, sx0 + sw):
                col = sw - 1 - (x - sx0)
                if tattered and (y >= sy0 + sh - cuts[col] or (x0 + col, y - sy0 + y0) in holes):
                    continue
                cv.put('trim', x, y, grey(weave(rng, 176, 5)))
        for f in ('east', 'west', 'up', 'down'):
            ex0, ey0, ew, eh = F[f]
            for y in range(ey0, ey0 + eh):
                for x in range(ex0, ex0 + ew):
                    if tattered and f in ('east', 'west') and y >= ey0 + eh - 3:
                        continue
                    cv.put('cloth', x, y, grey(weave(rng, 182, 4)))
    elif m.startswith('emblem_'):
        n = int(m.split('_')[1])
        pattern(cv, 'trim', F['north'], EMBLEMS[n], grey(232), grey(255))
    elif m.startswith('badge_'):
        n = int(m.split('_')[1])
        pattern(cv, 'fixed', F['north'], BADGES[n], rgb('#E8C04A'), rgb('#FFF0B0'))
    elif m == 'heart':
        shard = ['.X.', 'XoX', 'XXX', '.X.']
        x0, y0, w, h = F['north']
        for j, row in enumerate(shard):
            for i, ch in enumerate(row):
                if ch != '.':
                    cv.heart(x0 + i, y0 + j, grey(255 if ch == 'o' else 200))
        for f in ('east', 'west', 'up', 'down', 'south'):
            x0, y0, w, h = F[f]
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    cv.heart(x, y, grey(150, a=200))
    elif m == 'heart_halo':
        x0, y0, w, h = F['north']
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                r = math.hypot((x - x0 + 0.5) / w - 0.5, (y - y0 + 0.5) / h - 0.5) * 2
                if r < 1:
                    cv.heart(x, y, grey(255, a=int(120 * (1 - r) ** 1.5)))
    elif m == 'mantle':
        for f, r in F.items():
            fill(cv, rng, 'cloth', r, 206 if f == 'up' else 196, 6)
            if f in ('north', 'south', 'east', 'west'):
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('trim', x, y0 + h - 1, grey(weave(rng, 220, 4)))
    elif m == 'fur':
        for f, r in F.items():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if f in ('north', 'south', 'east', 'west') and y == y0 + h - 1 and rng.random() < 0.35:
                        continue
                    cv.put('trim', x, y, grey(rng.choice((178, 196, 212, 228, 240))))
    elif m == 'belt':
        for f, r in F.items():
            fill(cv, rng, 'trim', r, 178, 5)
            if f in ('north', 'south', 'east', 'west'):
                x0, y0, w, h = r
                for x in range(x0, x0 + w, 2):
                    cv.put('trim', x, y0, grey(214))
    elif m == 'buckle':
        for f, r in F.items():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                    cv.put('fixed', x, y, rgb('#C9A64A') if edge or w < 2 or h < 2 else rgb('#7A5E26'))
    elif m == 'satchel':
        for f, r in F.items():
            fill(cv, rng, 'trim', r, 184, 5)
        x0, y0, w, h = F['east']
        for x in range(x0, x0 + w):
            cv.put('trim', x, y0 + 1, grey(140))
        cv.put('fixed', x0 + w // 2, y0 + 2, rgb('#C9A64A'))
    elif m == 'sash':
        for f in ('north', 'south'):
            x0, y0, w, h = F[f]
            for y in range(y0, y0 + h):
                t = (y - y0) / max(1, h - 1)
                cx = x0 + (t if f == 'north' else 1 - t) * (w - 1)
                for x in range(x0, x0 + w):
                    if abs(x - cx) <= 1.2:
                        cv.put('trim', x, y, grey(weave(rng, 214, 5)))
        x0, y0, w, h = F['up']
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + 3):
                cv.put('trim', x, y, grey(weave(rng, 220, 4)))
    elif m == 'bandolier':
        for f in ('north', 'south'):
            x0, y0, w, h = F[f]
            pouches = 0
            for y in range(y0, y0 + h):
                t = (y - y0) / max(1, h - 1)
                cx = x0 + ((1 - t) if f == 'north' else t) * (w - 1)
                for x in range(x0, x0 + w):
                    if abs(x - cx) <= 0.8:
                        rivet = (y - y0) % 3 == 1 and abs(x - cx) < 0.5
                        cv.put('fixed', x, y, rgb('#C9A64A') if rivet else rgb('#5A3E28', k=1 + rng.uniform(-0.08, 0.08)))
                if f == 'north' and (y - y0) in (3, 7) and pouches < 2:
                    pouches += 1
                    px0 = int(cx) - 1
                    for yy in range(y, min(y0 + h, y + 2)):
                        for xx in range(max(x0, px0), min(x0 + w, px0 + 3)):
                            cv.put('fixed', xx, yy, rgb('#6B4A2E') if yy == y else rgb('#4A3220'))
        x0, y0, w, h = F['up']
        for y in range(y0, y0 + h):
            for x in range(x0 + w - 2, x0 + w):
                cv.put('fixed', x, y, rgb('#5A3E28'))
    elif m == 'lantern':
        for f, r in F.items():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    frame = f in ('up', 'down') or x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                    if frame:
                        cv.put('fixed', x, y, rgb('#3E3E44', k=1 + rng.uniform(-0.1, 0.1)))
                    else:
                        cv.put('fixed', x, y, rgb('#FFD27A'))
                        cv.glow(x, y, rgb('#FFC860', a=230))
    elif m == 'lantern_handle':
        for r in F.values():
            x0, y0, w, h = r
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    cv.put('fixed', x, y, rgb('#2E2E34'))
    elif m == 'bracer':
        for f, r in F.items():
            fill(cv, rng, 'trim', r, 180, 5)
            if f in ('north', 'south', 'east', 'west'):
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('trim', x, y0 + 1, grey(214))
                    cv.put('trim', x, y0 + h - 2, grey(214))
    elif m in ('armwrap', 'legwrap'):
        for f, r in F.items():
            if f in ('up', 'down'):
                continue
            stripes(cv, rng, 'cloth', r, 200, 168, 3)
            if m == 'legwrap':
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('trim', x, y0 + 1, grey(weave(rng, 212, 4)))
    elif m == 'boot':
        for f, r in F.items():
            fill(cv, rng, 'trim', r, 166, 5)
            if f in ('north', 'south', 'east', 'west'):
                x0, y0, w, h = r
                for x in range(x0, x0 + w):
                    cv.put('trim', x, y0, grey(206))
    else:
        raise KeyError(m)


def main():
    parts = spec()
    th = pack(parts)
    write_java(parts, th)
    cv = Canvas(th)
    rng = random.Random(4242)
    for part in parts:
        for b in part.boxes + (part.slim or []):
            paint_box(cv, rng, b)
    os.makedirs(TEX_OUT, exist_ok=True)
    for k, img in cv.img.items():
        img.save(os.path.join(TEX_OUT, k + '.png'))
    print('garb', TEX_W, 'x', th, 'parts', len(parts))


if __name__ == '__main__':
    main()
