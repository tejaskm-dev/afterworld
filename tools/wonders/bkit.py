"""Afterworld's building kit: houses, halls, shops and towers with the detailing that makes Minecraft
architecture read as built rather than boxed.

The rules it follows (from how the best builders work):
- No flat walls. Posts, beams, corbels, sills, shutters and lintels stand proud of the wall plane;
  upper storeys jut out over the street on carved corbels.
- Every surface is a gradient, never one block. Footings are cleaner high and darker, cracked and
  mossy toward the ground and at the corners, where weather wears hardest.
- Roofs are steep, overhang the walls, carry dormers and a chimney, and are capped along the ridge.
- Foundations look like they carry weight: a stone plinth under timber, a step to every door.
- Rooms are furnished for whoever lives there.

Coordinates are grid cells. A storey is four cells tall: three of room and one of floor beams; the
ground storey's floor is the layer at y - 1. Everything returns enough to place keepers and props.
"""
import math

import kit
from kit import CW, FACE, OPP
from vox import hash3


def clamp(v, a, b):
    return max(a, min(b, v))


def pick(pal, x, y, z, seed, t):
    """A block from a palette ordered clean -> weathered, chosen by t (0..1) with a little noise."""
    n = hash3(x, y, z, seed) - 0.5
    return pal[int(clamp(t + n * 0.55, 0.0, 0.999) * len(pal))]


class Style:
    """The materials of one building."""

    def __init__(self, found, wall, plaster, frame, roof, roof_full, trim, floor, door, glass='glass_pane', quoin=None, beam=None, cap=None):
        self.found = found          # footing palette, clean -> weathered
        self.wall = wall            # stone storey palette, clean -> weathered
        self.plaster = plaster      # timber infill palette
        self.frame = frame          # post log (axis set per use)
        self.beam = beam or frame
        self.roof = roof            # stair/slab family (e.g. 'dark_oak', 'deepslate_tile')
        self.roof_full = roof_full  # full block under the roof steps
        self.trim = trim            # wood family for trapdoors, stairs, slabs
        self.floor = floor
        self.door = door
        self.glass = glass
        self.quoin = quoin          # corner stones on stone storeys
        self.cap = cap or roof      # ridge slab family


STONE_FOUND = ['stone_bricks', 'stone_bricks', 'andesite', 'cracked_stone_bricks', 'cobblestone', 'mossy_stone_bricks', 'mossy_cobblestone']
PLASTER = ['calcite', 'calcite', 'calcite', 'white_terracotta', 'mushroom_stem']

TIMBER = Style(STONE_FOUND, ['stone_bricks', 'stone_bricks', 'andesite', 'cracked_stone_bricks', 'cobblestone', 'mossy_stone_bricks'],
               PLASTER, 'stripped_dark_oak_log', 'dark_oak', 'dark_oak_planks', 'spruce', 'spruce_planks', 'spruce',
               quoin='polished_andesite', beam='dark_oak_log')
TIMBER_RED = Style(STONE_FOUND, ['stone_bricks', 'andesite', 'cracked_stone_bricks', 'cobblestone'],
                   PLASTER, 'stripped_spruce_log', 'brick', 'bricks', 'spruce', 'spruce_planks', 'spruce', quoin='polished_andesite',
                   beam='spruce_log', cap='mud_brick')
TIMBER_SLATE = Style(STONE_FOUND, ['stone_bricks', 'andesite', 'cracked_stone_bricks', 'cobblestone'],
                     ['white_terracotta', 'calcite', 'mushroom_stem', 'light_gray_terracotta'], 'stripped_spruce_log', 'deepslate_tile',
                     'deepslate_tiles', 'spruce', 'oak_planks', 'oak', quoin='polished_andesite', beam='spruce_log', cap='polished_deepslate')
HALL = Style(['polished_andesite', 'stone_bricks', 'stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks', 'mossy_cobblestone'],
             ['stone_bricks', 'stone_bricks', 'polished_andesite', 'stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks'],
             PLASTER, 'stripped_dark_oak_log', 'deepslate_tile', 'deepslate_tiles', 'dark_oak', 'dark_oak_planks', 'dark_oak',
             quoin='chiseled_stone_bricks', beam='dark_oak_log', cap='polished_deepslate')


def _log(g, x, y, z, mat, axis):
    g.set(x, y, z, f'{mat}[axis={axis}]')


# ---------------------------------------------------------------- footings and walls

def footing(g, x0, z0, x1, z1, y, depth, pal, seed=0):
    """Stone under the walls, from y - depth up to y - 1: darker and mossier toward the ground."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for yy in range(y - depth, y):
                t = 0.35 + 0.45 * (y - 1 - yy) / max(1, depth) + (0.15 if x in (x0, x1) and z in (z0, z1) else 0.0)
                g.set(x, yy, z, pick(pal, x, yy, z, seed, t))


def plinth(g, x0, z0, x1, z1, y, mat):
    """A course of stairs round the foot of the walls, sloping out: the walls seem to grip the ground."""
    for x in range(x0, x1 + 1):
        kit.stairs(g, x, y, z0 - 1, mat, 'south')
        kit.stairs(g, x, y, z1 + 1, mat, 'north')
    for z in range(z0, z1 + 1):
        kit.stairs(g, x0 - 1, y, z, mat, 'east')
        kit.stairs(g, x1 + 1, y, z, mat, 'west')
    for (x, z, f, s) in ((x0 - 1, z0 - 1, 'south', 'outer_right'), (x1 + 1, z0 - 1, 'south', 'outer_left'),
                         (x0 - 1, z1 + 1, 'north', 'outer_left'), (x1 + 1, z1 + 1, 'north', 'outer_right')):
        kit.stairs(g, x, y, z, mat, f, 'bottom', s)


def stone_storey(g, x0, z0, x1, z1, y, style, seed=0, h=3):
    """A stone storey: walls with weathered corners and quoins, beam row on top."""
    for yy in range(y, y + h):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                _stone(g, x, yy, z, x0, x1, z0, z1, y, style, seed)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                _stone(g, x, yy, z, x0, x1, z0, z1, y, style, seed)
    _beams(g, x0, z0, x1, z1, y + h, style)


def _stone(g, x, yy, z, x0, x1, z0, z1, y, style, seed):
    corner = x in (x0, x1) and z in (z0, z1)
    if corner and style.quoin and (yy - y) % 2 == 0:
        g.set(x, yy, z, style.quoin)
        return
    t = 0.15 + 0.35 * max(0.0, 1.0 - (yy - y) / 3.0) + (0.25 if corner else 0.0)
    g.set(x, yy, z, pick(style.wall, x, yy, z, seed + 3, t))


def timber_storey(g, x0, z0, x1, z1, y, style, bay=3, seed=0, h=3):
    """A timber-framed storey: posts at the corners and every `bay`, plaster between, beams on top."""
    for yy in range(y, y + h):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                _panel(g, x, yy, z, x - x0, x1 - x0, style, bay, seed)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                _panel(g, x, yy, z, z - z0, z1 - z0, style, bay, seed)
    _beams(g, x0, z0, x1, z1, y + h, style)


def _panel(g, x, yy, z, i, n, style, bay, seed):
    if i == 0 or i == n or i % bay == 0:
        _log(g, x, yy, z, style.frame, 'y')
    else:
        g.set(x, yy, z, pick(style.plaster, x, yy, z, seed + 5, 0.3))


def _beams(g, x0, z0, x1, z1, y, style):
    for x in range(x0, x1 + 1):
        _log(g, x, y, z0, style.beam, 'x')
        _log(g, x, y, z1, style.beam, 'x')
    for z in range(z0 + 1, z1):
        _log(g, x0, y, z, style.beam, 'z')
        _log(g, x1, y, z, style.beam, 'z')


def corbels(g, x0, z0, x1, z1, y, sides, style, bay=3):
    """Carved brackets under a jettied storey: upside-down stairs under the overhang at each post."""
    for side in sides:
        dx, dz = FACE[side]
        if side in ('north', 'south'):
            z = z0 if side == 'north' else z1
            for x in range(x0, x1 + 1):
                if (x - x0) % bay == 0 or x == x1:
                    kit.stairs(g, x, y, z, style.trim, OPP[side], 'top')
        else:
            x = x0 if side == 'west' else x1
            for z in range(z0, z1 + 1):
                if (z - z0) % bay == 0 or z == z1:
                    kit.stairs(g, x, y, z, style.trim, OPP[side], 'top')


# ---------------------------------------------------------------- openings

def windows(g, x0, z0, x1, z1, y, style, sides, bay=3, seed=0, shutters=True, boxes=True, h=2, skip=()):
    """A window in the middle of each bay on the given sides, with shutters, a sill and (often) a
    flower box. `skip` lists (x, z) cells to leave solid (a door, a chimney)."""
    flowers = ['potted_red_tulip', 'potted_azure_bluet', 'potted_oxeye_daisy', 'potted_cornflower', 'potted_allium', 'potted_fern']
    for side in sides:
        dx, dz = FACE[side]
        cells = []
        if side in ('north', 'south'):
            z = z0 if side == 'north' else z1
            posts = [x for x in range(x0, x1 + 1) if (x - x0) % bay == 0 or x == x1]
            for a, b in zip(posts, posts[1:]):
                if b - a >= 2:
                    cells.append(((a + b) // 2, z))
        else:
            x = x0 if side == 'west' else x1
            posts = [z for z in range(z0, z1 + 1) if (z - z0) % bay == 0 or z == z1]
            for a, b in zip(posts, posts[1:]):
                if b - a >= 2:
                    cells.append((x, (a + b) // 2))
        for (x, z) in cells:
            if (x, z) in skip:
                continue
            for j in range(h):
                g.set(x, y + j, z, style.glass)
            ox, oz = x + dx, z + dz
            kit.stairs(g, ox, y - 1, oz, style.trim, OPP[side], 'top')
            if boxes and hash3(x, y, z, seed + 11) < 0.55:
                g.set(ox, y, oz, flowers[int(hash3(x, y, z, seed + 12) * len(flowers)) % len(flowers)])
            if shutters and hash3(x, y, z, seed + 13) < 0.35:
                along = (1, 0) if dx == 0 else (0, 1)
                for s in (-1, 1):
                    sx, sz = ox + along[0] * s, oz + along[1] * s
                    if g.get(sx, y, sz) in (None, 'minecraft:air'):
                        for j in range(h):
                            g.set(sx, y + j, sz, f'{style.trim}_trapdoor[facing={side},half=bottom,open=true]')
            # A lintel over the window.
            g.set(x, y + h, z, f'{style.beam}[axis={"x" if side in ("north", "south") else "z"}]')


def door(g, x, y, z, face, style, lantern=True, step=True, awning=True, double=False):
    """A framed door in the wall cell (x, z) facing out to `face`: posts, a lintel, a step and a lamp."""
    dx, dz = FACE[face]
    along = (1, 0) if dx == 0 else (0, 1)
    width = 2 if double else 1
    for i in range(width):
        cx, cz = x + along[0] * i, z + along[1] * i
        hinge = 'left' if i == 0 else 'right'
        g.set(cx, y, cz, f'{style.door}_door[facing={OPP[face]},half=lower,hinge={hinge},open=false]')
        g.set(cx, y + 1, cz, f'{style.door}_door[facing={OPP[face]},half=upper,hinge={hinge},open=false]')
        kit.stairs(g, cx, y + 2, cz, style.trim, OPP[face], 'top')
        if step:
            kit.stairs(g, cx + dx, y - 1, cz + dz, STEP.get(style.trim, 'stone_brick'), OPP[face])
        if awning:
            g.set(cx + dx, y + 2, cz + dz, f'{style.trim}_trapdoor[facing={face},half=top,open=false]')
    for s in (-1, width):
        px, pz = x + along[0] * s, z + along[1] * s
        for j in range(3):
            _log(g, px, y + j, pz, style.frame, 'y')
    if lantern:
        lx, lz = x + along[0] * -1 + dx, z + along[1] * -1 + dz
        if g.get(lx, y + 2, lz) in (None, 'minecraft:air'):
            g.set(lx, y + 2, lz, 'lantern[hanging=false]')
            g.set(lx, y + 1, lz, f'{style.trim}_fence')


STEP = {'spruce': 'stone_brick', 'dark_oak': 'stone_brick', 'oak': 'cobblestone'}


# ---------------------------------------------------------------- roofs

def roof(g, x0, z0, x1, z1, y, style, axis='x', overhang=1, rise=2, gable_fill=None, seed=0):
    """A steep gable roof over (x0..x1, z0..z1) with its ridge along `axis`: each step `rise` tall
    (full blocks under a stair), an overhang at the eaves, filled gable ends and a ridge cap.
    Returns the height of the ridge."""
    if axis == 'x':
        lo, hi, a0, a1 = z0, z1, x0, x1
    else:
        lo, hi, a0, a1 = x0, x1, z0, z1
    span = hi - lo
    steps = span // 2 + 1 + overhang
    top = y
    for k in range(steps):
        p, q = lo - overhang + k, hi + overhang - k
        if p > q:
            break
        base = y + rise * (k - overhang) if k >= overhang else y - (overhang - k)
        for a in range(a0 - overhang, a1 + 1 + overhang):
            for c, face in ((p, 'south' if axis == 'x' else 'east'), (q, 'north' if axis == 'x' else 'west')):
                if p == q:
                    _set_axis(g, axis, a, base + rise - 1, c, f'{style.cap}_slab[type=bottom]')
                    for r in range(rise - 1):
                        _set_axis(g, axis, a, base + r, c, style.roof_full)
                    continue
                for r in range(rise - 1):
                    if k >= overhang:
                        _set_axis(g, axis, a, base + r, c, style.roof_full)
                _set_axis(g, axis, a, base + rise - 1, c, f'{style.roof}_stairs[facing={face},half=bottom,shape=straight]')
        # Gable ends: fill the triangle inside the walls.
        if gable_fill and k >= overhang and p + 1 <= q - 1:
            for a in (a0, a1):
                for r in range(rise):
                    for c in range(p + 1, q):
                        _set_axis(g, axis, a, base + r, c, pick(gable_fill, a, base + r, c, seed + 9, 0.3) if isinstance(gable_fill, list) else gable_fill)
        top = max(top, base + rise)
    # Fill under the steps so no gap shows from below (rafters); and a ridge cap.
    return top


def _set_axis(g, axis, a, y, c, state):
    if axis == 'x':
        g.set(a, y, c, state)
    else:
        g.set(c, y, a, state)


def chimney(g, x, z, y0, top, mat='bricks', cap='stone_brick_wall', smoke=True):
    """A brick stack from y0 up past the roof, with a smoking hearth at the top in about one house
    of three (every one smoking at once is a fog of particles over a town, and costs frames)."""
    for y in range(y0, top + 1):
        g.set(x, y, z, mat)
    if smoke and hash3(x, top, z, 41) < 0.36:
        g.set(x, top + 1, z, 'campfire[lit=true,signal_fire=false,facing=north]')
    else:
        g.set(x, top + 1, z, cap)


def dormer(g, x, y, z, face, style, seed=0):
    """A small gabled window poking out of a roof slope: (x, y, z) is the window's lower cell on
    the outer face of the dormer, facing `face`. It reaches back three cells into the roof."""
    dx, dz = FACE[face]
    ax, az = (1, 0) if dx == 0 else (0, 1)
    for back in range(0, 4):
        for i in (-1, 0, 1):
            cx, cz = x + ax * i - dx * back, z + az * i - dz * back
            for j in range(2):
                if back == 0:
                    g.set(cx, y + j, cz, style.glass if i == 0 else _log_state(style.frame, 'y'))
                elif abs(i) == 1:
                    g.set(cx, y + j, cz, pick(style.plaster, cx, y + j, cz, seed, 0.3))
                else:
                    g.set(cx, y + j, cz, 'air')
            g.set(cx, y - 1, cz, style.floor if back > 0 else f'{style.trim}_stairs[facing={OPP[face]},half=top,shape=straight]')
        # Its own little gable: stairs either side, a cap on the ridge.
        left = (ax * -1, az * -1)
        lf = 'east' if face in ('north', 'south') else 'south'
        rf = 'west' if face in ('north', 'south') else 'north'
        bx, bz = x - dx * back, z - dz * back
        kit.stairs(g, bx - ax * 2, y + 2, bz - az * 2, style.roof, lf)
        kit.stairs(g, bx + ax * 2, y + 2, bz + az * 2, style.roof, rf)
        kit.stairs(g, bx - ax, y + 3, bz - az, style.roof, lf)
        kit.stairs(g, bx + ax, y + 3, bz + az, style.roof, rf)
        g.set(bx - ax, y + 2, bz - az, style.roof_full)
        g.set(bx + ax, y + 2, bz + az, style.roof_full)
        g.set(bx, y + 2, bz, pick(style.plaster, bx, y + 2, bz, seed, 0.3) if back == 0 else style.roof_full)
        g.set(bx, y + 3, bz, style.roof_full)
        g.set(bx, y + 4, bz, f'{style.cap}_slab[type=bottom]')


def _log_state(mat, axis):
    return f'{mat}[axis={axis}]'


# ---------------------------------------------------------------- inside

def floor(g, x0, z0, x1, z1, y, mat):
    g.fill(x0, y, z0, x1, y, z1, mat)


def ceiling_beams(g, x0, z0, x1, z1, y, mat, axis='x', every=3):
    """Exposed joists under a floor."""
    if axis == 'x':
        for z in range(z0, z1 + 1):
            if (z - z0) % every == 1:
                for x in range(x0, x1 + 1):
                    _log(g, x, y, z, mat, 'x')
    else:
        for x in range(x0, x1 + 1):
            if (x - x0) % every == 1:
                for z in range(z0, z1 + 1):
                    _log(g, x, y, z, mat, 'z')


def ladder(g, x, y0, y1, z, facing):
    for y in range(y0, y1 + 1):
        g.set(x, y, z, f'ladder[facing={facing}]')


def furnish(g, kind, x0, z0, x1, z1, y, style, seed=0, door=None):
    """Furniture for a room (x0..x1, z0..z1 inside the walls, floor at y - 1). `door` keeps its
    cell and the one inside it clear."""
    keep = set()
    if door:
        dx, dz = FACE[door[2]]
        keep = {(door[0], door[1]), (door[0] - dx, door[1] - dz), (door[0] - 2 * dx, door[1] - 2 * dz)}

    def put(x, z, state, yy=y):
        if (x, z) not in keep and x0 <= x <= x1 and z0 <= z <= z1:
            g.set(x, yy, z, state)

    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    g.set(cx, y + 2, cz, 'lantern[hanging=true]')
    if kind == 'home':
        kit.bed(g, x0, y, z0, ['red', 'blue', 'green', 'brown', 'cyan'][seed % 5], 'south') if (x0, z0) not in keep else None
        put(x1, z0, 'chest[facing=west]')
        put(x1, z0 + 1, 'barrel[facing=up]')
        put(x0, z1, 'crafting_table')
        put(x0 + 1, z1, 'furnace[facing=north,lit=false]')
        put(cx, cz, f'{style.trim}_fence')
        put(cx, cz, f'{style.trim}_fence')
        g.set(cx, y + 1, cz, f'{style.trim}_pressure_plate') if (cx, cz) not in keep else None
        put(cx - 1, cz, f'{style.trim}_stairs[facing=east,half=bottom,shape=straight]')
        put(cx + 1, cz, f'{style.trim}_stairs[facing=west,half=bottom,shape=straight]')
        put(x1, z1, 'potted_fern')
    elif kind == 'shop':
        for x in range(x0 + 1, x1):
            put(x, z0 + 2, 'barrel[facing=up]' if hash3(x, y, z0, seed) < 0.4 else f'{style.trim}_planks')
            g.set(x, y + 1, z0 + 2, f'{style.trim}_slab[type=bottom]') if (x, z0 + 2) not in keep else None
        for x in range(x0, x1 + 1):
            put(x, z0, 'barrel[facing=south]' if x % 2 else 'chest[facing=south]')
            put(x, z0, 'barrel[facing=south]', y + 1)
        put(x0, z1, 'decorated_pot[facing=north]')
        put(x1, z1, 'composter[level=0]')
    elif kind == 'inn':
        for (tx, tz) in ((x0 + 2, z0 + 2), (x1 - 2, z0 + 2), (x0 + 2, z1 - 2), (x1 - 2, z1 - 2)):
            put(tx, tz, f'{style.trim}_fence')
            g.set(tx, y + 1, tz, f'{style.trim}_pressure_plate') if (tx, tz) not in keep else None
            put(tx - 1, tz, f'{style.trim}_stairs[facing=east,half=bottom,shape=straight]')
            put(tx + 1, tz, f'{style.trim}_stairs[facing=west,half=bottom,shape=straight]')
        for z in range(z0, z1 + 1):
            put(x1, z, 'barrel[facing=west]')
            put(x1, z, 'barrel[facing=up]', y + 1)
        put(x0, cz, 'campfire[lit=true,signal_fire=false,facing=east]')
    elif kind == 'smithy':
        put(x0, z0, 'blast_furnace[facing=south,lit=true]')
        put(x0 + 1, z0, 'smithing_table')
        put(x0 + 2, z0, 'grindstone[face=floor,facing=south]')
        put(cx, cz, 'anvil[facing=north]')
        put(x1, z1, 'cauldron')
        put(x1, z0, 'barrel[facing=up]')
    elif kind == 'hall':
        for z in range(z0 + 2, z1 - 1):
            put(cx, z, f'{style.trim}_planks')
            g.set(cx, y + 1, z, f'{style.trim}_pressure_plate') if (cx, z) not in keep else None
            put(cx - 1, z, f'{style.trim}_stairs[facing=east,half=bottom,shape=straight]')
            put(cx + 1, z, f'{style.trim}_stairs[facing=west,half=bottom,shape=straight]')
        for x in range(x0, x1 + 1):
            put(x, z0, 'bookshelf')
        put(cx, z0 + 1, 'lectern[facing=south]')
        for z in range(z0 + 1, z1, 3):
            put(x0, z, 'red_wall_banner[facing=east]', y + 1)
            put(x1, z, 'red_wall_banner[facing=west]', y + 1)
    elif kind == 'maps':
        put(cx, cz, 'cartography_table')
        put(x0, z0, 'lectern[facing=south]')
        for x in range(x0, x1 + 1):
            put(x, z1, 'bookshelf')
        put(x1, z0, 'chest[facing=west]')
    elif kind == 'chapel':
        for z in range(z0 + 2, z1 - 1, 2):
            for x in range(x0, x1 + 1):
                if x != cx:
                    put(x, z, f'{style.trim}_stairs[facing=south,half=bottom,shape=straight]')
        put(cx, z0, 'lectern[facing=south]')
        put(cx - 1, z0, 'white_candle[candles=3,lit=true]')
        put(cx + 1, z0, 'white_candle[candles=3,lit=true]')


# ---------------------------------------------------------------- the whole building

def building(g, x0, z0, x1, z1, y, style, storeys=2, ground='stone', jetty=1, axis='x', door_side='south', door_at=None,
             chimney_at='west', dormers=True, kind='home', seed=0, bay=3, double_door=False, roof_rise=2, overhang=1):
    """A complete building on the footprint (x0..x1, z0..z1), floor level y. The ridge runs along
    `axis`; upper storeys jut out `jetty` on the two eave sides. Returns a dict with the door cell,
    the ground room and the ridge height."""
    footing(g, x0, z0, x1, z1, y, 3, style.found, seed)
    plinth(g, x0, z0, x1, z1, y - 1, 'stone_brick')
    eaves = ('north', 'south') if axis == 'x' else ('west', 'east')
    # Ground storey.
    if ground == 'stone':
        stone_storey(g, x0, z0, x1, z1, y, style, seed)
    else:
        timber_storey(g, x0, z0, x1, z1, y, style, bay, seed)
    g.fill(x0 + 1, y, z0 + 1, x1 - 1, y + 2, z1 - 1, 'air')
    floor(g, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y - 1, style.floor)
    # The door.
    if door_at is None:
        door_at = (x0 + x1) // 2 if door_side in ('north', 'south') else (z0 + z1) // 2
    if door_side in ('north', 'south'):
        dxz = (door_at, z0 if door_side == 'north' else z1)
    else:
        dxz = (x0 if door_side == 'west' else x1, door_at)
    skip = {dxz, (dxz[0] + (1 if door_side in ('north', 'south') else 0), dxz[1] + (0 if door_side in ('north', 'south') else 1))}
    windows(g, x0, z0, x1, z1, y + 1 if ground == 'stone' else y + 1, style, ('north', 'south', 'east', 'west'), bay, seed, skip=skip)
    door(g, dxz[0], y, dxz[1], door_side, style, double=double_door)
    rooms = [(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y)]
    # Upper storeys, each jutting out over the eaves.
    ux0, uz0, ux1, uz1 = x0, z0, x1, z1
    yy = y + 4
    for s in range(1, storeys):
        if jetty:
            if axis == 'x':
                uz0, uz1 = uz0 - jetty, uz1 + jetty
            else:
                ux0, ux1 = ux0 - jetty, ux1 + jetty
            corbels(g, ux0, uz0, ux1, uz1, yy - 1, eaves, style, bay)
        floor(g, ux0, uz0, ux1, uz1, yy - 1, style.floor)
        _beams(g, ux0, uz0, ux1, uz1, yy - 1, style)
        timber_storey(g, ux0, uz0, ux1, uz1, yy, style, bay, seed + s)
        g.fill(ux0 + 1, yy, uz0 + 1, ux1 - 1, yy + 2, uz1 - 1, 'air')
        windows(g, ux0, uz0, ux1, uz1, yy + 1, style, ('north', 'south', 'east', 'west'), bay, seed + s)
        rooms.append((ux0 + 1, uz0 + 1, ux1 - 1, uz1 - 1, yy))
        yy += 4
    # Ceiling joists in the ground room, a ladder up.
    if storeys > 1:
        ceiling_beams(g, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 3, style.beam, 'z' if axis == 'x' else 'x')
        lx, lz = (x1 - 1, z0 + 1) if door_side != 'east' else (x0 + 1, z0 + 1)
        ladder(g, lx, y, yy - 5, lz, 'south')
        for k in range(1, storeys):
            g.set(lx, y + 4 * k - 1, lz, f'ladder[facing=south]')
    # The roof, its ends, a chimney and dormers.
    top = roof(g, ux0, uz0, ux1, uz1, yy, style, axis, overhang, roof_rise, gable_fill=style.plaster, seed=seed)
    if chimney_at:
        if axis == 'x':
            cx = (x0 + 1) if chimney_at == 'west' else (x1 - 1)
            cz = (z0 + z1) // 2 + 1
        else:
            cz = (z0 + 1) if chimney_at == 'north' else (z1 - 1)
            cx = (x0 + x1) // 2 + 1
        chimney(g, cx, cz, y, top + 1)
    if dormers and storeys >= 1 and (x1 - x0 if axis == 'x' else z1 - z0) >= 8:
        length = (ux1 - ux0) if axis == 'x' else (uz1 - uz0)
        # On a roof that rises `roof_rise` per step, the slope is two steps in by a window's height.
        inset = 1 + max(1, 3 // max(1, roof_rise))
        for i in range(3, length - 2, 6):
            for face in eaves:
                if axis == 'x':
                    dz = uz0 + inset - 1 if face == 'north' else uz1 - inset + 1
                    dormer(g, ux0 + i, yy + 1, dz, face, style, seed)
                else:
                    dxx = ux0 + inset - 1 if face == 'west' else ux1 - inset + 1
                    dormer(g, dxx, yy + 1, uz0 + i, face, style, seed)
    for i, room in enumerate(rooms):
        rx0, rz0, rx1, rz1, ry = room
        furnish(g, kind if i == 0 else 'home', rx0, rz0, rx1, rz1, ry, style, seed + i, door=(dxz[0], dxz[1], door_side) if i == 0 else None)
    return {'door': dxz, 'rooms': rooms, 'top': top}


# ---------------------------------------------------------------- towers and walls

def cone_roof(g, cx, cz, y, r, mat):
    """A pointed roof over a round tower: rings of stairs facing out, one ring per course, each a
    little narrower, flaring at the eaves and finished with a spike."""
    full = {'deepslate_tile': 'deepslate_tiles', 'brick': 'bricks', 'mud_brick': 'mud_bricks', 'stone_brick': 'stone_bricks'}.get(mat, f'{mat}_planks')
    k = 0
    while True:
        rr = r + 0.5 - k * 0.55
        if rr < 0.7:
            g.set(cx, y + k, cz, full)
            g.set(cx, y + k + 1, cz, 'lightning_rod[facing=up]')
            break
        for x in range(int(cx - rr - 1), int(cx + rr + 2)):
            for z in range(int(cz - rr - 1), int(cz + rr + 2)):
                d = math.hypot(x - cx, z - cz)
                if d > rr:
                    continue
                if d > rr - 1.0:
                    a = math.atan2(z - cz, x - cx)
                    face = 'west' if -math.pi / 4 <= a < math.pi / 4 else 'north' if math.pi / 4 <= a < 3 * math.pi / 4 else 'south' if -3 * math.pi / 4 <= a < -math.pi / 4 else 'east'
                    kit.stairs(g, x, y + k, z, mat, face)
                else:
                    g.set(x, y + k, z, full)
        k += 1
    return y + k


def round_tower(g, cx, cz, y, r, h, wall_pal, roof_mat='deepslate_tile', seed=0, door_face=None, cone=True):
    """A round stone tower: weathered walls, arrow slits, a crenellated top or a conical roof."""
    for yy in range(y - 3, y + h):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = math.hypot(x - cx, z - cz)
                if d <= r + 0.35:
                    if d >= r - 0.65 or yy < y:
                        t = 0.2 + 0.5 * max(0.0, 1 - (yy - y + 3) / 5.0)
                        g.set(x, yy, z, pick(wall_pal, x, yy, z, seed, t))
                    else:
                        g.set(x, yy, z, 'air')
    # Slits.
    for yy in range(y + 3, y + h - 2, 4):
        for a in range(0, 360, 90):
            x = cx + int(round(math.cos(math.radians(a + 45)) * r))
            z = cz + int(round(math.sin(math.radians(a + 45)) * r))
            g.set(x, yy, z, 'iron_bars')
            g.set(x, yy + 1, z, 'iron_bars')
    # Floors inside with a ladder.
    for yy in range(y - 1, y + h, 5):
        g.disc(cx, cz, yy, r - 1, 'spruce_planks')
    for yy in range(y, y + h):
        g.set(cx + r - 1, yy, cz, 'ladder[facing=west]')
    if cone:
        cone_roof(g, cx, cz, y + h, r + 1, roof_mat)
    else:
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = math.hypot(x - cx, z - cz)
                if r - 0.65 <= d <= r + 0.35 and (x + z) % 2 == 0:
                    g.set(x, y + h, z, pick(wall_pal, x, y + h, z, seed, 0.3))
    if door_face:
        dx, dz = FACE[door_face]
        x, z = cx + dx * r, cz + dz * r
        g.set(x, y, z, 'air')
        g.set(x, y + 1, z, 'air')
        kit.stairs(g, x, y + 2, z, 'stone_brick', OPP[door_face], 'top')


def curtain(g, pts, y, h, thick, pal, seed=0, walk=True):
    """A wall along the polyline `pts` (closed), `thick` deep, with a walk and crenellations."""
    n = len(pts)
    for i in range(n):
        (ax, az), (bx, bz) = pts[i], pts[(i + 1) % n]
        steps = int(max(abs(bx - ax), abs(bz - az))) * 2 + 1
        for s in range(steps + 1):
            t = s / steps
            x, z = ax + (bx - ax) * t, az + (bz - az) * t
            nx, nz = -(bz - az), (bx - ax)
            ln = math.hypot(nx, nz) or 1
            nx, nz = nx / ln, nz / ln
            for k in range(thick):
                px, pz = int(round(x + nx * (k - thick // 2))), int(round(z + nz * (k - thick // 2)))
                for yy in range(y - 3, y + h):
                    tt = 0.15 + 0.5 * max(0.0, 1 - (yy - y + 3) / 6.0)
                    g.set(px, yy, pz, pick(pal, px, yy, pz, seed, tt))
                if walk and 0 < k < thick - 1:
                    g.set(px, y + h - 1, pz, 'stone_bricks')
            # Crenels on the outer edge.
            ox, oz = int(round(x + nx * (thick - 1 - thick // 2))), int(round(z + nz * (thick - 1 - thick // 2)))
            if (int(round(x)) + int(round(z))) % 2 == 0:
                g.set(ox, y + h, oz, pick(pal, ox, y + h, oz, seed, 0.25))
                g.set(ox, y + h + 1, oz, 'stone_brick_slab[type=bottom]')
            ix, iz = int(round(x - nx * (thick // 2))), int(round(z - nz * (thick // 2)))
            g.set(ix, y + h, iz, 'stone_brick_wall')


# ---------------------------------------------------------------- the street

def lamp_post(g, x, y, z, mat='dark_oak_fence', lantern='lantern'):
    for k in range(3):
        g.set(x, y + k, z, mat)
    g.set(x, y + 3, z, f'{lantern}[hanging=false]')


def stall(g, x, y, z, facing, colour, seed=0, goods=None):
    """A market stall: posts, a striped awning, a counter of barrels and goods on display."""
    dx, dz = FACE[facing]
    along = (1, 0) if dx == 0 else (0, 1)
    for i in range(-2, 3):
        ax, az = x + along[0] * i, z + along[1] * i
        for back in range(0, 3):
            bx, bz = ax - dx * back, az - dz * back
            g.set(bx, y + 3 + (1 if back == 2 else 0), bz, f'{colour if (i % 2 == 0) else "white"}_wool')
    for i in (-2, 2):
        for back in (0, 2):
            px, pz = x + along[0] * i - dx * back, z + along[1] * i - dz * back
            for k in range(3 + (1 if back == 2 else 0)):
                g.set(px, y + k, pz, 'spruce_fence')
    goods = goods or ['melon', 'pumpkin', 'hay_block[axis=y]', 'decorated_pot[facing=north]', 'barrel[facing=up]']
    for i in range(-1, 2):
        cx, cz = x + along[0] * i, z + along[1] * i
        g.set(cx, y, cz, 'barrel[facing=up]' if (i + seed) % 2 else 'spruce_planks')
        g.set(cx, y + 1, cz, goods[int(hash3(cx, y, cz, seed) * len(goods)) % len(goods)])
    g.set(x - dx * 2, y, z - dz * 2, 'barrel[facing=up]')


def tree(g, x, y, z, kind='oak', h=6, r=3, seed=0):
    """A rounded broadleaf tree with a few branches (not the vanilla lollipop)."""
    log = f'{kind}_log'
    for k in range(h):
        g.set(x, y + k, z, f'{log}[axis=y]')
    for (bx, bz, by) in ((1, 0, h - 2), (-1, 1, h - 3), (0, -1, h - 1)):
        g.set(x + bx, y + by, z + bz, f'{log}[axis={"x" if bx else "z"}]')
    for xx in range(x - r - 1, x + r + 2):
        for zz in range(z - r - 1, z + r + 2):
            for yy in range(y + h - 3, y + h + r):
                d = math.sqrt((xx - x) ** 2 + ((yy - (y + h)) * 1.4) ** 2 + (zz - z) ** 2)
                if d <= r + 0.5 and hash3(xx, yy, zz, seed) < 0.92 - 0.3 * max(0.0, d - r + 0.5):
                    if g.get(xx, yy, zz) in (None, 'minecraft:air'):
                        g.set(xx, yy, zz, f'{kind}_leaves[persistent=true,distance=1,waterlogged=false]')
