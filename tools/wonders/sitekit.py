"""Parts for Afterworld's biome sites: palettes per land, and the pieces sites are made of
(ruined walls, towers, tents, yurts, wagons, crypts and stairs down, braziers, standing stones,
rock mounds, bones, trees), plus the markers that bring a site to life (creatures, elites,
clues with words of their own, seal riddles, keepers).

Sites stand on the land as it is: cells left empty keep the world's own ground, so a fen site
sits on mud and a dune site on sand. Layer G is the first layer above the ground line.
"""
import math
import numpy as np
from vox import Grid, hash3, vnoise
import kit

FACE = kit.FACE
OPP = kit.OPP
CW = kit.CW


class P(dict):
    """A palette: what a land builds with. Missing keys fall back to stone."""
    def __getattr__(self, k):
        return self[k]


def palette(**kw):
    base = dict(block='stone_bricks', rough='cobblestone', accent='chiseled_stone_bricks', stair='stone_brick', slab='stone_brick',
                wall='stone_brick_wall', floor='stone_bricks', wood='spruce', log='spruce_log', cloth='white', weather={},
                rubble=['cobblestone', 'stone_brick_slab[type=bottom]', 'stone_brick_stairs[facing=north,half=bottom,shape=straight]'])
    base.update(kw)
    return P(base)


PAL = {
    'stone': palette(weather={'stone_bricks': ['cracked_stone_bricks', 'mossy_stone_bricks']}),
    'mossy': palette(block='mossy_stone_bricks', rough='mossy_cobblestone', stair='mossy_stone_brick', slab='mossy_stone_brick',
                     wall='mossy_stone_brick_wall', floor='mossy_stone_bricks', wood='dark_oak', log='dark_oak_log', cloth='green',
                     weather={'mossy_stone_bricks': ['cracked_stone_bricks', 'stone_bricks', 'moss_block']},
                     rubble=['mossy_cobblestone', 'mossy_stone_brick_slab[type=bottom]', 'moss_carpet']),
    'sand': palette(block='cut_sandstone', rough='sandstone', accent='chiseled_sandstone', stair='sandstone', slab='cut_sandstone',
                    wall='sandstone_wall', floor='smooth_sandstone', wood='acacia', log='acacia_log', cloth='orange',
                    weather={'cut_sandstone': ['sandstone', 'smooth_sandstone']},
                    rubble=['sandstone', 'sandstone_slab[type=bottom]', 'sand']),
    'red': palette(block='cut_red_sandstone', rough='red_sandstone', accent='chiseled_red_sandstone', stair='red_sandstone', slab='cut_red_sandstone',
                   wall='red_sandstone_wall', floor='smooth_red_sandstone', wood='spruce', log='spruce_log', cloth='orange',
                   weather={'cut_red_sandstone': ['red_sandstone', 'smooth_red_sandstone']},
                   rubble=['red_sandstone', 'red_sandstone_slab[type=bottom]', 'red_sand']),
    'black': palette(block='polished_blackstone_bricks', rough='blackstone', accent='chiseled_polished_blackstone', stair='polished_blackstone_brick',
                     slab='polished_blackstone_brick', wall='polished_blackstone_brick_wall', floor='polished_blackstone', wood='dark_oak',
                     log='dark_oak_log', cloth='black', weather={'polished_blackstone_bricks': ['cracked_polished_blackstone_bricks', 'blackstone']},
                     rubble=['blackstone', 'polished_blackstone_brick_slab[type=bottom]', 'basalt[axis=y]']),
    'deep': palette(block='deepslate_bricks', rough='cobbled_deepslate', accent='chiseled_deepslate', stair='deepslate_brick', slab='deepslate_brick',
                    wall='deepslate_brick_wall', floor='deepslate_tiles', wood='dark_oak', log='dark_oak_log', cloth='gray',
                    weather={'deepslate_bricks': ['cracked_deepslate_bricks', 'cobbled_deepslate']},
                    rubble=['cobbled_deepslate', 'deepslate_brick_slab[type=bottom]', 'tuff']),
    'pale': palette(block='polished_diorite', rough='calcite', accent='chiseled_quartz_block', stair='polished_diorite', slab='polished_diorite',
                    wall='diorite_wall', floor='calcite', wood='pale_oak', log='pale_oak_log', cloth='white',
                    weather={'polished_diorite': ['diorite', 'calcite']},
                    rubble=['calcite', 'polished_diorite_slab[type=bottom]', 'bone_block[axis=y]']),
    'tuff': palette(block='tuff_bricks', rough='tuff', accent='chiseled_tuff_bricks', stair='tuff_brick', slab='tuff_brick', wall='tuff_brick_wall',
                    floor='polished_tuff', weather={'tuff_bricks': ['tuff', 'polished_tuff']}, rubble=['tuff', 'tuff_brick_slab[type=bottom]', 'cobblestone']),
    'mud': palette(block='mud_bricks', rough='packed_mud', accent='chiseled_stone_bricks', stair='mud_brick', slab='mud_brick', wall='mud_brick_wall',
                   floor='packed_mud', wood='mangrove', log='mangrove_log', cloth='brown', weather={'mud_bricks': ['packed_mud']},
                   rubble=['packed_mud', 'mud_brick_slab[type=bottom]', 'mud']),
    'frost': palette(block='stone_bricks', rough='packed_ice', accent='blue_ice', floor='polished_andesite', wood='spruce', log='spruce_log', cloth='light_blue',
                     weather={'stone_bricks': ['cracked_stone_bricks', 'packed_ice']},
                     rubble=['packed_ice', 'stone_brick_slab[type=bottom]', 'snow_block']),
    'prism': palette(block='prismarine_bricks', rough='prismarine', accent='dark_prismarine', stair='prismarine_brick', slab='prismarine_brick',
                     wall='prismarine_wall', floor='dark_prismarine', cloth='cyan', weather={'prismarine_bricks': ['prismarine']},
                     rubble=['prismarine', 'prismarine_brick_slab[type=bottom]', 'packed_ice']),
    'andesite': palette(block='polished_andesite', rough='andesite', accent='chiseled_stone_bricks', stair='polished_andesite', slab='polished_andesite',
                        wall='andesite_wall', floor='polished_andesite', wood='spruce', log='spruce_log', cloth='red',
                        weather={'polished_andesite': ['andesite', 'stone']}, rubble=['andesite', 'polished_andesite_slab[type=bottom]', 'gravel']),
    'brick': palette(block='bricks', rough='terracotta', accent='chiseled_stone_bricks', stair='brick', slab='brick', wall='brick_wall',
                     floor='bricks', wood='dark_oak', log='dark_oak_log', weather={'bricks': ['terracotta', 'brown_terracotta']},
                     rubble=['bricks', 'brick_slab[type=bottom]', 'terracotta']),
}


def grid(w=32, h=32, d=32):
    return Grid(w, h, d)


def empty(g, x, y, z):
    """True where nothing has been placed (the world's own air or ground stays)."""
    s = g.get(x, y, z)
    return s is None or s == 'minecraft:air'


# ---------------------------------------------------------------- markers

def mob(g, x, y, z, entity, n=1, clear=True):
    entity = entity if ':' in entity else 'afterworld:' + entity
    if clear:
        g.set(x, y, z, 'air')
        g.set(x, y + 1, z, 'air')
    g.mark(x, y, z, f'mob:{entity}:{n}')


def elite(g, x, y, z, entity):
    entity = entity if ':' in entity else 'afterworld:' + entity
    g.set(x, y, z, 'air')
    g.set(x, y + 1, z, 'air')
    g.mark(x, y, z, f'elite:{entity}')


def clue(g, x, y, z, kind='note', lore=None):
    g.set(x, y, z, f'afterworld:clue[kind={kind}]')
    if lore:
        g.mark(x, y, z, f'lore:{lore}')


def seal(g, x, y, z, kind, hint, inputs, gates):
    """A seal stone at (x, y, z). inputs and gates are grid positions; the marker stores them as
    offsets from the seal, and the game turns them with the site."""
    g.set(x, y, z, 'afterworld:seal[solved=false]')
    rel = lambda pts: '/'.join(f'{px - x},{py - y},{pz - z}' for (px, py, pz) in pts)
    g.mark(x, y, z, f'seal:{kind}:{hint}:{rel(inputs)}:{rel(gates)}')


def boss(g, x, y, z, entity, dormant=False):
    """A Warden keeps this lair; a dormant one sleeps until a seal nearby is solved."""
    g.set(x, y, z, 'air')
    g.set(x, y + 1, z, 'air')
    g.mark(x, y, z, f'boss:afterworld:{entity}' + (':dormant' if dormant else ''))


def npc(g, x, y, z, who, facing):
    g.set(x, y, z, 'air')
    g.set(x, y + 1, z, 'air')
    g.mark(x, y, z, f'npc:{who}:{facing}')


# ---------------------------------------------------------------- small pieces

def campfire(g, x, y, z, lit=True, soul=False, facing='north'):
    g.set(x, y, z, f'{"soul_campfire" if soul else "campfire"}[lit={"true" if lit else "false"},facing={facing},signal_fire=false,waterlogged=false]')


def candles(g, x, y, z, n=1, lit=True, color=''):
    g.set(x, y, z, f'{color + "_" if color else ""}candle[candles={n},lit={"true" if lit else "false"},waterlogged=false]')


def brazier(g, x, y, z, P, lit=False, soul=False, h=1):
    """A short pillar with a campfire bowl on top. Returns where the fire sits."""
    for k in range(h):
        g.set(x, y + k, z, P.wall if k < h - 1 else P.block)
    campfire(g, x, y + h, z, lit, soul)
    return (x, y + h, z)


def barrels(g, x, y, z, seed=0, n=3):
    for i in range(n):
        dx, dz = [(0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)][i % 5]
        r = hash3(x + dx, y, z + dz, seed)
        g.set(x + dx, y, z + dz, 'barrel[facing=up,open=false]' if r < 0.5 else ('hay_block[axis=y]' if r < 0.7 else 'barrel[facing=north,open=false]'))
        if r < 0.25:
            g.set(x + dx, y + 1, z + dz, 'barrel[facing=up,open=false]')


def pots(g, pts):
    for (x, y, z) in pts:
        g.set(x, y, z, 'decorated_pot[facing=north,cracked=false,waterlogged=false]')


def skull(g, x, y, z, rot=0, wither=False):
    g.set(x, y, z, f'{"wither_skeleton" if wither else "skeleton"}_skull[rotation={rot % 16}]')


def ladder(g, x, z, y0, y1, facing):
    for y in range(y0, y1 + 1):
        g.set(x, y, z, f'ladder[facing={facing},waterlogged=false]')


def banner(g, x, y, z, color, facing):
    """A banner hung on a wall face (facing points away from the wall)."""
    g.set(x, y, z, f'{color}_wall_banner[facing={facing}]')


def lantern_post(g, x, y, z, P, h=3, soul=False):
    for k in range(h):
        g.set(x, y + k, z, f'{P.wood}_fence')
    kit.lantern(g, x, y + h, z, soul=soul)


# ---------------------------------------------------------------- walls and rooms

def ruin_wall(g, x0, z0, x1, z1, y0, h, mat, seed=0, decay=0.55, top=None, holes=0.06):
    """A straight wall (x0..x1 or z0..z1) whose top is broken by noise; some holes near the base.
    `top` caps standing heights with a slab/stair state where the wall is intact."""
    xs = range(min(x0, x1), max(x0, x1) + 1)
    zs = range(min(z0, z1), max(z0, z1) + 1)
    for x in xs:
        for z in zs:
            n = float(vnoise(x, 0, z, 3.5, seed))
            hh = max(0, int(round(h * (1.0 - decay * n * 1.3))))
            for y in range(y0, y0 + hh):
                if y > y0 and hash3(x, y, z, seed + 7) < holes:
                    continue
                g.set(x, y, z, mat)
            if top and hh >= h:
                g.set(x, y0 + hh, z, top)


def room(g, x0, z0, x1, z1, y0, h, P, floor=True, door=None, decay=0.0, seed=0, windows=False):
    """Four walls (ruined if decay > 0) on a floor. door: (x, z) to leave open (2 high)."""
    if floor:
        g.fill(x0, y0 - 1, z0, x1, y0 - 1, z1, P.floor)
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z1, x1, z1), (x0, z0, x0, z1), (x1, z0, x1, z1)):
        if decay > 0:
            ruin_wall(g, a, b, c, d, y0, h, P.block, seed=seed, decay=decay)
        else:
            g.fill(a, y0, b, c, y0 + h - 1, d, P.block)
    if door:
        dx, dz = door
        g.fill(dx, y0, dz, dx, y0 + 1, dz, 'air')
    if windows:
        for x in range(x0 + 2, x1 - 1, 3):
            for z in (z0, z1):
                if g.get(x, y0 + 2, z):
                    g.set(x, y0 + 2, z, 'air')
        for z in range(z0 + 2, z1 - 1, 3):
            for x in (x0, x1):
                if g.get(x, y0 + 2, z):
                    g.set(x, y0 + 2, z, 'air')


def floor(g, x0, z0, x1, z1, y, mat, rate=1.0, seed=0):
    """Paving at layer y (usually G - 1, replacing the ground); rate < 1 leaves gaps of ground."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if rate >= 1.0 or hash3(x, y, z, seed) < rate:
                g.set(x, y, z, mat)


def path(g, pts, y, mat, width=1, rate=0.8, seed=0):
    for (a, b) in zip(pts, pts[1:]):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
        for i in range(n):
            t = i / max(1, n - 1)
            x = int(round(a[0] + (b[0] - a[0]) * t))
            z = int(round(a[1] + (b[1] - a[1]) * t))
            for dx in range(-(width // 2), width - width // 2):
                for dz in range(-(width // 2), width - width // 2):
                    if hash3(x + dx, y, z + dz, seed) < rate:
                        g.set(x + dx, y, z + dz, mat)


def debris(g, x0, z0, x1, z1, y, choices, rate, seed=5):
    """Rubble, plants or bones on whatever lies at layer y - 1, where nothing stands yet."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not empty(g, x, y, z):
                continue
            below = g.get(x, y - 1, z)
            if below is not None and not g.is_solid(x, y - 1, z):
                continue
            if below is not None and any(k in below for k in ('slab', 'stairs', 'fence', 'wall', 'carpet', 'pane', 'candle', 'campfire', 'lantern')):
                continue
            r = hash3(x, y, z, seed)
            if r < rate:
                g.set(x, y, z, choices[int(r / rate * len(choices)) % len(choices)])


def top_scatter(g, x0, z0, x1, z1, choices, rate, seed=5, on=None):
    """Plants on the top of whatever was built (a mound's grass, a roof's moss): in each column,
    above the highest placed block, if its kind is in `on` (any kind when on is None)."""
    for x in range(max(0, x0), min(g.sx, x1 + 1)):
        for z in range(max(0, z0), min(g.sz, z1 + 1)):
            col = g.a[x, :, z]
            ys = np.nonzero(col)[0]
            if len(ys) == 0:
                continue
            y = int(ys.max())
            st = g.get(x, y, z)
            if st is None or y + 1 >= g.sy or not g.is_solid(x, y, z):
                continue
            if on is not None and st.split('[')[0].replace('minecraft:', '') not in on:
                continue
            r = hash3(x, y, z, seed)
            if r < rate:
                g.set(x, y + 1, z, choices[int(r / rate * len(choices)) % len(choices)])


def button(g, x, y, z, facing, mat='stone'):
    """A button on a wall face; facing points away from the block it is stuck to."""
    g.set(x, y, z, f'{mat}_button[face=wall,facing={facing},powered=false]')


def cluster(g, x, y, z, facing='up', kind='amethyst_cluster'):
    g.set(x, y, z, f'{kind}[facing={facing},waterlogged=false]')


# ---------------------------------------------------------------- towers and stairs

def round_tower(g, cx, cz, y0, h, r, P, floors=(), door='south', decay=0.0, seed=0, crenel=True, stair=True, inner='air'):
    """A hollow round tower with wooden floors at the given heights, a spiral stair against the
    inside wall, a door and arrow slits. decay breaks the top away."""
    X, Z = g.xz()
    d = np.hypot(X - cx, Z - cz)
    ring = (d <= r + 0.35) & (d > r - 0.85)
    inside = d <= r - 0.85
    for y in range(y0, y0 + h):
        g.a[:, y, :][ring] = g.pid(P.block)
        if inner:
            g.a[:, y, :][inside] = g.pid(inner)
    g.a[:, y0 - 1, :][d <= r + 0.35] = g.pid(P.floor)
    for fy in floors:
        g.a[:, fy, :][inside] = g.pid(f'{P.wood}_planks')
    if crenel and decay == 0:
        for y in (y0 + h,):
            for (x, z) in np.argwhere(ring):
                if (x + z) % 2 == 0:
                    g.set(x, y, z, P.block)
    dx, dz = FACE[door]
    g.fill(cx + dx * r, y0, cz + dz * r, cx + dx * r, y0 + 1, cz + dz * r, 'air')
    # Slits.
    for i, fy in enumerate((y0,) + tuple(floors)):
        for face in ('north', 'east', 'south', 'west'):
            if face == door and fy == y0:
                continue
            fx, fz = FACE[face]
            if hash3(i, fy, len(face), seed) < 0.7:
                g.set(cx + fx * r, fy + 2, cz + fz * r, 'air')
    if stair:
        spiral(g, cx, cz, y0, y0 + h - 1, r - 1, P, floors)
    if decay > 0:
        erode(g, cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, y0 + int(h * (1 - decay)), y0 + h + 1, seed, 3.0)


def spiral(g, cx, cz, y0, y1, r, P, floors=()):
    """Stairs climbing around the inside of a round wall, cutting a hole in each floor they pass."""
    ring = []
    for a in range(0, 360, 4):
        t = math.radians(a)
        p = (int(round(cx + math.cos(t) * r)), int(round(cz + math.sin(t) * r)))
        if not ring or ring[-1] != p:
            ring.append(p)
    ring = list(dict.fromkeys(ring))
    y = y0
    i = 0
    while y < y1:
        x, z = ring[i % len(ring)]
        nx, nz = ring[(i + 1) % len(ring)]
        ddx, ddz = nx - x, nz - z
        facing = 'east' if ddx > 0 and abs(ddx) >= abs(ddz) else 'west' if ddx < 0 and abs(ddx) >= abs(ddz) else 'south' if ddz > 0 else 'north'
        g.set(x, y, z, f'{P.stair}_stairs[facing={facing},half=bottom,shape=straight]')
        for k in (1, 2, 3):
            if y + k <= y1 + 1:
                g.set(x, y + k, z, 'air')
        y += 1
        i += 1


def erode(g, x0, z0, x1, z1, y0, y1, seed=0, scale=3.0):
    """Knocks the top of a build away: above a noisy height between y0 and y1, cells become air."""
    for x in range(max(0, x0), min(g.sx, x1 + 1)):
        for z in range(max(0, z0), min(g.sz, z1 + 1)):
            n = float(vnoise(x, 0, z, scale, seed))
            cut = int(y0 + (y1 - y0) * n)
            for y in range(cut, min(g.sy, y1 + 1)):
                if g.get(x, y, z) is not None:
                    g.set(x, y, z, 'air')


def stair_down(g, x, z, y_top, depth, facing, P, width=3, ceiling=True):
    """A straight stairway down from layer y_top, `depth` steps deep, heading `facing`. Walls and a
    ceiling of P.block; returns the (x, y, z) of the landing at the bottom."""
    dx, dz = FACE[facing]
    ax, az = (1, 0) if dz != 0 else (0, 1)
    half = width // 2
    for i in range(depth + 1):
        sx, sz = x + dx * i, z + dz * i
        y = y_top - i
        for w in range(-half - 1, half + 2):
            px, pz = sx + ax * w, sz + az * w
            edge = abs(w) == half + 1
            for k in range(-1, 5):
                if edge:
                    if k <= 3 and y + k < y_top + 1:
                        g.set(px, y + k, pz, P.block)
                elif k == -1:
                    g.set(px, y + k, pz, P.block)
                elif k == 0:
                    g.set(px, y, pz, f'{P.stair}_stairs[facing={OPP[facing]},half=bottom,shape=straight]' if i < depth else P.floor)
                elif k <= 3:
                    g.set(px, y + k, pz, 'air')
                elif ceiling and y + k <= y_top:
                    g.set(px, y + k, pz, P.block)
    return (x + dx * depth, y_top - depth + 1, z + dz * depth)


def chamber(g, x0, y0, z0, x1, y1, z1, P, inner='air'):
    """A room dug into the ground: walls, floor and ceiling of P, the inside cleared."""
    g.fill(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, P.block)
    g.fill(x0, y0 - 1, z0, x1, y0 - 1, z1, P.floor)
    g.fill(x0, y0, z0, x1, y1, z1, inner)


def sarcophagus(g, x, y, z, axis, P):
    ax, az = (1, 0) if axis == 'x' else (0, 1)
    for i in range(2):
        g.set(x + ax * i, y, z + az * i, P.block)
        g.set(x + ax * i, y + 1, z + az * i, f'{P.slab}_slab[type=bottom]')
    candles(g, x - ax, y, z - az, 2, True)


# ---------------------------------------------------------------- shapes of the land

def mound(g, cx, cz, y0, rx, ry, rz, mats, seed=0, top=None, shell=None):
    """An ellipsoid rock or earth mound rising from layer y0, roughened by noise. mats: [(state, weight)]
    chosen by noise; `top` covers the exposed top; `shell` keeps only a crust of that thickness."""
    for x in range(max(0, int(cx - rx - 2)), min(g.sx, int(cx + rx + 3))):
        for z in range(max(0, int(cz - rz - 2)), min(g.sz, int(cz + rz + 3))):
            for y in range(y0 - 2, min(g.sy, int(y0 + ry + 2))):
                n = float(vnoise(x, y, z, 4.0, seed)) - 0.5
                q = ((x - cx) / rx) ** 2 + (max(0, y - y0) / ry) ** 2 + ((z - cz) / rz) ** 2
                if q <= 1.0 + n * 0.5:
                    if shell is not None:
                        qi = ((x - cx) / max(1, rx - shell)) ** 2 + (max(0, y - y0) / max(1, ry - shell)) ** 2 + ((z - cz) / max(1, rz - shell)) ** 2
                        if qi < 1.0 + n * 0.3 and y >= y0:
                            g.set(x, y, z, 'air')
                            continue
                    r = hash3(x, y, z, seed + 3)
                    acc = 0.0
                    total = sum(w for _, w in mats)
                    for st, w in mats:
                        acc += w / total
                        if r <= acc:
                            g.set(x, y, z, st)
                            break
    if top:
        for x in range(g.sx):
            for z in range(g.sz):
                col = g.a[x, :, z]
                ys = np.nonzero(col)[0]
                if len(ys) == 0:
                    continue
                y = int(ys.max())
                st = g.get(x, y, z)
                if st and any(m[0] in st for m in mats) and y >= y0:
                    g.set(x, y, z, top)


def bowl(g, cx, cz, y_ground, r, depth, lining, seed=0):
    """A crater: earth cleared in a bowl below the ground line, lined with `lining` [(state, w)]."""
    for x in range(max(0, int(cx - r - 2)), min(g.sx, int(cx + r + 3))):
        for z in range(max(0, int(cz - r - 2)), min(g.sz, int(cz + r + 3))):
            d = math.hypot(x - cx, z - cz) / r
            if d > 1.0:
                continue
            bottom = int(round(y_ground - depth * (1 - d * d)))
            for y in range(bottom, y_ground + 1):
                g.set(x, y, z, 'air')
            rr = hash3(x, bottom, z, seed)
            acc, total = 0.0, sum(w for _, w in lining)
            for st, w in lining:
                acc += w / total
                if rr <= acc:
                    g.set(x, bottom - 1, z, st)
                    break


def standing_stone(g, x, z, y0, h, mat, seed=0, lean=0):
    for k in range(h):
        off = int(round(lean * k / max(1, h)))
        g.set(x + off, y0 + k, z, mat)
        if k < h // 2 and hash3(x, k, z, seed) < 0.5:
            g.set(x + 1, y0 + k, z, mat)


def obelisk(g, cx, cz, y0, h, P, cap='gold_block', glyph=None):
    """A 3x3 obelisk tapering to 1x1, with glyph bands and a gilt cap."""
    for y in range(y0, y0 + h):
        r = 1 if y < y0 + h - 3 else 0
        g.fill(cx - r, y, cz - r, cx + r, y, cz + r, P.block)
        if glyph and (y - y0) % 5 == 2 and r == 1:
            for (x, z) in ((cx, cz - 1), (cx, cz + 1), (cx - 1, cz), (cx + 1, cz)):
                g.set(x, y, z, glyph)
    g.set(cx, y0 + h, cz, cap)


def dead_tree(g, x, z, y0, h, log, seed=0, branches=3):
    for k in range(h):
        kit.log(g, x, y0 + k, z, log)
    for b in range(branches):
        t = hash3(x, b, z, seed) * math.pi * 2
        by = y0 + h // 2 + int(hash3(b, x, z, seed + 1) * (h // 2))
        length = 2 + int(hash3(z, b, x, seed + 2) * 2)
        for i in range(1, length + 1):
            bx = int(round(x + math.cos(t) * i))
            bz = int(round(z + math.sin(t) * i))
            axis = 'x' if abs(math.cos(t)) > abs(math.sin(t)) else 'z'
            kit.log(g, bx, by + (i // 2), bz, log, axis)
    return (x, y0 + h, z)


def tree(g, x, z, y0, h, log, leaves, r=3, seed=0):
    for k in range(h):
        kit.log(g, x, y0 + k, z, log)
    for y in range(y0 + h - r, y0 + h + 2):
        rr = r - max(0, y - (y0 + h - 1))
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if dx * dx + dz * dz <= rr * rr + 0.5 and hash3(x + dx, y, z + dz, seed) < 0.88:
                    kit.leaves(g, x + dx, y, z + dz, leaves)


def big_trunk(g, cx, cz, y0, h, r, log, seed=0):
    """A giant's trunk: a thick log cylinder flaring into roots at its foot."""
    for y in range(y0 - 2, y0 + h):
        rr = r + (1.6 if y < y0 + 2 else 0) * (1 - (y - y0 + 2) / 4)
        for x in range(int(cx - rr - 1), int(cx + rr + 2)):
            for z in range(int(cz - rr - 1), int(cz + rr + 2)):
                if math.hypot(x - cx, z - cz) <= rr + 0.3 * float(vnoise(x, y, z, 2.0, seed)):
                    kit.log(g, x, y, z, log)


def canopy(g, cx, cz, y, r, leaves, seed=0, h=4):
    for yy in range(y, y + h):
        rr = r * (1 - ((yy - y) / h) ** 2 * 0.7)
        for x in range(int(cx - rr - 1), int(cx + rr + 2)):
            for z in range(int(cz - rr - 1), int(cz + rr + 2)):
                if math.hypot(x - cx, z - cz) <= rr + float(vnoise(x, yy, z, 3.0, seed)) - 0.5:
                    kit.leaves(g, x, yy, z, leaves)


def rib(g, cx, cz, y0, span, height, axis='x', mat='bone_block'):
    """One arch of a giant ribcage: a parabola of bone across `span`, standing on layer y0."""
    n = span * 3
    for i in range(n + 1):
        t = i / n * 2 - 1
        a = int(round(t * span / 2))
        y = int(round(y0 + height * (1 - t * t)))
        x, z = (cx + a, cz) if axis == 'x' else (cx, cz + a)
        g.set(x, y, z, f'{mat}[axis=y]')
        g.set(x, y - 1, z, f'{mat}[axis=y]') if abs(t) > 0.7 else None


# ---------------------------------------------------------------- tents, yurts, wagons

def tent(g, x0, z0, length, y0, cloth, axis='x', poles='spruce_fence', open_end=True):
    """An A-frame tent, five wide and three high, running `length` along axis."""
    for i in range(length):
        for w, hgt in ((-2, 1), (-1, 2), (0, 3), (1, 2), (2, 1)):
            x, z = (x0 + i, z0 + w) if axis == 'x' else (x0 + w, z0 + i)
            g.set(x, y0 + hgt - 1, z, f'{cloth}_wool')
            for k in range(hgt - 1):
                if abs(w) < 2 and (i in (0, length - 1)) and not open_end:
                    g.set(x, y0 + k, z, f'{cloth}_wool')
    for i in (-1, length):
        x, z = (x0 + i, z0) if axis == 'x' else (x0, z0 + i)
        for k in range(3):
            g.set(x, y0 + k, z, poles)
    floor_x = range(x0, x0 + length) if axis == 'x' else range(x0 - 1, x0 + 2)
    floor_z = range(z0 - 1, z0 + 2) if axis == 'x' else range(z0, z0 + length)
    for x in floor_x:
        for z in floor_z:
            if empty(g, x, y0, z):
                g.set(x, y0, z, f'{cloth}_carpet')


def yurt(g, cx, cz, y0, r, cloth, trim, door='south', wood='acacia'):
    """A round felt tent: a wall of wool on a wooden ring, a cone roof, a smoke hole and a door."""
    X, Z = g.xz()
    d = np.hypot(X - cx, Z - cz)
    wall = (d <= r + 0.35) & (d > r - 0.85)
    for y in range(y0, y0 + 3):
        g.a[:, y, :][wall] = g.pid(f'{cloth}_wool' if y != y0 + 1 else f'{trim}_wool')
    g.a[:, y0 - 1, :][d <= r + 0.35] = g.pid(f'{wood}_planks')
    for i in range(r + 1):
        rr = r + 0.6 - i
        ring = (d <= rr + 0.35) & (d > rr - 0.85)
        g.a[:, y0 + 3 + i, :][ring] = g.pid(f'{cloth}_wool' if i % 2 == 0 else f'{trim}_wool')
    g.set(cx, y0 + 3 + r, cz, 'air')
    dx, dz = FACE[door]
    g.fill(cx + dx * r, y0, cz + dz * r, cx + dx * r, y0 + 1, cz + dz * r, 'air')
    for (fx, fz) in ((cx + dx * r + 1 * (dz != 0), cz + dz * r + 1 * (dx != 0)), (cx + dx * r - 1 * (dz != 0), cz + dz * r - 1 * (dx != 0))):
        for k in range(3):
            g.set(fx, y0 + k, fz, f'{wood}_log[axis=y]')


def wagon(g, x, z, y0, axis, wood='spruce', cover='white', broken=False, tipped=False, seed=0):
    """A covered wagon, 3 wide and 5 long: a plank bed on axles, four wheels (trapdoors), hoops
    and a cloth cover. Broken: a wheel gone, a hoop down. Tipped: lying on its side."""
    ax, az = (1, 0) if axis == 'x' else (0, 1)
    sx, sz = (0, 1) if axis == 'x' else (1, 0)
    side_face = 'north' if axis == 'x' else 'west'
    other_face = 'south' if axis == 'x' else 'east'
    if tipped:
        # On its side: the bed stands up as a wall, the cover spills out flat.
        for i in range(5):
            for k in range(3):
                g.set(x + ax * i + sx * -1, y0 + k, z + az * i + sz * -1, f'{wood}_planks' if k < 2 else f'{wood}_slab[type=bottom]')
            for w in range(0, 3):
                if hash3(i, w, seed, 4) < 0.75:
                    g.set(x + ax * i + sx * w, y0, z + az * i + sz * w, f'{cover}_carpet')
        for i in (0, 4):
            g.set(x + ax * i + sx * -2, y0 + 1, z + az * i + sz * -2, f'{wood}_trapdoor[facing={side_face},half=bottom,open=true]')
        return
    for i in range(5):
        for w in (-1, 0, 1):
            g.set(x + ax * i + sx * w, y0 + 1, z + az * i + sz * w, f'{wood}_slab[type=bottom]' if broken and i == 4 and w == 1 else f'{wood}_planks')
    for i in (0, 4):
        for w, f in ((-2, side_face), (2, other_face)):
            if broken and i == 4 and w == 2:
                continue
            g.set(x + ax * i + sx * w, y0 + 1, z + az * i + sz * w, f'{wood}_trapdoor[facing={f},half=bottom,open=true]')
        g.set(x + ax * i, y0, z + az * i, f'{wood}_fence')
    for i in (0, 2, 4):
        if broken and i == 4:
            continue
        for w in (-1, 1):
            g.set(x + ax * i + sx * w, y0 + 2, z + az * i + sz * w, f'{wood}_fence')
        for w in (-1, 0, 1):
            g.set(x + ax * i + sx * w, y0 + 3, z + az * i + sz * w, f'{cover}_wool')
    for i in (1, 3):
        for w in (-1, 0, 1):
            g.set(x + ax * i + sx * w, y0 + 3, z + az * i + sz * w, f'{cover}_wool')
    # The shaft out front.
    g.set(x - ax, y0 + 1, z - az, f'{wood}_fence')
    g.set(x - 2 * ax, y0 + 1, z - 2 * az, f'{wood}_fence')


def crenellate(g, x0, z0, x1, z1, y, mat):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (x + z) % 2 == 0:
                g.set(x, y, z, mat)


# ---------------------------------------------------------------- finishing

SUPPORT = {'sand': 'sandstone', 'red_sand': 'red_sandstone', 'gravel': 'stone', 'suspicious_sand': 'sandstone', 'suspicious_gravel': 'stone'}
SOFT_GROUND = ('grass_block', 'dirt', 'coarse_dirt', 'moss_block', 'mud', 'rooted_dirt', 'snow_block', 'sand', 'gravel', 'clay')


def settle(g):
    """Placed builds do not stand on thin air: sand and gravel get a footing where nothing was built
    under them (the land may dip lower than the site's ground line), and mushrooms get podzol so
    they survive in daylight."""
    for i, state in enumerate(list(g.palette)):
        name = state.split('[')[0].replace('minecraft:', '')
        if name in SUPPORT:
            for (x, y, z) in np.argwhere(g.a == i):
                for k in range(1, 5):
                    if y - k < 0 or g.a[x, y - k, z] != 0:
                        break
                    g.a[x, y - k, z] = g.pid(SUPPORT[name])
        elif name in ('red_mushroom', 'brown_mushroom'):
            for (x, y, z) in np.argwhere(g.a == i):
                if y == 0:
                    continue
                below = g.get(x, y - 1, z)
                if below is None or below.split('[')[0].replace('minecraft:', '') in SOFT_GROUND:
                    g.set(x, y - 1, z, 'podzol[snowy=false]')


def clear_core(g, G, frac=0.44, height=30, seed=17):
    """Sites are set down after the forest grows, so trees can stand in their courts. This clears
    whatever grew in the site's heart (an oval with a ragged edge) above the ground line, leaving
    the woods around it, and anything the site placed itself, as they are."""
    X, Z = g.xz()
    cx, cz = (g.sx - 1) / 2.0, (g.sz - 1) / 2.0
    d = np.sqrt(((X - cx) / (g.sx * frac)) ** 2 + ((Z - cz) / (g.sz * frac)) ** 2)
    rough = np.vectorize(lambda x, z: float(vnoise(x, 0, z, 4.0, seed)))(X, Z)
    core = d + (rough - 0.5) * 0.25 < 1.0
    air = g.pid('air')
    for y in range(G, min(g.sy, G + height)):
        layer = g.a[:, y, :]
        layer[core & (layer == 0)] = air


def finish(g, P=None, weather_rate=0.22, seed=3):
    if P is not None and P.weather:
        kit.weather(g, P.weather, rate=weather_rate, seed=seed)
    settle(g)
    kit.connect(g)
    return g
